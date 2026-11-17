package com.vaadin.testing.unified.internal.steps;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The files changed against a base ref, for "Changed in this PR" in the run summary, and how a scenario's call
 * sites match them.
 * <p>
 * From git: the files that differ between the merge base of the ref and {@code HEAD} and the working tree
 * ({@code git diff --name-status --no-renames <merge-base>}), plus untracked files. In CI the working tree is
 * clean, so that is {@code git diff <ref>...HEAD}; locally, uncommitted changes count too.
 *
 * @param baseRef the ref compared against, as given ({@code origin/main})
 * @param files repository-relative paths with {@code /}, mapped to whether the file was added
 * @param baseContent a path's content at the merge base, if it existed there
 */
public record ChangedFiles(String baseRef, Map<String, Boolean> files, Function<String, Optional<String>> baseContent) {

    private static final long GIT_TIMEOUT_SECONDS = 30;

    /**
     * Asks git in {@code workDir}. Empty, after one line to {@code log}, when git isn't there, {@code workDir}
     * isn't in a repository, or the ref is unknown.
     */
    public static Optional<ChangedFiles> fromGit(String baseRef, Path workDir, Consumer<String> log) {
        Optional<String> mergeBase = git(workDir, "merge-base", baseRef, "HEAD").map(String::strip);
        if (mergeBase.isEmpty() || mergeBase.get().isEmpty()) {
            log.accept("\"Changed in this PR\" left out: git merge-base " + baseRef
                    + " HEAD failed (no git, not a repository, or an unknown ref)");
            return Optional.empty();
        }
        String base = mergeBase.get();
        Optional<String> diff = git(workDir, "diff", "--name-status", "--no-renames", base);
        if (diff.isEmpty()) {
            log.accept("\"Changed in this PR\" left out: git diff against " + baseRef + " failed");
            return Optional.empty();
        }
        Map<String, Boolean> files = new LinkedHashMap<>();
        for (String line : diff.get().lines().toList()) {
            int tab = line.indexOf('\t');
            if (tab > 0) {
                files.put(line.substring(tab + 1).strip(), line.charAt(0) == 'A');
            }
        }
        git(workDir, "ls-files", "--others", "--exclude-standard", "--full-name")
                .ifPresent(out -> out.lines().filter(l -> !l.isBlank()).forEach(l -> files.put(l.strip(), true)));
        return Optional.of(new ChangedFiles(baseRef, files, path -> git(workDir, "show", base + ":" + path)));
    }

    /** Runs git; empty if it can't start, fails or takes too long. */
    private static Optional<String> git(Path workDir, String... args) {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        try {
            Process process = new ProcessBuilder(command).directory(workDir.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            process.getOutputStream().close();
            CompletableFuture<String> out = CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
            if (!process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            return process.exitValue() == 0 ? Optional.of(out.get(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    : Optional.empty();
        } catch (IOException | RuntimeException | java.util.concurrent.ExecutionException
                | java.util.concurrent.TimeoutException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static String read(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    // ---------------------------------------------------------------- matching

    /**
     * The path a class's source file has under its source root: the package as directories, then the file
     * ({@code demo/EditPersonTest.java}). Without a file name (no debug information), the outermost class's
     * name with {@code .java}.
     */
    public static String sourcePath(String className, String fileName) {
        int dot = className.lastIndexOf('.');
        String packagePath = dot < 0 ? "" : className.substring(0, dot).replace('.', '/') + "/";
        String file = fileName;
        if (file == null || file.isBlank()) {
            String outer = className.substring(dot + 1);
            int dollar = outer.indexOf('$');
            file = (dollar > 0 ? outer.substring(0, dollar) : outer) + ".java";
        }
        return packagePath + file;
    }

    /** The changed file whose path ends with the source path of this class and file, if any. */
    public Optional<String> changedFile(String className, String fileName) {
        String suffix = sourcePath(className, fileName);
        return files.keySet().stream().filter(path -> path.equals(suffix) || path.endsWith("/" + suffix)).findFirst();
    }

    /** Whether any entry or step group of the scenario was called from a changed file. */
    public boolean touches(ScenarioResult scenario) {
        return scenario.callSites().stream().anyMatch(site -> {
            String className = ScenarioResult.string(site, "className");
            return className != null && changedFile(className, ScenarioResult.string(site, "file")).isPresent();
        });
    }

    /**
     * Why the scenario is listed: each changed file one of its entries or step groups is called from, by file name,
     * with the methods calling from it, both in the order the scenario first calls them. A file is matched as a
     * whole, so a listed method may itself be unchanged.
     */
    public Map<String, List<String>> callersByFile(ScenarioResult scenario) {
        Map<String, List<String>> callers = new LinkedHashMap<>();
        for (Map<String, Object> site : scenario.callSites()) {
            String className = ScenarioResult.string(site, "className");
            if (className == null) {
                continue;
            }
            changedFile(className, ScenarioResult.string(site, "file")).ifPresent(path -> {
                List<String> methods = callers.computeIfAbsent(path.substring(path.lastIndexOf('/') + 1),
                        k -> new java.util.ArrayList<>());
                String method = ScenarioResult.string(site, "method");
                if (method != null && !methods.contains(method)) {
                    methods.add(method);
                }
            });
        }
        return callers;
    }

    /**
     * Whether the scenario is new: its test class's file was added, or that file on the base has no method of
     * the test method's name ({@code name(}). A plain text check: a method renamed counts as new, a test whose
     * method name also appears elsewhere in the old file does not.
     */
    public boolean isNew(ScenarioResult scenario) {
        String testFile = scenario.callSites().stream()
                .filter(site -> outerClass(scenario.testClass()).equals(outerClass(ScenarioResult.string(site,
                        "className"))))
                .map(site -> ScenarioResult.string(site, "file"))
                .filter(f -> f != null)
                .findFirst().orElse(null);
        Optional<String> path = changedFile(scenario.testClass(), testFile);
        if (path.isEmpty()) {
            return false;
        }
        if (files.get(path.get())) {
            return true;
        }
        Pattern method = Pattern.compile("\\b" + Pattern.quote(scenario.testMethod()) + "\\s*\\(");
        return baseContent.apply(path.get()).map(content -> !method.matcher(content).find()).orElse(true);
    }

    private static String outerClass(String className) {
        if (className == null) {
            return "";
        }
        int dollar = className.indexOf('$');
        return dollar < 0 ? className : className.substring(0, dollar);
    }

    /** For tests: changed files without git. */
    public static ChangedFiles of(String baseRef, List<String> modified, List<String> added,
            Map<String, String> baseContents) {
        Map<String, Boolean> files = new LinkedHashMap<>();
        modified.forEach(f -> files.put(f, false));
        added.forEach(f -> files.put(f, true));
        return new ChangedFiles(baseRef, files, path -> Optional.ofNullable(baseContents.get(path)));
    }
}
