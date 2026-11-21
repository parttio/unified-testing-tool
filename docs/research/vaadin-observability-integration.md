# Vaadin 25.3/25.4 observability as an optional input to Unified UI Tests

Background research, not tied to a ticket. It builds on [agentic-testing-environment-roadmap.md](agentic-testing-environment-roadmap.md) (candidates C2, C5, C13 below), [user-step-recording.md](user-step-recording.md) and [unified-ui-test-noise-baseline.md](unified-ui-test-noise-baseline.md), and touches the open issues [#289](https://github.com/parttio/unified-testing-tool/issues/289) (server logs under the User Step), [#296](https://github.com/parttio/unified-testing-tool/issues/296) (flaky check), [#215](https://github.com/parttio/unified-testing-tool/issues/215) (Unified Recorder) and [#101](https://github.com/parttio/unified-testing-tool/issues/101) (Browserless listener exceptions).

**Question.** Vaadin 25.3 and 25.4 updated their observability support, with features for production and for development. How could the Unified API use it as an *optional* dependency?

Researched 2026-11-21 against `main` at `111cbb0` (Vaadin `25.3.5`, `pom.xml`). External sources are pinned where they are code:

| Source | Pinned at |
|---|---|
| Vaadin docs, `articles/tools/observability/` (renders as `vaadin.com/docs/next/…`, "Vaadin 25.4 (pre-release)") | [`vaadin/docs@b9e3440`][docs-main] (branch `main`, 2026-11-21) |
| The same pages for 25.3 (renders as `vaadin.com/docs/v25/…`) | [`vaadin/docs@bc94e61`][docs-253] (branch `v25.3`, 2026-11-20) |
| Observability Kit source | [`vaadin/observability-kit@24d099a`][okit] (tag `25.4.0-beta1`), and tag [`5.0.1`][okit-501] |
| Flow source | [`vaadin/flow@b128e53`][flow] (tag `25.4.0-beta1`) |
| TestBench source | [`vaadin/testbench@11908ed`][tb] (`main`) |
| Browserless Test | `browserless-test-shared-25.3.2-sources.jar` from Maven Central |

Labels: a **fact** has a source (a URL, a file path or an issue). **[inference]** and **[opinion]** are this note's reasoning. Nothing below was run.

## Answer in short

1. **There are two layers, and the free one matters most.** "Vaadin observability" is (A) a **service event bus in Flow** (Apache-2.0) that reports RPC invocations, data-provider queries and session-lock events since 25.3, and request and navigation start/end since 25.4; and (B) **Observability Kit 5**, a *commercial* library that turns those events into Micrometer meters, Observation spans, "interaction insights" JSON and a dev-mode Copilot panel. Layer A needs no new dependency: `flow-server` is already on the Unified API's classpath (`unified-test-api/pom.xml`, `provided`).
2. **What is verified about the versions.** Kit `5.0.0` shipped with Vaadin 25.3.0 (2026-09-23), `5.0.1` on 2026-10-08; on 2026-11-18 the kit was renumbered to the platform's line as `25.4.0-beta1`. **Vaadin 25.4 is in beta; no 25.4 GA exists as of this date.** Almost all of the kit's features (insights, Copilot panel, UI-state, database monitoring) are already in 25.3 / kit 5.0.x; 25.4 adds Flow's request/navigation lifecycle events, RPC and fetch durations on the events, and browser interaction timing that works in production and with WebSocket push.
3. **The application runs in the test JVM** under `@UnifiedSpringBootTest` on every Testing Tool, so a test can listen to the server's events and read the kit's registry and insight buffers **in-process**, with no exporter, collector or HTTP. That is the same property that makes server logs per User Step possible today (`internal/logs/ServerLogs.java`).
4. **Per Testing Tool, the reach differs.** Browserless clicks by firing component events directly (`ComponentUtil.fireEvent`), not through Flow's RPC handler, and makes no HTTP requests. So on Browserless the RPC, request and client-side signals never fire; navigation and data-provider events do [inference from source]. On Playwright and Selenium every server signal fires; the client meters arrive in 5-second batches.
5. **Best uses, in order [opinion]:** (U1) server activity per User Step in `user-steps.json`: RPCs, navigation outcome, data fetches, server time. (U2) failure evidence: the server-side exception and its application frame, plus the kit's insight JSON when it is present. (U3) **deterministic counts as a ratchet**: data fetches and, with the kit, SQL queries per User Step, which catches N+1 loads the way spec 13 catches round trips. Then leak checks, flake signals and agent context.
6. **Do not use it to change Auto-Wait.** The browser adapters already wait on Flow's client `isActive()`; the server bus exposes no "background work pending" signal that would make that wait more correct. It is more useful to *explain* a wait that timed out ("a data fetch for `Grid` was running for 4.2 s").
7. **Recommendation [opinion]:** build U1–U3 on layer A inside the existing modules, gated by a class check for Flow 25.3/25.4 like `LogbackHook`. Put kit-specific features (insights, SQL counts, UI-state leaks) in a separate opt-in module, `unified-test-observability-kit`. Its blocker is the **licence**: in development mode, the only mode tests run in, the kit registers *nothing* without a valid licence key, and it does so silently.

---

## 1. What shipped, and when

### Verified facts

| # | Fact | Source |
|---|---|---|
| V1 | Vaadin 25.3.0 was released on 23 Sep. Its notes headline "Observability Kit 5 (Pro)", an "agent less rebuild on Micrometer": "The OpenTelemetry Java agent of 4.x is gone", "Spans go through the Micrometer Observation API", plus an insights endpoint, and "a failed license check … degrades to no telemetry". For Flow: "a VaadinService event bus replaces the session lock and RPC invocation listeners added in 25.2", and the bus also reports data-provider counts and fetches. | [platform 25.3.0 release][rel-253] |
| V2 | Vaadin 25.4.0-beta1 was released on 18 Nov. Flow: "the service event bus now reports the start and end of every request and navigation, with outcome, duration and handler" ("Observability Kit uses these events"). Kit: "Browser interaction timing now works in production and with WebSocket push"; "Navigation, request and session lock metrics are measured from Flow's new lifecycle events". | [platform 25.4.0-beta1 release][rel-254b1] |
| V3 | Maven Central, `com.vaadin`: `observability-kit-starter`, `-spring` and `-micrometer` have `5.0.0-beta1`, `5.0.0-rc1`, `5.0.0` (POM dated 2026-09-22), `5.0.1` (2026-10-08) and `25.4.0-beta1` (2026-11-18). `observability-kit-test` is a stale `2.0.0` (2023), unrelated. | [maven-metadata.xml][mc-okit] |
| V4 | `observability-kit-micrometer:5.0.1` depends on `flow-server:25.3.3`, `license-checker:3.1.3`, `micrometer-core`/`micrometer-observation:1.17.0` and `context-propagation:1.2.1`; `vaadin-dev-server` is optional. `25.4.0-beta1` depends on `flow-server:25.4.0-beta1`. Licence: "Vaadin Commercial License and Service Terms". | [5.0.1 POM][mc-501-pom], [25.4.0-beta1 POM][mc-254-pom] |
| V5 | Requirements: the v25.3 docs say "Vaadin 25.3 or newer"; the 25.4 docs and the kit's README on `main` say "Vaadin 25.4 or newer (Flow 25.4+)". Both: Java 21, Spring Boot 4 for the starter. | [getting-started (25.3)][d253-gs], [getting-started (25.4)][d-gs], [README][okit-readme] |
| V6 | Between the docs branches `v25.3` and `main`, the observability pages changed in 5 files (+225/−26 lines): the version requirement, navigation timing redefined on `NavigationStartedEvent`/`NavigationEndedEvent` (one sample per navigation, unknown URL now `outcome=error`), a "Which Meters Appear When" table, an "Empty Payload" section, and Spring Security notes. Insights, the Copilot panel, client-error insights, UI-state and database monitoring are on both branches. | `git diff v25.3 main -- articles/tools/observability` in [vaadin/docs][docs-main] |
| V7 | Kit `5.0.1` already contains the insights collectors, `LatestInteraction`, the dev-tools handler for the Copilot panel and the UI-state binder. | `git ls-tree 5.0.1` of [observability-kit][okit-501] |

**Could not be verified:** the 25.4 GA date and its final contents. Everything marked 25.4 comes from a beta and from docs labelled pre-release.

---

## 2. Layer A: Flow's service event bus (free, in Flow)

### Verified facts

`VaadinService.getEventBus()` returns a `VaadinServiceEventBus` (`@since 25.3`). It is thread-safe, dispatches by exact event type, and a listener that throws is logged and does not disturb the caller ([VaadinServiceEventBus.java][flow-bus]).

| Event (package) | Since | Carries | Fired from |
|---|---|---|---|
| `RpcInvocationStartedEvent`, `…FailedEvent`, `…EndedEvent` (`server.communication`) | 25.3; `getDuration()` and `getError()` on Ended since **25.4** | UI, protocol type (`event`, `mSync`, `publishedEventHandler`, `channel`, `navigation`), target node id, name; start/failed/end on one thread, in order | `ServerRpcHandler` |
| `DataFetchStarted/Failed/EndedEvent`, `DataCount…Event` (`server.data`) | 25.3; fetch `getDuration()` since **25.4** | UI, component (`Grid`, `ComboBox`…), offset, limit, filtered, rows returned (`-1` = threw) | `DataCommunicator`, `HierarchicalDataCommunicator` via `DataFetchObserver` |
| `SessionLockRequested/Acquired/ReleasedEvent` (`server`) | 25.3 | session lock lifecycle | `InstrumentedReentrantLock` |
| `ClientMessageResentEvent`, `MessageIdSyncErrorEvent`, `UIResynchronizationEvent` | 25.3.3 | client message recovery | `ServerRpcHandler` (resend, resync) |
| `RequestStartedEvent`, `RequestEndedEvent` (`server`) | **25.4** | request, response, session, handler that handled it, exception, `getDuration()` | `VaadinService` |
| `NavigationStartedEvent`, `NavigationEndedEvent` (`router`) | **25.4** | UI, location, trigger; a sealed `Outcome`: `Completed(target, params)`, `Postponed`, `Failed(error)`, `NotShown`. Only the outermost navigation is reported | `Router.observeNavigation` |

Sources: `@since` tags and Javadoc in [flow@b128e53][flow] under `flow-server/src/main/java/com/vaadin/flow/{server,router,server/data,server/communication}` and `flow-data/…/provider/DataFetchObserver.java`. The Kit's binders are written against exactly these events ([README "How it works"][okit-readme]; `NavigationMetricsBinder`, `DataQueryMetricsBinder`, `ErrorMetricsBinder` in [okit][okit]).

The browser side also has free timing: with Flow's `requestTiming` deployment setting (on by default outside production mode), each client publishes `getProfilingData()`: last and total processing time, the server timing info and the bootstrap time ([publishClient.ts][flow-publish], [MessageHandler.ts][flow-mh]). TestBench reads it as `timeSpentRenderingLastRequest()`, `totalTimeSpentRendering()`, `timeSpentServicingLastRequest()` and `totalTimeSpentServicingRequests()` ([TestBenchCommands.java][tb-cmds]).

### Inferences

- The bus gives the Unified API server-side facts for each User Step without any new dependency: what the server did, how long it took, whether it threw, and what it loaded. All of it is keyed by `UI`, which the Browserless adapter holds directly. In a browser run the test can tell its UI apart only by time window, as `ServerLogs` does today.
- On Vaadin 25.3.x (this repo's baseline) RPC and fetch events fire but carry **no duration and no error** on the ended event, so a listener has to time them itself (started/ended run on the same thread, so a `ThreadLocal` works, as the Javadoc says). Request and navigation events need 25.4.

---

## 3. Layer B: Observability Kit (commercial)

### Verified facts

**Artifacts and setup.** `com.vaadin:observability-kit-starter` (Spring Boot: "add one dependency and you're done"), `observability-kit-spring` (plain Spring: `@Import(ObservabilityConfiguration.class)` plus a `MeterRegistry` bean) and `observability-kit-micrometer` (standalone: `ObservabilityKit.install(registry, settings)` from a `ServletContextListener`). It is a library, not an agent: `MetricsServiceInitListener` registers binders at `VaadinService` init. It records into the application's `MeterRegistry` and emits spans through the Micrometer Observation API. "The kit only *records* into a registry"; export is Actuator's job ([getting-started][d-gs], [README][okit-readme]).

**Licence gate.** `ObservabilityLicense.isLicensed(productionMode)` returns `true` without a check in production mode ("Production builds are validated at build time"). In development mode it calls `LicenseChecker.checkLicense(...)` with no wait, and on failure returns `false`. The kit then "registers no instrumentation" and only logs a warning ([ObservabilityLicense.java][okit-lic], [getting-started, Troubleshooting][d-gs]). The insights payload then reports `"instrumentation": "inactive"` ([insights][d-ins]). TestBench uses the same `license-checker` artifact (`vaadin-testbench-core-junit6/pom.xml` in [testbench][tb]).

**What it emits** (from [reference][d-ref]; toggles from [configuration][d-conf], all `vaadin.observability.*`):

| Group (toggle, default) | Meters | Notes for tests |
|---|---|---|
| `sessions` (on) | `vaadin.sessions.active/created/duration`, `vaadin.session.lock.wait/hold` (tag `context=request|access`) | lock wait points at contention |
| `uis` (on) | `vaadin.ui.active`, `vaadin.ui.created` | |
| `ui-state` (**off**) | `vaadin.ui.state.nodes(.max)`, `.components`, `.views`, **`.views.stale`** ("normally zero"), **`.retained.growing`** ("normally zero"), `vaadin.session.state.nodes.max`… | sampled at most once per UI per `ui-state-sample-interval` (default 10 000 ms), at UI init, after navigation and at RPC end |
| `navigation` (on) | `vaadin.navigation` timer, tags `route`, `outcome` (`success`, `rerouted`, `forwarded`, `error`, `unknown`), `error` | |
| `requests` (on) | `vaadin.request.duration` (tags `vaadin.request.type` = `uidl`/`bootstrap`/`stream`/`push`/`heartbeat`/`static`/`other`, `vaadin.interaction`, …), `vaadin.rpc.duration` (tag `type`) | |
| `data` (on) | `vaadin.data.count.duration`, `vaadin.data.fetch.duration`, `vaadin.data.fetch.requested/rows` (tag `route`) | |
| `database` (**off**, Boot only) | `vaadin.db.query`, `vaadin.db.fetch.rows` per route; wraps every `DataSource` bean | the SQL work behind a slow fetch: `sqlQueries`, `sqlRowsRead` |
| `errors` (on) | `vaadin.errors` (tags `exception`, `route`, `component`) | decorates the session `ErrorHandler`; a handler read back is the kit's wrapper, so an `instanceof` on it fails |
| `client` (on) | `vaadin.client.bootstrap/navigation/request/render.duration`, `web_vitals.lcp/fcp`, `vaadin.client.errors`, `.connection`, `.connection.downtime` | the browser flushes every **5 s** and when the page is hidden; rate limit 100 samples per session per 10 s |
| `resync` (on) | `vaadin.resync` (tag `type` = `resend`/`resync`/`out_of_sync`) | |
| `traces` (on) | spans `vaadin.request.<type>`, `vaadin.navigation <route>`, `vaadin.rpc.<type>` (span-only `vaadin.rpc.event`, `vaadin.rpc.component`), `vaadin.data.fetch/count`, `vaadin.db.query`, `vaadin.executor.task` | spans leave the app only with a tracing bridge |

Tagged meters appear only after their first value ("absent rather than zero") ([reference, "Which Meters Appear When"][d-ref]).

**Interaction insights.** These are versioned JSON (`schemaVersion: 1`) with six types: `user-interaction-error`, `slow-user-interaction`, `data-query-error`, `slow-data-query`, `client-error` and `growing-view-state`. They are grouped by route template, component, event and exception, and each carries `evidence`, a `replay` list, `suggestion` and `applicationFrame` (the first non-framework stack frame). "Slow" is a **fixed** 1000 ms of server handling. The records are kept in in-memory ring buffers of 100 per kind, since the application started, with no time window. The payload is served at `/actuator/vaadin/observability`, from the `VaadinObservabilityEndpoint` bean's `section("observability")`, or, outside Spring, from `ObservabilityKit.getRecentInteractions()/getRecentQueries()/getRecentClientErrors()/getRetainedStateGrowth()` rendered by an `InsightsService` ([insights][d-ins], [ObservabilityKit.java][okit-kit]).

**Production versus development.**

| | Production mode | Development mode |
|---|---|---|
| Licence check | none at runtime (build-time) | at startup; on failure, **no instrumentation** |
| Meters, spans, insights | yes | yes |
| `vaadin.client.render.duration` | only with `vaadin.requestTiming=true` | yes (default `requestTiming`) |
| Insight `replay` steps | name the component by class: "Locate component Button" | name it by caption and list the view's changed field values: "Set the 'Reason' Select to 'Defective'", "Click the 'Process return' Button" (captions ≤ 60 chars, values ≤ 40, at most 10) |
| "Last interaction" (network / server / render split) | not recorded | recorded, matched to browser samples within 5 s |
| Copilot "Observability" panel (Vitals with budgets, Last interaction, Metrics, Findings) | absent | present; it polls every 3 s while open and announces new findings in the Copilot log |

Sources: [insights, "Replay Steps That Replay"][d-ins], [copilot-panel][d-cop], [reference][d-ref].

**Ways a test process can read Micrometer data** (not Vaadin-specific):

| Tool | What | Source |
|---|---|---|
| `SimpleMeterRegistry` (`micrometer-core`) | holds the latest value of each meter in memory, exports nothing | [Micrometer registry docs][mm-reg] |
| `@SpringBootTest` | "meter registries, except the in-memory backed, are not auto-configured"; reporting tracing components are not auto-configured unless `@AutoConfigureTracing`/`@AutoConfigureMetrics` | [Spring Boot 4.1 testing][boot-test] |
| `TestObservationRegistry` (`io.micrometer:micrometer-observation-test`) | asserts on observations (`hasObservationWithNameEqualTo`, key values) | [Micrometer observation testing][mm-obs-test] |
| `SimpleTracer` (`io.micrometer:micrometer-tracing-test`) | an in-memory `Tracer` with `TracerAssert` | [Micrometer tracing testing][mm-tr-test] |
| `InMemorySpanExporter` (`io.opentelemetry:opentelemetry-sdk-testing`) | "Captures exported spans in memory" | [OpenTelemetry Java SDK][otel-sdk] |

### Inferences

- In a `@UnifiedSpringBootTest` with the starter, the kit binds to the Spring context's in-memory registry, so `ObservabilityKit.getActiveMeterRegistry()` and the insight buffers can be read from the test thread on every Testing Tool, **if the licence check passed**.
- **Tests always run in development mode** in this project. Browser runs need `vaadin-dev` ([02-test-setup.md](../spec/02-test-setup.md)), and a Browserless `MockVaadin` service is not a production build [inference; not checked in Browserless source]. So on CI the kit is inert without a licence key, and nothing fails to say so. This is the same situation as the Selenium adapter's TestBench key ([10-packaging.md](../spec/10-packaging.md)), but quieter.
- The dev-mode `replay` lines are the closest thing in Vaadin to User Steps generated from the server, and they differ: one state snapshot, not a sequence, by the kit's own account ([insights][d-ins]).

---

## 4. What fires on which Testing Tool

| Signal | Browserless | Playwright / Selenium, app in the test JVM (`@UnifiedSpringBootTest`) | Browser against an external server (Failsafe + `jetty:start`) |
|---|---|---|---|
| Navigation events (25.4) | **yes**: `UI.navigate` goes through `Router.observeNavigation` [inference from [Router.java][flow-router]] | yes | no (other JVM) |
| RPC events | **no**: Browserless testers call `ComponentUtil.fireEvent` (`Clickable.java`, `*Tester.java` in `browserless-test-shared-25.3.2`), never `ServerRpcHandler` | yes | no |
| Request events (25.4) | **no**: no HTTP | yes | no |
| Data fetch/count events | probably yes, wherever a `DataCommunicator` fetches [not run] | yes | no |
| Session lock events | unknown [not checked] | yes | no |
| Kit meters derived from the above | as above | yes | only over HTTP: `/actuator/prometheus` |
| Kit insights | `user-interaction-error` and `slow-user-interaction` **never** (they come from RPC events); query insights probably | yes | `/actuator/vaadin/observability` |
| Kit client meters | none (no browser) | yes, in 5 s batches, rate-limited | yes |
| Flow `getProfilingData()` | n/a | yes: `page.evaluate` / TestBench commands | yes |

**Consequence [inference]:** layer A gives the same User-Step-level facts on all three Testing Tools only for navigation and data loading. RPC-level facts are browser-only, unless the Browserless adapter emits its own equivalent when it fires a listener, which it could do, since it is the one firing it. This is exactly the kind of difference [C2, the cross-tool divergence report](agentic-testing-environment-roadmap.md#c2-cross-tool-divergence-report) would surface.

---

## 5. Uses in the Unified API

Each use names the module, how it switches on, the reach per Testing Tool, and its risks. Effort figures are guesses.

### U1. Server activity per User Step

**What.** Under each User Step in `user-steps.txt`/`.json`, record what the server did: the RPCs and their types, the navigation and its outcome (`Completed → OrderView`, `Failed: NullPointerException`, `Postponed`), the data fetches (`Grid: rows 0–50, 50 returned, 12 ms`) and the server time. For example: `click button "Save"` → `1 RPC (event, 38 ms) · navigation → OrdersView (completed) · Grid fetch 0–50 (50 rows, 9 ms)`.

**Module.** `unified-test-api`, beside `internal/logs/ServerLogs` and `StepRecorder`. No new dependency: Flow is already `provided`.

**Switch.** A class check like `LogbackHook.available()`: `com.vaadin.flow.server.VaadinServiceEventBus` present means 25.3 facts (RPC, data), and `com.vaadin.flow.router.NavigationEndedEvent` present means 25.4 facts. It is not a Capability: nothing is skipped and the section is just absent. The service is `VaadinService.getCurrent()` on Browserless, and the `SpringServlet`'s service from the Spring context on browsers [inference; `internal/junit/SpringContexts` already finds the context].

**Reach.** Section 4. On Browserless, add the RPC-equivalent from the adapter itself.

**Risks.** Server threads in browser runs mean attribution by time, as `ServerLogs` does today, and its rule for parallel tests applies (lines of other threads are dropped). Listener overhead on the performance benchmark: the bus skips building events with no listener (`hasListener`), so the cost appears only while recording. Measure it against the [spec 13](../spec/13-performance.md) budget. **Effort:** S–M.

### U2. Failure evidence from the server

**What.** When a User Step fails, add to `<test>.failure.json` and the report: the server-side exception from `RpcInvocationFailedEvent` / `NavigationEndedEvent.Failed`, with its first application frame (computed as the kit does, or taken from the kit's `applicationFrame` when present). With the kit, also attach any insight whose `lastSeen` falls inside the test, as JSON.

**Why.** A UI test often fails at "notification 'Saved' not found" when the real cause was an NPE in the click listener. The kit's insight says exactly that, and its payload is designed to be handed to an agent ([insights, "Fixing Insights with an AI Agent"][d-ins]). This is [roadmap C5](agentic-testing-environment-roadmap.md#c5-server-logs-and-browser-console-per-user-step-and-a-smoke-check) / #289, with structured data instead of log lines.

**Module.** The layer-A part in `unified-test-api`. The insight attachment in `unified-test-observability-kit` (section 6).

**Reach.** Browser runs in-JVM: full. Browserless: navigation failures only; listener exceptions there are #101's subject.

**Risks.** Insight buffers are process-wide and never cleared (no public reset, section 3), so a test must take a snapshot before and after and diff by group key and `lastSeen`. The kit decorates the session `ErrorHandler` and so changes what `getErrorHandler()` returns, which can break an application's own `instanceof` check in a test (documented in the [reference][d-ref]). Exception messages are withheld unless `insights-details=true`. **Effort:** S (layer A), S (kit).

### U3. Deterministic counts as a ratchet: data loads and SQL

**What.** Per User Step, count data fetches and counts (layer A), and SQL queries and rows read (kit with `database=true`, Boot only). Report them, and optionally commit them per scenario and fail on an increase, like the round-trip ratchet in [spec 13](../spec/13-performance.md#round-trips-a-ratchet). An N+1 regression ("open the orders view: 1 → 51 SQL queries") then fails a pull request on Browserless in milliseconds. That is something a timing budget can't do on a shared runner.

**Why counts and not times [opinion].** Spec 13 already decided that times vary between runs and only counts can gate a pull request. The kit's own slow thresholds (1000 ms, fixed) never trigger on a demo-sized database.

**Module.** Fetch counts in `unified-test-api`. SQL counts in `unified-test-observability-kit`, read from `vaadin.db.query` timer counts and `vaadin.db.fetch.rows`, or from the `sqlQueries` evidence.

**Reach.** Fetch counts: every tool. SQL: every tool in-JVM, *only* with the Boot starter and a licence.

**Risks.** Lazy-loading counts depend on the viewport in a browser (the page size asked for differs from Browserless), so a count file has to be kept per Testing Tool, as the round-trip files are. Background or cached queries add noise. **Effort:** M.

### U4. Explaining timeouts, not changing Auto-Wait

**What.** When an Auto-Wait or a retrying assertion times out, say what the server was doing: "a fetch for `Grid` started 4.2 s ago and has not ended", "session lock waited 3.1 s", "last RPC failed with …". The data comes from in-flight started-without-ended events.

**Not recommended:** replacing or extending the browser adapters' idle wait (`PlaywrightSession` polls `Vaadin.Flow.clients[*].isActive()`). The bus has no event for `UI.access` work queued from background threads: the kit itself notes that a plain `UI.access()` from a background thread "never passes through the executor and gets no span" ([reference, Background Work][d-ref]). So "server idle" can't be decided from it. Browserless is synchronous already ([05-waiting.md](../spec/05-waiting.md)).

**Effort:** S on top of U1.

### U5. Flakiness and run-to-run noise

**What.** Record per User Step the signals that explain an intermittent failure: client resync events (`ClientMessageResentEvent`, `UIResynchronizationEvent`, `MessageIdSyncErrorEvent`, Flow 25.3.3+; the kit's `vaadin.resync`), session lock wait, and with the kit `vaadin.client.connection` transitions. `summary.md` marks a scenario whose passing rerun showed one, which feeds #296. Server time per step across the weekly benchmark runs gives a distribution per step rather than per test.

**Reach.** Browser runs only; resyncs don't happen on Browserless.

**Risks.** Client meters arrive up to 5 s late, after the step and maybe after the test: read them at test end, not per step. **Effort:** S–M.

### U6. Leak and state checks after a scenario

**What.** With the kit and `ui-state=true`, add `assertThat(page).hasNoStaleViews()` / `hasNoGrowingViewState()` on `vaadin.ui.state.views.stale` and `vaadin.ui.state.retained.growing`, or include the `growing-view-state` insight (view class and field) in the run summary. Repeating a refresh five times in a scenario is exactly the kit's trigger ([insights, "A Growing View Field"][d-ins]).

**Reach.** Sampling happens at UI init, after navigation and at RPC end, throttled by `ui-state-sample-interval`. So a test needs the interval lowered (say `0`–`100` ms) and, on Browserless, gets samples only from navigation [inference]. The gauges are aggregates over **all** UIs of the process, so they are clean only with one test running at a time.

**Effort:** M. **Risk:** an assertion that depends on a commercial module must fail with a message naming the module and the licence, not silently pass.

### U7. Context for agents and the recorder

**What.** (a) Put the kit insight (or the layer-A failure facts) into the failure JSON that agents read (`schemaVersion`ed, like the kit's). (b) Turn a **dev-mode insight's `replay` lines into a Unified UI Test** skeleton: "Open route '/returns'", "Set the 'Reason' Select to 'Defective'", "Click the 'Process return' Button" map one-to-one onto `navigate`, `find(UnifiedSelect).withLabel("Reason").selectItem(..)` and `find(UnifiedButton).withText("Process return").click()`. That reproduces a production report as a test, and overlaps #215 (Unified Recorder).

**Caveat.** (b) parses free text with no published grammar; the replay is a state snapshot, not a sequence, and production insights carry class names only. Ask Vaadin for a structured `replay` before building on it [opinion]. **Effort:** (a) S, (b) M plus the dependency on Vaadin.

### U8. Cross-tool divergence (roadmap C2)

The same scenario should produce the same navigation outcomes and the same fetch sequence on Browserless and in a browser. Diffing the U1 records across Testing Tools is a cheap first divergence report. **Effort:** S once U1 exists.

### Not worth doing [opinion]

- **Exporting test runs to OTLP/Jaeger.** Possible with Boot's `@AutoConfigureTracing` and a bridge ([Spring Boot testing][boot-test]), but the User Step report already answers "what happened in this test", and a trace backend in CI is a new piece of infrastructure.
- **Time budgets as failing assertions** (`serverTimeBelow(50ms)`): the Copilot panel's budgets (200 ms interaction, 50 ms server, 100 ms fetch) are "rules of thumb, not hard limits" ([copilot-panel][d-cop]), and spec 13 rejects time gates on shared runners. Report them, and at most warn.
- **Driving the Copilot panel** from tests: it is off in tests (`vaadin.devmode.devTools.enabled=false`, [02-test-setup.md](../spec/02-test-setup.md)) and polls every 3 s.

---

## 6. Packaging and detection

| Part | Where | Dependency | Detection | When absent |
|---|---|---|---|---|
| Layer A (U1, U2 core, U3 fetch counts, U4, U5, U8) | `unified-test-api` (recorder), adapters for the Browserless RPC equivalent | none new: `flow-server` already `provided` | `Class.forName` for `VaadinServiceEventBus` (25.3) and `NavigationEndedEvent` (25.4), the pattern of `internal/logs/LogbackHook` | on Vaadin 25.3.x, no request/navigation facts and no durations on ended events; the section shrinks, nothing fails |
| Kit features (U2 insight, U3 SQL, U6, U7a) | new `unified-test-observability-kit` (or `-observability`), test scope, opt-in | `com.vaadin:observability-kit-micrometer`, **`optional`** in the module's POM, so the application supplies its own kit version | `ServiceLoader` of a `StepObserver` SPI from `unified-test-api`, plus `ObservabilityKit.isLicensePresent()` and `getActiveMeterRegistry() != null` at test start | report sections absent; an explicit assertion (`hasNoStaleViews`) **fails** naming the artifact, the licence and `vaadin.observability.*` setting it needs |

Why not a Capability: [09-capabilities.md](../spec/09-capabilities.md) defines Capabilities as what a *Testing Tool* lacks, decided per test. Observability depends on the application's setup and licence, not on the Testing Tool. A "missing feature fails with a message" fits better, as an unknown adapter does in [10-packaging.md](../spec/10-packaging.md) [opinion].

Versioning follows the rule "one release line per Vaadin minor": the 25.3 line gets layer A at 25.3 level, and the 25.4 line adds request/navigation facts and requires the kit `25.4.x` (renamed numbering, V3).

---

## 7. Risks and costs

- **Licence, silently.** In development mode an unlicensed kit binds nothing and only logs a warning (section 3). A CI job without a key would report "no insights" as if nothing went wrong. The module must check `isLicensePresent()` and say so in `summary.md`.
- **Commercial dependency** in an Apache-2.0 project: acceptable by precedent (the Selenium adapter on TestBench, [10-packaging.md](../spec/10-packaging.md)), as long as it stays a separate, optional module that nothing else depends on.
- **Process-wide state.** The kit's buffers and gauges cover the whole JVM and are never reset (only package-private `reset()`/`clearBound`, [ObservabilityKit.java][okit-kit]); Spring context caching keeps them across test classes. Everything has to be a before/after delta, which is wrong under parallel tests.
- **Behaviour change in the application under test.** The kit decorates `ErrorHandler`, wraps the service `Executor` and, with `database=true`, wraps every `DataSource` bean ([reference][d-ref]). A test suite that turns it on is no longer testing exactly the production configuration, unless production runs the kit too.
- **Performance budget.** Listeners on the bus run on request threads. Recording must stay inside spec 13's 10 % / 2 ms-per-action budget; `ui-state` tree walks with a low interval are the most expensive part.
- **Spec churn.** The 25.4 docs are pre-release, and navigation semantics changed between 25.3 and 25.4 (V6). Build on the Flow events, which are public API with `@since`, rather than on meter names, which already changed once in the v4 to v5 move ([migrating-to-v5][d-mig]).

---

## Recommendation [opinion]

1. **Now, no new dependency:** U1 on the Flow event bus, with the RPC and data facts that 25.3.x already has, and request/navigation facts when the build is on 25.4. Then U2's layer-A part, merged with #289, so a failed step shows "the click's listener threw `NullPointerException` at `OrderView.save(OrderView.java:88)`". Then U4 and U8, which come almost free from the same records.
2. **Next:** U3's fetch-count ratchet, the most valuable new check, and the one only this project can run on Browserless cheaply.
3. **Later, opt-in module:** `unified-test-observability-kit` for SQL counts (U3), insight attachments (U2/U7a) and leak checks (U6), once a licence story for CI is written down.
4. **Ask Vaadin**, rather than build: a structured `replay` format (U7b), a public way to reset or scope the insight buffers to a test, and a bus event for queued `UI.access` work.

## Open questions

1. Is a "server activity" section per User Step noise for a human reader? Should it show only in JSON and on failure, as console errors do today?
2. Should the fetch/SQL count ratchet be a project feature (users commit count files for their own scenarios) or stay a tool for this repo's Performance Pairs?
3. On Browserless, should the adapter emit RPC-like records when it fires a listener, so that U1 reads the same on every Testing Tool? Or should the difference be documented, as in [09-capabilities.md](../spec/09-capabilities.md)?
4. Does a Vaadin licence key on CI (already needed for Selenium) cover Observability Kit? Which subscriptions include it was not checked.
5. Do we raise the baseline to 25.4 for request/navigation events, or keep 25.3 and degrade?
6. Is the kit's error-handler decoration acceptable in Browserless runs, given #101 (listener exceptions and the session `ErrorHandler`)?

## Not verified

- **Nothing was run**: no listener on the bus, no kit in a test, no Browserless or browser run. The Browserless rows of section 4 come from reading `Clickable.java`/`*Tester.java` and Flow's `ServerRpcHandler`/`Router`, not from execution. Whether `MockVaadin` runs in production mode, whether it fires session-lock and data events, and whether the kit's `MetricsServiceInitListener` runs under Browserless's Spring integration are unknown.
- **Vaadin 25.4 GA**: not released by 2026-11-21. The 25.4 facts are from `25.4.0-beta1` (Flow tag, kit tag, Maven Central) and docs marked "pre-release".
- **Which events the platform 25.3.5 used by this repo contains**: the `@since 25.3.3` events assume its Flow is 25.3.3 or later; the platform-to-Flow version mapping was not looked up.
- **The licence key mechanism on CI** (environment variable or file location for `license-checker`) and which subscriptions include Observability Kit: not read.
- **Spring Boot 4 test slices:** only the 4.1 testing page was read. Whether `@SpringBootTest` gives the kit a `SimpleMeterRegistry` specifically was inferred from "the in-memory backed" registry.
- **Kit integrations pages** (Prometheus, Grafana, Datadog, New Relic), `client-errors.adoc`, `customization.adoc` and `migrating-to-v5.adoc` were only skimmed or not read; nothing above depends on them except the remark on meter renames.
- The kit's git history between `5.0.1` and `25.4.0-beta1` was not read commit by commit; V6 and V7 come from the docs diff and the file list.

## Sources

[docs-main]: https://github.com/vaadin/docs/tree/b9e344068c4c8b36897e1730accebaffb859fd0a/articles/tools/observability
[docs-253]: https://github.com/vaadin/docs/tree/bc94e6191bdf4b7700eb26273f1932ccdb1e7912/articles/tools/observability
[d-gs]: https://vaadin.com/docs/next/tools/observability/getting-started
[d253-gs]: https://vaadin.com/docs/v25/tools/observability/getting-started
[d-ref]: https://vaadin.com/docs/next/tools/observability/reference
[d-conf]: https://vaadin.com/docs/next/tools/observability/configuration
[d-ins]: https://vaadin.com/docs/next/tools/observability/insights
[d-cop]: https://vaadin.com/docs/next/tools/observability/copilot-panel
[d-mig]: https://vaadin.com/docs/next/tools/observability/migrating-to-v5
[okit]: https://github.com/vaadin/observability-kit/tree/24d099ae22f69b7a6d914f2d88f6fbcc851d43ec
[okit-501]: https://github.com/vaadin/observability-kit/tree/5.0.1
[okit-readme]: https://github.com/vaadin/observability-kit/blob/24d099ae22f69b7a6d914f2d88f6fbcc851d43ec/README.md
[okit-lic]: https://github.com/vaadin/observability-kit/blob/24d099ae22f69b7a6d914f2d88f6fbcc851d43ec/observability-kit-micrometer/src/main/java/com/vaadin/observability/micrometer/ObservabilityLicense.java
[okit-kit]: https://github.com/vaadin/observability-kit/blob/24d099ae22f69b7a6d914f2d88f6fbcc851d43ec/observability-kit-micrometer/src/main/java/com/vaadin/observability/micrometer/ObservabilityKit.java
[flow]: https://github.com/vaadin/flow/tree/b128e535866970750aa969201fa0334897b22290
[flow-bus]: https://github.com/vaadin/flow/blob/b128e535866970750aa969201fa0334897b22290/flow-server/src/main/java/com/vaadin/flow/server/VaadinServiceEventBus.java
[flow-router]: https://github.com/vaadin/flow/blob/b128e535866970750aa969201fa0334897b22290/flow-server/src/main/java/com/vaadin/flow/router/Router.java
[flow-publish]: https://github.com/vaadin/flow/blob/b128e535866970750aa969201fa0334897b22290/flow-client/src/main/frontend/internal/client/publishClient.ts
[flow-mh]: https://github.com/vaadin/flow/blob/b128e535866970750aa969201fa0334897b22290/flow-client/src/main/frontend/internal/client/communication/MessageHandler.ts
[tb]: https://github.com/vaadin/testbench/tree/11908ed9fceb892fec1c854ac3d858ecf5f7dc45
[tb-cmds]: https://github.com/vaadin/testbench/blob/11908ed9fceb892fec1c854ac3d858ecf5f7dc45/vaadin-testbench-shared/src/main/java/com/vaadin/testbench/commands/TestBenchCommands.java
[rel-253]: https://github.com/vaadin/platform/releases/tag/25.3.0
[rel-254b1]: https://github.com/vaadin/platform/releases/tag/25.4.0-beta1
[mc-okit]: https://repo1.maven.org/maven2/com/vaadin/observability-kit-micrometer/maven-metadata.xml
[mc-501-pom]: https://repo1.maven.org/maven2/com/vaadin/observability-kit-micrometer/5.0.1/observability-kit-micrometer-5.0.1.pom
[mc-254-pom]: https://repo1.maven.org/maven2/com/vaadin/observability-kit-micrometer/25.4.0-beta1/observability-kit-micrometer-25.4.0-beta1.pom
[mm-reg]: https://docs.micrometer.io/micrometer/reference/concepts/registry.html
[mm-obs-test]: https://docs.micrometer.io/micrometer/reference/observation/testing.html
[mm-tr-test]: https://docs.micrometer.io/tracing/reference/testing.html
[otel-sdk]: https://opentelemetry.io/docs/languages/java/sdk/
[boot-test]: https://docs.spring.io/spring-boot/4.1/reference/testing/spring-boot-applications.html

All links above, in the order first cited: Vaadin docs source [`main`][docs-main] and [`v25.3`][docs-253]; rendered pages [getting started (25.4)][d-gs], [getting started (25.3)][d253-gs], [reference][d-ref], [configuration][d-conf], [insights][d-ins], [Copilot panel][d-cop], [migrating to v5][d-mig]; Observability Kit [source][okit], [5.0.1][okit-501], [README][okit-readme], [`ObservabilityLicense`][okit-lic], [`ObservabilityKit`][okit-kit]; Flow [source][flow], [`VaadinServiceEventBus`][flow-bus], [`Router`][flow-router], [`publishClient.ts`][flow-publish], [`MessageHandler.ts`][flow-mh]; TestBench [source][tb], [`TestBenchCommands`][tb-cmds]; platform releases [25.3.0][rel-253], [25.4.0-beta1][rel-254b1]; Maven Central [metadata][mc-okit], [5.0.1 POM][mc-501-pom], [25.4.0-beta1 POM][mc-254-pom]; Micrometer [registries][mm-reg], [observation testing][mm-obs-test], [tracing testing][mm-tr-test]; [OpenTelemetry Java SDK][otel-sdk]; [Spring Boot 4.1 testing][boot-test].
