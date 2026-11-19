package com.vaadin.testing.unified.playwright;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.impl.driver.Driver;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * The browser of {@code vaadin.testing.playwright.browser}, and its install. Left alone,
 * {@code Playwright.create()} downloads every browser Playwright supports, some 1.3 GB, though a run opens one:
 * the adapter turns that off and installs only the browser it launches, as Playwright's {@code install} command.
 */
enum BrowserInstall {

    CHROMIUM("chromium", Playwright::chromium),
    FIREFOX("firefox", Playwright::firefox),
    WEBKIT("webkit", Playwright::webkit);

    /** Turns off the download of every browser, read by Playwright's driver; its install command ignores it. */
    static final String SKIP_DOWNLOAD = "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD";

    private static final String REMOTE_URL = "SELENIUM_REMOTE_URL";

    private final String name;
    private final Function<Playwright, BrowserType> type;

    BrowserInstall(String name, Function<Playwright, BrowserType> type) {
        this.name = name;
        this.type = type;
    }

    /** {@code firefox} or {@code webkit}; anything else is Chromium. */
    static BrowserInstall of(String setting) {
        for (BrowserInstall browser : values()) {
            if (browser.name.equals(setting)) {
                return browser;
            }
        }
        return CHROMIUM;
    }

    BrowserType type(Playwright playwright) {
        return type.apply(playwright);
    }

    /**
     * Playwright's install command for this browser. Headless Chromium runs Chromium's headless shell, a
     * separate download, and headed Chromium the full browser: each needs only its own.
     */
    List<String> installArgs(boolean headless) {
        return this == CHROMIUM
                ? List.of("install", name, headless ? "--only-shell" : "--no-shell")
                : List.of("install", name);
    }

    /**
     * Whether to install: not when the environment tells Playwright to skip its download, or to use a Selenium
     * Grid's browser instead (Playwright's own rules).
     */
    static boolean wanted(Map<String, String> environment) {
        String skip = environment.get(SKIP_DOWNLOAD);
        boolean skipped = skip != null && !"0".equals(skip) && !"false".equals(skip);
        return !skipped && environment.get(REMOTE_URL) == null;
    }

    /**
     * Installs this browser, unless the process's environment says not to. Quick when it is installed already.
     * Call after {@code Playwright.create()}, which installs Playwright's driver; the install command runs on it.
     */
    void install(boolean headless) {
        if (!wanted(System.getenv())) {
            return;
        }
        List<String> args = installArgs(headless);
        // the driver of the run's Playwright: created once per JVM, by the first Playwright.create()
        ProcessBuilder install = Driver.ensureDriverInstalled(Map.of(), false).createProcessBuilder();
        install.command().addAll(args);
        install.command().add("--no-progress"); // a line per browser in a CI log, not a progress bar
        install.environment().remove(SKIP_DOWNLOAD);
        // through System.out, not the JVM's own stdout: Surefire captures one and takes the other for its channel
        install.redirectErrorStream(true);
        try {
            Process process = install.start();
            Thread.ofVirtual().start(() -> process.inputReader().lines().forEach(System.out::println));
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroy();
                throw failure(args, "timed out after 10 minutes");
            }
            if (process.exitValue() != 0) {
                throw failure(args, "exit code " + process.exitValue());
            }
        } catch (IOException e) {
            throw failure(args, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failure(args, "interrupted");
        }
    }

    private static IllegalStateException failure(List<String> args, String reason) {
        return new IllegalStateException("Could not install Playwright's " + args.get(1) + " (" + reason
                + "). Install it by hand:\n  mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI"
                + " -Dexec.classpathScope=test -Dexec.args=\"" + String.join(" ", args) + "\"\n"
                + "or point vaadin.testing.playwright.executable at an installed browser.");
    }
}
