# How to count round trips per user action on each Testing Tool

Research for [#300](https://github.com/parttio/unified-testing-tool/issues/300), on the map
[#294](https://github.com/parttio/unified-testing-tool/issues/294) "Performance: show a Unified UI Test is not slower
than the same Direct Test".

**Question:** how can a test run count, per user action, the round trips the Testing Tool makes, for both a Unified UI
Test and its Direct Test, without changing either test? For each tool: how a benchmark harness switches counting on, and
what the count includes and leaves out.

Researched 2026-11-18 against `origin/main` at `6c15e62`. Versions as resolved by `unified-test-demo`: Playwright Java
**1.63.0** (driver bundle 1.63.0), Dramafinder **2.2.0**, Selenium **4.52.0**, TestBench **25.3.2**, Browserless Test
**25.3.2**, JUnit Platform 6 / Surefire 3.5.4, JDK 21. Library facts come from the published `-sources.jar` of each
version on Maven Central, and from `javap` on the jars in `~/.m2`. Playwright's Node driver code was read in
`playwright-core` 1.63.0 (`lib/coreBundle.js`), the same version as the driver bundled in `driver-bundle-1.63.0.jar`.
Repo paths are relative to the repo root. Each Testing Tool's hook was also **run** on the demo tests (see the "Measured"
parts), except Selenium (see [Not checked](#not-checked)).

## Answer in short

One harness shape works for all three Testing Tools. It is a **JUnit Platform `TestExecutionListener` registered
through `META-INF/services`** on the benchmark's test classpath. It switches a counter on at the start of the run.
For each counted call, it takes the **first stack frame in the test class** as the user action (the test's source line).
That frame is in the test for both kinds of test, so neither test changes and both are split by the same rule.

| Testing Tool | Hook to use | How the harness switches it on | What one count is | Confidence |
|---|---|---|---|---|
| **Playwright** (via Dramafinder) | Playwright Java's client-side channel log: `DEBUG=pw:channel` makes `Connection` print every message to the driver, on the calling thread, through `System.err` | Environment variable `DEBUG=pw:channel` on the test JVM (Surefire `environmentVariables`, or exported); the listener wraps `System.err` and counts `SEND ►` lines | One Java-to-driver call (`fill`, `click`, `queryCount`, `evaluateExpression`, `waitForFunction`…) | **High**: source read, and run on the demo |
| | (second layer) the driver's CDP log, `DEBUG=pw:protocol` | Same variable; output is the driver process's stderr | One driver-to-browser CDP command | **High** on what it is; it cannot be split per action, only per run or per test class |
| **Selenium** (TestBench) | `RemoteWebDriver.execute` logs every W3C command at `FINE` on the `java.util.logging` logger `org.openqa.selenium.remote.RemoteWebDriver` | The listener adds a JUL `Handler` to that logger and sets it to `FINE` (or, without code, `-Dselenium.debug=true` and parse stderr) | One HTTP command to the driver server, including each `waitForVaadin` poll | **Medium-high**: source read; not run here |
| | `EventFiringDecorator` / `WebDriverListener` | Only by wrapping the driver before TestBench does. That needs adapter code for the Unified test and a `DriverSupplier` in the Direct Test, so it **changes the Direct Test** | One WebDriver interface call | Works with TestBench by code reading if it wraps the raw driver, but not recommended |
| **Browserless Test** | Browserless Test's own `TestingLifecycleHook` (lookups and tree walks), plus a bytecode agent on `BrowserlessDSL.roundTrip` and `MockVaadin.clientRoundtrip` (server round trips) | The listener calls `TestingLifecycleHookKt.setTestingLifecycleHook(counting)` and installs a ByteBuddy agent (both on the demo's test classpath already) | A lookup, a node expanded in a tree walk, a walk from the UI root, a server round trip | **High**: source read, and run on the demo |

What the counts **leave out**, for all three: the work inside one call. A Playwright `click` is one call even though the
driver retries actionability checks over many CDP commands. A TestBench `waitForVaadin` and the Unified API's
`TestRun.pollFor` are **poll loops**, so their counts depend on timing. The harness should keep "wait" calls apart from
"work" calls and report the median of several runs.

Measured on the demo (one run, Unified vs. Direct, same user action):

| User action | Playwright, Direct (`ProfileDramafinderTest`) | Playwright, Unified (`ProfileMigratedTest`) | Browserless, Direct (`ProfileBrowserlessTest`) | Browserless, Unified (`ProfileBrowserlessMigratedTest`) |
|---|---|---|---|---|
| fill "Name" with "Alice" | 1 call (`fill`) | 9 calls | 1 lookup, 13 nodes walked, 1 round trip | 1 lookup, 2 walks from the UI root, 26 nodes walked, 2 round trips |
| click "Save" | 1 call (`click`) | 7 calls | 1 lookup, 13 nodes, 1 round trip | 1 lookup, 2 walks, 26 nodes, 2 round trips |

---

## The common harness

### Verified facts

| # | Fact | Source |
|---|---|---|
| H1 | A JUnit Platform `TestExecutionListener` listed in `META-INF/services/org.junit.platform.launcher.TestExecutionListener` is loaded by the launcher Surefire runs. Its `executionStarted` / `executionFinished` receive a `TestIdentifier` whose `MethodSource` names the test class and method. | Run: the throwaway listener below was picked up by `mvn test` in `unified-test-demo` with no test or POM change |
| H2 | Surefire runs a test class's methods on the thread that calls the listener (no parallel execution is configured), and every counted call below happens synchronously on that thread. So `StackWalker` at count time sees the test method's frame. | Run (all counts below are attributed to test lines); `Connection.internalSendMessage` → `transport.send` (Playwright), `RemoteWebDriver.execute` → `log` (Selenium), `LocatorKt.find` → hook (Browserless) are plain synchronous calls |
| H3 | The Unified API already has a "first frame outside the API" rule, `CallSite.here()`. It skips the Unified API, its adapters, Wrapper interfaces and proxies. | `unified-test-api/src/main/java/com/vaadin/testing/unified/internal/steps/CallSite.java` L17–30 |
| H4 | A Unified Wrapper is lazy. `UnifiedTextField email = page.find(..).withLabel("Email");` makes no call; the lookup runs on `email.setValue(..)`, the next line. Per-line counts show this: line 32 of `ProfileBrowserlessMigratedTest` has no count, and line 33 has the lookup. | Run (Browserless table below) |

### How to attribute a call to a user action

Use the **first frame whose class is the test class** (or one of its nested or lambda classes), not `CallSite.here()`.
`CallSite` works only for the Unified API. In a Direct Test it would stop at Dramafinder or TestBench element classes,
which are not the test. "First frame in the test class" gives the same unit for both kinds: one test-source line, which
is one user action in these tests. Counts with no test frame are setup and teardown: base-class `@BeforeEach`, browser
launch, context creation. Report them on their own, as "(outside test)".

For a test that uses a page object or helper (`ProfilePageObjectTestBenchTest`, User Tasks), extend the rule to "first
frame in the test's source set". Then a call is attributed to the page-object line, and one level up gives the test line.

---

## 1. Playwright (Java 1.63, through Dramafinder 2.2)

### Verified facts

| # | Fact | Source |
|---|---|---|
| P1 | There is **no public listener** for protocol calls. `Connection` and its `Transport` are in `com.microsoft.playwright.impl`; `Connection` is created in `PlaywrightImpl.createImpl` with a `PipeTransport` to the driver process. | `playwright-1.63.0` `impl/PlaywrightImpl.java` L39–58, `impl/Connection.java` L59–110 |
| P2 | `Connection` reads `DEBUG` **once**, in a static initializer: `isLogging = debug != null && debug.contains("pw:channel")`. When set, it wraps the transport in `TransportLogger`. | `impl/Connection.java` L69–73, L104–106 |
| P3 | `TransportLogger.send` prints `SEND ► <json>` for every message, and `poll` prints `◀ RECV <json>`. It prints with `LoggingSupport.logWithTimestamp`, which is `System.err.println(timestamp + " " + message)`. So the lines go through `System.err` **in the JVM, on the thread that sends**. | `impl/TransportLogger.java` L35–38; `impl/LoggingSupport.java` L33–37 |
| P4 | Each message carries `id`, `guid` (the target object), `method` (`fill`, `click`, `queryCount`, `evaluateExpression`, `waitForFunction`, `goto`…) and `metadata`. After the first message of a custom-titled API call (`withTitle`, e.g. `Locator.clear`, `Browser.newPage`), later messages get `metadata.internal = true`. | `impl/Connection.java` L148–181; `impl/ChannelOwner.java` L88–102; `withTitle` callers: `LocatorImpl.java` L72, L165, `BrowserImpl.java` L195, `PageImpl.java` L1475 |
| P5 | The driver (Node) process is started with stderr **inherited** (`Redirect.INHERIT`), and it inherits the JVM's environment plus `Playwright.CreateOptions.env`. Its debug logger writes CDP traffic as `pw:protocol SEND ► …` / `◀ RECV …` when `DEBUG` includes `pw:protocol`. | `impl/PlaywrightImpl.java` L48–50; `driver-1.63.0` `impl/driver/Driver.java` L70–85; `Playwright.java` `CreateOptions` L44–58 (javadoc: "By default driver process inherits environment variables"); `playwright-core` 1.63.0 `lib/coreBundle.js` L21034–21038 |
| P6 | A trace's action list is written by the driver. It **drops** calls whose metadata is `internal`, and every `tracing*` call (`createBeforeActionTraceEvent` returns null for them). The dispatcher also marks some protocol methods internal on its own (`metainfo.internal`). | `lib/coreBundle.js` L25487–25500 (before), L25524 (after), L19100–19103 |
| P7 | Dramafinder's base class creates Playwright with `Playwright.create()`, no options, in `@BeforeAll`, and opens a page plus the view in `@BeforeEach` (`page.navigate(..)`, `page.waitForFunction(WAIT_FOR_VAADIN_SCRIPT)`). It has no tracing switch. | `dramafinder-2.2.0` `AbstractBasePlaywrightIT.java` L60–66, L99–105; `grep tracing` over Dramafinder finds nothing |
| P8 | The Unified Playwright adapter also uses `Playwright.create()` with no options, and has its own trace switch, `vaadin.testing.playwright.trace`. | `unified-test-playwright/.../PlaywrightAdapter.java` L39, L48–58, L77–88 |

### How the harness switches it on

- **Set `DEBUG=pw:channel` in the test JVM's environment.** With Surefire, use `<environmentVariables><DEBUG>pw:channel</DEBUG></environmentVariables>`
  in a benchmark profile, or export it before `mvn`. It must be set before `Connection` is loaded (P2). A system
  property does not work, and setting it from inside the JVM does not work either. Both the Dramafinder base class and
  the Unified adapter create Playwright the same way (P7, P8), so the same variable covers both kinds of test.
- **Count in-process** with a listener that wraps `System.err` once (`System.setErr(new PrintStream(tee))`) and matches
  `SEND ► {"id":…,"guid":…,"method":"(\w+)"`. The line is printed on the test thread (P3), so the listener can take the
  test frame at that moment. With no in-process counter, Surefire also puts the lines into each test case's
  `<system-err>` in `TEST-*.xml`, which gives per-test (not per-line) counts after the run.
- Count `SEND` only, not `RECV`. Each `SEND` with an `id` gets exactly one reply. `RECV` also has events (`__create__`,
  console messages…), which are not calls.
- Optionally add `pw:protocol` (`DEBUG=pw:channel,pw:protocol`) for the CDP layer. Those lines come from the driver
  process's inherited stderr (P5), not from `System.err`. So they **cannot be split per action or per test**, only per
  run, or per test class by their place in the console between Surefire's `Running demo.X` lines.

Not recommended:

- **A trace** (`Tracing.start` / the trace zip's action list). The Direct Test cannot switch it on without a change,
  because Dramafinder has no switch (P7). Its action list leaves out internal calls (P6). Snapshots on every action also
  slow the run being measured.
- **Wrapping `Page`/`Locator`.** Dramafinder's elements and the adapter call Playwright's own classes. A wrapper sees
  only what the test passes through it, and both tests get their `Page` from code they don't control.
- **Reflection into `Connection.transport`.** It would count the same messages as P2 without the env var, but against
  `impl` internals that can change in any release.

### What the count includes and leaves out

- **Includes:** every Java client call to the driver: locator queries (`queryCount`, `evalOnSelectorAll`), evaluations
  (`evaluateExpression`), actions (`fill`, `click`, `dispatchEvent`), waits (`waitForFunction`, `waitForSelector`),
  assertions (`expect`), navigation (`goto`), handle disposal (`dispose`), and page and context setup. Internal follow-up
  messages of a titled call are included and can be told apart by `metadata.internal`.
- **Leaves out:**
  - The driver's own retries inside one call. Actionability checks, `expect` polling and `waitForFunction` polling all
    happen in the driver or the page, as CDP commands (see `pw:protocol`).
  - Async browser events.
  - Time.

### Measured (one run, `-Dvaadin.testing.tool=playwright`, `DEBUG=pw:channel`, headless Chromium 153)

`ProfileDramafinderTest.savesTheProfile` (Direct) and `ProfileMigratedTest.savesTheProfile` (Unified). These are
different files with the same user story: open the profile, fill Name and Email, click Save, check. The Direct Test
finds by ARIA role, and the Unified test by label. Calls per test line:

| User action | Direct calls | Unified calls |
|---|---|---|
| open the profile view | `goto`, `waitForFunction` (2) | `goto`, `waitForFunction` (2) |
| fill Name | `fill` (1) | `queryCount`, `evalOnSelectorAll`, `evaluateExpression`, `getAttribute`, `isVisible`, `waitForFunction` ×2, `fill`, `dispatchEvent` (9) |
| fill Email | `fill` (1) | same 9 |
| click Save | `click` (1) | `queryCount`, `evaluateExpression`, `getAttribute`, `isVisible`, `waitForFunction` ×2, `click` (7) |
| check the result | `expect` (1) | 5 and 4 calls for two assertions (`queryCount`, `evalOnSelectorAll`, `evaluateExpression`, `waitForSelector`, `dispose`) |
| (outside test: context, page, setup navigation) | 6 (`newContext`, `newPage`, `goto`, `waitForFunction`, `dispose`, `close`) | 4 (`newContext`, `newPage`, `dispose`, `close`), plus `initialize` and `launch` once per run (shared browser) |

At the CDP layer (`DEBUG=pw:protocol`, per test class only): the `ProfileDramafinderTest` class (two tests, including
setup) sent **88** CDP commands, and the `ProfileMigratedTest` class (one test, including the browser launch) sent
**152**. Most were `Runtime.callFunctionOn` (19 vs. 81) and `Runtime.releaseObject` (8 vs. 28).

---

## 2. Selenium (TestBench 25.3, Selenium 4.52)

### Verified facts

| # | Fact | Source |
|---|---|---|
| S1 | Every W3C command goes through `RemoteWebDriver.execute(CommandPayload)`. That covers `get`, `findElement(s)`, `executeScript`, and the window and URL getters. Element commands go there too: `RemoteWebElement.execute` calls `parent.execute`. `ChromeDriver` → `ChromiumDriver` extends `RemoteWebDriver`. | `selenium-remote-driver-4.52.0` `remote/RemoteWebDriver.java` L693–706, L401–414, L634; `remote/RemoteWebElement.java` L221–233; `javap` on `selenium-chromium-driver-4.52.0.jar` |
| S2 | `execute` calls `log(sessionId, commandName, command, When.BEFORE)` before sending. `log` writes `"Executing: {0} {1}"` with the **command name as parameter 0**, at `level` (default `Level.FINE`), on the JUL logger `org.openqa.selenium.remote.RemoteWebDriver`. It returns early unless that logger is loggable at `FINE`. | `RemoteWebDriver.java` L127, L134, L704, L985–1028 |
| S3 | `-Dselenium.debug=true` (or `selenium.webdriver.verbose`, or env `SE_DEBUG=true`) makes `Debug.configureLogger()` lower `org.openqa.selenium` to `FINE` and attach a console handler on `System.err`. The `RemoteWebDriver` constructor calls it before the session starts. | `selenium-api-4.52.0` `internal/Debug.java` L70–72, L136–147; `RemoteWebDriver.java` L257 |
| S4 | TestBench wraps the browser's driver in a javassist proxy, `TestBenchDriverProxy`. Before every driver method except `close`, `get`, `navigate`, `quit`, `switchTo`, `manage` and a few TestBench ones, it calls `waitForVaadin()`. | `vaadin-testbench-shared-25.3.2` `TestBench.java` L101–133; `DriverInvocationHandler.java` L29–76 |
| S5 | `waitForVaadin()` is a **busy loop without sleep**: up to 40 s, `executeScript(WAIT_FOR_VAADIN_SCRIPT)` on `getDriver().getWrappedDriver()` until the script returns true. So it sends one or more `executeScript` commands, depending on how busy the page is. Its only test hook, `waitForVaadinLoopHook`, is a private field with no setter. | `commands/TestBenchCommandExecutor.java` L51–67, L71, L113–136 |
| S6 | The Direct Test's driver is created by TestBench's JUnit 6 `BrowserExtension`. The only way to supply your own driver is for **the test instance** to implement `DriverSupplier`. Otherwise it is `SetupDriver.setupLocalDriver(..)` or a remote driver. `setDriver` wraps anything that is not already a `TestBenchDriverProxy`. | `vaadin-testbench-core-junit6-25.3.2` `browser/BrowserExtension.java` L86–91, L215–250 |
| S7 | The Unified adapter creates its driver with `TestBench.createDriver(newBrowser(context))`. So a decorator could be put in `newBrowser`, which is adapter code and not the test. | `unified-test-selenium/.../SeleniumAdapter.java` L50, L72–90 |
| S8 | `EventFiringDecorator` (`@Beta`) extends `WebDriverDecorator`, which builds a ByteBuddy class implementing the original's interfaces. With the default constructor that class subclasses `Object`, not `ChromeDriver`. It passes arguments to the original **unchanged**. It decorates results that are a `WebElement`, an `Alert`, a `WebDriver.*` helper or a `List` of them, but **not elements inside a `Map`**. A decorated `WebElement` also gets `WrapsElement` and `toJson`, so it can be passed back as a script argument. | `selenium-support-4.52.0` `events/EventFiringDecorator.java` L166–186; `selenium-remote-driver-4.52.0` `support/decorators/WebDriverDecorator.java` L276–280, L322–373, L385–465, L491–530 |
| S9 | TestBench wraps elements by building a javassist proxy over **whatever `WebElement` it gets** (`TestBench.createElement`). `executeScript` results are wrapped from `getWrappedDriver()`'s result. TestBench checks for `RemoteWebDriver` in only two places: `getRemoteControlName`, and the screenshot extension after unwrapping `WrapsDriver`. | `TestBench.java` L141–195; `TestBenchDriverProxy.java` L124, L152–185; `TestBenchCommandExecutor.java` L87–89; `ScreenshotOnFailureExtension.java` L113–116 |

### How the harness switches it on

- **Recommended: the JUL command log (S1–S2).** The listener does
  `Logger.getLogger("org.openqa.selenium.remote.RemoteWebDriver").setLevel(Level.FINE)` and adds a `Handler` that
  counts records whose message is `"Executing: {0} {1}"`, keyed by `getParameters()[0]`. JUL calls the handler
  synchronously on the thread running the command, so the test frame is on the stack. Both kinds of test use a
  `ChromeDriver` under TestBench (S6, S7), so both are counted and neither changes. Keep a reference to the logger, so
  the level is not lost when the logger is garbage-collected. With no code at all, `-Dselenium.debug=true` prints the
  same records to stderr (S3), and Surefire files them under each test case.
- **Split `executeScript` by script.** The second log parameter is the command's text, which holds the script. A
  harness can tell TestBench's `WAIT_FOR_VAADIN_SCRIPT` (S5) apart from real work, and should report "waitForVaadin
  polls" separately, because their number depends on timing.

**Does a decorated driver still work with TestBench's element proxies and `waitForVaadin`?** By code reading, yes, if it
wraps the **raw** driver, inside TestBench: `TestBench.createDriver(new EventFiringDecorator<>(listener).decorate(chrome))`.
- TestBench proxies whatever driver it gets (S4). `waitForVaadin` runs on `getWrappedDriver()`, which is then the
  decorated driver, so its polls reach the listener (S5).
- Elements found through it are decorated, then wrapped by TestBench (S9). They can be passed back as script arguments
  (S8).

Gaps:
- **Element calls are missed** on elements a script returns inside a `Map` (S8).
- **Escape Hatch code that unwraps** to `ChromeDriver` or `RemoteWebDriver` breaks. `SeleniumSession.unwrap` checks
  `type.isInstance(wrapped)`, and the decorated class is not a `RemoteWebDriver`.
- **The Direct Test cannot get the decorator** without implementing `DriverSupplier` (S6), which is a test change.

A `WebDriverListener` also counts **interface calls, not wire commands**. That is close to one-to-one, but S1's log is the
wire command itself. This was not run (see [Not checked](#not-checked)), so treat it as medium confidence.

### What the count includes and leaves out

- **Includes:** every HTTP command to chromedriver, geckodriver or a Grid:
  - `newSession`, `get`, `findElement(s)` (including from elements and shadow roots), and element commands (`click`,
    `sendKeysToElement`, `getElementAttribute`…).
  - `executeScript` / `executeAsyncScript`, which carry the Unified adapter's batched matching and facts, and TestBench
    queries such as `ElementQuery`'s script (`ElementQuery.java` L1596).
  - `actions`, `getCurrentUrl`, window switching, screenshots.
  - The adapter's CDP cookie clearing between tests. `executeCdpCommand` is a chromedriver HTTP command too, but it falls
    outside a test.
  - **TestBench's `waitForVaadin` polls**: at least one before almost every proxied call (S4), so they often outnumber
    the work.
- **Leaves out:**
  - chromedriver's own CDP traffic to the browser.
  - Anything sent over a BiDi or DevTools WebSocket. Neither the demo tests nor TestBench use one: a `grep` finds only
    the adapter's `executeCdpCommand`.
  - Time.

---

## 3. Browserless Test (25.3)

### Verified facts

| # | Fact | Source |
|---|---|---|
| B1 | Browserless Test has a documented global hook: `var testingLifecycleHook: TestingLifecycleHook`. Its KDoc says: "If you need to hook into the testing lifecycle … provide your own custom implementation of this interface, then set it into `testingLifecycleHook`." From Java it is `TestingLifecycleHookKt.setTestingLifecycleHook(..)`. It lives in the `com.vaadin.browserless.internal` package. Nothing in the library sets it. | `browserless-test-shared-25.3.2` `internal/TestingLifecycleHook.kt` L30–45, L157; `javap com.vaadin.browserless.internal.TestingLifecycleHookKt`; `grep setTestingLifecycleHook` over the sources |
| B2 | The hook has three methods. `awaitBeforeLookup()` defaults to `MockVaadin.clientRoundtrip()` when a UI is current. `awaitAfterLookup()` does nothing by default. `getAllChildren(component)` gives the children a tree walk descends into. | `TestingLifecycleHook.kt` L51–56, L61, L68–141 |
| B3 | Every Browserless lookup runs `Component.find`. It calls `awaitBeforeLookup()`, walks the whole subtree with `_walkAll()`, and then calls `awaitAfterLookup()`. `_walkAll` is a `DepthFirstTreeIterator` that calls `testingLifecycleHook.getAllChildren(c)` once per node it expands. | `internal/Locator.kt` L234–243, L255–257; `internal/DepthFirstTreeIterator.kt` L30–38 |
| B4 | A Direct Test's `$(X.class)…single()/first()/all()` runs one `LocatorKt._find` / `_get`, so one lookup, one walk from the search root, and one `clientRoundtrip`, per terminal call. | `ComponentQuery.java` L928–960 |
| B5 | There are **two kinds of server round trip**, and neither has a hook. `MockVaadin.clientRoundtrip()` runs the UI access queue, `runExecutionsBeforeClientResponse`, and `cleanupDialogs` (whose `walk()` uses plain `children`, not the hook). `BrowserlessDSL.roundTrip(ui)` collects changes, runs `runExecutionsBeforeClientResponse` and flushes focus. The test's `roundTrip()`, `ComponentTester.roundTrip()` and `BrowserlessUIContext.roundTrip()` all call `BrowserlessDSL.roundTrip`, a static method of a package-private final class. | `internal/MockVaadin.kt` L421–427; `TestingLifecycleHook.kt` L187–192; `DepthFirstTreeIterator.kt` L44–46; `BrowserlessDSL.java` L116–121 (`javap`: `final class`); `BaseBrowserlessTest.java` L570–572; `ComponentTester.java` L378–380; `BrowserlessUIContext.java` L428–431 |
| B6 | The Unified Browserless adapter walks the tree itself with `LocatorKt._walkAll(root)`, which goes through the hook's `getAllChildren` but **not** through `awaitBeforeLookup`. It then looks for Grids under the same roots with Browserless's own `window().find(Grid.class, root).all()`, which is a second walk with a `clientRoundtrip` (B3). Actions and `sync()` end with `window().roundTrip()` (B5). | `unified-test-browserless/.../BrowserlessSession.java` L131–143, L183–193, L223–231, L336–339; `BrowserlessElement.java` L215–299 |
| B7 | The Unified core retries a lookup until it matches, calling `session.sync()` and sleeping `POLL_MILLIS` between attempts. So one Unified action can run its query more than once. | `unified-test-api/.../internal/TestRun.java` L55–79 |
| B8 | `byte-buddy` and `byte-buddy-agent` 1.18.11 are already on the demo's test classpath (through Mockito). | `mvn dependency:list` in `unified-test-demo` |

### How the harness switches it on

- **Lookups and tree walks:** install a counting `TestingLifecycleHook` that delegates to `TestingLifecycleHook.Companion.getDefault()`.
  - Count `awaitBeforeLookup` as "Browserless lookup". In Direct Tests this is one per query run (B4). In Unified tests
    it is the Grid search only (B6).
  - Count `getAllChildren` as "nodes expanded", and the calls whose argument is a `UI` as "walks from the UI root". The
    second is a fair proxy for "query runs", because both kinds query from the UI unless scoped.
  - Both kinds of test go through the hook (B3, B6). It is a global, so set it once per run and restore the default
    afterwards.
- **Server round trips:** there is no hook (B5), so install a ByteBuddy `Advice` on entry to
  `com.vaadin.browserless.BrowserlessDSL.roundTrip` and `com.vaadin.browserless.internal.MockVaadin.clientRoundtrip`.
  Use `ByteBuddyAgent.install()` from the listener (B8). On JDK 21 this self-attach prints a dynamic-agent warning; pass
  `-XX:+EnableDynamicAgentLoading` or `-javaagent` to silence it. The advice must call a `public static` counter method:
  it is inlined into Browserless classes, and calling a non-public method failed with `IllegalAccessError` when tried.
- **How many times a query runs:** on the Unified side, count `session.find` attempts per action. B7 makes this
  timing-dependent: the first attempt usually matches in Browserless Test, because the server is in the same JVM. A
  Direct Test runs each query exactly once per terminal call (B4).

### What the count includes and leaves out

- **Includes:**
  - Every Browserless lookup.
  - Every node expanded by a hook-based walk, whether it comes from Browserless queries or from the Unified adapter.
  - Every simulated server round trip of either kind.
- **Leaves out:**
  - Walks that don't use the hook, such as `cleanupDialogs`' `walk()`, `PrettyPrintTree` and the Unified adapter's
    parent walks for labels.
  - The listener work done inside a round trip, such as the app's own value-change and click listeners.
  - Spring and database time.

  A Browserless "round trip" is a method call in the same JVM. It is not a network hop, so for #294 it measures **work
  done**, not latency.

### Measured (one run, default `vaadin.testing.tool`, i.e. Browserless)

`ProfileBrowserlessTest.savesTheProfile` (Direct) vs. `ProfileBrowserlessMigratedTest.savesTheProfile` (Unified), the
golden migration pair, per test line:

| User action | Direct: lookups / walks from UI / nodes / round trips | Unified: lookups / walks from UI / nodes / round trips |
|---|---|---|
| open the view | (in `@BeforeEach`: 0 / 0 / 0 / 0) | 0 / 0 / 0 / 1 |
| fill Name | 1 / 1 / 13 / 1 (`clientRoundtrip`) | 1 / 2 / 26 / 2 (`clientRoundtrip` + `roundTrip`) |
| find Email (Direct `first()`; Unified declares a lazy Wrapper) | 1 / 1 / 13 / 1 | none on this line (H4) |
| fill Email | 0 (`TextFieldTester.setValue` does no round trip) | 1 / 2 / 26 / 2 |
| click Save | 1 / 1 / 13 / 1 | 1 / 2 / 26 / 2 |
| explicit `roundTrip()` | 0 / 0 / 0 / 1 (`BrowserlessDSL.roundTrip`) | (not needed) |
| each of three checks | 1 / 1 / 14 / 1 | 1 / 2 / 28 / 1 |

Every Unified lookup walks the tree **twice**: once on its own and once for the Grid search (B6). That shows up here
before any timing.

---

## Sketch of the harness listener

This is a shortened version of what was run (throwaway, not committed). It goes in the benchmark's test sources with
`META-INF/services/org.junit.platform.launcher.TestExecutionListener` naming it.

```java
public class RoundTripCounter implements TestExecutionListener {
    static volatile String testClass;                       // set in executionStarted from MethodSource

    public static synchronized void count(String kind) {    // public: ByteBuddy advice inlines a call to it
        String site = StackWalker.getInstance().walk(s -> s
                .filter(f -> testClass != null && f.getClassName().startsWith(testClass))
                .findFirst().map(f -> f.getMethodName() + ":" + f.getLineNumber()).orElse("(outside test)"));
        // add (site, kind) to this test's counts
    }

    static void install() {
        // Browserless: lookups and walks (B1–B3)
        TestingLifecycleHook def = TestingLifecycleHook.Companion.getDefault();
        TestingLifecycleHookKt.setTestingLifecycleHook(new TestingLifecycleHook() {
            public void awaitBeforeLookup() { count("bl:lookup"); def.awaitBeforeLookup(); }
            public void awaitAfterLookup() { def.awaitAfterLookup(); }
            public List<Component> getAllChildren(Component c) {
                count(c instanceof UI ? "bl:walk-from-UI" : "bl:node"); return def.getAllChildren(c);
            }
        });
        // Browserless: server round trips (B5), by bytecode advice
        new AgentBuilder.Default().disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .type(named("com.vaadin.browserless.BrowserlessDSL")
                        .or(named("com.vaadin.browserless.internal.MockVaadin")))
                .transform((b, td, cl, m, pd) -> b.visit(Advice.to(RoundTripAdvice.class)
                        .on(named("roundTrip").or(named("clientRoundtrip")))))
                .installOn(ByteBuddyAgent.install());
        // Selenium: one record per W3C command (S2)
        Logger rwd = Logger.getLogger("org.openqa.selenium.remote.RemoteWebDriver");
        rwd.setLevel(Level.FINE);
        rwd.addHandler(/* publish: if "Executing: {0} {1}".equals(r.getMessage()) count("se:" + r.getParameters()[0]) */);
        // Playwright: needs DEBUG=pw:channel in the environment (P2); tee System.err and count SEND lines (P3)
        System.setErr(/* tee that matches SEND ► {"id":..,"guid":"..","method":"(\w+)" and calls count("pw:" + method) */);
    }
}
```

For #294's benchmark, three changes would make the counts comparable.

- **Split the kinds.** Report Selenium's `waitForVaadin` `executeScript`s, Playwright's `waitForFunction` /
  `waitForSelector`, and Browserless's `clientRoundtrip` apart from the work calls, since the waits depend on timing.
- **Repeat runs.** Run each pair several times and report the median per action.
- **Count retries.** Add a count of `TestRun.pollFor` attempts on the Unified side (B7), so a retry shows up as a retry
  and not as extra cost per lookup.

## Not checked

- **Selenium was not run.** The container's Chrome is 153 and its only chromedriver is 147, so no session could start,
  and downloading a matching driver was not allowed here. The Selenium section rests on source reading (S1–S9). The
  JUL handler is in the same listener that ran for Playwright and Browserless, but it saw no commands.
- **`EventFiringDecorator` with TestBench** was not run either. The compatibility answer is from code reading only.
  `EventFiringDecorator(Class<T> targetClass, …)` can build a subclass of a concrete driver class. Whether that keeps
  `instanceof RemoteWebDriver` true, without starting a second driver, was not checked.
- **Playwright on Firefox or WebKit**, and Dramafinder's `AutoWait` mode on: the channel counts would differ, and the
  CDP layer does not exist on Firefox.
- **Parallel test execution.** The stack-based attribution assumes a test runs on the listener's thread, with no
  parallel JUnit execution. Playwright's `Connection` is not thread-safe anyway.
- Counts are from **one run each**. They show that the hooks work and what they see. They are not the benchmark result.
