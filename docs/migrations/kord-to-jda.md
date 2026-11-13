# Kord → JDA 6.7 Migration Plan

Tracking issue: [#114](https://github.com/Wikijito7/ECIBotKt/issues/114) — *Migrate to JDA / jda-ktx*
Epic integration branch: `feature/114-migrate-to-jda-jda-ktx`, based on `master` @ `4badb92`; it now
carries the **squash merge of PR #116** at `c00bdf1`, on top of `5aad7c7` (the **squash merge of PR
#115**), and remains the merge baseline for this epic. Phase 2A working branch
`feature/114-kord-to-jda-migration` **no longer exists**: PR
[#115](https://github.com/Wikijito7/ECIBotKt/pull/115) was **squash-merged** into the epic branch as
`5aad7c7` and the remote branch was deleted. Phase 2A stays **IN PROGRESS / partial** — the merge is
not a 2A completion and does not complete Phase 2.
Phase 1B subphase branch: `feature/114-native-failfast-check`; its PR
[#116](https://github.com/Wikijito7/ECIBotKt/pull/116) had targeted the **epic branch**, because the
former 2A branch was merged and deleted. Its negative fail-fast test **passed** on 2026-11-08 (see the
Phase 1B result below). Its head `1b753b9` is an **ancestry-only merge** of `5aad7c7` into `49b4bc1`:
the four add/add conflicts were resolved by retaining this branch's Phase 1B content, so the resolved
tree is **byte-identical** to `49b4bc1` and **no source content changed**. All four checks on
`1b753b9` passed on 2026-11-13 — Check Code Quality 17:20:31Z, SonarCloud Code Analysis 17:22:32Z,
Build and analyze 17:22:35Z and SonarCloud 17:22:40Z. PR #116 is now **CLOSED / MERGED by squash** into
the epic branch as `c00bdf1`.
Phase 2B subphase branch: `feature/114-phase-2b-command-port`, originally based on Phase 1B
`feature/114-native-failfast-check`; PR
[#117](https://github.com/Wikijito7/ECIBotKt/pull/117) targeted the **epic branch** (base branch name
only; this document does not assert its live base SHA) and was **merged** into it by a regular merge
commit — not a squash — at `255893abcef31554e13ae1713086a9b7c1217c60`, whose parents are the epic tip
`c00bdf1` and the Phase 2B head `7d8661d`. The merge tree is identical to `7d8661d`, so the epic tip
now carries the Phase 2B pass unchanged. Phase 2B is **PASS — gate closed on 2026-11-13** (see the
Phase 2B section below). The next subphase is **Phase 2C**, which is now
**IN PROGRESS — implementation with local tests**: its acceptance criteria and branch/base posture are
recorded in the Phase 2C section, and its local branch `feature/114-phase-2c-voice-ports` was created
from the fetched epic head `255893a`. Its production implementation and unit tests exist as
**uncommitted local working-tree changes** and pass locally (see the Phase 2C section), but **no 2C PR
has been opened (no #118 yet), and no CI run or staging result exists**. Broader
**Phase 2 remains IN PROGRESS**: subphases 2C/2D, root Kord removal/bootstrap wiring and the overall
Phase 2 exit criteria are still open. `7bf5419` (`Corrected Phase 2B CI status record`) is the latest
**verified all-green CI checkpoint** before the subsequent docs-only updates: all four #117 checks
passed on **2026-11-13** — Check Code Quality (16:47:15Z), SonarCloud Code Analysis (16:48:52Z),
Build and analyze (16:48:56Z) and SonarCloud (16:48:58Z) — and Sonar reported **75.0% Coverage on New
Code**, 0 new issues and 0 hotspots. It was PR #117's head at that point; later commits, including
this docs-only update, move the head. The preceding docs-only checkpoint
`988a7cd` (`Clarified Phase 2B CI and artifact scope`) is **also green**: the same four checks passed,
latest completion **2026-11-13 16:38:44Z**. The earlier docs-only `d353113` (`Document legacy guild
locale defaults`, latest completion **16:34:38Z**) and `cd9d6db` are green as well; `cd9d6db`
reported the Sonar quality gate result (**75.0% Coverage on New Code**, 0 new issues, 0 hotspots).
`e4a9e29` (`Refreshed Phase 2B CI handoff head`) was observed on 2026-11-13 at **16:41:06Z** — that
timestamp is the observation time, at which only **Check Code Quality** had concluded successfully —
with **Build and analyze** still in progress and the SonarCloud checks not yet listed. That
in-progress observation is kept as dated history only — `e4a9e29` **later completed all four checks
successfully** (SonarCloud Code Analysis 16:44:04Z, Build and analyze 16:44:09Z, SonarCloud 16:44:11Z)
and is green, but it is no longer the head. `f5f04d1` (`Recorded passing Phase 2B CI runs`, docs-only)
was PR #117's head in a dated snapshot observed on **2026-11-13**: only **Check Code Quality** had
concluded successfully (**16:53:24Z**), **Build and analyze** was still in progress and the
SonarCloud checks were not yet listed, so it was **not green at that snapshot**. That observation
predates this docs-only follow-up, which itself creates a newer head, so it is not a claim about the
resulting HEAD or its status. No recorded checkpoint is a promise about later commits: read the live
GitHub check runs on PR #117 for whatever head is current. The earlier head `001e722` failed only its
**SonarCloud Code Analysis** check, whose Quality Gate reported **0.0% Coverage on New Code**, because
`:core` and `:jda-runtime` had no JaCoCo XML reports; on that same head the separately named
**SonarCloud** check, **Check Code Quality** and **Build and analyze** all succeeded. The JaCoCo 0.8.12
wiring that fixed the coverage gap is recorded below. The subsequent docs-only
checkpoints `2850907` (`Recorded Phase 2B coverage gate pass`) and `16247f8` (`Recorded
passing Phase 2B docs checkpoint`) also passed all four #117 checks — Check Code Quality, Build and
analyze, SonarCloud Code Analysis and SonarCloud. All of these are **historical verified checkpoints**,
not a standing guarantee: read the GitHub check runs for any later head. Local QA and reviewer agents
satisfied the independent review criterion; no GitHub PR review has been submitted, which is not itself
a required gate. PR #117 is **merged** into the epic branch at `255893a`; it is not modified here.
Issue #114 stays open because broader Phase 2 (2C/2D, root Kord removal and bootstrap wiring) is
unfinished; **Phase 2C implementation is in progress with local working-tree tests only** on
`feature/114-phase-2c-voice-ports`.

> **This is a plan, not implementation.** Nothing below is proven until the gate it belongs to
> produces recorded evidence. "Fact" = verified against the repository or upstream sources today.
> "Gate" / "Recommendation" = untested and must be validated before being relied on.

---

## Progress record (verified, 2026-11-13)

Status labels: **PASS** = recorded evidence, gate closed · **IN PROGRESS** = work started, gate
still open · **OPEN** = not started, gate still open.

| Item | Status | Recorded |
| --- | --- | --- |
| Phase 1 — dependency and DAVE transport feasibility | **PASS** (staging spike) | Subphase **1A** passed on staging (protocol v1 + audible tone) and subphase **1B** passed on 2026-11-08 (exit `3` `EXIT_NATIVE` with no gateway login); every Phase 1 exit criterion now has recorded evidence. Root dependency alignment and root fat-JAR checks remain **Phase 6**, not Phase 1 |
| Phase 1B — missing-DAVE-native fail-fast simulation | **PASS — gate closed (2026-11-08)** | User-run negative container on Linux amd64, Temurin 21.0.13 (`eclipse-temurin:21`), negative no-DAVE-native JAR: exit exactly `3` (`EXIT_NATIVE`), DAVE native unavailable with `UnsatisfiedLinkError: Required library was not found`, **zero** `[gateway] logging in` and **zero** `[gateway] ready` lines, fake placeholder token never printed. The host pre-pulled the image, so the image pull was not itself isolated; the container ran with `--network none`. Sanitized record only |
| R1 — `jda-ktx 0.14.2` + JDA 6.7.0 pairing | **IN PROGRESS — transport subgate PASS only** | Candidate resolved and exercised for voice transport; still not an upstream-declared combination and not yet re-verified on the final Phase 6 artifact |
| R2 — `adapter-jda` 0.2.0 vs JDA 6.7.0 ABI | **IN PROGRESS — transport subgate PASS only** | DAVE negotiated and played through JDA; published metadata still does not declare or test 6.7.0, and the ABI is not yet re-verified on the final Phase 6 artifact |
| Phase 2 — JDA bootstrap, events, command response | **IN PROGRESS** | Phase 2A neutral repository/use-case groundwork **landed on the epic branch** through the squash merge of PR #115 (`5aad7c7`, tree identical to the former 2A head `67802d1`); Phase 2A itself remains **partial**, not complete. Phase 2B is **PASS — gate closed 2026-11-13**: its neutral command boundary and isolated JDA runtime exist with unit tests on child branch `feature/114-phase-2b-command-port`, JaCoCo coverage is wired for both isolated modules, CI passed on the verified all-green checkpoints `988a7cd` and `7bf5419` (the latest verified all-green checkpoint before the subsequent docs-only updates; all of these are historical checkpoints, so read the live checks for later heads), and the user-run staging `/jda-walkthrough` interaction succeeded (sanitized evidence in the Phase 2B section). Nothing is wired into the root runtime, and the command layer plus locale service/command neutrality remain **OPEN**. Remaining work is subphases 2C and 2D (see Phase 2); **2C is IN PROGRESS — implementation with local tests**: its acceptance criteria are written in the Phase 2C section and its local branch `feature/114-phase-2c-voice-ports` was created from the fetched epic head `255893a`; implementation and unit tests exist as uncommitted local working-tree changes and pass locally, but there is no 2C PR (no #118), no CI run and no staging evidence yet. PR #116 was **squash-merged** into the epic branch (`c00bdf1`); PR #117 was then **merged** into the epic branch by a regular merge commit at `255893a` (parents `c00bdf1` and `7d8661d`, merge tree identical to the Phase 2B head) |
| Phase 2A — domain-owned ID/locale values + locale persistence refactor | **IN PROGRESS / partial** | Phase 2A source and test changes were **squash-merged** into the epic branch by PR #115 as `5aad7c7`; that commit's tree equals the former 2A head `67802d1`, and the former `feature/114-kord-to-jda-migration` branch is deleted. The merge brings the groundwork in but does **not** complete 2A: `GuildId`/`LocaleCode` and the locale repository/use cases are **neutral**, while `LocalizationService` and `/locale` remain Kord-facing and must be moved behind/adapted in a later Phase 2 subphase (2D) |
| Phase 2B — neutral command context + responder/response port | **PASS — gate closed (2026-11-13)** | Implementation and unit tests are present on `feature/114-phase-2b-command-port` as the initial implementation checkpoint `fbd9f5a`, originally stacked on Phase 1B `feature/114-native-failfast-check`. PR #116 has since been **squash-merged** into the epic branch (`c00bdf1`), so PR #117 was retargeted to the epic branch and was later **merged** into it by the regular merge commit `255893a`; its only conflict — this plan — was resolved by an ancestry merge that changed no code. The branch adds isolated Gradle modules `:core` (gateway-free neutral ports and a pure walkthrough handler) and `:jda-runtime` (JDA 6.7.0 adapter, guild-only `/jda-walkthrough`); the root project depends on `:core` only and remains Kord-based and JDA-free. On head `001e722` **Check Code Quality**, **Build and analyze** and the separately named **SonarCloud** check passed, while **SonarCloud Code Analysis** failed its Quality Gate at 0.0% coverage on new code (required >=50%) because the modules emitted no JaCoCo XML; the JaCoCo 0.8.12 fix then passed CI on the covered checkpoint `e607ff7` — all four #117 checks green, including both SonarCloud checks, with 75.0% Coverage on New Code, 0 new issues and 0 hotspots. The later docs-only checkpoints `2850907`, `16247f8` and `d353113` also passed all four #117 checks, as did `988a7cd` (last check completed 2026-11-13 16:38:44Z), and `e4a9e29` was observed in progress at 16:41:06Z (the observation time, when only Check Code Quality had concluded successfully) and later completed all four checks successfully (last at 16:44:11Z). `7bf5419` (`Corrected Phase 2B CI status record`) is the latest verified all-green checkpoint before the subsequent docs-only updates: all four checks passed on 2026-11-13 — Check Code Quality 16:47:15Z, SonarCloud Code Analysis 16:48:52Z, Build and analyze 16:48:56Z, SonarCloud 16:48:58Z — with 75.0% Coverage on New Code, 0 new issues and 0 hotspots; it was the head at that point, not afterwards. The later docs-only `f5f04d1` (`Recorded passing Phase 2B CI runs`) was the head in a dated snapshot observed on 2026-11-13 in which only Check Code Quality had concluded successfully (16:53:24Z), Build and analyze was still in progress and the SonarCloud checks were not yet listed — so it was **not green at that snapshot**; GitHub holds the status of the head after this docs-only follow-up. All of these are historical verified checkpoints, not a guarantee for later commits, so CI status for any newer head must be read from the GitHub check runs. Local QA and reviewer agents satisfied the independent review criterion while no GitHub PR review has been submitted. The user ran the isolated runtime on 2026-11-13 against staging and the guild-scoped `/jda-walkthrough` replied `JDA walkthrough OK`, closing the gate. PR #117 was subsequently **merged** into the epic branch by a regular merge commit at `255893a`, so the Phase 2B pass is now on the epic integration branch. The root runtime is still Kord-based and JDA-free |
| Phase 2C — voice/session/player message ports and adapters | **IN PROGRESS — implementation + local tests** | Acceptance criteria and branch/base posture are recorded in the Phase 2C section. Local branch `feature/114-phase-2c-voice-ports` was created from the fetched epic head `255893a` (the regular merge commit that brought PR #117 into the epic branch), and its plan commit is pushed to the same branch name. The neutral voice/audio/player-message ports, their JDA adapters, the DAVE 0.2.0 wiring and `/jda-voice-walkthrough` exist as uncommitted local working-tree changes with unit tests. Latest **local working-tree** run: `:core:test` 81 tests, `:jda-runtime:test` 224 tests and the root suite 380 tests (5 pre-existing skips) all pass with 0 failures/errors — **685 cases total (680 executed + 5 skips)**; local JaCoCo **module** line coverage is `:core` **207/219 = 94.52%** (branch **108/115 = 93.91%**) and `:jda-runtime` **869/947 = 91.76%** (branch **342/418 = 81.82%**), and the full `./gradlew build` passed cleanly. All coverage figures here are **local module JaCoCo report readings only — not Sonar, new-code or CI evidence**. Phase 2C is **not CI-verified**: no 2C PR has been opened (no #118), no CI run exists and user-run staging playback has not been done, so the accepted playback gate stays open. `master` is untouched |
| Phase 2D — channel/message/locale edge adapters | **OPEN** | Not started. Planned child branch and PR based on the immediately preceding unmerged branch (2C); carries the `LocalizationService` and `/locale` neutrality work. Must preserve the guild default locale (`en_US` for non-Community guilds, community-configurable) as distinct from the user/client locale, and validate conversion/fallback at the adapter boundary. The root bot's localization behaviour is **not** migrated |
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

With this result, subphase **1B is PASS and its gate is closed**, and the Phase 1 stop condition no
longer blocks Phase 2. Phase 2B was subsequently implemented on
`feature/114-phase-2b-command-port` and is now **PASS — gate closed 2026-11-13** (see the Phase 2B
section); broader Phase 2 remains **IN PROGRESS**.

### Phase 2A — domain-owned ID/locale values and locale persistence — IN PROGRESS

This plan, `.opencode/opencode.json`, `spikes/jda-dave/**` and the Phase 2A source and test changes
were carried on `feature/114-kord-to-jda-migration` and **squash-merged** into the epic integration
branch by PR #115 as `5aad7c7`; the merged tree is identical to the former Phase 2A head `67802d1`.
That branch was deleted after the merge. The merge makes the 2A groundwork part of the epic branch; it
does **not** complete Phase 2A, which stays **IN PROGRESS / partial** because `LocalizationService`
and `/locale` are still Kord-facing (2D). Read Git and the pull request for commit history; this
document asserts no head beyond the recorded merge commit.

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

Phase 2 is delivered in subphases. Each subsequent subphase gets its **own child branch and PR**, based
on the branch that carries the immediately preceding subphase; it is not folded into an already-open
earlier PR. Phase 2A's combined Phase 1/2A checkpoint landed as PR #115, **squash-merged** into the
epic branch at `5aad7c7`; the former `feature/114-kord-to-jda-migration` branch is deleted and nothing
is restructured in place. Phase 2B branched from the Phase 1B branch
`feature/114-native-failfast-check`; PR #116 was then **squash-merged** into the epic branch at
`c00bdf1`, after which PR #117 targeted the epic branch and was later **merged** there by a regular
merge commit at `255893a`. **2C is IN PROGRESS — implementation with local tests**: its acceptance
criteria are written below and its branch `feature/114-phase-2c-voice-ports` was created from the
fetched epic head `255893a`, because the Phase 2B pass is contained in that merge tree; its
implementation and unit tests exist as uncommitted local working-tree changes and pass locally, but no
2C PR (no #118), CI run or staging evidence exists yet. The epic branch
`feature/114-migrate-to-jda-jda-ktx` remains the
**final integration target**, and `master` is merged only at migration completion.

| Subphase | Scope | Branch/PR posture | Status |
| --- | --- | --- | --- |
| **2A** | Domain-owned `GuildId`/`LocaleCode` + neutral locale repository/use cases | **Squash-merged** into the epic branch by PR #115 as `5aad7c7`; the former `feature/114-kord-to-jda-migration` branch is deleted and the checkpoint is not restructured | **IN PROGRESS / partial** — merged groundwork does not complete 2A; see above |
| **2B** | Neutral command context + responder/response port; adapters translate defer/ephemeral, locales and IDs | Child branch `feature/114-phase-2b-command-port`, originally based on Phase 1B `feature/114-native-failfast-check`; after #116 merged to the epic branch (`c00bdf1`), PR #117 was retargeted to the epic branch (base branch name only; no live base SHA asserted) and later **merged** there by the regular merge commit `255893a`; its one plan conflict was resolved by an ancestry merge that changed no code | **PASS — gate closed 2026-11-13** — isolated `:core`/`:jda-runtime` modules, neutral command boundary and JDA 6.7.0 adapter implemented with unit tests; #117 CI passed on covered checkpoint `e607ff7`, on the docs-only `2850907`/`16247f8`/`d353113`/`988a7cd`, on `e4a9e29` (later completed green after an in-progress observation), and on `7bf5419`, the latest verified all-green checkpoint before the subsequent docs-only updates (75.0% new-code coverage, 0 new issues, 0 hotspots); a later dated snapshot of `f5f04d1` (docs-only) was **not yet green** — only Check Code Quality had concluded at 16:53:24Z — so the status of any later head is read from GitHub; the user-run staging `/jda-walkthrough` interaction succeeded on 2026-11-13. Local QA/reviewer agents satisfied the independent review criterion; no GitHub PR review submitted, and the root runtime is still Kord-based. The pass is unaffected by the #116 ancestry merge |
| **2C** | Voice/session/player-message narrow ports; JDA `AudioSendHandler`/DAVE and lifecycle stay in the adapter | Local branch `feature/114-phase-2c-voice-ports`, created from the fetched epic head `255893a` — the regular merge commit that contains PR #117's Phase 2B pass. Branch is pushed with the plan commit; implementation and tests are uncommitted local working-tree changes; **no PR is opened yet (no #118)** | **IN PROGRESS — implementation + local tests.** Acceptance criteria and branch/base posture are recorded in the Phase 2C section. Latest local working-tree run: 685 cases (680 executed + 5 pre-existing skips), all pass with 0 failures/errors; local JaCoCo module line coverage `:core` 207/219 = 94.52% (branch 108/115 = 93.91%), `:jda-runtime` 869/947 = 91.76% (branch 342/418 = 81.82%) — local module JaCoCo only, not Sonar/new-code CI evidence. **Not CI-verified**, no 2C PR (no #118), no CI run, no staging result |
| **2D** | Channel/message/locale edge adapters; move `LocalizationService` and `/locale` behind/adapted to a neutral port; keep the community-configurable guild default distinct from user/client locale and validate conversion/fallback at the adapter boundary | New child branch/PR based on the preceding subphase branch (2C) | **OPEN** — not started |

**Phase 2D locale acceptance note (OPEN — intended scope, not evidence).** 2D must keep the **guild
default locale** distinct from the **invoking user/client locale**. Legacy behaviour is that a
non-Community guild's default language is always `en_US`; only a Discord **Community** guild can
have its default language changed. The user/client locale describes the invoking client and must
never be persisted as, or conflated with, the guild default. Locale conversion and fallback —
including `unknown`, invalid or unsupported values — are validated at the Discord adapter boundary.
This note records intended 2D scope only: the root bot's localization behaviour has **not** migrated,
`LocalizationService` and `/locale` remain Kord-facing, and 2D stays **OPEN**.

Stacking rules:

- Each subsequent subphase branches from, and opens its PR against, the branch that carries the
  preceding subphase — never `master` and never the epic branch directly, until the epic integration
  step. Phase 2B branched from Phase 1B, `feature/114-native-failfast-check`; after PR #116 merged to
  the epic branch, PR #117 was retargeted to the epic branch. Because #117 was then **merged** into
  the epic branch at `255893a`, that merge commit is now what carries the Phase 2B pass, so **2C
  branches from the epic head `255893a`** rather than from the old Phase 2B branch. This is the
  documented exception to the child-branch rule: the preceding subphase is already integrated, and
  the epic head is its carrier.
- PR #115 was the **combined Phase 1/2A checkpoint**; it is **squash-merged** at `5aad7c7` and was
  never restructured. Later subphase work is not pushed into it.
- Subphase branches are **not** squashed into an already-merged earlier PR and **not** rebased onto
  `master` mid-flight.
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

### Phase 2B — neutral command boundary + isolated JDA runtime — PASS (gate closed 2026-11-13)

Phase 2B is **PASS** as of **2026-11-13**, and the pass remains valid through the #116 squash merge
and the subsequent ancestry merge that resolved the plan conflict. The implementation is on the
child branch `feature/114-phase-2b-command-port`, originally stacked on Phase 1B
`feature/114-native-failfast-check`. PR #116 has since been squash-merged into the epic branch
(`c00bdf1`), so PR #117 was retargeted to the epic branch and was later **merged** there by the regular
merge commit `255893a`; its single conflict in this plan was resolved with a normal ancestry merge that
kept the Phase 2B document. No code changed in that merge. #117's base is recorded here by branch name
only; no live base SHA is asserted. Commit `fbd9f5a` is the
**initial implementation checkpoint** and `e607ff7` the **covered implementation/CI checkpoint**.
`7bf5419` (`Corrected Phase 2B CI status record`) is the latest **verified all-green CI checkpoint**
before the subsequent docs-only updates: all four checks passed on 2026-11-13 — Check Code Quality
(16:47:15Z), SonarCloud Code Analysis (16:48:52Z), Build and analyze (16:48:56Z) and SonarCloud
(16:48:58Z) — with **75.0% Coverage on New Code**, 0 new issues and 0 hotspots. It was PR #117's head
at that point, not afterwards: the docs-only commits that follow it, including this one, move the
head. The earlier docs-only checkpoints `2850907` (`Recorded Phase 2B coverage gate pass`),
`16247f8` (`Recorded passing Phase 2B docs checkpoint`), `d353113` (`Document legacy guild locale
defaults`) and `988a7cd` (`Clarified Phase 2B CI and artifact scope`, last check at 16:38:44Z) also
passed all four #117 checks — Check Code Quality, Build and analyze, SonarCloud Code Analysis and
SonarCloud — as did `cd9d6db` and `e4a9e29` after its in-progress observation completed. `f5f04d1`
(`Recorded passing Phase 2B CI runs`, docs-only) was the head in a dated snapshot observed later on
2026-11-13 in which only **Check Code Quality** had concluded successfully (16:53:24Z), while
**Build and analyze** was still in progress and the SonarCloud checks were not yet listed; it was
therefore **not green at that snapshot**, and it is no longer the head. All of these
are **historical verified checkpoints**, not a guarantee about later heads: read the GitHub check runs
on PR #117 for whatever head is current rather than assuming green CI.

#### Phase 2B CI in-progress observation — `e4a9e29`, 2026-11-13 (later completed all-green)

This is a **dated in-progress observation**, not a CI verdict and not a failure, recorded so the
earlier handoff head is not confused with the verified checkpoints above.

| Item | Recorded state |
| --- | --- |
| Observation time | 2026-11-13 at **16:41:06Z** — the snapshot time, when PR #117 was on `e4a9e29` (`Refreshed Phase 2B CI handoff head`, docs-only). This timestamp is the observation time and the Check Code Quality completion time, **not** completion of the overall run |
| Check Code Quality | Concluded successfully — 16:41:06Z |
| Build and analyze | Still in progress at that moment |
| SonarCloud Code Analysis | Not yet listed at that moment |
| SonarCloud | Not yet listed at that moment |
| Later outcome | All four checks **completed successfully**: SonarCloud Code Analysis 16:44:04Z, Build and analyze 16:44:09Z, SonarCloud 16:44:11Z |
| Status now | Green, but only a **historical checkpoint** — `e4a9e29` was the head at that observation, and later docs-only commits (including this one) are newer heads |

#### Phase 2B CI snapshot — `f5f04d1`, 2026-11-13 (not green at that snapshot)

This is a **dated historical status snapshot**, not a failure verdict and not a statement about the
current head.

| Item | Recorded state |
| --- | --- |
| Observation time | 2026-11-13 at about **16:53:24Z**, while PR #117 was on `f5f04d1` (`Recorded passing Phase 2B CI runs`, docs-only) |
| Check Code Quality | Concluded successfully — 16:53:24Z |
| Build and analyze | In progress at that moment |
| SonarCloud Code Analysis | Not yet listed at that moment |
| SonarCloud | Not yet listed at that moment |
| Snapshot verdict | **Not green at that snapshot** — two of four checks had not concluded |
| Scope | Observed **before this docs-only follow-up**, which itself creates a newer head; GitHub is authoritative for the resulting head and its status |

`988a7cd` (`Clarified Phase 2B CI and artifact scope`) remains an earlier green checkpoint: all four
#117 checks — Check Code Quality, Build and analyze, SonarCloud Code Analysis and SonarCloud —
concluded successfully, the last of them at **2026-11-13 16:38:44Z**. The earlier docs-only commit
`d353113` (`Document legacy guild locale defaults`) is **also green**: the same four checks passed,
latest completion **2026-11-13 16:34:38Z**. None of these green checkpoints is evidence that a later
head passes. **GitHub's live check runs on PR #117 are authoritative** for `f5f04d1`, for `7bf5419`,
for `e4a9e29`, for `988a7cd`, for `d353113`, and for any later commit — refresh them before relying on
CI status.

The gate was closed by recorded evidence: green CI on the verified checkpoints `cd9d6db`, `988a7cd`,
`e4a9e29` and `7bf5419` (and on the docs-only `d353113`), with 75.0% new-code coverage and no new
issues or hotspots recorded on the covered and Sonar-gated checkpoints, local QA and reviewer agents
passing, and a user-run staging interaction of the isolated JDA runtime on 2026-11-13 (see the staging
evidence subsection below). Green CI on `7bf5419` reflects that checkpoint only, not a later head;
read the live checks for any newer commit. Broader **Phase 2 remains IN PROGRESS** — subphases
2C/2D, root Kord removal and root bootstrap wiring are still open, and the isolated runtime is
**not** wired into the root application.

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

Phase 2B verifies the **Gradle dependency boundary** only: the root project declares no JDA
dependency and does not wire `:jda-runtime`. That is not production-artifact validation. The formal
**Phase 6** Linux/production fat-JAR checks — Kord-free artifact inspection, duplicate/`META-INF`
handling, digest recording and the Temurin 21 Linux staging run — remain **OPEN** and are not closed
by the Phase 2B PASS or by any local inspection.

Unit tests exist in both modules. The last local run recorded **18 `:core` tests** and
**62 `:jda-runtime` tests**; the root project suite recorded **380 tests with 5 pre-existing
skips**, which is **460** tests combined. The implementation is exercised only through the isolated
`:jda-runtime` entry point — the root application remains Kord-based and contains no JDA wiring.

#### Phase 2B staging evidence — PASS (2026-11-13)

User-run evidence from the isolated JDA runtime on **2026-11-13**, configured with the staging
environment variables. This is a sanitized record: no raw log, no token, and no guild, channel, user
or application identifiers are reproduced here or in the repository.

| Check | Recorded result |
| --- | --- |
| Command | `./gradlew :jda-runtime:run` on `feature/114-phase-2b-command-port` |
| JDA login | Completed |
| WebSocket | Connected |
| Ready state | Reached |
| Command registration | `/jda-walkthrough` registered guild-scoped |
| Gateway intents | Logged as **no intents enabled** |
| Command reply | `JDA walkthrough OK` with the expected context and `locale=en-US` (guild default; see the locale interpretation below) |
| Shutdown | User stopped the long-running process with Ctrl+C; no failure reported |

Scope of this evidence:

- It validates the **isolated `:jda-runtime` module only**: login, gateway connection, ready state,
  guild-scoped command registration and one command round-trip. It does not validate the root
  application, root bootstrap wiring, `/locale`, voice/DAVE, or any other command family — those
  remain 2C/2D and Phase 6 work.
- No global command was published and no gateway intents were enabled.
- The runtime was stopped deliberately with Ctrl+C; this is a normal termination, not a failure, and
  no error was reported.
- Only the sanitized facts above are recorded.

**Locale interpretation for this run.** The observed `locale=en-US` is the **guild default locale**
reported by Discord for a non-Community guild, not evidence that anyone explicitly selected English
or that a user-preferred client locale was applied. Legacy behaviour is that unless a guild is a
Discord **Community** guild — where the default language may be changed — its default language is
always `en_US`; the API's hyphenated `en-US` is the same default. The walkthrough context resolves
the **guild locale first**, falling back to the user locale only when the guild locale is
unavailable, so this observation asserts only the expected non-Community guild default. It says
nothing about `/locale`, persisted per-guild choices, or the root bot's localization behaviour,
which remain **2D** work and are **not** migrated by Phase 2B.

#### Phase 2B coverage and CI checkpoints

The checkpoint entries below span **2026-11-08** (`e607ff7`, `2850907`, `16247f8`) and
**2026-11-13** (`cd9d6db`, `d353113`, `988a7cd`, `e4a9e29`, `7bf5419`, `f5f04d1`); they are retained to
preserve the CI history and are superseded by the 2026-11-13 gate closure above.
The 2B **gate** status is **PASS** on the recorded evidence; CI status for the branch head is a
separate question, and every entry below is a dated historical checkpoint — read the live GitHub
check runs on PR #117 for whichever head is current.

- **PR #117 CI on `e607ff7` — PASS (covered implementation/CI checkpoint).** All four checks
  concluded successfully: **Check Code Quality**, **Build and analyze**, **SonarCloud Code
  Analysis** and **SonarCloud**. The Sonar quality gate reported **75.0% Coverage on New Code**,
  **0** new issues and **0** hotspots. If a later docs-only commit moves the branch head, `e607ff7`
  remains the implementation/CI checkpoint rather than the current HEAD.
- **PR #117 CI on later docs-only `2850907` — PASS (docs-only checkpoint).** This commit changed
  only `docs/migrations/kord-to-jda.md` and also concluded all four checks successfully: **Check
  Code Quality**, **Build and analyze**, **SonarCloud Code Analysis** and **SonarCloud**. It is
  recorded as a historical verified checkpoint alongside `e607ff7`, not as proof that every later
  commit is green; read the GitHub check runs for the current head.
- **PR #117 CI on later docs-only `16247f8` — PASS (docs-only checkpoint).** This commit
  (`Recorded passing Phase 2B docs checkpoint`) changed only `docs/migrations/kord-to-jda.md` and
  also passed all four checks: **Check Code Quality**, **Build and analyze**, **SonarCloud Code
  Analysis** and **SonarCloud**. Like `e607ff7` and `2850907` it is a **historical verified
  checkpoint**; it is not evidence that heads after it are green, so read the current GitHub check
  runs on PR #117.
- **PR #117 CI on `cd9d6db` (`Clarified Phase 2B CI checkpoint history`) — PASS.** All four checks
  passed: **Check Code Quality**, **Build and analyze**, **SonarCloud Code Analysis** and
  **SonarCloud**, with 75.0% Coverage on New Code, 0 new issues and 0 hotspots. It is an earlier
  verified checkpoint, no longer the latest; read the check runs for any later head.
- **PR #117 CI on the later docs-only `d353113` (`Document legacy guild locale defaults`) — PASS.**
  All four checks concluded successfully: **Check Code Quality**, **Build and analyze**,
  **SonarCloud Code Analysis** and **SonarCloud**, the last of them at **2026-11-13 16:34:38Z**.
- **PR #117 CI on the later docs-only `988a7cd` (`Clarified Phase 2B CI and artifact scope`) —
  PASS, earlier all-green checkpoint.** All four checks concluded successfully: **Check Code
  Quality**, **Build and analyze**, **SonarCloud Code Analysis** and **SonarCloud**, the last of them
  at **2026-11-13 16:38:44Z**. Like the other checkpoints it is historical and does not prove that a
  later head is green; the later commits `e4a9e29`, `7bf5419` and `f5f04d1` are recorded separately
  below.
- **PR #117 CI on the later docs-only `e4a9e29` (`Refreshed Phase 2B CI handoff head`) — PASS,
  completed after an in-progress observation.** At the 2026-11-13 refresh — **16:41:06Z**, the
  observation time, not the end of the run — only **Check Code Quality** had concluded successfully,
  with **Build and analyze** still in progress and both
  SonarCloud checks not yet listed. All four then concluded successfully: **SonarCloud Code
  Analysis** 16:44:04Z, **Build and analyze** 16:44:09Z and **SonarCloud** 16:44:11Z. It was green from
  that point while it was the head; the earlier partial snapshot is retained only as dated history and
  must not be read as a current failure or as a statement about the current head.
- **PR #117 CI on `7bf5419` (`Corrected Phase 2B CI status record`) — PASS, latest verified all-green
  checkpoint before the subsequent docs-only updates.** All four checks concluded successfully
  on **2026-11-13**: **Check Code Quality** 16:47:15Z, **SonarCloud Code Analysis** 16:48:52Z,
  **Build and analyze** 16:48:56Z and **SonarCloud** 16:48:58Z; Sonar reported **75.0% Coverage on
  New Code**, **0** new issues and **0** hotspots. It was the head at that point; later docs-only
  commits, including this update, move the head. This is a checkpoint, not a promise that later
  commits are green — read the live check runs on PR #117 for any newer head.
- **PR #117 CI snapshot on the later docs-only `f5f04d1` (`Recorded passing Phase 2B CI runs`) — NOT
  GREEN at that snapshot (dated observation).** Observed on **2026-11-13**: only **Check Code Quality**
  had concluded successfully (**16:53:24Z**), **Build and analyze** was still in progress and the
  SonarCloud checks were not yet listed. That snapshot was taken **before this docs-only follow-up**,
  which itself creates a newer head, so it is historical only and not a claim about the resulting
  HEAD; GitHub is authoritative for the later status.
- **Historical failure on prior head `001e722`.** There the **Check Code Quality** and **Build and
  analyze** jobs passed, as did the separately named **SonarCloud** check, while the **SonarCloud
  Code Analysis** check failed its Quality Gate at **0.0% Coverage on New Code** against a required
  **>=50%**, because `:core` and `:jda-runtime` emitted **no JaCoCo XML reports**. That failure is
  kept as history, not as current status, and must not be summarised as "SonarCloud failed" without
  naming the check that did.
- **Coverage wiring that fixed it.** JaCoCo `0.8.12` (from the version catalog) is applied in
  both modules; each module's `tasks.test` finalizes its own `jacocoTestReport`, which requires XML
  at the module-local default `build/reports/jacoco/test/jacocoTestReport.xml`; the root `sonar`
  task depends on `:core:jacocoTestReport` and `:jda-runtime:jacocoTestReport`. Those Gradle build
  changes passed the #117 CI run on `e607ff7`. The implementation change touched the three Gradle
  build files plus this migration document — no root runtime, `fatJar` task or dependency change,
  and the root `sonar` task only gained the two module report dependencies.
- **Local QA (not CI).** Module XML reports exist; line coverage is `:core` **81.40%** (35/43),
  `:jda-runtime` **73.63%** (148/201), combined **75.00%** (183/244), matching the CI Sonar figure.
  Tests are unchanged at **18 `:core` / 62 `:jda-runtime` / 380 root with 5 pre-existing skips =
  460**. The Code Quality and reviewer agents passed **locally**, which satisfies the independent
  review criterion; no GitHub PR review has been submitted.
- **Then-open item, now closed:** manual staging login and `/jda-walkthrough` interaction were
  pending on 2026-11-08 and were recorded as passing on **2026-11-13**.

Acceptance for closing 2B — **all criteria met, gate closed 2026-11-13**:

- `:core` compiles with no Kord/JDA dependency, and the neutral boundary exposes no `dev.kord.*` /
  `net.dv8tion.*` types. The branch carries a reflection-based unit test over the neutral types; the
  full import rule covering all core packages remains the Phase 2 exit/CI gate and is **not** closed
  by it.
- The JDA responder honours the neutral contract under test: one defer, one response, deferred
  responses edit the original with the defer visibility, and a failed REST action restores the prior
  state so the router fallback stays reachable.
- The bootstrap registers exactly one guild-scoped command and refuses to start without a token and
  a development guild id; no global registration path exists. **Confirmed on staging 2026-11-13** —
  guild-scoped `/jda-walkthrough` registered, no intents enabled, no global command published.
- Independent final QA/review passes, and any required manual staging login plus a
  `/jda-walkthrough` interaction are recorded. Local Code Quality and reviewer agents passed, #117 CI
  is green on the latest verified all-green checkpoint `7bf5419` (the last verified all-green
  checkpoint before the subsequent docs-only updates), and the staging interaction is
  recorded above. No GitHub PR review has been submitted; that is not itself a required gate. Phase 2B
  is therefore **PASS / closed** while broader Phase 2 remains **IN PROGRESS**.

### Phase 2C — voice/session/player-message ports and adapters — IN PROGRESS (implementation; local tests only)

**Status: IN PROGRESS — implementation with local working-tree tests.** This section records the
agreed acceptance criteria, scope limits, branch/base posture and the latest local test/coverage
status for Phase 2C. The acceptance criteria below are the gate; they are **not** evidence of a pass.
The implementation and its unit tests exist as **uncommitted local working-tree changes** and pass
locally, but **no 2C PR has been opened (no #118 yet), and no CI run or staging result exists**.
Nothing here closes 2C or broader Phase 2.

#### Branch and base posture

- Phase 2B was **merged** into the epic branch `feature/114-migrate-to-jda-jda-ktx` by a **regular
  merge commit** — not a squash — at
  `255893abcef31554e13ae1713086a9b7c1217c60`. Its parents are the epic tip `c00bdf1` and the Phase 2B
  head `7d8661d`, and its tree is identical to `7d8661d`, so the epic head carries the Phase 2B pass
  unchanged.
- The correct 2C base is therefore the **fetched remote epic head `255893a`**. It is **not** the stale
  local `4badb92` (that is `master`), and it is not the old Phase 2B branch.
- Phase 2C branch: **`feature/114-phase-2c-voice-ports`**, created from the fetched
  `origin/feature/114-migrate-to-jda-jda-ktx` at `255893a`. The plan commit is pushed to the same
  branch name; the implementation and unit tests are present only in the **local working tree** at
  this checkpoint (they are not committed here). **No 2C PR exists**, and the intended PR targets the
  epic branch only after the CI and staging gates exist.
- PR #117 stays **closed / merged** and is not modified. `master` stays untouched, and the root
  runtime stays Kord-based and JDA-free during 2C.

#### Implementation checkpoint — implemented in the working tree, tested locally

- `:core` gains neutral outgoing-audio values/ports (`AudioFrame`, `AudioFrameFormat`,
  `AudioFrameSource`, `VoiceSessionPort` and its result types), a text-only `PlayerMessagePort`, and a
  neutral `/jda-voice-walkthrough` handler that depends only on those ports.
- `:jda-runtime` gains the JDA `AudioSendHandler`, the voice connection/lifecycle adapter, the
  text-only player-message adapter, DAVE 0.2.0 configuration and the pre-encoded 20 ms Opus tone
  source. The tone is a generated bare-packet resource in
  `jda-runtime/src/main/resources` — present in the working tree, not yet committed at this
  checkpoint — produced by `jda-runtime/tools/generate-walkthrough-tone.py` (Python stdlib + macOS
  `afconvert` only), with exactly 75 packets, every packet's 20 ms duration derived from its Opus TOC
  byte and every packet at or below the 1275-byte limit. The `api`, `impl-jni`, `adapter-jda`,
  `natives-darwin` and `natives-linux-x86-64` 0.2.0 modules are confined to this module; the root
  catalog's existing `libdave` / `libdave-natives` aliases and root dependency graph are unchanged.
- The isolated bootstrap enables only `GUILD_VOICE_STATES` plus `CacheFlag.VOICE_STATE`, performs the
  DAVE native/protocol preflight before gateway login, and registers both guild-scoped diagnostics
  through the existing registrar. No global command and no privileged intent is introduced.
- **Latest local working-tree result (not CI):** `:core:test` **81** tests and `:jda-runtime:test`
  **224** tests pass with 0 failures and 0 skips, and the root suite is **380** tests (5
  pre-existing skips) with 0 failures/errors — **685 cases total (680 executed + 5 skips)**. Local
  JaCoCo **module** line coverage is `:core` **207/219 = 94.52%** (branch **108/115 = 93.91%**) and
  `:jda-runtime` **869/947 = 91.76%** (branch **342/418 = 81.82%**). Those are **local module
  JaCoCo report readings only** — independently reproducible per module, **not** Sonar's new-code
  metric and not CI evidence; no aggregate changed-lines coverage figure is claimed for Phase 2C.
  The clean full `./gradlew build` passed locally.
- This checkpoint is **not** a pass: local tests and the local build are green, but the source, tests
  and tone resource are **uncommitted working-tree changes**, and **no 2C PR (no #118), no CI run and
  no staging evidence exist**, so the 2C gate remains open.

#### Scope

Phase 2C introduces the **narrow ports** that voice/session and player messaging need, plus their JDA
adapters, so the business layer can eventually stop depending on Kord:

- Neutral **outgoing audio/frame** port and neutral **voice-session** port in `:core`.
- Neutral **text-only player-message** port in `:core` (send/edit a plain text player message).
- JDA 6.7.0 **voice/session/message adapters** in `:jda-runtime`, including the `AudioSendHandler`,
  DAVE integration and JDA voice lifecycle.
- A guild-only diagnostic **`/jda-voice-walkthrough`** that proves the port/adapter path end to end on
  staging.

Only the ports the business logic actually needs are introduced. Phase 2C does **not** build a generic
facade over the JDA voice or message APIs. The 2C diagnostic covers **outgoing playback only**;
pause/resume preservation is deferred to **Phase 3** real-player integration (see below) and remains
an overall migration requirement.

#### Acceptance criteria

1. **Core neutrality.** Neutral outgoing-audio/frame, voice-session and text-only player-message ports
   live in `:core` and expose **no** Kord, JDA or libdave types — no `dev.kord.*`, no `net.dv8tion.*`,
   no `moe.kyokobot.libdave.*` (and no adapter/DAVE types) in their signatures.
2. **Adapter containment.** The JDA 6.7.0 voice/session/message adapters, the `libdave` /
   `adapter-jda` **0.2.0** modules and the DAVE native dependencies appear **only** in
   `:jda-runtime`. The root project's **Kord runtime, dependency graph and `fatJar` task are
   untouched**; any version-catalog addition is additive and consumed only by `:jda-runtime`, and does
   not change root resolution.
3. **Guild-only diagnostic `/jda-voice-walkthrough`.** It uses only the **non-privileged
   `GUILD_VOICE_STATES` intent** plus the voice-state cache, performs a **DAVE preflight**, sends
   **one controlled Opus tone**, reports a **frame count**, and **attempts bounded voice cleanup and
   reports failures** on every exit path (success, failure, timeout, cancellation): an unconfirmed
   disconnect is reported as `DISCONNECT_UNCONFIRMED` and a failed restore as `CLEANUP_FAILED`, so
   **no clean-disconnect pass is claimed if cleanup fails**. The isolated bootstrap also attempts its
   own cleanup steps **independently** — voice disconnect first, then the command router, the
   shutdown hook and the JDA client with a bounded await — so one failing step cannot skip the rest;
   a cleanup failure after an otherwise normal shutdown is reported as a failure exit, and cleanup
   exceptions are attached as suppressed to a preserved primary startup failure (a fatal cleanup
   error still propagates). The reported **frame count is the number of frames supplied to JDA's
   `AudioSendHandler`/send loop** — a hand-off count, **not** a confirmation of network delivery.
   It is registered guild-scoped only; no global command and no privileged intents.
4. **Explicit non-goals (out of scope for 2C).** Receive-audio and the assistant voice feature, TTS /
   Whisper, rich player UI and message components, root bootstrap wiring, and Phase 6 packaging and
   artifact validation are **not** part of 2C.
5. **Unit tests.** Cover at minimum: core neutrality (no Kord/JDA/libdave leak in the new ports),
   audio-frame semantics (exactly one frame per send cycle, no blocking), adapter and
   session **connection errors, timeouts and cleanup**, the **DAVE configuration seam**, the
   **text-only message port**, and **bootstrap guild-only registration**. Pause/resume preservation is
   **not** part of this criterion — it is deferred to Phase 3 real-player integration (see below).
6. **Build and quality.** Full module and root build must pass, and the PR's CI must be green with
   **Sonar new-code coverage >= 50% (target >= 75%)**, no new issues and no hotspots. Local status at
   this checkpoint: the clean full `./gradlew build` passed locally and local module JaCoCo reports
   `:core` line coverage 207/219 = 94.52% (branch 108/115 = 93.91%) and `:jda-runtime` line coverage
   869/947 = 91.76% (branch 342/418 = 81.82%); those are **local module JaCoCo reports only, not
   Sonar/new-code coverage or CI evidence**, and **no 2C CI run exists yet**, so the CI-side >= 50%
   Sonar new-code check is not met.
7. **User-run staging gate (sanitized evidence only).** Bot login and ready state; the walkthrough
   command is available **guild-scoped only**; DAVE native and protocol initialize; **all 75 20 ms
   Opus frames are supplied to JDA's `AudioSendHandler`/send loop** — the reported frame count proves
   hand-off only, **not** confirmed network delivery; a second client in the channel **hears the
   tone**, which is the **actual delivery proof**; the bot **disconnects cleanly**; and **no receive
   path** is exercised. Record sanitized facts only — no token, raw log, or
   guild/channel/user/application identifier. **Status at this checkpoint: not yet
   run** — no user-run staging playback has been performed, so this accepted playback gate remains
   open.
8. **Phase boundary.** Passing 2C does **not** close broader Phase 2: **2D** (channel/message/locale
   edge adapters), the **root runtime migration**, and the **architectural import rule** remain open.
   **Phase 6** JDK 21 / fat-JAR / native artifact validation also remains open.

#### Deferred to Phase 3 — pause/resume preservation (user decision)

The user explicitly chose to **defer pause/resume preservation to Phase 3 real-player integration**.
It remains an **overall migration requirement** — it is carried by the Phase 3 exit criteria and the
architecture guardrail below — but it is **not a Phase 2C acceptance gate**. The 2C diagnostic sends
one controlled tone and has no pause control, so 2C makes no pause-semantics claim. Do not record 2C
as covering pause/resume, and do not treat the deferral as dropping the requirement.

#### Inherited caveat from Phase 2B

The Phase 2B locale caveat still applies: a non-Community guild's default language is `en_US`, and the
observed `locale=en-US` is that guild default, not an explicit or user-selected locale. The guild
default locale stays distinct from the invoking user/client locale; 2D behaviour is out of scope for
2C and no localization behaviour is migrated here.

#### Recording rules

Only sanitized evidence is recorded in this plan. No secrets, bot/guild/channel/user identifiers,
tokens or raw logs are added. The earlier plan checkpoint changed **only this migration plan**. The
current working tree also contains the uncommitted 2C implementation, test and resource files plus
the module build/catalog edits recorded in the implementation checkpoint; this document update
itself modifies **no source, build, test or asset file and no PR metadata**. The local test,
coverage and build numbers above are **working-tree results**, not committed or CI evidence.

### Checkpoint state

- The epic integration branch `feature/114-migrate-to-jda-jda-ktx` is at
  `255893abcef31554e13ae1713086a9b7c1217c60`, the **regular merge commit of PR #117** whose parents are
  `c00bdf1` (squash merge of PR #116 on top of `5aad7c7`, itself the squash merge of PR #115 on base
  `4badb92`) and `7d8661d` (the Phase 2B head). Its tree is identical to `7d8661d`. PRs #115, #116 and
  #117 are all **closed/merged**, and the former `feature/114-kord-to-jda-migration` branch is deleted.
- Phase 1B sat on `feature/114-native-failfast-check` as PR #116, which had been retargeted to the
  **epic branch** at `5aad7c7` because the former parent branch was squash-merged and deleted. It
  carried only the negative-test packaging task, the spike runbook section and these plan edits. Its
  head `1b753b9` was an **ancestry-only merge** of `5aad7c7` into `49b4bc1`: the four add/add
  conflicts were resolved by retaining the Phase 1B versions, so the resolved tree was
  **byte-identical** to `49b4bc1` and **no content changed**. All four checks on `1b753b9` passed on
  2026-11-13. PR #116 has since been **squash-merged** into the epic branch as `c00bdf1`. Its gate
  is **PASS** as of 2026-11-08.
- Phase 2B is on `feature/114-phase-2b-command-port`, which was originally stacked on Phase 1B. It
  adds the isolated `:core`/`:jda-runtime` modules plus the root `:core` dependency and
  version-catalog entries; the parent branches are not rebased or rewritten for it. Initial
  implementation checkpoint
  `fbd9f5a`; covered implementation/CI checkpoint `e607ff7`, where all four #117 checks passed
  (75.0% new-code coverage, 0 new issues, 0 hotspots); the later docs-only checkpoints `2850907`,
  `16247f8`, `d353113` and `988a7cd` also passed all four, as did `cd9d6db`, and `e4a9e29` later
  completed all four checks successfully after an in-progress observation at 16:41:06Z (the
  observation time, when only Check Code Quality had concluded). `7bf5419`
  (`Corrected Phase 2B CI status record`) is the latest verified all-green CI checkpoint before the
  subsequent docs-only updates: Check Code Quality 16:47:15Z, SonarCloud Code Analysis
  16:48:52Z, Build and analyze 16:48:56Z, SonarCloud 16:48:58Z, with 75.0% new-code coverage, 0 new
  issues and 0 hotspots; it was PR #117's head at that point, not afterwards. A later dated snapshot
  of `f5f04d1` (docs-only) was **not green** — only Check Code Quality had concluded at 16:53:24Z —
  and GitHub holds the status of the head that follows it. All are historical verified checkpoints —
  read the GitHub check runs for any later head, because no recorded run proves a newer commit green.
  The earlier head `001e722` passed **Check Code
  Quality**, **Build and analyze** and the separately named **SonarCloud** check, but the **SonarCloud
  Code Analysis** check failed its Quality Gate at 0.0% new-code coverage; the JaCoCo 0.8.12 plus root
  `sonar` wiring fixed it. Local QA and reviewer agents satisfied the independent review criterion; no
  GitHub PR review is submitted. The user-run staging `/jda-walkthrough` interaction on **2026-11-13**
  closed the gate: **Phase 2B is PASS**. The root runtime is still Kord-based. After #116 merged,
  PR #117 was retargeted to the epic branch and was later **merged** there by a regular merge commit
  at `255893a`; the Phase 2B pass is now carried by the epic head, not by the old child branch.
- **Phase 2C** is on `feature/114-phase-2c-voice-ports`, created from the fetched epic head
  `255893a` — the correct base, not the stale local `4badb92` (that is `master`) and not the old Phase
  2B branch. Its first pushed commit was **plan-only** (this document); the neutral ports, JDA
  adapters, DAVE 0.2.0 wiring, tone resource and unit tests exist as **uncommitted local
  working-tree changes** and pass locally (685 cases: `:core` 81 + `:jda-runtime` 224 + root 380 with
  5 pre-existing skips = 680 executed + 5 skips, 0 failures/errors; local module JaCoCo line coverage
  `:core` 207/219 = 94.52% (branch 108/115 = 93.91%) and `:jda-runtime` 869/947 = 91.76% (branch
  342/418 = 81.82%) — local module reports only, not Sonar/new-code CI evidence; the clean full
  `./gradlew build` passed locally). **No 2C PR has been opened (no #118), no CI run exists and no
  staging result exists**; its acceptance criteria and scope are recorded in the Phase 2C section, and
  `master` is untouched.
- The PR #117 conflict with the epic branch was resolved by a **normal ancestry merge** of `c00bdf1`
  into `feature/114-phase-2b-command-port`. Only `docs/migrations/kord-to-jda.md` conflicted; the
  Phase 2B document was kept, and every non-doc path's merged tree equals the prior Phase 2B head
  `e61f301`. **No code changed** in that merge — only this current-stack/handoff record.
- `.opencode/opencode.json`, this plan and `spikes/jda-dave/**` are **tracked** in the epic branch
  history: `8ca6242` recorded them on the former working branch, `5aad7c7` brought them into the
  epic branch and `c00bdf1` carried them forward with the #116 squash merge. They are part of the
  migration record and are updated as the migration proceeds.
- This plan is now maintained on `feature/114-phase-2c-voice-ports`. The epic head `255893a` contains
  the Phase 2B version of this document through the #117 merge; the Phase 2C plan and current-status
  edits are on the 2C branch (the status update itself is uncommitted at this checkpoint) and do
  **not** reach the epic branch until a 2C PR exists and is merged. Commit status and history are read
  from Git and the pull requests, not asserted by this document.
- PR #115 was the **agreed combined Phase 1/2A checkpoint** and is **not restructured**; it was
  squash-merged as `5aad7c7`. PR #116 was then squash-merged as `c00bdf1`, and PR #117 was merged as
  the regular merge commit `255893a`. Later subphases land as separate branches/PRs: **2C branches from
  the epic head `255893a`** because it now carries the Phase 2B pass, and **2D branches from the 2C
  branch**. The epic branch remains the **final integration target**; `master` is merged only at
  migration completion.
- No secrets, bot/guild/channel/user identifiers, tokens or raw logs are recorded in this plan or
  in the spike.

### Handoff snapshot — 2026-11-08 (historical, superseded by 2026-11-13)

> Historical snapshot only. It accurately reflected the state on 2026-11-08, when Phase 2B was
> **IN PROGRESS** and staging was pending. It is retained for the record and is **superseded** by
> the post-merge 2026-11-13 handoff snapshot below. The branch/PR posture recorded here is itself
> out of date: `feature/114-kord-to-jda-migration` was later squash-merged by #115 and deleted, and
> #116 now targets the epic branch.

Branch, commit and PR facts below were read from Git and GitHub; they are **not** gate evidence.

| Branch / PR | Handoff state |
| --- | --- |
| `feature/114-migrate-to-jda-jda-ktx` (epic baseline) | Still at `4badb92`; unchanged |
| `feature/114-kord-to-jda-migration` → draft PR #115 (base: epic) | Remote head `67802d1`. Carries the combined Phase 1/2A checkpoint: the plan/spike record and the neutral locale repository/use-case boundary. As of 2026-11-08, Phase 1 was **PASS** (1A + 1B recorded), Phase 2A was **partial**, and Phase 2B was **IN PROGRESS** on its child branch |
| `feature/114-native-failfast-check` → draft PR #116 (base: the 2A feature branch) | Remote head `49b4bc1` (`Recorded Phase 1B fail-fast pass`); `1461204` was the earlier pre-pass-update head. Artifact, runbook and the pass record live here. **Phase 1B PASS — gate closed on 2026-11-08** |
| `feature/114-phase-2b-command-port` → draft PR #117 (base: the Phase 1B branch, PR #116) | As of 2026-11-08: initial implementation checkpoint `fbd9f5a`; the documentation commits that recorded this handoff followed it. Carried the isolated `:core`/`:jda-runtime` modules, the neutral command boundary, the JDA 6.7.0 adapter and the guild-only `/jda-walkthrough`, with unit tests, plus JaCoCo 0.8.12 coverage for both modules wired into root `sonar`. **Phase 2B was IN PROGRESS at that date** — all four #117 checks had passed on covered implementation/CI checkpoint `e607ff7` (75.0% new-code coverage, 0 new issues, 0 hotspots) and again on the later docs-only checkpoints `2850907` and `16247f8`; all are historical verified checkpoints, so read the GitHub check runs for the current head. The earlier `001e722` history is that the SonarCloud Code Analysis check failed its Quality Gate at 0.0% new-code coverage while Check Code Quality, Build and analyze and the separately named SonarCloud check passed. Local QA/review had passed, no GitHub PR review was submitted, and manual staging interaction evidence was still pending at that date |

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
  (`feature/114-native-failfast-check`) and targets that branch; PR #115 was the combined
  Phase 1/2A checkpoint.

These next actions were **superseded on 2026-11-13** and are retained only as history.

### Handoff snapshot — 2026-11-13 pre-merge (historical)

> Historical snapshot only. It reflected the stack **before** PR #115 was squash-merged: at that time
> `feature/114-kord-to-jda-migration` still existed, #115 was still open, and #116 still targeted the
> 2A branch. It is retained for the record and is **superseded** by the post-merge snapshot below.

Branch, commit and PR facts below were read from Git and GitHub; they are **not** gate evidence
beyond what is recorded above.

| Branch / PR | Handoff state |
| --- | --- |
| `feature/114-migrate-to-jda-jda-ktx` (epic baseline) | Was at `4badb92` at that time; it has since advanced to `5aad7c7`, the squash merge of PR #115 |
| `feature/114-kord-to-jda-migration` → draft PR #115 (base: epic) | Carried the combined Phase 1/2A checkpoint: the plan/spike record and the neutral locale repository/use-case boundary. Phase 1 is **PASS** (1A + 1B recorded), Phase 2A is **partial/IN PROGRESS**, Phase 2B is **PASS on its child branch**. PR #115 has since been **squash-merged** as `5aad7c7` and this branch **deleted**; the merge does not complete Phase 2A |
| `feature/114-native-failfast-check` → draft PR #116 (base: the 2A feature branch at that time, since retargeted to the epic branch) | Artifact, runbook and the pass record live here. **Phase 1B PASS — gate closed on 2026-11-08**. Read the actual head from Git and PR #116 |
| `feature/114-phase-2b-command-port` → draft PR #117 (base: the Phase 1B branch, PR #116) | Latest verified all-green CI checkpoint before the subsequent docs-only updates: `7bf5419` (`Corrected Phase 2B CI status record`, docs-only), where all four checks passed on 2026-11-13 — Check Code Quality 16:47:15Z, SonarCloud Code Analysis 16:48:52Z, Build and analyze 16:48:56Z, SonarCloud 16:48:58Z — with 75.0% new-code coverage, 0 new issues and 0 hotspots; it was the head at that point, not afterwards. The earlier docs-only `988a7cd` is also green (all four checks, last completed 16:38:44Z), and `e4a9e29` completed all four checks successfully after an in-progress observation at 16:41:06Z. A later dated snapshot of `f5f04d1` (docs-only) was **not green** — only Check Code Quality had concluded at 16:53:24Z, with Build and analyze running and the SonarCloud checks unlisted. These are dated checkpoints only; the current head is whatever this follow-up produced, so GitHub's live check runs on PR #117 are authoritative for it. Carries the isolated `:core`/`:jda-runtime` modules, the neutral command boundary, the JDA 6.7.0 adapter and the guild-only `/jda-walkthrough`, with unit tests, plus JaCoCo 0.8.12 coverage for both modules wired into root `sonar`. **Phase 2B PASS — gate closed 2026-11-13**: all four #117 checks passed on `7bf5419`, `e4a9e29` and `988a7cd` (and earlier on `cd9d6db`, `e607ff7`, `2850907`, `16247f8`, `d353113`; 75.0% new-code coverage, 0 new issues, 0 hotspots), local QA and reviewer agents satisfied the independent review criterion, and the user-run staging `/jda-walkthrough` interaction succeeded. No GitHub PR review is submitted; that is not itself a required gate. **Keep #117 draft and do not merge it ahead of parent PR #116** |

- **Phase 2B staging evidence (2026-11-13, sanitized).** The user ran
  `./gradlew :jda-runtime:run` on the Phase 2B branch with staging environment variables. The
  isolated runtime completed JDA login, WebSocket connection, ready state and guild-scoped
  `/jda-walkthrough` registration, and logged no gateway intents enabled. Invoking
  `/jda-walkthrough` returned `JDA walkthrough OK` with the expected context and `locale=en-US`,
  which is the expected **guild default** for a non-Community guild: unless a guild is a Discord
  Community guild where the default language may be changed, its default is always `en_US`. It is
  **not** evidence of an explicit English selection or of a user-preferred locale, and it does not
  migrate the root bot's localization behaviour. The user stopped the long-running process with
  Ctrl+C and no failure was reported. No token, raw log, or guild/channel/user/application identifier
  is recorded.
- **Gate posture.** Phase 2B is **PASS / closed** on 2026-11-13. Broader **Phase 2 remains IN
  PROGRESS**: 2C and 2D are **OPEN**, root Kord removal and bootstrap wiring are not done, and the
  Phase 2 exit criteria (including the architectural import rule) are still open. Issue #114 stays
  **open**.
- **No root wiring.** The root application remains Kord-based and JDA-free; only the isolated
  `:jda-runtime` module touches JDA. Phase 2B does **not** mean JDA is wired into the root app.
- **Test counts.** **18 `:core` / 62 `:jda-runtime` / 380 root with 5 pre-existing skips = 460.**

Next actions at that time (**superseded** by the post-merge next actions below):

1. Keep PR #117 draft and do not merge it ahead of parent PR #116; the coordinator updates the
   PR #117 body. Leave #115 as the combined Phase 1/2A checkpoint.
2. Start Phase 2C (voice/session/player-message narrow ports) on a new child branch based on the
   Phase 2B branch head, then Phase 2D on a child of 2C.
3. Keep the root Kord removal, root bootstrap wiring, the architectural import rule and issue #114
   open until their own gates are satisfied.

### Handoff snapshot — 2026-11-13 post-merge (current)

Branch, commit and PR facts below were read from Git and GitHub, and the ancestry and tree-equality
statements were verified against the repository; they are **not** gate evidence beyond what is
recorded above. #117's merge is recorded by commit SHAs; no live PR base SHA is asserted.

| Branch / PR | Handoff state |
| --- | --- |
| `feature/114-migrate-to-jda-jda-ktx` (epic baseline) | At **`255893abcef31554e13ae1713086a9b7c1217c60`**, the **regular merge commit of PR #117**. Parents: `c00bdf1` (the **squash merge of PR #116** on top of `5aad7c7`, itself the **squash merge of PR #115** on base `4badb92`; `5aad7c7`'s tree equals the former Phase 2A head `67802d1`) and `7d8661d` (the Phase 2B head). The merge tree is **identical** to `7d8661d` |
| PR #115 (former `feature/114-kord-to-jda-migration` → epic) | **CLOSED / MERGED by squash** as `5aad7c7`; the remote branch `feature/114-kord-to-jda-migration` was **deleted**. Phase 2A stays **IN PROGRESS / partial** — the merge completes neither 2A nor Phase 2 |
| `feature/114-native-failfast-check` → PR #116 (base: epic) | Head **`1b753b9`**, an **ancestry-only merge** committing `5aad7c7` into `49b4bc1`. Four add/add conflicts — `docs/migrations/kord-to-jda.md`, `spikes/jda-dave/.gitignore`, `spikes/jda-dave/README.md`, `spikes/jda-dave/build.gradle.kts` — were resolved by retaining the Phase 1B versions, so the resolved tree is **byte-identical** to `49b4bc1` and **no content changed**. All four checks on `1b753b9` passed on 2026-11-13 — Check Code Quality 17:20:31Z, SonarCloud Code Analysis 17:22:32Z, Build and analyze 17:22:35Z, SonarCloud 17:22:40Z. **Phase 1B PASS — gate closed 2026-11-08.** PR #116 is **CLOSED / MERGED by squash** into the epic branch as `c00bdf1` |
| `feature/114-phase-2b-command-port` → PR #117 (base: epic `feature/114-migrate-to-jda-jda-ktx`) | **Phase 2B PASS — gate closed 2026-11-13**, unaffected by the #116 merge. Carried the isolated `:core`/`:jda-runtime` modules, the neutral command boundary, the JDA 6.7.0 adapter and the guild-only `/jda-walkthrough`, with unit tests, plus JaCoCo 0.8.12 coverage for both modules wired into root `sonar`. CI history is recorded in the Phase 2B section; those are dated checkpoints, not a statement about any later head. After #116 merged, the base was **auto-retargeted to the epic branch**; the PR was later **CLOSED / MERGED** into the epic branch by the regular merge commit `255893a`. Its one conflict — this plan — had been resolved earlier by a **normal ancestry merge** of `c00bdf1` that kept the Phase 2B document; every non-doc path's tree equalled `e61f301` and **no code changed** |
| `feature/114-phase-2c-voice-ports` (no PR yet) | **IN PROGRESS — implementation + local tests.** Created from the fetched epic head `255893a` — the correct 2C base, not the stale local `4badb92` (`master`) and not the old Phase 2B branch. Its first pushed commit was **plan-only** (this document); the neutral voice/audio/player-message ports, their JDA adapters, DAVE 0.2.0 wiring, tone resource and unit tests are present as **uncommitted local working-tree changes**. Latest local run: 685 cases (680 executed + 5 pre-existing skips, 0 failures/errors) all pass; local module JaCoCo line coverage `:core` 207/219 = 94.52% (branch 108/115 = 93.91%) and `:jda-runtime` 869/947 = 91.76% (branch 342/418 = 81.82%) — local module reports only, not Sonar/new-code CI evidence — and the clean full `./gradlew build` passed locally. **Not committed and not CI-verified**; no 2C PR (no #118), no CI run and no staging result exist, so the accepted playback gate stays open; `master` is untouched |

- **Phase 2B staging evidence** remains the sanitized 2026-11-13 record in the Phase 2B section:
  isolated `:jda-runtime` login, ready state, guild-scoped `/jda-walkthrough` registration, the
  `JDA walkthrough OK` reply with the non-Community guild default `locale=en-US`, no intents enabled,
  no global command, and a deliberate Ctrl+C shutdown. No token, raw log or identifier is recorded.
- **Gate posture.** Phase 2B is **PASS / closed**. Broader **Phase 2 remains IN PROGRESS**: **2C is
  IN PROGRESS — implementation with local tests** — acceptance criteria are written, the branch exists,
  and the implementation plus unit tests pass locally (685 cases, 680 executed + 5 pre-existing skips,
  0 failures/errors; local module JaCoCo line coverage `:core` 207/219 = 94.52% with branch
  108/115 = 93.91%, `:jda-runtime` 869/947 = 91.76% with branch 342/418 = 81.82% — local module
  reports only, not Sonar/new-code CI evidence),
  but there is **no 2C PR (no #118)**, **no 2C CI run** and
  **no staging result**, so the 2C gate — including the accepted user-run playback gate — remains
  open; 2D follows 2C. Root Kord removal and bootstrap wiring are
  not done, the Phase 2 exit criteria (including the architectural import rule) remain open, and issue
  #114 stays **open**.
- **No root wiring.** The root application remains Kord-based and JDA-free; only the isolated
  `:jda-runtime` module touches JDA. 2C keeps the root Kord runtime, root dependency graph and
  `fatJar` untouched.
- **Test counts.** Phase 2B's recorded baseline is **18 `:core` / 62 `:jda-runtime` / 380 root with 5
  pre-existing skips = 460** (kept in the Phase 2B coverage section). The latest **local 2C
  working-tree** results are higher: `:core` **81** (0 skips), `:jda-runtime` **224** (0 skips) and
  root **380** (5 pre-existing skips) = **685 cases, 680 executed + 5 skips, all passing with 0
  failures/errors**. These are local working-tree results only — the 2C source, tests and tone
  resource are uncommitted and not CI-verified at this checkpoint.

Next actions, in order:

1. Commit the **Phase 2C** implementation, tests and tone resource on `feature/114-phase-2c-voice-ports`
   against the recorded acceptance criteria, then open the 2C PR against the **epic branch**; do not
   touch PR #117 and do not modify `master`.
2. Get **2C CI green** (Sonar new-code coverage >= 50%, target >= 75%, no new issues or hotspots).
3. Run the **user-run staging gate** and record sanitized evidence only — bot login/ready, guild-scoped
   walkthrough, DAVE init, all 75 20 ms frames supplied to JDA's `AudioSendHandler`/send loop (a
   hand-off count, not confirmed network delivery), a second client hears the tone (the actual
   delivery proof), clean disconnect, no receive path. Pause/resume preservation is checked in
   **Phase 3**, not here.
4. Keep the root Kord removal, root bootstrap wiring, the architectural import rule, Phase 6
   packaging/artifact validation and issue #114 open until their own gates are satisfied.

---

## 1. Context and settled decisions

### Baseline

| Item | State |
| --- | --- |
| Baseline commit | Clean `master` @ `4badb92` |
| Epic integration branch | `feature/114-migrate-to-jda-jda-ktx`, based on `master` @ `4badb92`; now at `255893a`, the regular merge commit of PR #117 whose parents are `c00bdf1` (squash merge of PR #116 on top of `5aad7c7`, the squash merge of PR #115) and `7d8661d` (Phase 2B head). It is the merge baseline for the remaining subphases |
| Working branch (current) | `feature/114-phase-2c-voice-ports`, created from the fetched epic head `255893a`; in progress — implementation with uncommitted local working-tree tests at this checkpoint, no PR open (no #118). The former `feature/114-kord-to-jda-migration` branch is deleted; PRs #115, #116 and #117 are merged |
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
  behaviour, and never block the audio thread. Pause/resume preservation is verified in **Phase 3**
  real-player integration — it was explicitly deferred from 2C and remains a migration requirement.

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
  `4badb92`, and migration work landed on the child working branch
  `feature/114-kord-to-jda-migration` through draft PR #115, which targeted the epic branch and not
  `master`. #115 has since been **squash-merged** as `5aad7c7` and that working branch deleted.
- The working branch carried migration work as checkpoint commits and proposed it to the epic
  branch through draft PR #115. `8ca6242` recorded this plan, `.opencode/opencode.json` and
  `spikes/jda-dave/**` in branch history; the Phase 2A source/test changes and the matching plan
  edits were present on `feature/114-kord-to-jda-migration` and are now in the epic branch through
  `5aad7c7`. Current Git and PR state is authoritative for what is committed and pushed.
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
| **1A** | DAVE-enabled staging voice transport probe (protocol v1 + audible tone) | Recorded on the former `feature/114-kord-to-jda-migration` branch through PR #115, now **squash-merged** as `5aad7c7` | **PASS** — transport subgate only |
| **1B** | Missing-DAVE-native fail-fast simulation: negative packaging artifact plus the user-run container check | Child branch `feature/114-native-failfast-check`, originally based on the former 2A branch; after #115 was squash-merged its PR #116 **targets the epic branch**. Its head `1b753b9` is an ancestry-only merge with **no content change** | **PASS** — recorded 2026-11-08; gate closed |

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
governs progression. Phase 2B was subsequently unblocked on
`feature/114-phase-2b-command-port` and is now **PASS — gate closed 2026-11-13**; broader Phase 2
remains **IN PROGRESS**.

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

**Status: IN PROGRESS** — the Phase 2A locale repository/use-case boundary is implemented and
**squash-merged into the epic branch** (`5aad7c7`), but `LocalizationService` and the `/locale`
command remain Kord-facing. Phase 2B is **PASS — gate closed
2026-11-13**: its neutral command boundary and isolated JDA adapter are implemented with unit tests,
green CI on its verified checkpoints and a recorded staging `/jda-walkthrough` interaction. The
earlier handoff head `e4a9e29` later completed all four checks successfully, `7bf5419` is the latest
verified all-green checkpoint before the subsequent docs-only updates (it was the head at that point,
not afterwards), and CI state for any later head is read live. A later dated snapshot of `f5f04d1` was
**not green**. The bootstrap
and command layer are still **not** wired into the root runtime, so this phase is **not** complete.
Remaining work is subphases **2C** (voice/session/player-message ports) and **2D**
(channel/message/locale edge adapters), each with its own branch and PR (see the subphase plan
above). **2C is IN PROGRESS — implementation with local tests**: its acceptance criteria and
branch/base posture are recorded in the Phase 2C section, and its branch
`feature/114-phase-2c-voice-ports` was created from the fetched epic head `255893a`; implementation and
unit tests exist as uncommitted local working-tree changes and pass locally (685 cases, 680 executed +
5 pre-existing skips; local module JaCoCo line coverage `:core` 207/219 = 94.52% with branch
108/115 = 93.91% and `:jda-runtime` 869/947 = 91.76% with branch 342/418 = 81.82% — local module
reports only, not Sonar/new-code CI evidence), with no 2C PR (no #118), no CI run and no staging
result yet.

**Gate status:** the Phase 1 missing-native fault test passed on 2026-11-08, so it no longer blocks
this phase. Phase 2B is **PASS / closed on 2026-11-13** on `feature/114-phase-2b-command-port` under
PR #117 (base: the epic branch, after #116 merged), and #117 has since been **merged** into the epic
branch at `255893a`; the user-run staging `/jda-walkthrough` interaction is
recorded above. For CI history: the earlier head `001e722` passed Check Code Quality, Build and
analyze and the separately named SonarCloud check, while the SonarCloud Code Analysis check failed
its Quality Gate at 0.0% new-code coverage because the modules had no JaCoCo XML; after the JaCoCo
0.8.12 plus root `sonar` wiring, all four #117 checks passed on covered checkpoint `e607ff7` (75.0%
new-code coverage, 0 new issues, 0 hotspots), on the later docs-only checkpoints `2850907`,
`16247f8`, `d353113` and `988a7cd` (last check completed 2026-11-13 16:38:44Z), on `e4a9e29` (which
completed all four successfully at 16:44:11Z after an in-progress observation at 16:41:06Z, the
observation time), and on `7bf5419`, the latest verified all-green checkpoint before the subsequent
docs-only updates (all four checks, last at 16:48:58Z; the head at that point only). A later dated
snapshot of `f5f04d1` was **not green** (only Check Code Quality concluded, at 16:53:24Z). All are
historical verified checkpoints, not a guarantee for later commits — read the live GitHub checks for
whatever head is current. Local QA and reviewer agents
satisfied the independent review criterion; no GitHub PR review is submitted, which is not itself a
required gate. **Phase 2
remains IN PROGRESS**: the root runtime remains Kord-based and JDA-free. PR #117 is **merged**
(regular merge commit `255893a`); Phase 2C is in progress on `feature/114-phase-2c-voice-ports` with
uncommitted implementation and passing local working-tree tests (not CI-verified), and no 2C PR has
been opened (no #118).

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
- Bot connects on staging with the staging token. **PASS for the isolated `:jda-runtime` only
  (2026-11-13); OPEN for the root application**, which is still Kord-based.
- Slash commands register and respond; deferred vs. immediate reply is explicit and tested.
  **PASS for the isolated guild-scoped `/jda-walkthrough` only (2026-11-13: registered and replied
  `JDA walkthrough OK`). OPEN for the root runtime and the remaining command families.**
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
  Pause/resume preservation was **explicitly deferred from Phase 2C** to this phase's real-player
  integration and is a required migration outcome, not optional.
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

- The epic integration branch `feature/114-migrate-to-jda-jda-ktx` is at
  `255893abcef31554e13ae1713086a9b7c1217c60`, the **regular merge commit of PR #117** (parents
  `c00bdf1` and `7d8661d`, tree identical to `7d8661d`). PRs
  #115, #116 and #117 are **closed/merged** and the former `feature/114-kord-to-jda-migration` child
  branch is deleted; the current child branch is `feature/114-phase-2c-voice-ports`, created from the
  fetched epic head `255893a`. It is **in progress — implementation with uncommitted local
  working-tree tests** at this checkpoint; no 2C PR has been opened (no #118).
- `.opencode/opencode.json`, this migration plan (`docs/migrations/kord-to-jda.md`) and
  `spikes/jda-dave/**` are **tracked** in the epic branch history: `8ca6242` recorded them on the
  former working branch, `5aad7c7` brought them into the epic branch and `c00bdf1` carried them
  forward with the #116 squash merge. They are part of the migration record and are expected to be
  updated as the migration proceeds.
- The Phase 1B negative-test packaging (`fatJarWithoutDaveNative`, `verifyNegativeTestJar`), its
  runbook section, the Phase 1B plan edits and the 2026-11-08 pass record were committed on the child
  branch `feature/114-native-failfast-check`, whose PR #116 was **squash-merged** into the epic branch
  as `c00bdf1`; its head `1b753b9` was an **ancestry-only merge** with **no content change**. Current
  Git state is authoritative for what is committed and pushed.
- The Phase 2A source and test changes, and the plan edits made after `8ca6242`, were **squash-merged
  into the epic branch** by PR #115 as `5aad7c7`; the former branch is deleted. Current Git and PR
  state is authoritative for what is committed and pushed.
- The Phase 2B modules `core/` and `jda-runtime/`, the root `settings.gradle.kts` /
  `build.gradle.kts` changes and the version-catalog entries are present on
  `feature/114-phase-2b-command-port`, originally stacked on `feature/114-native-failfast-check` and
  proposed through PR #117 (now base: the epic branch, after #116 merged); **Phase 2B is PASS — gate closed on
  2026-11-13**. The initial implementation checkpoint is `fbd9f5a`; the covered implementation/CI
  checkpoint is `e607ff7`, on which all four #117 checks passed; the later docs-only checkpoints
  `2850907`, `16247f8`, `d353113` and `988a7cd` also passed all four, as did `cd9d6db`, and
  `e4a9e29` completed all four successfully after an in-progress observation. `7bf5419` is the latest
  verified all-green CI checkpoint before the subsequent docs-only updates, not a later head. All are
  historical verified checkpoints, so read the GitHub check runs for any later head. JaCoCo 0.8.12 coverage is applied in
  `core/build.gradle.kts` and `jda-runtime/build.gradle.kts`, and root `sonar` depends on both module
  reports. The user-run staging `/jda-walkthrough` interaction on 2026-11-13
  is recorded in this plan as sanitized evidence only. PR #117 was **merged** into the epic branch by
  the regular merge commit `255893a` (parents `c00bdf1` and `7d8661d`, merge tree identical to
  `7d8661d`); its one earlier conflict had been resolved by an ancestry merge that changed no code.
  Current Git and PR state is authoritative for what is committed and pushed.
- Phase 2C is **in progress — implementation with local working-tree tests** at this checkpoint: the
  plan was committed first on `feature/114-phase-2c-voice-ports`, created from the fetched epic head
  `255893a`, and the neutral ports, JDA adapters, DAVE 0.2.0 wiring, tone resource and unit tests exist
  as **uncommitted local working-tree changes**. Latest local run: `:core` 81, `:jda-runtime` 224 and
  root 380 tests (5 pre-existing skips) — 685 cases, 680 executed + 5 skips, 0 failures/errors, all
  passing; local module JaCoCo line coverage `:core` 207/219 = 94.52% (branch 108/115 = 93.91%) and
  `:jda-runtime` 869/947 = 91.76% (branch 342/418 = 81.82%) — local module reports only, not
  Sonar/new-code CI evidence — and the clean full `./gradlew build` passed locally. There is
  **no 2C PR (no #118), no 2C CI run and no staging result**, and `master` is untouched.
- `stash@{0}` — housekeeping WIP; preserved, untouched.
- `stash@{1}` — assistant voice feature WIP (out of scope); preserved, untouched.
- Ignored caches and user data (for example `.gradle/`, `build/`, `data/`, `.idea/`, `.memory/`,
  `.opencode/node_modules/`) are **gitignored**, untouched, and stay that way.
