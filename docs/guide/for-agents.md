# For agents

What an agent that writes, fixes or reviews Unified UI Tests can read, and how to check what it did. Everything here is plain files: no network service, no model of ours.

| What | Where | For |
|---|---|---|
| the `write-unified-ui-tests` skill | `.claude/skills/write-unified-ui-tests/SKILL.md` in your project | writing, fixing and *(unreleased)* reviewing tests |
| the API reference *(unreleased)* | `META-INF/unified/api-reference.md` in the `unified-test-api` jar | every Wrapper's filters, actions, reads and checks, of the version you use |
| `llms.txt`, `llms-full.txt` | [on this site](https://parttio.github.io/unified-testing-tool/llms.txt) | the rules, every page as Markdown, the guide in one file |
| `user-steps.txt`, `user-steps.json` | each test's evidence directory | what a test did, step by step, and why it failed |
| `summary.json` *(unreleased)* | the run's evidence directory | what failed in a run, and where each test's files are |

## The skill

The `AddUnifiedTestingSkill` recipe writes the skill to `.claude/skills/write-unified-ui-tests/SKILL.md` and points to it from `AGENTS.md`, or from `CLAUDE.md` when the project has no `AGENTS.md` *(unreleased)*. Every Migration Recipe runs it, and it runs alone too ([Migrating from TestBench, the agent skill](../migrating-from-testbench.md#the-agent-skill)). The skill ships with each release and matches its API; the recipe replaces an older copy and keeps one you edited by hand.

With it, an agent:

- **writes** a test from a scenario: it reads the view's code before writing a lookup, proves the test on Browserless and Playwright, runs the style rules, and checks the test's `user-steps.txt` against the scenario;
- **fixes** a failing test: it reads the failure, and decides whether the test or the application is wrong before changing anything;
- **reviews** a test *(unreleased)*: it applies the checklist of [Reviewing a test](reviewing-tests.md) and reports its findings without changing the code.

## The rules

The skill follows the guide's [rules of thumb](index.md#rules-of-thumb). They are also at the top of `llms.txt`, for agents without the skill, and `docs/snippets/rules-of-thumb.md` is their one source.

## Reading a run

An agent reads the same outputs as a person ([Reading reports and failures](reports-and-failures.md)):

- **The failure message** has fixed labels in a fixed order, with `Did you mean` when one Near Miss is clearly meant. The same parts are in the `failure` of `user-steps.json`.
- **The Findable View** *(unreleased)*, `<test>.view.txt` next to a failure, lists what the page held as lookups to paste. In a test, `page.describe()` prints it.
- **`summary.json`** *(unreleased)* says which tests failed, why, and where their files are ([The run as data](reports-and-failures.md#the-run-as-data)).

## Checking what an agent did

Treat a test an agent wrote as a colleague's: read its User Steps against the scenario before its code, run it again, and look for changes outside the tests. [Reviewing a test](reviewing-tests.md) has the checklist and the order, and [Common mistakes](common-mistakes.md) the code to look for. The run summary helps in a pull request: *Changed in this PR* shows how each changed scenario's steps differ, and *Scenarios without a check* *(unreleased)* lists the tests that end without one.
