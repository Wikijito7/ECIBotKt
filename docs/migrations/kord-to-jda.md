# Kord → JDA 6.7 Migration Plan

Tracking issue: [#114](https://github.com/Wikijito7/ECIBotKt/issues/114) — *Migrate to JDA / jda-ktx*
Epic integration branch: `feature/114-migrate-to-jda-jda-ktx`, based on `master` @ `4badb92`; it
remains the merge baseline for this epic until PR #115 lands.
Active working branch: `feature/114-kord-to-jda-migration` — draft PR
[#115](https://github.com/Wikijito7/ECIBotKt/pull/115) targets the **epic branch**, not `master`.
Phase 1B subphase branch: `feature/114-native-failfast-check`, based on the working branch; its PR
targets the working branch, not the epic branch. Its negative fail-fast test **passed** on
2026-11-08 (see the Phase 1B result below).
Phase 2B subphase branch: `feature/114-phase-2b-command-port`, based on Phase 1B
`feature/114-native-failfast-check`; draft PR
[#117](https://github.com/Wikijito7/ECIBotKt/pull/117) targets that Phase 1B branch (PR #116), not
#115. Phase 2B is **IN PROGRESS** (see the Phase 2B section below), not complete. On head `001e722`
the #117 quality and build jobs passed but SonarCloud failed its Quality Gate at 0.0% coverage on
new code because `:core` and `:jda-runtime` had no JaCoCo XML reports; the branch now adds JaCoCo
0.8.12 to both modules and makes root `sonar` depend on their reports, but the PR gate has **not**
been re-run or verified, and no staging interaction has been recorded.

> **This is a plan, not implementation.** Nothing below is proven until the gate it belongs to
> produces recorded evidence. "Fact" = verified against the repository or upstream sources today.
> "Gate" / "Recommendation" = untested and must be validated before being relied on.

---

## Progress record (verified, 2026-11-08)

Status labels: **PASS** = recorded evidence, gate closed · **IN PROGRESS** = work started, gate
still open · **OPEN** = not started, gate still open.

| Item | Status | Recorded |
| --- | --- | --- |
| Phase 1 — dependency and DAVE transport feasibility | **PASS** (staging spike) | Subphase **1A** passed on staging (protocol v1 + audible tone) and subphase **1B** passed on 2026-11-08 (exit `3` `EXIT_NATIVE` with no gateway login); every Phase 1 exit criterion now has recorded evidence. Root dependency alignment and root fat-JAR checks remain **Phase 6**, not Phase 1 |
| Phase 1B — missing-DAVE-native fail-fast simulation | **PASS — gate closed (2026-11-08)** | User-run negative container on Linux amd64, Temurin 21.0.13 (`eclipse-temurin:21`), negative no-DAVE-native JAR: exit exactly `3` (`EXIT_NATIVE`), DAVE native unavailable with `UnsatisfiedLinkError: Required library was not found`, **zero** `[gateway] logging in` and **zero** `[gateway] ready` lines, fake placeholder token never printed. The host pre-pulled the image, so the image pull was not itself isolated; the container ran with `--network none`. Sanitized record only |
| R1 — `jda-ktx 0.14.2` + JDA 6.7.0 pairing | **IN PROGRESS — transport subgate PASS only** | Candidate resolved and exercised for voice transport; still not an upstream-declared combination and not yet re-verified on the final Phase 6 artifact |
| R2 — `adapter-jda` 0.2.0 vs JDA 6.7.0 ABI | **IN PROGRESS — transport subgate PASS only** | DAVE negotiated and played through JDA; published metadata still does not declare or test 6.7.0, and the ABI is not yet re-verified on the final Phase 6 artifact |
| Phase 2 — JDA bootstrap, events, command response | **IN PROGRESS** | Phase 2A neutral repository/use-case groundwork is on `feature/114-kord-to-jda-migration` and proposed to the epic branch through draft PR #115; commit and push state are read from Git and PR #115, not asserted here. Phase 2B's neutral command boundary and isolated JDA runtime now exist with unit tests on child branch `feature/114-phase-2b-command-port`, and JaCoCo coverage is wired for both isolated modules, but nothing is wired into the root runtime and the command layer and locale service/command neutrality remain **OPEN**. Remaining work is split into subphases 2B, 2C and 2D, each with its own child branch and PR based on the immediately preceding unmerged branch (see Phase 2) |
| Phase 2A — domain-owned ID/locale values + locale persistence refactor | **IN PROGRESS** | Phase 2A source and test changes are present on `feature/114-kord-to-jda-migration`, and draft PR #115 targets the epic branch; whether they are committed or pushed is read from Git and PR #115, not asserted here. `GuildId`/`LocaleCode` and the locale repository/use cases are **neutral**, but the Phase 2A scope itself is not finished: `LocalizationService` and `/locale` remain Kord-facing and must be moved behind/adapted in a later Phase 2 subphase (2D) |
| Phase 2B — neutral command context + responder/response port | **IN PROGRESS** | Implementation and unit tests are present on `feature/114-phase-2b-command-port` as the initial implementation checkpoint `fbd9f5a`, stacked on Phase 1B `feature/114-native-failfast-check`; draft PR #117 targets that Phase 1B branch (PR #116), not #115. The branch adds isolated Gradle modules `:core` (gateway-free neutral ports and a pure walkthrough handler) and `:jda-runtime` (JDA 6.7.0 adapter, guild-only `/jda-walkthrough`); the root project depends on `:core` only and remains Kord-based and JDA-free. On head `001e722` the #117 quality and build jobs passed while SonarCloud failed its Quality Gate at 0.0% coverage on new code (required >=50%) because the modules emitted no JaCoCo XML; the branch now adds JaCoCo 0.8.12 to both modules and root `sonar` depends on their reports. The PR Sonar gate is **not** re-verified, and manual staging login/interaction is still pending, so the gate is **not** closed |
| Phase 2C — voice/session/player message ports and adapters | **OPEN** | Not started. Planned child branch and PR based on the immediately preceding unmerged branch (2B); do not restructure #115 |
| Phase 2D — channel/message/locale edge adapters | **OPEN** | Not started. Planned child branch and PR based on the immediately preceding unmerged branch (2C); carries the `LocalizationService` and `/locale` neutrality work |
| Phase 6 — Kord-free build, artifact and digest gates | **OPEN** | Not started |
| Production cutover (Phase 7) | **OPEN** | Blocked by the Phase 6 and staging gates |

### Phase 1 DAVE transport subgate — PASS (Phase 1 now PASS overall)

Run on 2026-10-30 in the external Temurin 21 Linux x86_64/amd64 container, against the separate
staging bot, using the spike candidate **JDA 6.7.0, jda-ktx 0.14.2, Kotlin 2.3.10, libdave 0.2.0**:

- DAVE native loaded, `maxSupportedProtocolVersion=1`.
- JDA Opus encoder available.
- Voice reached `CONNECTED`; `DAVE Init - Protocol version=1` observed.
- 75 frames provided to JDA; a user in the channel confirmed the tone was audible.
- One-shot run exited `0`.

This run used the isolated spike build with spike-local dependency overrides. It did **not**
validate the root version catalog or the root fat JAR: no root dependency alignment was performed,
and no root fat-JAR build, packaging inspection or startup was attempted. Root dependency
alignment and final-artifact checks belong to the Kord-free build phase (Phase 6), not Phase 1.

The companion fail-fast simulation with a **missing native** (subphase **1B**) was **not** part of
this run; it passed separately on 2026-11-08 (see the Phase 1B result below). With both subphases
recorded, Phase 1 has evidence for every exit criterion and its gate is closed.

Scope limit: this verifies **JDA/DAVE voice transport only**. It does **not** verify production
Lavaplayer integration, the production audio pipeline, commands, the per-guild serialization
design, the root dependency graph, or the root fat JAR — those stay in their own gates. The public
JDA API does not attribute the cause of a `NOT_CONNECTED` state, so no cause is claimed here.

Operational note: the staging container's restart policy restarted the one-shot process after it
had exited. Stop the service, or disable the restart policy for strictly one-shot probe runs,
before repeating the test.

### Phase 1B fail-fast negative test — PASS (recorded 2026-11-08)

User-run negative test with the **no-DAVE-native** fat JAR
(`jda-dave-spike-negative-no-dave-native-0.1.0-all.jar`) in the Linux amd64
`eclipse-temurin:21` container, Temurin **21.0.13**, against no live Discord endpoint.

| Check | Result |
| --- | --- |
| Host / runtime | Linux amd64, Temurin 21.0.13 (`eclipse-temurin:21`) |
| Artifact | Negative no-DAVE-native JAR (DAVE native entry deliberately absent) |
| Container exit code | exactly `3` (`EXIT_NATIVE`) |
| DAVE native | Unavailable — `UnsatisfiedLinkError: Required library was not found` |
| `[gateway] logging in` lines | **zero** |
| `[gateway] ready` lines | **zero** |
| Placeholder token | fake placeholder; never printed |

Scope of this evidence:

- It proves only that the probe **fails fast when the DAVE native is missing**, before any gateway
  login. It is a guard check, not transport evidence; the audible DAVE run above remains the
  transport evidence.
- The container itself ran with `--network none`. The image was **pre-pulled on the host before that
  container run**, so the image pull was **not** network-isolated and this run must not be described
  as a fully offline verification end-to-end.
- Packaging alone never closed this gate; the recorded container execution is what closed it.
- Sanitized record: no token, no bot/guild/channel/user identifiers and no raw log lines are
  recorded here or in the repository.

With this result, subphase **1B is PASS and its gate is closed**, the Phase 1 stop condition no
longer blocks Phase 2, and Phase 2B is **unblocked**; a Phase 2B implementation checkpoint now
exists on `feature/114-phase-2b-command-port` and is **IN PROGRESS**, not complete.

### Phase 2A — domain-owned ID/locale values and locale persistence — IN PROGRESS

The active feature branch `feature/114-kord-to-jda-migration` carries this plan,
`.opencode/opencode.json`, `spikes/jda-dave/**`, and the Phase 2A source and test changes; draft PR
#115 targets the epic integration branch. Commit status and history are not asserted by this
document — read them from Git and the pull request.

- `GuildId(ULong)` now lives in `es.wokis.domain.model` and `LocaleCode` in
  `es.wokis.domain.locale`; both are **domain-owned**, platform-neutral values.
- The locale repository and its use cases (`LocaleRepository`, `LocalJsonLocaleRepository`,
  `GetGuildLocaleUseCase`, `SetGuildLocaleUseCase`) speak only those domain values.
- `LocalizationService` is **not** neutral: its public signatures still take Kord `Snowflake` /
  `Locale` and convert to the domain values internally. The `/locale` command is also still
  Kord-facing. This is expected: **Phase 2A is a neutral-values/persistence checkpoint, not the
  endpoint**. `LocalizationService` and `/locale` must be moved behind a neutral port and adapted
  at the Discord edge in a **later Phase 2 subphase** (planned **2D — channel/message/locale edge
  adapters**); until then, the Phase 2A boundary is incomplete by design and must not be recorded
  as fully neutral.
- Kord conversions stay at the existing Kord edge (`utils/LocaleExtensions.kt` and the
  Kord-facing service/command), not in the repository or use cases.
- The persisted `guild_locales` JSON shape is preserved
  (`{ "guild_locales": { "<decimal-id>": "<locale-code>" } }`), so existing
  `data/guild_locales.json` files stay readable.
- Kord's `Snowflake` clamps its value to `0..Long.MAX_VALUE`; the domain `GuildId` keeps the full
  `ULong` decimal range and only narrows when converted to a Kord `Snowflake`.
- No JDA dependency was added to the root runtime; Kord is still the runtime gateway library.
- Phase 2A full-suite result after the domain package move and the test-import update: **380 tests,
  0 failures, 0 errors, 5 pre-existing skips**.
- Repository tests now use a JUnit-managed temporary file instead of reading or writing the real
  `./data` directory; the production constructor still resolves `./data/guild_locales.json`.
- Phase 2A remains **IN PROGRESS** and is **not** marked complete: it records only the neutral
  `GuildId`/`LocaleCode` and locale persistence boundary. JDA bootstrap, shard/event wiring, the
  command dispatch/response layer, and locale service/command neutrality are **OPEN** and belong to
  the later Phase 2 subphases below.

#### Phase 2 subphase plan and stacked branches

Phase 2 is delivered in subphases. Each subsequent subphase gets its **own child branch and PR based
on the immediately preceding unmerged branch**; it is not folded into an already-open earlier PR.
Phase 2A is the checkpoint already on `feature/114-kord-to-jda-migration`, and **draft PR #115 is
the agreed combined Phase 1/2A checkpoint — it is not restructured**. Phase 2B therefore branches
from, and targets, the Phase 1B branch `feature/114-native-failfast-check`, not #115 directly; 2C
then branches from 2B and 2D from 2C. The epic branch `feature/114-migrate-to-jda-jda-ktx` remains
the **final integration target**, and `master` is merged only at migration completion.

| Subphase | Scope | Branch/PR posture | Status |
| --- | --- | --- | --- |
| **2A** | Domain-owned `GuildId`/`LocaleCode` + neutral locale repository/use cases | On `feature/114-kord-to-jda-migration`; draft PR #115 is the agreed combined Phase 1/2A checkpoint; leave it as-is | **IN PROGRESS** — as recorded above |
| **2B** | Neutral command context + responder/response port; adapters translate defer/ephemeral, locales and IDs | Child branch `feature/114-phase-2b-command-port`, based on the immediately preceding unmerged branch, Phase 1B `feature/114-native-failfast-check`; draft PR #117 targets that branch, not #115 | **IN PROGRESS** — isolated `:core`/`:jda-runtime` modules, neutral command boundary and JDA 6.7.0 adapter implemented with unit tests; CI on #117 pending, independent QA/review and staging evidence pending |
| **2C** | Voice/session/player-message narrow ports; JDA `AudioSendHandler`/DAVE and lifecycle stay in the adapter | New child branch/PR based on the immediately preceding unmerged branch (2B) | **OPEN** — not started |
| **2D** | Channel/message/locale edge adapters; move `LocalizationService` and `/locale` behind/adapted to a neutral port | New child branch/PR based on the immediately preceding unmerged branch (2C) | **OPEN** — not started |

Stacking rules:

- Each subsequent subphase branches from, and opens its PR against, the **immediately preceding
  unmerged branch** — never `master` and never the epic branch directly, until the epic integration
  step. For Phase 2B that preceding branch is Phase 1B, `feature/114-native-failfast-check`.
- PR #115 stays the **combined Phase 1/2A checkpoint**; it is not restructured and later subphase
  work is not pushed into it.
- Subphase branches are **not** squashed into #115 or rebased onto `master` mid-flight.
- A subphase is recorded complete only when its own gate evidence exists. **Open subphases are
  never marked complete** because an earlier checkpoint passed.

#### Phase 2A — recorded behavior shifts (tested)

- Raw invalid locale codes now pass through the neutral repository unchanged; parsing and fallback
  happen at the Kord edge via `LocaleCode.toKordLocaleOrNull()`. For `LocalizationService` this is
  externally equivalent: an invalid stored code still falls back to the supplied Discord locale,
  then to `en-US`.
- `getAllGuildLocales` now returns raw `LocaleCode` values rather than defaulting malformed entries
  to `en-US`. It has **no production caller**; only tests exercise it.
- The rest of the repository contract is unchanged: `getGuildLocale` returns `null` when no value is
  stored, `setGuildLocale`/`removeGuildLocale` propagate write failures by throwing, and the
  persisted JSON shape and raw stored values are preserved.

### Phase 2B — neutral command boundary + isolated JDA runtime — IN PROGRESS

Phase 2B is implemented and unit-tested on the child branch
`feature/114-phase-2b-command-port`, stacked on Phase 1B
`feature/114-native-failfast-check`; draft PR #117 targets that Phase 1B branch (PR #116), not #115.
Commit `fbd9f5a` is the **initial implementation checkpoint**; the documentation commit that records
this handoff follows it, so read the current head from Git rather than from this line. CI on #117
head `001e722` ran and is recorded below; this is a **work-in-progress checkpoint, not a closed
gate**.

What the branch contains today:

- **Isolated Gradle modules.** `settings.gradle.kts` includes `:core` and `:jda-runtime`. The root
  project adds only `implementation(project(":core"))`; it remains Kord-based (local
  `libs/kord-*.jar`) and declares **no** JDA dependency or JDA runtime wiring. JDA 6.7.0 and
  coroutines are declared in the version catalog and consumed only by `:jda-runtime`.
- **`:core` is gateway-free.** It declares no Kord or JDA dependency and contains the neutral
  `CommandContext` (`guildId`, `channelId`, `userId`, `locale`, all domain values),
  `CommandHandler`, `CommandResponder` (at most one `defer` and one `respond`; after `defer` the
  response edits the original and visibility stays fixed by the defer), `CommandResponse`, the pure
  `WalkthroughCommand` handler, and the domain-owned `GuildId`/`LocaleCode` values moved out of the
  root source set alongside the new `ChannelId` and `UserId` values introduced by `:core`.
- **`:jda-runtime` is the only gateway adapter.** It depends on `:core` and JDA 6.7.0 and contains
  `JdaBootstrap`, `JdaCommandRouter` (the gateway callback only identifies the handler; handler and
  REST work run off the JDA event thread), `JdaCommandContextMapper`, the state-machine
  `JdaCommandResponder` (a failed REST action restores the previous state so the router can fall
  back safely) and the coroutine `RestActionBridge`.
- **Guild-only walkthrough command.** The only registered command is `/jda-walkthrough`, upserted
  guild-scoped from `DISCORD_DEV_GUILD_ID`; `DISCORD_BOT_TOKEN` and `DISCORD_DEV_GUILD_ID` are
  mandatory and the isolated runtime fails fast without them. No global command is published, no
  gateway intents are enabled, and the isolated JDA entry point is **not** wired into the root
  runtime.
- **Deliberately out of scope for 2B:** `/locale` and `LocalizationService` stay Kord-facing (2D),
  voice/DAVE and session/player ports are 2C, and root bootstrap/fat-JAR wiring stays Phase 6.

Unit tests exist in both modules. The last local run recorded **18 `:core` tests** and
**62 `:jda-runtime` tests**; the root project suite recorded **380 tests with 5 pre-existing
skips**, which is **460** tests combined. That is local implementation evidence only, **not** gate
evidence, and no staging or Phase 2B pass is claimed.

#### Phase 2B coverage and CI checkpoint (2026-11-08)

- **PR #117 CI on prior head `001e722`** (draft; head branch `feature/114-phase-2b-command-port`,
  base `feature/114-native-failfast-check` = #116): **Check Code Quality passed**, **Build and
  analyze completed successfully**, and **SonarCloud Code Analysis failed its Quality Gate** at
  **0.0% Coverage on New Code** against a required **>=50%**. Root cause: `:core` and
  `:jda-runtime` had **no JaCoCo XML reports** for Sonar to consume.
- **Coverage wiring now on the branch.** JaCoCo `0.8.12` (from the version catalog) is applied in
  both modules; each module's `tasks.test` finalizes its own `jacocoTestReport`, which requires XML
  at the module-local default `build/reports/jacoco/test/jacocoTestReport.xml`; the root `sonar`
  task depends on `:core:jacocoTestReport` and `:jda-runtime:jacocoTestReport`. The change touches
  only the three Gradle build files — no root runtime, `fatJar` task, dependency or wiring change.
- **Local QA (not CI, not gate evidence).** Module XML reports exist; line coverage is `:core`
  **81.40%** (35/43), `:jda-runtime` **73.63%** (148/201), combined **75.00%** (183/244). Tests are
  unchanged at **18 `:core` / 62 `:jda-runtime` / 380 root with 5 pre-existing skips = 460**. The
  Code Quality and review agents passed **locally**.
- **Not done:** manual staging login and `/jda-walkthrough` interaction. **Not claimed:** the PR
  #117 Sonar gate is fixed or passing — the wiring has **not** been re-run or verified on CI yet.
  Phase 2B remains **IN PROGRESS**.

Acceptance for closing 2B (not yet met):

- `:core` compiles with no Kord/JDA dependency, and the neutral boundary exposes no `dev.kord.*` /
  `net.dv8tion.*` types. The branch carries a reflection-based unit test over the neutral types; the
  full import rule covering all core packages remains the Phase 2 exit/CI gate and is **not** closed
  by it.
- The JDA responder honours the neutral contract under test: one defer, one response, deferred
  responses edit the original with the defer visibility, and a failed REST action restores the prior
  state so the router fallback stays reachable.
- The bootstrap registers exactly one guild-scoped command and refuses to start without a token and
  a development guild id; no global registration path exists.
- Independent final QA/review passes, and any required manual staging login plus a
  `/jda-walkthrough` interaction are recorded. Local Code Quality and review agents passed, but the
  PR CI has not been re-run since the coverage fix and manual staging is still pending. Until then
  Phase 2B stays **IN PROGRESS**, is not described as complete, and no staging pass is claimed.

### Checkpoint state

- The epic integration branch `feature/114-migrate-to-jda-jda-ktx` remains at its base
  `4badb92`. The active child branch is `feature/114-kord-to-jda-migration`; open draft PR #115
  targets the epic branch.
- Phase 1B is stacked one level deeper: `feature/114-native-failfast-check` branches from the head of
  `feature/114-kord-to-jda-migration` and will PR back into that parent branch, not into the epic
  branch. It carries only the negative-test packaging task, the spike runbook section and these plan
  edits; the parent branch is not rebased or rewritten for it. Its gate is **PASS** as of
  2026-11-08.
- Phase 2B is stacked one level deeper again: `feature/114-phase-2b-command-port` branches from
  Phase 1B and its draft PR #117 targets `feature/114-native-failfast-check` (PR #116). It adds the
  isolated `:core`/`:jda-runtime` modules plus the root `:core` dependency and version-catalog
  entries; the parent branches are not rebased or rewritten for it. Initial implementation checkpoint
  `fbd9f5a`; #117 CI on head `001e722` passed Check Code Quality and Build and analyze but failed the
  SonarCloud Quality Gate at 0.0% coverage on new code, and the branch now adds JaCoCo 0.8.12 to
  `:core`/`:jda-runtime` plus module XML reports wired into root `sonar`. The PR gate has not been
  re-run since that wiring, and no staging interaction has been recorded.
- `.opencode/opencode.json`, this plan and `spikes/jda-dave/**` are **tracked** on
  `feature/114-kord-to-jda-migration`; the Phase 1 checkpoint `8ca6242` records them in branch
  history. They are part of the migration record and are updated as the migration proceeds.
- The active feature branch `feature/114-kord-to-jda-migration` contains this plan, the OpenCode
  configuration, `spikes/jda-dave/**`, and the Phase 2A source and test changes; draft PR #115
  targets the epic integration branch. Commit status and history are read from Git and the pull
  request, not asserted by this document.
- PR #115 is the **agreed combined Phase 1/2A checkpoint** and is **not restructured**. Later
  subphases land as separate child branches/PRs **based on the immediately preceding unmerged
  branch**: Phase 1B on `feature/114-native-failfast-check` (PR targets the parent feature branch),
  then Phase 2B as a child of Phase 1B targeting that branch, and 2C/2D chained the same way. The
  epic branch remains the **final integration target**; `master` is merged only at migration
  completion.
- No secrets, bot/guild/channel/user identifiers, tokens or raw logs are recorded in this plan or
  in the spike.

### Handoff snapshot — 2026-11-08

Branch, commit and PR facts below were read from Git and GitHub; they are **not** gate evidence.

| Branch / PR | Handoff state |
| --- | --- |
| `feature/114-migrate-to-jda-jda-ktx` (epic baseline) | Still at `4badb92`; unchanged |
| `feature/114-kord-to-jda-migration` → draft PR #115 (base: epic) | Remote head `67802d1`. Carries the combined Phase 1/2A checkpoint: the plan/spike record and the neutral locale repository/use-case boundary. Phase 1 is **PASS** (1A + 1B recorded), Phase 2A is **partial**, Phase 2B is **IN PROGRESS** on its child branch |
| `feature/114-native-failfast-check` → draft PR #116 (base: the 2A feature branch) | Remote head `1461204` at handoff. Artifact, runbook and the pass record live here. **Phase 1B PASS — gate closed on 2026-11-08** |
| `feature/114-phase-2b-command-port` → draft PR #117 (base: the Phase 1B branch, PR #116) | Initial implementation checkpoint `fbd9f5a`; the documentation commit that records this handoff follows it, so read the current head from Git. Carries the isolated `:core`/`:jda-runtime` modules, the neutral command boundary, the JDA 6.7.0 adapter and the guild-only `/jda-walkthrough`, with unit tests, plus JaCoCo 0.8.12 coverage for both modules wired into root `sonar`. **Phase 2B IN PROGRESS** — CI on head `001e722` passed quality/build and failed the SonarCloud gate at 0.0% new-code coverage; the coverage wiring is not yet re-verified on CI, and manual staging interaction evidence is still pending |

- Phase 1B evidence, sanitized: user-run negative no-DAVE-native JAR in the Linux amd64
  `eclipse-temurin:21` container (Temurin 21.0.13), exit exactly `3` (`EXIT_NATIVE`), DAVE native
  unavailable with `UnsatisfiedLinkError: Required library was not found`, **zero**
  `[gateway] logging in` and **zero** `[gateway] ready` lines, fake placeholder token never printed.
  The image was pre-pulled on the host before the `--network none` container run, so the pull itself
  was **not** network-isolated. No token, identifier or raw log is recorded.
- The negative-jar packaging verifier (`verifyNegativeTestJar`) still passes locally, but packaging
  was never the gate; the recorded container execution is what closed it.
- The positive Phase 1 DAVE transport staging run **passed**: Temurin 21, Linux amd64, protocol
  version 1, audible tone, exit `0`. Together with 1B it closes Phase 1; root artifact checks stay
  in Phase 6.
- Last local validation (earlier checkpoint, not re-run for this documentation change): root
  build/tests passed with **380 tests, 5 pre-existing skips**; the isolated spike passed **75
  tests**. Phase 1B changes are documentation, Gradle task and logging only — no production JDA
  dependency was added.
- CI, last known (not re-verified): PR #115's SonarCloud job failed with a **403 on JRE
  provisioning**, while **Check Code Quality passed**; the user is updating `SONAR_TOKEN`. A
  rerun or fix is **not** confirmed here — refresh CI for current status.
- Branch policy: each subsequent subphase gets its **own child branch and PR based on the
  immediately preceding unmerged branch**. Phase 2B is a child of Phase 1B
  (`feature/114-native-failfast-check`) and targets that branch; PR #115 stays the combined
  Phase 1/2A checkpoint.

Next actions, in order:

1. Re-read CI on draft PR #117 (`feature/114-phase-2b-command-port` → base
   `feature/114-native-failfast-check`, PR #116) after the JaCoCo coverage commit; the prior head
   `001e722` passed quality/build and failed SonarCloud at 0.0% new-code coverage, so the new wiring
   must be confirmed by CI before any gate claim. Leave #115 as the combined Phase 1/2A checkpoint and
   do not merge #117.
2. Once CI is green, prepare the staging `/jda-walkthrough` evidence with the isolated
   `:jda-runtime` against the staging bot, then close Phase 2B's acceptance: independent final
   QA/review plus the recorded staging login and `/jda-walkthrough` interaction. Do not record 2B as
   complete, and do not claim a staging pass, before that evidence exists.
3. Push this Phase 1B pass record to PR #116 and refresh CI status for #115/#116.

---

## 1. Context and settled decisions

### Baseline

| Item | State |
| --- | --- |
| Baseline commit | Clean `master` @ `4badb92` |
| Epic integration branch | `feature/114-migrate-to-jda-jda-ktx`, based on `master` @ `4badb92`; the merge baseline until PR #115 lands |
| Working branch | `feature/114-kord-to-jda-migration`; draft PR #115 targets the epic branch, not `master` |
| Assistant receive-audio | Stays separately stashed; **out of scope** for this migration |
| Parked PRs | #106, #109, #111 — parked, ported individually **after** cutover |
| Docker / deployment | Deployment stays a fat JAR. Do **not** add a `Dockerfile` to this repo |
| Target environment | Linux x86_64 MVP, user's external `eclipse-temurin:21` image |

### Driver

JDA **6.7.0** is the migration target because of the Discord channel-obfuscation change due
**2026-11-16**. That date is a *readiness* target, not a hard cutover date.

JDA **6.7.0 intentionally supersedes** the **6.4.2** pin proposed earlier in issue #114. That
older pin predates the channel-obfuscation handling 6.7.0 provides and cannot meet the
2026-11-16 deadline. 6.4.2 is **not** a fallback and must not be reintroduced.

### Parity and quality bar

- Full current-`master` feature parity is required at production cutover.
- The three existing button-routing fixes ship with tests and are treated as part of parity,
  not as follow-up work.

### Settled decisions (locked)

These decisions were made by the user and are not open for re-litigation during implementation:

| Decision | Locked state |
| --- | --- |
| Schedule | 2026-11-16 is a **readiness target**, not a hard cutover date |
| Kord contingency | **None** — no Kord-side changes, no Kord rollback path |
| Cutover disruption | A **brief outage** is acceptable |
| Feature bar | **Full current-`master` parity**, including the three button-routing fixes with tests |
| Assistant receive-audio | **Excluded** from this migration |
| Parked PRs | #106, #109, #111 stay **parked**, ported individually after cutover |
| Artifact | Linux x86_64 **fat JAR**; no `Dockerfile` is added to this repo |
| Staging | Separate **staging bot/token** and the **full staging matrix** before production |
| Concurrency | **Per-guild serialization** via a narrow actor/mailbox |
| jda-ktx | **Optional**; if 0.14.2 + JDA 6.7.0 fails, fall back to direct JDA + coroutine bridge |

### Delivery and rollback posture

- No Kord-side changes are made as a contingency.
- No Kord rollback path is maintained; a brief outage during cutover is acceptable.
- A separate staging bot/token and a full staging matrix are mandatory before the switch.

### Version posture

- Keep **Kotlin 2.3.10** and **Java 21** initially.
- Try **jda-ktx 0.14.2** with an explicit **JDA 6.7.0**. This pairing is **not officially
  declared** by upstream; it is a hypothesis to be tested.
- If the pair is incompatible, fall back to **direct JDA + a minimal coroutine bridge** rather
  than upgrading Kotlin solely to satisfy KTX.
- Actual version resolution, compilation, and one real interaction round-trip are an **early gate**.

### DAVE

- Align **all** `libdave` modules — `api`, `impl-jni`, `adapter-jda`, and the `natives-*`
  artifacts — at **0.2.0**, Linux x86_64 native. Do not mix versions across modules.
- **`adapter-jda` is a new dependency.** It is **not** present in the root version catalog today
  (`libdave = "0.1.2"`, `libdave-natives = "0.1.3"`, and no `adapter-jda` entry). The isolated
  spike declares and proves the 0.2.0 module set with spike-local versions. Adding `adapter-jda`
  to `gradle/libs.versions.toml` and bumping the root `libdave` / `libdave-natives` references to
  `0.2.0` is **deferred to the final root dependency replacement stage (Phase 6)**; Phase 1 does
  **not** change the root catalog, which still carries the Kord-era versions.
- **Adapter evidence, qualified:** the upstream `libdave` source build catalog declares
  `compileOnly` **JDA 6.3.1**. The published `adapter-jda` artifact metadata does **not** declare
  or test against JDA **6.7.0**. ABI compatibility with 6.7.0 is therefore **inferred, not
  certified**.
- That inference is not accepted risk. Real DAVE-enabled voice playback through JDA on the Linux
  x86_64 staging bot is a **mandatory go/no-go gate**. If it fails, migration progression pauses
  until the incompatibility is resolved and re-tested or the user approves a defined alternative.
- DAVE-enabled real voice playback on staging is required **before** removing Kord.
- Ship fail-fast native availability checks and fat-JAR content checks.

### Framework replaceability goal (acceptance criterion)

The migration is **not** only a Kord → JDA swap. Its architectural goal is that the Discord
library is **replaceable**: if JDA is ever replaced again, most business logic stays intact and the
change is confined to adapter/edge code. This goal is an **acceptance criterion for migration
completion**, not an optional aspiration.

- Domain value objects, use cases, repositories and core business services must **not** import
  `dev.kord.*` or `net.dv8tion.*`. They speak neutral value types — for example `GuildId`,
  `LocaleCode`, and domain-owned channel/user/message identifiers — and collaborate through
  **domain-owned ports**.
- Repository **interfaces belong in the domain**; their **implementations live in
  infrastructure/repository adapters**. The domain never sees a Kord/JDA-backed implementation type.
- Commands and application handlers receive a **neutral command context** plus a
  **responder/response port**, not Kord/JDA interaction objects or response-builder types. The
  Kord/JDA adapters translate responses, defer/ephemeral behaviour, locales, IDs and component IDs
  **at the edge**.
- Voice/audio transport and Discord message/channel delivery sit behind **narrow ports**.
  Lavaplayer, queue, TTS and radio behaviour stays **framework-neutral**; JDA `AudioSendHandler`,
  DAVE and JDA lifecycle stay **in the adapter**.
- Use only the ports the business logic actually needs. Do **not** build a generic facade over every
  JDA API — that would re-couple the domain to one library's shape.

**Enforcement gate:** before migration completion, an **architectural test/CI rule** must forbid
`dev.kord.*` and `net.dv8tion.*` imports outside explicitly declared Discord adapter packages.
Tests and core code must mock the **neutral ports**, not Kord/JDA types. Without that rule in
place and passing, the replaceability criterion is **not** met.

### Architecture guardrail

Kord and KTX both dispatch events concurrently. The JDA design must add a **narrow per-guild
actor/mailbox** that serializes:

- player creation
- queue and track transitions
- voice lifecycle (connect / reconnect / disconnect)

Rules that follow from this:

- Lavaplayer callbacks may only **enqueue** work; they must never block.
- JDA's `AudioSendHandler` must consume **exactly one Opus frame per send cycle**, preserve pause
  behaviour, and never block the audio thread.

---

## 2. Scope inventory (verified against `master` @ `4badb92`)

| Metric | Value |
| --- | --- |
| Main Kotlin files importing Kord | 46 / 86 |
| Test Kotlin files importing Kord | 35 / 58 |
| Tests living in Kord-dependent files | 190 / 350 |
| Top-level command groups | 13 (`disconnect`, `locale`, `next`, `play`, `player`, `queue`, `radio`, `reconnect`, `shuffle`, `skip`, `sound`, `sounds`, `tts`) |
| Custom component IDs | 18 (`ComponentsEnum`) |
| Assistant receive-audio in `master` | Absent |

The issue's current **17–19 day** estimate is **unvalidated and likely optimistic**: it predates
the DAVE/JDA 6.7 ABI proof, the staging matrix, and the per-guild serialization work.

---

## 3. Phases

Each phase exits only on its objective criteria, and every gate is **go/no-go**. A phase may not
start its exit review while a prior gate is open. An unsuccessful gate is a **stop condition**:
progression pauses until it is resolved and re-tested or the user approves a defined alternative.
A documented blocker, a "known issue", or an accepted-risk note is **not** a pass.

### Phase 0 — Baseline and scope

- Freeze the baseline at `master` @ `4badb92`; record the epic integration branch and the separate
  child working branch/PR that carries migration commits.
- Re-derive the scope inventory above and record it in the issue.
- Confirm parked PRs and the stashed assistant work stay untouched.

**Exit criteria**
- The epic integration branch `feature/114-migrate-to-jda-jda-ktx` is based on `master` @
  `4badb92`, and migration work lands on the child working branch
  `feature/114-kord-to-jda-migration` through draft PR #115, which targets the epic branch and not
  `master`.
- The working branch carries migration work as checkpoint commits and proposes it to the epic
  branch through draft PR #115. `8ca6242` records this plan, `.opencode/opencode.json` and
  `spikes/jda-dave/**` in branch history; the Phase 2A source/test changes and the matching plan
  edits are present on `feature/114-kord-to-jda-migration`. Current Git and PR state is
  authoritative for what is committed and pushed.
- Inventory table reproduced from a clean checkout.
- Out-of-scope items explicitly listed.

### Phase 1 — Dependency and DAVE transport feasibility

**Status: PASS** — the DAVE staging transport subgate passed on 2026-10-30 and the missing-native
fail-fast simulation (subphase 1B) passed on 2026-11-08, so every Phase 1 exit criterion below has
recorded evidence. No root dependency or root fat-JAR validation was established by the spike; root
dependency alignment and final-artifact checks belong to the Kord-free build phase (Phase 6), not
Phase 1.

- Resolve `jda-ktx 0.14.2` against explicit `jda 6.7.0` in the isolated spike; record the resolved
  dependency graph there.
- Compile a throwaway slice that registers one slash command and answers one interaction.
- Align DAVE at 0.2.0 across **all** modules (`api`, `impl-jni`, `adapter-jda`, Linux x86_64
  natives) **in the isolated spike**. The root version catalog, which still carries the Kord-era
  versions, is aligned only at the final root dependency replacement stage (Phase 6); Phase 1 does
  not change it.
- Build the **spike** fat JAR; root fat-JAR build, duplicate handling, OkHttp, JNA,
  `META-INF/services` and root JNI-extraction checks are **Phase 6** work, not Phase 1.

**Exit criteria (go/no-go — all mandatory)**
- Resolved versions recorded in the spike, or the KTX hypothesis rejected in favour of direct JDA.
  **PASS for the staging spike only; the final Phase 6 artifact must re-verify it.**
- One interaction round-trip succeeds against a real guild. **PASS for the staging `/voice-test`
  only.**
- **Real DAVE-enabled voice playback succeeds through JDA on the Linux x86_64 staging bot** —
  not a mock, not a unit test, not a "native located" check. **PASS (staging, protocol v1 +
  audible tone).**
- Native fail-fast check demonstrated by simulating a missing native. **PASS (2026-11-08)** — the
  negative-test jar (`fatJarWithoutDaveNative`) was run by the user in the Linux amd64
  `eclipse-temurin:21` container with `--network none` and a fake placeholder token, and exited
  exactly `3` (`EXIT_NATIVE`) with the DAVE native unavailable
  (`UnsatisfiedLinkError: Required library was not found`) and **zero** `[gateway] logging in` /
  `[gateway] ready` lines. Packaging alone did not close this criterion; the recorded execution
  did.
- Root dependency alignment and root fat-JAR validation are **Phase 6** exit criteria, not Phase 1.

**Stop condition:** a documented blocker is **not** a pass. If native loading or DAVE-enabled
voice playback fails, Phase 1 fails and migration progression pauses until the failure is resolved
and re-tested or the user approves a defined alternative. This Phase 1 gate is now closed, so it no
longer defers the JDA runtime/bootstrap and production integration portion of Phase 2; neutral
Phase 2A groundwork may still proceed in parallel (see Phase 2).

#### Phase 1 subphase plan and stacked branches

Phase 1 follows the same branch-per-subphase strategy as Phase 2: each subphase gets its **own child
branch and PR based on the immediately preceding unmerged branch**, never on `master`, and it is
never folded into an already-open PR.

| Subphase | Scope | Branch/PR posture | Status |
| --- | --- | --- | --- |
| **1A** | DAVE-enabled staging voice transport probe (protocol v1 + audible tone) | Recorded on `feature/114-kord-to-jda-migration`; draft PR #115 | **PASS** — transport subgate only |
| **1B** | Missing-DAVE-native fail-fast simulation: negative packaging artifact plus the user-run container check | Child branch `feature/114-native-failfast-check` based on `feature/114-kord-to-jda-migration`; its PR targets the **parent feature branch**, not the epic branch and not `master` | **PASS** — recorded 2026-11-08; gate closed |

**1B evidence requirements** (all mandatory; only the user-run execution counts):

- Artifact: `spikes/jda-dave/build/libs/jda-dave-spike-negative-no-dave-native-0.1.0-all.jar`,
  produced by `./gradlew -p spikes/jda-dave verifyNegativeTestJar`. The guard proves the jar omits
  **only** `natives/linux-x86-64/libdave-jvm.so` while keeping the libdave API classes, Opus, JNA and
  the `spike.jdadave.MainKt` entry point.
- Run conditions: `--network none` (no network path exists) and a deliberately invalid
  **placeholder** token; no real token, no secret, no live Discord login.
- Required result: **exit code `3`** (`EXIT_NATIVE`) with the DAVE loader failure
  (`[dave] native library is unavailable`, `UnsatisfiedLinkError` / `Required library was not
  found`) and **zero** `[gateway] logging in` / `[gateway] ready` lines.
- A run that logs in, contacts Discord, exits `2` (config/platform rejection), or exits `0`/`1` is a
  **failure** of this subphase.

These requirements were met by the recorded run: **exit `3`**, `[dave] native library is
unavailable` with `UnsatisfiedLinkError: Required library was not found`, **zero**
`[gateway] logging in` and **zero** `[gateway] ready` lines, and the fake placeholder token was
never printed. **Phase 1B is PASS and its gate is closed**, so the Phase 1 stop condition no longer
governs progression and **Phase 2B is unblocked**; it is now **IN PROGRESS** on
`feature/114-phase-2b-command-port` and is **not** complete.

#### Phase 1 staging result — voice transport probe (recorded)

The isolated `/voice-test` probe in `spikes/jda-dave/` was run on the external
`eclipse-temurin:21` container, Linux x86_64/amd64, against the separate staging bot.

| Check | Result |
| --- | --- |
| Candidate | JDA 6.7.0, jda-ktx 0.14.2, Kotlin 2.3.10, libdave 0.2.0 (Linux x86-64) |
| DAVE native | Loaded; `maxSupportedProtocolVersion=1` |
| Opus | JDA Opus encoder available |
| Voice | JDA connected to the test voice channel |
| DAVE negotiation | `DAVE Init - Protocol version=1` observed |
| Tone | All 75 frames handed to JDA |
| Audible tone | Confirmed by a user in the channel |
| Run mode / exit | One-shot run exited `0` |

Scope of this evidence:

- It validates the **staging voice transport** only: JDA login, DAVE native load and
  negotiation, Opus availability, and JDA's send path through to audible output.
- It does **not** validate **Lavaplayer integration**, real track decoding, the production
  audio pipeline, or the per-guild serialization design. Those remain later gates.
- It does **not** validate the root version catalog, root dependency resolution, or the root fat
  JAR. Those are Phase 6 gates; this run used the isolated spike build only.
- The public JDA API **cannot attribute the cause of a `NOT_CONNECTED`**, so the observed
  requested-disconnect state is not evidence of what caused it.
- Sanitized record: no bot, guild, channel or user identifiers, no token, and no raw log is
  included here or in the repository.

Operational note: the staging container carries a restart policy, so it **restarted the
one-shot process** after it had exited. Stop the staging service, or run it strictly one-shot
with the restart policy disabled, before repeating the test; otherwise a restarted process can
race the new run.

This is transport evidence only. The DAVE transport subgate is recorded as **PASS**; together with
the 1B fail-fast pass it closes the overall Phase 1 gate. Passing R1 and R2 here does **not** convert
the vendor caveats above into certified support: the pairing is still undeclared upstream,
`adapter-jda` still does not declare or test JDA 6.7.0, and both must be re-verified against the
final Phase 6 artifact before promotion.

### Phase 2 — JDA bootstrap, event and command-response foundation

**Status: IN PROGRESS** — the Phase 2A locale repository/use-case boundary is implemented, but
`LocalizationService` and the `/locale` command remain Kord-facing. Phase 2B's neutral command
boundary and isolated JDA adapter are implemented with unit tests on their child branch, but the
bootstrap and command layer are not wired into the root runtime, so this phase is **not** complete.
Remaining work is split into subphases **2B** (neutral command context/responders, in progress),
**2C** (voice/session/player-message ports) and **2D** (channel/message/locale edge adapters), each
with its own child branch and PR based on the immediately preceding unmerged branch (see the
subphase plan above).

**Gate status:** the Phase 1 missing-native fault test passed on 2026-11-08, so it no longer blocks
this phase. Phase 2B is now **IN PROGRESS** on `feature/114-phase-2b-command-port` under draft PR #117
(base: the Phase 1B branch). On head `001e722` the #117 quality and build jobs passed but the
SonarCloud Quality Gate failed at 0.0% new-code coverage because the modules had no JaCoCo XML; the
branch now wires JaCoCo 0.8.12 into `:core`/`:jda-runtime` and root `sonar`, but that change is
**not** yet verified on CI, and manual staging interaction evidence is still pending, while the root
runtime remains Kord-based.

- Replace Kord bootstrap with JDA lifecycle (login, shard/event wiring, shutdown).
- Introduce neutral abstractions for IDs and locales so command code stops speaking Kord types.
  **Partially done** (Phase 2A): domain-owned `GuildId`/`LocaleCode` plus a neutral locale
  repository and use cases. Phase 2B moves those values into `:core` and adds neutral command
  ports, but production command code still speaks Kord types.
- Establish a single command dispatch/response layer with correct deferred/replied semantics.
  **Partially done** (Phase 2B): implemented and unit-tested in the isolated `:jda-runtime`; not
  wired into the root runtime.
- Apply the framework-replaceability goal from §1: commands/handlers take a neutral command
  context and responder/response port; only Discord adapter packages may touch `dev.kord.*` /
  `net.dv8tion.*`.

**Exit criteria**
- Bot connects on staging with the staging token. **OPEN**
- Slash commands register and respond; deferred vs. immediate reply is explicit and tested.
  **OPEN** — implemented and unit-tested only in the isolated `:jda-runtime`; no staging evidence and
  no root-runtime wiring.
- No Kord types leak into the new abstraction boundary. **PASS for the locale repository/use-case
  boundary and for the Phase 2B `:core` module only. OPEN for `LocalizationService`, `/locale`, all
  other commands and bootstrap.**
- The architectural import rule (no `dev.kord.*` / `net.dv8tion.*` outside declared Discord adapter
  packages) exists and passes as a test/CI gate. **OPEN** — required before migration completion;
  Phase 2 exit cannot be recorded without it. The Phase 2B reflection test covers only the neutral
  types, not the whole import rule.

### Phase 3 — Per-guild serialized voice

- Implement the per-guild actor/mailbox for player creation, queue/track transitions, and voice
  lifecycle.
- Implement `AudioSendHandler` with one Opus frame per cycle, pause preserved, non-blocking.
- Make Lavaplayer callbacks enqueue-only.

**Exit criteria**
- Concurrent play/queue/skip/disconnect per guild produce a single deterministic state order.
- Pause/resume, reconnect and disconnect behave correctly under load.
- No blocking call observed on the audio send path.

### Phase 4 — Commands, components, messages, channels, locales

- Port all 13 command groups and all 18 component IDs.
- Port message sending/editing, player-channel management, and locale persistence.
- Land the three button-routing fixes with tests.

**Exit criteria**
- Every command family and every component ID reachable and behaving as on `master`.
- Hidden/obfuscated channels work end-to-end.
- Locale selection persists across restarts and is applied per guild.
- Routing-fix tests pass and would fail against the pre-fix routing.

### Phase 5 — Test and staging gate

- Run the full suite on the JDA implementation. **Migrate and rewrite** the Kord-shaped mocks
  and tests to JDA; do **not** retire Kord-only test scaffolding at this phase. The Kord
  dependency and Kord JARs are still present here, so removing Kord scaffolding is Phase 6
  work, not Phase 5 work.
- Execute the full staging matrix on the separate staging bot/token.

**Exit criteria**
- Full test suite green on the JDA implementation **with the Kord dependency still present** —
  the Phase 5 test gate is allowed to pass while Kord remains on the dependency graph.
- Kord-shaped mocks and tests have been migrated/rewritten to JDA equivalents, with no test
  still asserting against Kord-only APIs.
- Staging matrix covers: hidden/obfuscated channels, deferred interactions, all command
  families, all components, message and player-channel management, locale persistence, voice
  pause/reconnect/disconnect, DAVE-enabled real playback.
- Known-issue list signed off as "acceptable for cutover" **only** for items that are not gate
  criteria. Any parity gap or any matrix/DAVE failure is a stop condition, not an accepted risk.

### Phase 6 — Kord-free implementation and artifact (pre-cutover)

Cleanup and the promotable artifact are completed **before** the production switch, not after it.

- Remove Kord dependencies, local `libs/kord-*.jar`, Kord-only dependencies, and Kord-specific
  documentation/CI references. Only Kord-specific docs and references are removed or updated; this
  migration plan (`docs/migrations/kord-to-jda.md`) is **not** Kord-specific documentation and must
  be kept. Note the current setup: the SonarCloud build job sets up **JDK 17**
  (`actions/setup-java`, zulu) despite the Java 21 toolchain, and the ktlint workflow does not set
  up a JDK at all.
- Align the root dependency set to the tested JDA/DAVE stack at this point: add `adapter-jda` and
  bump root `libdave` / `libdave-natives` to the 0.2.0 set proven by the spike. Phase 1 leaves the
  root catalog on its current Kord-era versions; the root dependency replacement happens here,
  not through spike-local overrides.
- Update the SonarCloud build job to set up **JDK 21 explicitly**, and make that build/CI gate
  pass. Do **not** add a JDK step to the ktlint workflow unless it is shown to need one; that
  workflow does not set up a JDK today. This is **R3**, a go/no-go gate.
- After every Kord JAR/dependency and obsolete Kord scaffolding is removed, run
  **`./gradlew clean build`** on the resulting Kord-free tree and require it to **pass** before
  any artifact inspection or promotion. A "fresh clean build" means exactly this command on the
  Kord-free tree; a build from a pre-cleanup tree or an incremental build does not satisfy it.
- Produce the fat JAR from that same Kord-free tree.
- Inspect the artifact: no Kord classes anywhere on the classpath; correct duplicate handling,
  OkHttp/JNA/`META-INF/services` entries; Linux x86_64 DAVE natives packaged and loadable.
- Compute the **SHA-256 digest** of the exact fat JAR produced above and record it **before** any
  deployment. From this point on, do not rebuild, repackage, or substitute another artifact.
- Deploy **that exact artifact** into the external `eclipse-temurin:21` **staging** container,
  verify the **same digest inside/on the staging container** before running the matrix, then run
  the **full staging matrix** on the separate staging bot. A digest mismatch is a stop condition.

**Exit criteria**
- The build tree contains no Kord source, Kord dependency, or local Kord JAR.
- The architectural import rule forbids `dev.kord.*` and `net.dv8tion.*` outside declared Discord
  adapter packages, and passes as a test/CI gate on the Kord-free tree. Core tests mock neutral
  ports, not library types.
- The root version catalog resolves the tested stack — JDA 6.7.0, jda-ktx 0.14.2, libdave 0.2.0
  including `adapter-jda` and the Linux x86_64 natives — with no spike-local force or override
  carrying the runtime.
- `./gradlew clean build` passes on the Kord-free tree.
- A clean build produces the fat JAR with no Kord classes — verified by inspecting the artifact,
  not by trusting the build file — and with no packaging or native defects.
- The build/CI gate passes with an explicit **JDK 21** setup in the SonarCloud build job; the
  ktlint workflow is out of scope unless it is shown to need a JDK. **R3 is closed here.**
- The exact fat JAR being promoted passes the full staging matrix in `eclipse-temurin:21` on
  Linux x86_64, including DAVE-enabled real voice playback.
- Record in migration issue #114 (or the release record), against the staged artifact, all of:
  the **resolved dependency versions**; the **JDK 21 CI pass**; the exact fat JAR **SHA-256
  digest**; the **full staging matrix result**; the **staging-container digest verification**;
  and the **production-server digest verification** (recorded at cutover). No separate evidence
  file is created.

**Stop condition:** a failed artifact inspection, a digest mismatch, or a failed staging run
blocks cutover. Because the user rejected a Kord rollback, this gate cannot be waived, downgraded,
or replaced by "roll forward".

### Phase 7 — Production cutover

- Stop Kord and start the gated JDA artifact in production during an announced window. The user
  deploys the JAR through their **external `eclipse-temurin:21` Dockerfile**; this repo adds no
  Docker files and assumes no artifact registry.
- Before start, compute the SHA-256 digest of the production JAR **on the server** and require it
  to equal the digest recorded in Phase 6 **and** verified on staging. Any mismatch is a stop
  condition. Do not rebuild or substitute a different artifact.
- Run post-start smoke checks only. The no-rebuild/no-substitution rule applies through cutover
  and after it.

**Exit criteria**
- Production runs the **same** fat JAR that passed Phase 6, proven by matching SHA-256 digest.
- No Kord artifacts on the production classpath.
- Production healthy; rollback path is deliberately absent per decision above.

### Phase 8 — Port parked PRs

- Rebase/port #106, #109, #111 individually onto the JDA implementation.

**Exit criteria**
- Each PR merged or explicitly re-scoped, with its own tests.
- No parked branch retains Kord-only APIs.

---

## 4. Risks and open gates

| # | Risk / gate | Status |
| --- | --- | --- |
| R1 | No official `jda-ktx 0.14.2` + JDA 6.7 combination exists | **IN PROGRESS — transport subgate PASS only (2026-10-30)** — resolved and exercised for voice transport. Residual: the pairing is still undeclared upstream and must be re-verified against the final Phase 6 artifact |
| R2 | `adapter-jda` 0.2.0: source build catalog declares `compileOnly` JDA 6.3.1; published metadata does not declare/test 6.7.0 | **IN PROGRESS — transport subgate PASS only (2026-10-30)** — DAVE negotiated and played through JDA. Residual: inferred, not certified, and vendor metadata still targets 6.3.1; re-verify on the Phase 6 artifact |
| R3 | The SonarCloud build job explicitly sets up **JDK 17** (`actions/setup-java`, zulu) despite the build's **Java 21** toolchain; the ktlint workflow does **not** set up a JDK at all | Open — **go/no-go**; closed only when the SonarCloud build job sets up JDK 21 explicitly and that build/CI gate passes (Phase 6 exit criterion) |
| R4 | `fatJar` uses `DuplicatesStrategy.EXCLUDE`; duplicate handling, OkHttp, JNA and `META-INF/services` entries are unverified for the JDA stack | Open — Phase 6 artifact gate; the Phase 1 evidence came from the isolated spike build, not the root fat JAR |
| R5 | `docker/docker-compose.yml` references build contexts that do not exist in this repo; the real Dockerfile is external | Fact — no action in repo, confirm image contract with owner |
| R6 | Base image immutability/pinning is unconfirmed; `eclipse-temurin:21` is currently glibc/Ubuntu, and `/tmp` `noexec` would break extracted JNI natives | Open — Phase 6 staging gate; verify or pin |
| R7 | Existing Kord build does not support channel obfuscation at all | Fact — this is the reason for the migration, not a regression |
| R8 | Per-guild serialization regressions when porting concurrent Kord handlers | Open — Phase 3 go/no-go |
| R9 | Schedule pressure: Nov 16 is a readiness target; the 17–19 day estimate is unvalidated | Open — re-estimate now that the DAVE transport subgate and Phase 1's missing-native fail-fast check both have recorded evidence (2026-11-08) |
| R10 | Domain/command code can silently re-couple to Kord/JDA during the port, defeating library replaceability | Open — **acceptance criterion**; closed only when the architectural test/CI rule forbids `dev.kord.*` / `net.dv8tion.*` outside declared Discord adapter packages and core tests mock neutral ports |

Open entries marked as gates are **stop conditions** per §3. They are never downgraded to
accepted risk, and a documented blocker does not close a gate.

## 5. Tracked and ignored local artifacts

- The epic integration branch `feature/114-migrate-to-jda-jda-ktx` remains at base `4badb92`; the
  active child branch is `feature/114-kord-to-jda-migration`, with draft PR #115 targeting the epic
  branch rather than `master`.
- `.opencode/opencode.json`, this migration plan (`docs/migrations/kord-to-jda.md`) and
  `spikes/jda-dave/**` are **tracked** on `feature/114-kord-to-jda-migration`; the Phase 1
  checkpoint `8ca6242` records them in branch history. They are part of the migration record and
  are expected to be updated as the migration proceeds.
- The Phase 1B negative-test packaging (`fatJarWithoutDaveNative`, `verifyNegativeTestJar`), its
  runbook section, the Phase 1B plan edits and the 2026-11-08 pass record are committed on the child
  branch `feature/114-native-failfast-check`, which is based on
  `feature/114-kord-to-jda-migration`. Its PR targets the parent feature branch. Current Git state
  is authoritative for what is committed and pushed.
- The Phase 2A source and test changes, and the plan edits made after `8ca6242`, are present on
  `feature/114-kord-to-jda-migration` and proposed to the epic branch through draft PR #115.
  Current Git and PR state is authoritative for what is committed and pushed.
- The Phase 2B modules `core/` and `jda-runtime/`, the root `settings.gradle.kts` /
  `build.gradle.kts` changes and the version-catalog entries are present on
  `feature/114-phase-2b-command-port`, stacked on `feature/114-native-failfast-check` and proposed
  through draft PR #117 (base: that Phase 1B branch). The initial implementation checkpoint is
  `fbd9f5a`; the documentation commit that records this handoff follows it. JaCoCo 0.8.12 coverage
  is applied in `core/build.gradle.kts` and `jda-runtime/build.gradle.kts`, and root `sonar` depends
  on both module reports. Current Git and PR state is authoritative for what is committed and pushed.
- `stash@{0}` — housekeeping WIP; preserved, untouched.
- `stash@{1}` — assistant voice feature WIP (out of scope); preserved, untouched.
- Ignored caches and user data (for example `.gradle/`, `build/`, `data/`, `.idea/`, `.memory/`,
  `.opencode/node_modules/`) are **gitignored**, untouched, and stay that way.
