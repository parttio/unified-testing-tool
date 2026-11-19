package com.vaadin.testing.unified.internal.steps;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * The code a run tested, as git names it: what a comparison with another Testing Tool's run checks before it
 * blames a difference on the Testing Tool.
 *
 * @param commit the commit checked out ({@code HEAD}), 40 hex digits
 * @param uncommitted a fingerprint of the tracked files' uncommitted changes ({@code git diff HEAD}), or null when
 *        there are none. Untracked files are not in it
 */
public record Revision(String commit, String uncommitted) {

    /** Asks git in {@code workDir}; empty without git, outside a repository, or before the first commit. */
    public static Optional<Revision> fromGit(Path workDir) {
        Optional<String> head = ChangedFiles.git(workDir, "rev-parse", "HEAD").map(String::strip)
                .filter(h -> h.matches("[0-9a-f]{40,64}"));
        if (head.isEmpty()) {
            return Optional.empty();
        }
        String diff = ChangedFiles.git(workDir, "diff", "HEAD").orElse("");
        return Optional.of(new Revision(head.get(), diff.isBlank() ? null : fingerprint(diff)));
    }

    /** The first 12 hex digits of the text's SHA-256. */
    static String fingerprint(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The commit as people write it: its first seven digits. */
    public String shortCommit() {
        return commit.length() > 7 ? commit.substring(0, 7) : commit;
    }

    /**
     * The run a {@code summary.json} is of: its Testing Tool and its revision.
     *
     * @param tool the Testing Tool, lower case
     * @param revision what it tested, or null when the file names no commit
     */
    public record OfRun(String tool, Revision revision) {
    }

    /** Reads the Testing Tool and the revision from a {@code summary.json}; empty when it isn't one. */
    public static Optional<OfRun> read(String summaryJson) {
        try {
            if (JsonReader.parse(summaryJson) instanceof Map<?, ?> run && run.get("tool") instanceof String tool
                    && run.get("scenarios") != null) {
                Revision revision = run.get("commit") instanceof String commit ? new Revision(commit,
                        run.get("uncommittedChanges") instanceof String changes ? changes : null) : null;
                return Optional.of(new OfRun(tool, revision));
            }
        } catch (RuntimeException e) {
            // not JSON
        }
        return Optional.empty();
    }
}
