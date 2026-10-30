# Kord → JDA 6.7 Migration Plan

Tracking issue: [#114](https://github.com/Wikijito7/ECIBotKt/issues/114) — *Migrate to JDA / jda-ktx*
Epic integration branch: `feature/114-migrate-to-jda-jda-ktx`, based on `master` @ `4badb92`; it
remains the merge baseline for this epic until PR #115 lands.
Active working branch: `feature/114-kord-to-jda-migration` — draft PR
[#115](https://github.com/Wikijito7/ECIBotKt/pull/115) targets the **epic branch**, not `master`.

> **This is a plan, not implementation.** Nothing below is proven until the gate it belongs to
> produces recorded evidence. "Fact" = verified against the repository or upstream sources today.
> "Gate" / "Recommendation" = untested and must be validated before being relied on.

---

## Progress record (verified, 2026-10-30)

Status labels: **PASS** = recorded evidence, gate closed · **IN PROGRESS** = work started, gate
still open · **OPEN** = not started, gate open.

| Item | Status | Recorded 2026-10-30 |
| --- | --- | --- |
| Phase 1 — dependency and DAVE transport feasibility | **IN PROGRESS** | **DAVE transport subgate PASS** on staging (protocol v1 + audible tone); the planned missing-native fail-fast simulation is not demonstrated, so the overall gate remains open on that mandatory criterion |
| R1 — `jda-ktx 0.14.2` + JDA 6.7.0 pairing | **IN PROGRESS — transport subgate PASS only** | Candidate resolved and exercised for voice transport; still not an upstream-declared combination and not yet re-verified on the final Phase 6 artifact |
| R2 — `adapter-jda` 0.2.0 vs JDA 6.7.0 ABI | **IN PROGRESS — transport subgate PASS only** | DAVE negotiated and played through JDA; published metadata still does not declare or test 6.7.0, and the ABI is not yet re-verified on the final Phase 6 artifact |
| Phase 2 — JDA bootstrap, events, command response | **IN PROGRESS** | Phase 2A neutral repository/use-case groundwork only; JDA runtime/bootstrap and production integration remain blocked by the open Phase 1 missing-native fault test; command layer and locale service/command neutrality remain **OPEN** |
| Phase 2A — domain-owned ID/locale values + locale persistence refactor | **IN PROGRESS** | Locale repository and use cases are neutral; `LocalizationService` and `/locale` remain Kord-facing |
| Phase 6 — Kord-free build, artifact and digest gates | **OPEN** | Not started |
| Production cutover (Phase 7) | **OPEN** | Blocked by the Phase 6 and staging gates |

### Phase 1 DAVE transport subgate — PASS (Phase 1 overall IN PROGRESS)

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

The planned fail-fast simulation with a **missing native** was **not** demonstrated. That is a
mandatory Phase 1 exit criterion, so the overall Phase 1 gate remains **IN PROGRESS**, not closed.

Scope limit: this verifies **JDA/DAVE voice transport only**. It does **not** verify production
Lavaplayer integration, the production audio pipeline, commands, the per-guild serialization
design, the root dependency graph, or the root fat JAR — those stay in their own gates. The public
JDA API does not attribute the cause of a `NOT_CONNECTED` state, so no cause is claimed here.

Operational note: the staging container's restart policy restarted the one-shot process after it
had exited. Stop the service, or disable the restart policy for strictly one-shot probe runs,
before repeating the test.

### Phase 2A — domain-owned ID/locale values and locale persistence — IN PROGRESS

- `GuildId(ULong)` now lives in `es.wokis.domain.model` and `LocaleCode` in
  `es.wokis.domain.locale`; both are **domain-owned**, platform-neutral values.
- The locale repository and its use cases (`LocaleRepository`, `LocalJsonLocaleRepository`,
  `GetGuildLocaleUseCase`, `SetGuildLocaleUseCase`) speak only those domain values.
- `LocalizationService` is **not** neutral: its public signatures still take Kord `Snowflake` /
  `Locale` and convert to the domain values internally. The `/locale` command is also still
  Kord-facing. Locale service and command neutrality remain open Phase 2/Phase 4 work.
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
- Phase 2 stays **IN PROGRESS**: JDA bootstrap, shard/event wiring, the command dispatch/response
  layer, and locale service/command neutrality are **OPEN**.

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

### Checkpoint state

- The epic integration branch `feature/114-migrate-to-jda-jda-ktx` remains at its base
  `4badb92`. The active child branch is `feature/114-kord-to-jda-migration`; open draft PR #115
  targets the epic branch.
- `.opencode/opencode.json`, this plan and `spikes/jda-dave/**` are **tracked** on
  `feature/114-kord-to-jda-migration` by the Phase 1 checkpoint commit `8ca6242`; they are part of
  the branch history, not untracked or local-only artifacts.
- The Phase 2A source and test changes, plus the later edits to this plan, are **uncommitted** until
  the next checkpoint commit.
- No secrets, bot/guild/channel/user identifiers, tokens or raw logs are recorded in this plan or
  in the spike.

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
- The working branch records migration work in checkpoint commits and draft PR #115 against the
  epic branch. `8ca6242` tracks this plan, `.opencode/opencode.json` and `spikes/jda-dave/**`; the
  Phase 2A source/test changes remain **uncommitted** and are added by the next checkpoint, so the
  committed diff at `8ca6242` does not yet contain them.
- Inventory table reproduced from a clean checkout.
- Out-of-scope items explicitly listed.

### Phase 1 — Dependency and DAVE transport feasibility

**Status: IN PROGRESS** — the DAVE staging transport subgate passed on 2026-10-30 (evidence below),
but the planned missing-native fail-fast simulation is not demonstrated, so the overall gate is not
closed. No root dependency or root fat-JAR validation was established by the spike; root dependency
alignment and final-artifact checks belong to the Kord-free build phase (Phase 6), not Phase 1.

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
- Native fail-fast check demonstrated by simulating a missing native. **OPEN — not demonstrated.**
- Root dependency alignment and root fat-JAR validation are **Phase 6** exit criteria, not Phase 1.

**Stop condition:** a documented blocker is **not** a pass. If native loading or DAVE-enabled
voice playback fails, Phase 1 fails and migration progression pauses until the failure is resolved
and re-tested or the user approves a defined alternative. The JDA runtime/bootstrap and production
integration portion of Phase 2 does not start while the missing-native fail-fast check is open;
neutral Phase 2A locale groundwork may proceed in parallel (see Phase 2).

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

This is transport evidence only. The DAVE transport subgate is recorded as **PASS**; the overall
Phase 1 gate stays **IN PROGRESS** (open on the missing-native fail-fast criterion) because that
simulation was not demonstrated and no root dependency/fat-JAR validation was performed. Passing
R1 and R2 here does **not** convert the vendor caveats above into certified support: the pairing is
still undeclared upstream,
`adapter-jda` still does not declare or test JDA 6.7.0, and both must be re-verified against the
final Phase 6 artifact before promotion.

### Phase 2 — JDA bootstrap, event and command-response foundation

**Status: IN PROGRESS** — the Phase 2A locale repository/use-case boundary is implemented, but
`LocalizationService` and the `/locale` command remain Kord-facing. Bootstrap and the command layer
are still open, so this phase is **not** complete.

**Safe parallel prep:** the neutral Phase 2A groundwork (domain-owned `GuildId`/`LocaleCode`, locale
repository and use cases) may proceed independently while the Phase 1 missing-native fault test is
open, because it adds no JDA runtime, bootstrap or production wiring. The JDA
runtime/bootstrap/event/production integration portion of Phase 2 remains **blocked** until that
Phase 1 gate is closed; the Phase 1 stop condition still governs migration progression.

- Replace Kord bootstrap with JDA lifecycle (login, shard/event wiring, shutdown).
- Introduce neutral abstractions for IDs and locales so command code stops speaking Kord types.
  **Partially done** (Phase 2A): domain-owned `GuildId`/`LocaleCode` plus a neutral locale
  repository and use cases. Command code still speaks Kord types.
- Establish a single command dispatch/response layer with correct deferred/replied semantics.

**Exit criteria**
- Bot connects on staging with the staging token. **OPEN**
- Slash commands register and respond; deferred vs. immediate reply is explicit and tested. **OPEN**
- No Kord types leak into the new abstraction boundary. **PASS for the locale repository/use-case
  boundary only. OPEN for `LocalizationService`, `/locale`, all other commands and bootstrap.**

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
| R9 | Schedule pressure: Nov 16 is a readiness target; the 17–19 day estimate is unvalidated | Open — re-estimate once the DAVE transport subgate result is folded in and Phase 1's missing-native fail-fast check is demonstrated |

Open entries marked as gates are **stop conditions** per §3. They are never downgraded to
accepted risk, and a documented blocker does not close a gate.

## 5. Tracked and ignored local artifacts

- The epic integration branch `feature/114-migrate-to-jda-jda-ktx` remains at base `4badb92`; the
  active child branch is `feature/114-kord-to-jda-migration`, with draft PR #115 targeting the epic
  branch rather than `master`.
- `.opencode/opencode.json`, this migration plan (`docs/migrations/kord-to-jda.md`) and
  `spikes/jda-dave/**` are **tracked** on `feature/114-kord-to-jda-migration` by the Phase 1
  checkpoint commit `8ca6242`. They are part of the migration record and are expected to be
  updated as the migration proceeds.
- The Phase 2A source and test changes, and the plan edits made after `8ca6242`, are
  **uncommitted** on the working branch until the next checkpoint commit; they are not otherwise
  preserved.
- `stash@{0}` — housekeeping WIP; preserved, untouched.
- `stash@{1}` — assistant voice feature WIP (out of scope); preserved, untouched.
- Ignored caches and user data (for example `.gradle/`, `build/`, `data/`, `.idea/`, `.memory/`,
  `.opencode/node_modules/`) are **gitignored**, untouched, and stay that way.
