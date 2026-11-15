# Kord → JDA 6.7 Migration Plan

Tracking issue: [#114](https://github.com/Wikijito7/ECIBotKt/issues/114) — *Migrate to JDA / jda-ktx*
Epic integration branch: `feature/114-migrate-to-jda-jda-ktx`, based on `master` @ `4badb92`. Its
history includes `5fa9c63bc85ad3d5df1ed9c43c05f3e9b7d72e85` on top of `255893a` (the regular merge
commit of PR #117), which in turn carried the **squash merge of PR #116** at `c00bdf1` over `5aad7c7`
(the **squash merge of PR #115**). `5fa9c63` is the PR #118 merge commit. The Phase 2C staging
evidence was then recorded by the documentation-only commit `269acc1`; that commit was the epic tip
when the earlier documentation update was written, and that statement is **historical**. The Phase 2D
child branch was based on the later epic tip `1304aa6`; its tested implementation snapshot
`4ff8167` and the `a4bc1ae` time-qualified green snapshot were pushed, followed by the docs-only live
head `8c20a1f` which was fully green at the recorded 2026-11-14 snapshot, and the final merged head
`0b365aa`, whose checks were also all green. **PR #119 is MERGED into
`feature/114-migrate-to-jda-jda-ktx`** by the regular merge commit
`f56d2252506617ae282c50a3cc95fb830c644424`, whose two parents are the base `1304aa6` and the final head
`0b365aa`; the merge was recorded on 2026-11-14 and the epic branch advanced to `f56d225` at that
recorded snapshot. **`f56d225` was the epic tip before the earlier documentation update**, not a claim
about the tip after it; the later documentation-only `38bdba1` (`Recorded Phase 2D merge and next
steps`) was followed by its child `955ac49` (`Clarified migration phase gates and adapter
boundaries`), which was the epic tip before PR #120 and the base of the import-scanner child branch.
**PR #120 is MERGED into `feature/114-migrate-to-jda-jda-ktx`** by the regular merge commit
`96e5fbbd7c3660b53414f4cc67033060749fcbe1` on **2026-11-15 08:03:45Z**; its two parents are the base
`955ac49` and the scanner head `66f7783`. **The live epic tip before this documentation-only update was
`96e5fbb`** — that is time-qualified, and this doc-only edit makes no claim about the resulting head.
`master` remains `4badb92`. The live tip is read from fetched Git/GitHub. The epic remains the merge baseline
for this migration. Phase 2A working branch
`feature/114-kord-to-jda-migration` **no longer exists on the remote**: PR
[#115](https://github.com/Wikijito7/ECIBotKt/pull/115) was **squash-merged** into the epic branch as
`5aad7c7` and the remote branch was deleted; a stale local ref may remain. Phase 2A is **PASS — gate
closed (owner scope decision 2026-11-14)** for its neutral-value/locale-persistence scope. That closure
did **not** by itself complete Phase 2, whose only remaining gate was the scoped architectural
import-rule CI gate; **Phase 2 is now PASS — gate closed (2026-11-15)**: 2A–2D are **PASS**, the
isolated-runtime criteria already have recorded evidence, and PR #120's root JUnit import-boundary
scanner plus its four passing CI checks closed the scoped gate. The scanner implementation was earlier
seen as **uncommitted test-source changes** in the **local working-tree snapshot taken 2026-11-14 on base
`955ac49`**; that snapshot is **historical** — at it **no PR and no CI run existed**, which is not a
standing statement about the branch, and it is recorded below only as dated evidence. The root `LocalizationService` and root `/locale` are intentionally
Kord-facing (already refactored in 2D while remaining Kord-based); their root code/build switch
belongs to **Phase 6** (production deployment is Phase 7), not to Phase 2A.
Phase 1B subphase branch: `feature/114-native-failfast-check`; its PR
[#116](https://github.com/Wikijito7/ECIBotKt/pull/116) had targeted the **epic branch**, because the
former 2A branch was merged and its remote ref deleted (a stale local ref may remain). Its negative fail-fast test **passed** on 2026-11-08 (see the
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
Phase 2B section below). Phase 2C is now **PASS — gate closed (2026-11-14)**: its acceptance
criteria, scope limits and the sanitized staging evidence are recorded in the Phase 2C section. Its
branch `feature/114-phase-2c-voice-ports` was created from the fetched epic head `255893a`, and PR
[#118](https://github.com/Wikijito7/ECIBotKt/pull/118) is **merged** into the epic branch by the
regular merge commit `5fa9c63bc85ad3d5df1ed9c43c05f3e9b7d72e85` (parents `255893a` and the PR head
`3e50248`), so **no open Phase 2C PR remains** and the epic branch now carries the 2C pass.
The implementation head is `8eb0349c4f65997d0dd69ffdc2b245268c207074` (`Fixed ktlint violations in
Phase 2C`); the docs-only head `9fd1826731916111f47517b574a33b98d81c8880` (`Recorded Phase 2C CI
checkpoint`) is a **dated historical green checkpoint**, and the final PR head was
`3e50248446a5ce65ea2a1f648576523fb5b25c6a` (`Recorded passing Phase 2C CI checkpoint`), where all
four checks also succeeded. All four checks on the implementation head **succeeded**: Check Code
Quality, Build and analyze, SonarCloud Code Analysis and SonarCloud. The Sonar Quality Gate
**passed** with **90.1% Coverage on New Code**, **11 new issues** (10 major, 1 critical), **0
security hotspots** and **0.0% duplication** — the 11 new issues are recorded as-is, not treated as
zero, and are carried forward as **tracked follow-up debt** under the owner-approved 2026-11-14
interpretation (the repository's configured Sonar Quality Gate is the criterion). The formatting-only
ktlint repairs that fixed the
earlier `c893567` failure are part of the merged implementation. The gate was closed by the
**user-run staging evidence recorded on 2026-11-14** (sanitized in the Phase 2C section), not by CI
alone. No human GitHub review is recorded here; read the PR for its live review state. **Phase 2 is
PASS — gate closed (2026-11-15)**: its isolated-runtime criteria are satisfied by the 2A–2D evidence and
its only remaining scoped import-rule CI gate was closed by **PR #120**, merged on 2026-11-15 by the
regular merge commit `96e5fbb` (head `66f7783`, base `955ac49`), whose four checks all succeeded —
Check Code Quality (2026-11-14 21:52:37Z), SonarCloud Code Analysis (21:56:59Z), Build and analyze
(21:57:10Z) and SonarCloud (21:57:06Z). The earlier scanner observation belongs to the **historical
local test-source working-tree snapshot taken 2026-11-14 on base `955ac49`, before the branch was
committed or pushed**: at that snapshot the scanner existed with no PR or CI result. That is dated
evidence only and is not a current state. The root Kord runtime is **not** migrated by Phase 2: the root code/build switch and
the Kord dependency/JAR removal belong to **Phase 6**, whose packaging/artifact validation is still
**OPEN**. **Phase 2D is PASS — gate closed (2026-11-14)**: the live remote epic tip was fetched and
verified as `1304aa6` (`Clarified Phase 2C staging provenance`) before branching, and the child branch
`feature/114-phase-2d-locale-edge-adapters` was created from that tip, not from `master` (`4badb92`)
or a stale SHA. `master` remains untouched. 2C and 2D are **PASS**; Phase 6 — including the root
code/build switch and Kord removal — remains **OPEN** (Phase 7 deploys the verified artifact); issue #114 remains open. Final local 2D QA on **2026-11-14** passed
the clean full build and recorded **758 test cases: 753 executed, 5 skipped (pre-existing), 0
failures/errors**, with local module JaCoCo readings and a local ktlint check scoped to changed files:
**0 findings**. The `a4bc1ae` time-qualified green snapshot has all four checks green, the Sonar
Quality Gate OK, and the sanitized user-run staging/restart evidence is recorded in the Phase 2D
section. At the recorded 2026-11-14 snapshot, live PR #119 head was the later docs-only `8c20a1f`,
fully green (all four checks); the final merged head `0b365aa` also had all four checks green
(2026-11-14; see the merged-head CI snapshot in the Phase 2D section), and live GitHub checks remain
authoritative for any later commit. PR #119 is **merged** into the epic branch by the regular merge
commit `f56d225` and is no longer open. Pause/resume preservation stays
deferred to Phase 3 and remains a **required** migration outcome, not optional. `7bf5419` (`Corrected Phase 2B CI status record`) is the latest
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
Issue #114 stays open because the wider migration is unfinished — Phases 3–7 — **not** because of
Phase 2: **Phase 2 is PASS — gate closed (2026-11-15)**, since 2A–2D are **PASS**, their
isolated-runtime evidence exists, and PR #120 closed the scoped architectural import-rule CI gate on
2026-11-15. The earlier note that the scanner had "no PR/CI verdict" belongs to the **historical local
test-source working-tree snapshot taken 2026-11-14 on base `955ac49`, before the branch was committed or
pushed**; it is dated evidence only. The root code/build switch belongs to Phase 6 (production deployment is Phase 7), whose
packaging/artifact validation is still open. **Phase 2C is PASS — gate closed (2026-11-14)** on the recorded user-run staging evidence,
and PR #118 is **merged** into the epic branch by the regular merge commit `5fa9c63`, so **no open
Phase 2C PR remains**.

> **This is a plan, not implementation.** Nothing below is proven until the gate it belongs to
> produces recorded evidence. "Fact" = verified against the repository or upstream sources today.
> "Gate" / "Recommendation" = untested and must be validated before being relied on.

---

## Progress record (verified, 2026-11-15)

Status labels: **PASS** = recorded evidence, gate closed · **IN PROGRESS** = work started, gate
still open · **OPEN** = not started, gate still open.

**Phase ownership (current model).** Root production stays on **Kord through Phases 2–5**. Phase **6**
switches the root code/build to the JDA lifecycle and removes Kord; Phase **7** deploys the exact
Phase 6-verified artifact. Phases 2–5 therefore deliver and prove an **isolated** JDA implementation in
`:core` + `:jda-runtime` while the root Kord bot keeps serving production. Code already delivered in
2B/2C/2D is isolated-runtime work. Phase 3 is **IN PROGRESS — implementation landed**: its
implementation, the `:player` tests and the scanner's `:player` classification are present on
`feature/114-phase-3-player-engine` (base `6d14167`) and pass locally, but its acceptance gate remains
**open/not passed** pending PR CI/Sonar and Phase 3 staging; Phase 4 has not started.

| Item | Status | Recorded |
| --- | --- | --- |
| Phase 1 — dependency and DAVE transport feasibility | **PASS** (staging spike) | Subphase **1A** passed on staging (protocol v1 + audible tone) and subphase **1B** passed on 2026-11-08 (exit `3` `EXIT_NATIVE` with no gateway login); every Phase 1 exit criterion now has recorded evidence. Root dependency alignment and root fat-JAR checks remain **Phase 6**, not Phase 1 |
| Phase 1B — missing-DAVE-native fail-fast simulation | **PASS — gate closed (2026-11-08)** | User-run negative container on Linux amd64, Temurin 21.0.13 (`eclipse-temurin:21`), negative no-DAVE-native JAR: exit exactly `3` (`EXIT_NATIVE`), DAVE native unavailable with `UnsatisfiedLinkError: Required library was not found`, **zero** `[gateway] logging in` and **zero** `[gateway] ready` lines, fake placeholder token never printed. The host pre-pulled the image, so the image pull was not itself isolated; the container ran with `--network none`. Sanitized record only |
| R1 — `jda-ktx 0.14.2` + JDA 6.7.0 pairing | **IN PROGRESS — transport subgate PASS only** | Candidate resolved and exercised for voice transport; still not an upstream-declared combination and not yet re-verified on the final Phase 6 artifact. **Conditional:** only applies if jda-ktx is the selected path; direct JDA + coroutine bridge requires no jda-ktx gate |
| R2 — `adapter-jda` 0.2.0 vs JDA 6.7.0 ABI | **IN PROGRESS — transport subgate PASS only** | DAVE negotiated and played through JDA; published metadata still does not declare or test 6.7.0, and the ABI is not yet re-verified on the final Phase 6 artifact |
| Phase 2 — isolated JDA runtime: bootstrap, events, command response | **PASS — gate closed (2026-11-15)** | Phase 2A neutral repository/use-case groundwork **landed on the epic branch** through the squash merge of PR #115 (`5aad7c7`, tree identical to the former 2A head `67802d1`); **2A is PASS — gate closed (owner scope decision 2026-11-14)** for its neutral-value/locale-persistence scope. Phase 2 as a whole is **PASS — gate closed (2026-11-15)**: 2A, 2B, 2C and 2D are all **PASS**, criteria 1–3 are satisfied by their recorded isolated-runtime evidence, and criterion 4 — the only remaining gate, the scoped architectural import rule — was closed by **PR #120**, merged into the epic branch on **2026-11-15 08:03:45Z** by the regular merge commit `96e5fbb` (head `66f7783`, base `955ac49`, parents `955ac49` + `66f7783`). Its four checks all succeeded — Check Code Quality (2026-11-14 21:52:37Z), SonarCloud Code Analysis (21:56:59Z), Build and analyze (21:57:10Z) and SonarCloud (21:57:06Z). PR #120 is **test-source-only** (root JUnit import-boundary scanner under `src/test/kotlin/architecture/`; no production source, dependency, build-script or CI-workflow change, and no new Gradle task): **191** architecture tests (policy 148, parser 21, source-tree 22) all pass, and its historical local snapshot recorded **949 cases (944 executed + 5 pre-existing skips, 0 failures/errors)** with local ktlint 1.8.0 **0 findings** on the changed Kotlin test files. **Root cutover is not a Phase 2 criterion**: the root Kord `LocalizationService`, root `/locale` and the root code/build switch remain **Phase 6** scope, and the root Kord exception in the import matrix is unchanged until Phase 6 extends the same scanner repo-wide on the Kord-free tree. Phase 2B is **PASS — gate closed 2026-11-13**: its neutral command boundary and isolated JDA runtime exist with unit tests on child branch `feature/114-phase-2b-command-port`, JaCoCo coverage is wired for both isolated modules, CI passed on the verified all-green checkpoints `988a7cd` and `7bf5419` (the latest verified all-green checkpoint before the subsequent docs-only updates; all of these are historical checkpoints, so read the live checks for later heads), and the user-run staging `/jda-walkthrough` interaction succeeded (sanitized evidence in the Phase 2B section). No JDA runtime is wired into the root runtime (the 2D root locale refactor stays Kord-based and is not JDA wiring). **2D is PASS — gate closed (2026-11-14)** on the `a4bc1ae` time-qualified green snapshot (all four checks green and Sonar Quality Gate OK) plus sanitized user-run staging evidence in the Phase 2D section; PR #119 is **merged** into the epic branch by the regular merge commit `f56d225` (merge recorded 2026-11-14; head `0b365aa`, base `1304aa6`). Root Kord production is **not cut over** through Phase 2 (and through Phase 5); the root gateway/runtime cutover is **Phase 6**. **2C is PASS — gate closed (2026-11-14)**: its acceptance criteria and sanitized staging evidence are recorded in the Phase 2C section; its branch `feature/114-phase-2c-voice-ports` was created from the fetched epic head `255893a`, and PR #118 is **merged** into the epic branch by the regular merge commit `5fa9c63` (parents `255893a` and PR head `3e50248`), whose four checks succeeded; the Sonar gate reported 90.1% coverage on new code with 11 new issues (10 major, 1 critical), 0 hotspots and 0.0% duplication — recorded as-is, not zero. The implementation head `8eb0349`, the docs-only `9fd1826` and the merged head `3e50248` are dated historical checkpoints; read the live checks for any later epic head. PR #116 was **squash-merged** into the epic branch (`c00bdf1`); PR #117 was then **merged** into the epic branch by a regular merge commit at `255893a` (parents `c00bdf1` and `7d8661d`, merge tree identical to the Phase 2B head) |
| Phase 2A — domain-owned ID/locale values + locale persistence refactor | **PASS — gate closed (owner scope decision 2026-11-14)** | Phase 2A source and test changes were **squash-merged** into the epic branch by PR #115 as `5aad7c7`; that commit's tree equals the former 2A head `67802d1`, and the remote `feature/114-kord-to-jda-migration` branch was deleted (a stale local ref may remain). The delivered scope is closed as **PASS**: `GuildId`/`LocaleCode` as domain-owned values, the neutral locale repository and use cases, and preserved `guild_locales` JSON compatibility. Neutral locale contracts/catalog/shared persistence and the isolated JDA `/locale` were completed in **Phase 2D**. The **root** `LocalizationService` and **root** `/locale` stay Kord-facing (2D refactored them — DI, resources, module deps — without cutting the gateway over) and the **gateway/runtime cutover** is **Phase 6** scope — not a Phase 2A blocker |
| Phase 2B — neutral command context + responder/response port | **PASS — gate closed (2026-11-13)** | Implementation and unit tests are present on `feature/114-phase-2b-command-port` as the initial implementation checkpoint `fbd9f5a`, originally stacked on Phase 1B `feature/114-native-failfast-check`. PR #116 has since been **squash-merged** into the epic branch (`c00bdf1`), so PR #117 was retargeted to the epic branch and was later **merged** into it by the regular merge commit `255893a`; its only conflict — this plan — was resolved by an ancestry merge that changed no code. The branch adds isolated Gradle modules `:core` (gateway-free neutral ports and a pure walkthrough handler) and `:jda-runtime` (JDA 6.7.0 adapter, guild-only `/jda-walkthrough`); **at the Phase 2B snapshot** the root project depended on `:core` only and remained Kord-based and JDA-free. **Phase 2D later added the shared `:locale-persistence` module to both the root project and `:jda-runtime`**, so that 2B dependency statement is historical and does not describe the current graph. On head `001e722` **Check Code Quality**, **Build and analyze** and the separately named **SonarCloud** check passed, while **SonarCloud Code Analysis** failed its Quality Gate at 0.0% coverage on new code (required >=50%) because the modules emitted no JaCoCo XML; the JaCoCo 0.8.12 fix then passed CI on the covered checkpoint `e607ff7` — all four #117 checks green, including both SonarCloud checks, with 75.0% Coverage on New Code, 0 new issues and 0 hotspots. The later docs-only checkpoints `2850907`, `16247f8` and `d353113` also passed all four #117 checks, as did `988a7cd` (last check completed 2026-11-13 16:38:44Z), and `e4a9e29` was observed in progress at 16:41:06Z (the observation time, when only Check Code Quality had concluded successfully) and later completed all four checks successfully (last at 16:44:11Z). `7bf5419` (`Corrected Phase 2B CI status record`) is the latest verified all-green checkpoint before the subsequent docs-only updates: all four checks passed on 2026-11-13 — Check Code Quality 16:47:15Z, SonarCloud Code Analysis 16:48:52Z, Build and analyze 16:48:56Z, SonarCloud 16:48:58Z — with 75.0% Coverage on New Code, 0 new issues and 0 hotspots; it was the head at that point, not afterwards. The later docs-only `f5f04d1` (`Recorded passing Phase 2B CI runs`) was the head in a dated snapshot observed on 2026-11-13 in which only Check Code Quality had concluded successfully (16:53:24Z), Build and analyze was still in progress and the SonarCloud checks were not yet listed — so it was **not green at that snapshot**; GitHub holds the status of the head after this docs-only follow-up. All of these are historical verified checkpoints, not a guarantee for later commits, so CI status for any newer head must be read from the GitHub check runs. Local QA and reviewer agents satisfied the independent review criterion while no GitHub PR review has been submitted. The user ran the isolated runtime on 2026-11-13 against staging and the guild-scoped `/jda-walkthrough` replied `JDA walkthrough OK`, closing the gate. PR #117 was subsequently **merged** into the epic branch by a regular merge commit at `255893a`, so the Phase 2B pass is now on the epic integration branch. The root runtime is still Kord-based and JDA-free |
| Phase 2C — voice/session/player message ports and adapters | **PASS — gate closed (2026-11-14)** | Acceptance criteria, scope limits and sanitized staging evidence are recorded in the Phase 2C section. Branch `feature/114-phase-2c-voice-ports` was created from the fetched epic head `255893a` (the regular merge commit that brought PR #117 into the epic branch). The neutral voice/audio/player-message ports, their JDA adapters, the DAVE 0.2.0 wiring, `/jda-voice-walkthrough` and the unit tests were merged into the epic branch through PR #118 by the regular merge commit `5fa9c63` (PR head `3e50248`); the implementation head is `8eb0349` (`Fixed ktlint violations in Phase 2C`). All four checks on that head **succeeded** — Check Code Quality, Build and analyze, SonarCloud Code Analysis and SonarCloud — and the Sonar Quality Gate **passed** with **90.1% new-code coverage, 11 new issues (10 major, 1 critical), 0 security hotspots and 0.0% duplication** (no zero-issue claim). The earlier head `c893567` is kept as history: **Check Code Quality failed** there on 25 ktlint formatting findings while the other three checks passed with the same Sonar figures; the formatting-only repairs were committed and pushed as `8eb0349`. The docs-only `9fd1826` (`Recorded Phase 2C CI checkpoint`) is a **dated historical green checkpoint** too: all four checks succeeded on 2026-11-14 — Check Code Quality 08:28:21Z, SonarCloud Code Analysis 08:31:00Z, Build and analyze 08:31:04Z, SonarCloud 08:31:05Z — with the Sonar Quality Gate passed at 90.1% Coverage on New Code, 11 new issues, 0 security hotspots and 0.0% duplication. Those checkpoints are historical; the merged PR head `3e50248` also passed all four checks, and later epic heads are read live from GitHub. Latest **local** run on the formatted tree: `:core:test` 81 tests, `:jda-runtime:test` 224 tests and the root suite 380 tests (5 pre-existing skips) all pass with 0 failures/errors — **685 cases total (680 executed + 5 skips)**; local JaCoCo **module** line coverage is `:core` **207/219 = 94.52%** (branch **108/115 = 93.91%**) and `:jda-runtime` **870/948 = 91.77%** (branch **342/418 = 81.82%**), and the full `./gradlew build` passed locally. All coverage figures here are **local module JaCoCo report readings only — not Sonar, new-code or CI evidence**. Phase 2C is **PASS — gate closed (2026-11-14)** on the recorded user-run staging playback evidence (sanitized in the Phase 2C section); the 11 Sonar new issues remain recorded as **tracked follow-up debt** rather than being described as resolved or zero, because under the **owner-approved 2026-11-14 interpretation** the repository's configured Quality Gate is the criterion and it passed. `master` is untouched |
| Phase 2D — channel/message/locale edge adapters | **PASS — gate closed (2026-11-14)** | Branch `feature/114-phase-2d-locale-edge-adapters` was created from the **live remote epic tip verified at branch creation**: `1304aa6` (`Clarified Phase 2C staging provenance`), not from `master` (`4badb92`) and not from a stale/hardcoded SHA such as `5fa9c63` or `269acc1`. The worktree was clean and on the epic branch before branching; no 2D branch existed locally or remotely. 2C remains **PASS**; Phase 6 — root code/build switch plus artifact/digest gates — remains **OPEN**; issue #114 remains open. The implementation in this branch moves the locale port, use cases and catalog into `:core`, extracts the JSON persistence adapter into the shared `:locale-persistence` module, moves the unchanged `lang.yml`/`lang_es-ES.yml` resources onto the shared runtime classpath, and adds a guild-scoped JDA `/locale` command with neutral command/locale boundaries. **2D is PASS — gate closed (2026-11-14)**: the tested code/test snapshot `4ff8167` is pushed, the `a4bc1ae` time-qualified green snapshot has all four checks green and the Sonar Quality Gate OK, and the sanitized user-run staging/restart evidence is recorded in the Phase 2D section. Final local QA recorded **758 test cases: 753 executed, 5 skipped (pre-existing), 0 failures/errors**, with a clean local build, local module JaCoCo readings and a local ktlint check scoped to changed files showing **0 findings**. One new MAJOR `kotlin:S6524` Sonar code smell is disclosed as tracked follow-up debt under the configured gate criterion. The root runtime stays Kord-based; no Phase 6 wiring. PR #119 is **merged** into the epic branch by the regular merge commit `f56d225` on 2026-11-14 (head `0b365aa`, base `1304aa6`). Details, accepted scope and gate evidence are recorded in the Phase 2D section |
| Phase 3 — per-guild serialized voice (isolated JDA real-player slice) | **IN PROGRESS — implementation landed, gate remains open/not passed (2026-11-15)** | Phase 2 is **PASS — gate closed 2026-11-15** via PR #120. The owner approved the two former blockers on 2026-11-15: (a) pause stops the position and resume reuses the same track at that position; skip while paused stays paused; (b) the real player lives in a new gateway-free `:player` module (`es.wokis.player.engine`) shared by the root Kord adapter and `:jda-runtime`, with Lavaplayer kept internal. Production source landed on `feature/114-phase-3-player-engine` at base `6d14167`: per-guild mailbox/actor, enqueue-only Lavaplayer callbacks, neutral track/queue/event snapshots, continuous player-audio stream with `Frame`/`PausedSilence`/`Ended`, extended (not duplicated) `JdaAudioSendHandler`, preserved source providers and root Kord behaviour, and manual JDA runtime/voice-adapter wiring. **The gate stays open**: the Test Agent has added the `:player` scanner classification, engine/stream tests and JDA lifecycle coverage in the local working tree; the time-qualified 2026-11-15 local snapshot records **1,024 cases (1,020 executed + 4 pre-existing skips, 0 failures/errors)**, a clean build, changed-file ktlint **0 findings** and **no new external dependency or version** (the Phase 3 local verification snapshot is the authoritative detailed breakdown of that snapshot, including the exact per-module counts, build task count and ktlint scope; summaries elsewhere may repeat the headline totals). **At that snapshot the branch was not pushed, no PR was open and no CI/Sonar/staging evidence existed** — that is a dated record, not a standing no-PR claim. The `GuildLavaPlayerService.kt` Sonar exclusion was removed, so its **Sonar new-code gate is pending live PR CI** (local JaCoCo line coverage on that file is exactly **72/242 lines = 29.75%**, read from `build/reports/jacoco/test-results/jacocoTestReport.xml`; **local JaCoCo only, not Sonar or new-code coverage**) and must not be hidden or waived if CI fails. This row does **not** claim PASS. Details are in the Phase 3 implementation-scope subsection |
| Phase 4 — commands, components, messages, channels, locales | **OPEN** | Starts after Phase 3 exit. Ports the remaining command groups and component IDs into neutral `:core` contracts plus JDA adapters in the isolated `:jda-runtime`. Root Kord commands remain production until Phase 6 |
| Phase 5 — test/staging gate on the full isolated JDA implementation | **OPEN** | Starts after Phases 3–4 exit. Tests and stages the complete isolated JDA implementation while the Kord dependency and the untouched root Kord suite are still present. Not the root production cutover |
| Phase 6 — root Kord→JDA code/build switch, Kord removal, repo-wide import rule, artifact gates | **OPEN** | Not started. Owns the root code/build switch, removal of Kord dependencies/local Kord JARs, the **same custom import scanner** extended repo-wide on the Kord-free tree, and the JDK 21 / packaging / digest / staging gates; Phase 7 deploys the verified artifact. Conditional jda-ktx gate (not required if direct JDA is selected) |
| Phase 7 — production deployment of the Phase 6-verified artifact | **OPEN** | Blocked by the Phase 6 gates. Deploys the exact Phase 6-verified artifact with the same digest verification |
| Phase 8 — port parked PRs #106/#109/#111 (post-migration follow-up) | **OPEN** | Not started. Runs after Phase 7. Ports each parked PR individually from a new child branch off the verified live epic tip (reviewed cherry-pick or reimplementation, regular merge commit), without rebasing or rewriting the parked branches. **Not** a prerequisite for Phase 2, Phase 6 or issue #114 closure |

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
section); **Phase 2 as a whole is now PASS — gate closed (2026-11-15)** through PR #120.

### Phase 2A — domain-owned ID/locale values and locale persistence — PASS (owner scope decision 2026-11-14)

**Status: PASS — gate closed (owner scope decision 2026-11-14)** for the scope Phase 2A actually
delivered: domain-owned `GuildId`/`LocaleCode`, the neutral locale repository and use cases, and
preserved `guild_locales` JSON compatibility. The root `LocalizationService` and root `/locale` remain
Kord-facing while **already having been refactored in Phase 2D** (DI, resources, module dependencies);
that is a permitted non-cutover refactor, and the **root code/build switch** belongs to **Phase 6** (deployment is Phase 7).
They are **not** a Phase 2A blocker and were never part of the 2A acceptance scope. **Scope limitation
(explicit):** this PASS is the **owner-approved scope decision** and asserts only the neutral values /
locale-persistence scope; it does **not** assert that the root Kord `LocalizationService` or root
`/locale` are migrated. That exclusion is intentional and assigned to **Phase 6** — it is not a partial
result, a rollback, or a reason to reopen 2A.

This plan, `.opencode/opencode.json`, `spikes/jda-dave/**` and the Phase 2A source and test changes
were carried on `feature/114-kord-to-jda-migration` and **squash-merged** into the epic integration
branch by PR #115 as `5aad7c7`; the merged tree is identical to the former Phase 2A head `67802d1`.
That branch's remote ref was deleted after the merge; a stale local ref may remain. The neutral/shared
locale contracts and the isolated JDA `/locale` adapter were completed in Phase 2D and passed on
2026-11-14. Read Git and the pull request for commit history; this document asserts no head beyond the
recorded merge commit.

- `GuildId(ULong)` now lives in `es.wokis.domain.model` and `LocaleCode` in
  `es.wokis.domain.locale`; both are **domain-owned**, platform-neutral values.
- The locale repository and its use cases (`LocaleRepository`, `LocalJsonLocaleRepository`,
  `GetGuildLocaleUseCase`, `SetGuildLocaleUseCase`) speak only those domain values.
- **Root** `LocalizationService` is **not** neutral: its public signatures still take Kord
  `Snowflake` / `Locale` and convert to the domain values internally, and the **root** `/locale`
  command is Kord-facing. That is intentionally **Phase 6** scope, not 2A scope. **Phase 2A was a
  neutral-values/persistence checkpoint, and its scope is now closed as PASS.** The neutral/shared
  locale contracts and the isolated JDA `/locale` adapter were completed in **Phase 2D** and passed on
  2026-11-14; the root Kord service/command migration remains for Phase 6.
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
- Phase 2A is **PASS — gate closed (owner scope decision 2026-11-14)** and records exactly the neutral
  `GuildId`/`LocaleCode` and locale persistence boundary. JDA bootstrap, shard/event wiring and the
  command dispatch/response layer were **open at 2A closure** and were addressed by the isolated
  **Phase 2B** runtime; neutral/shared locale contracts and the isolated JDA `/locale` adapter were
  completed in **Phase 2D**, which **passed on 2026-11-14**. The root code/build switch and the
  Kord-facing root locale service/command belong to **Phase 6**; 2A closure did not by itself close
  Phase 2, whose only remaining gate then was the scoped import-rule gate — **that gate closed on
  2026-11-15 through PR #120**, so Phase 2 is **PASS — gate closed (2026-11-15)**.

#### Phase 2 subphase plan and stacking policy

Phase 2 is delivered in subphases. Each subsequent subphase gets its **own child branch and PR** and is
not folded into an already-open earlier PR. The **current stacking policy (owner decision 2026-11-14,
see "Stacking rules" below)** is to fetch and verify the **live epic tip** and branch from it, opening
the PR against the epic integration branch; the earlier "branch from the preceding subphase's branch"
pattern is **historical**. Phase 2A's combined Phase 1/2A checkpoint landed as PR #115,
**squash-merged** into the epic branch at `5aad7c7` (historical exception); the remote
`feature/114-kord-to-jda-migration` branch was deleted (a stale local ref may remain) and nothing
is restructured in place. Phase 2B branched from the Phase 1B branch
`feature/114-native-failfast-check`; PR #116 was then **squash-merged** into the epic branch at
`c00bdf1` (historical exception), after which PR #117 targeted the epic branch and was later **merged** there by a regular
merge commit at `255893a`. **2C is PASS — gate closed (2026-11-14)**: its acceptance criteria and
sanitized staging evidence are written below, and its branch `feature/114-phase-2c-voice-ports` was
created from the fetched epic head `255893a`, because the Phase 2B pass is contained in that merge
tree. The implementation, unit tests, tone resource and formatting-only ktlint repairs were pushed on
head `8eb0349c4f65997d0dd69ffdc2b245268c207074`, the docs-only checkpoint `9fd1826` followed, and PR
#118 (base: `feature/114-migrate-to-jda-jda-ktx`) was **merged** into the epic branch by the regular
merge commit `5fa9c63bc85ad3d5df1ed9c43c05f3e9b7d72e85` (parents `255893a` and PR head `3e50248`).
The four checks succeeded on the implementation head, on the docs-only `9fd1826` and on the merged
head `3e50248`; the Sonar Quality Gate passed with 90.1% new-code coverage, 11 new issues (10 major,
1 critical), 0 security hotspots and 0.0% duplication — recorded as-is, not zero. These are dated
checkpoints only, so read GitHub for any subsequent epic head and its checks. **No open Phase 2C PR
remains.** The epic branch `feature/114-migrate-to-jda-jda-ktx` remains the
**final integration target**, and `master` is merged only at migration completion.

| Subphase | Scope | Branch/PR posture | Status |
| --- | --- | --- | --- |
| **2A** | Domain-owned `GuildId`/`LocaleCode` + neutral locale repository/use cases + preserved `guild_locales` JSON | **Squash-merged** into the epic branch by PR #115 as `5aad7c7`; the remote `feature/114-kord-to-jda-migration` branch was deleted (a stale local ref may remain) and the checkpoint is not restructured | **PASS — gate closed (owner scope decision 2026-11-14)** for the delivered neutral-value/locale-persistence scope; the root Kord `LocalizationService`/`/locale` are Phase 6 scope, not a 2A blocker |
| **2B** | Neutral command context + responder/response port; adapters translate defer/ephemeral, locales and IDs | Child branch `feature/114-phase-2b-command-port`, originally based on Phase 1B `feature/114-native-failfast-check`; after #116 merged to the epic branch (`c00bdf1`), PR #117 was retargeted to the epic branch (base branch name only; no live base SHA asserted) and later **merged** there by the regular merge commit `255893a`; its one plan conflict was resolved by an ancestry merge that changed no code | **PASS — gate closed 2026-11-13** — isolated `:core`/`:jda-runtime` modules, neutral command boundary and JDA 6.7.0 adapter implemented with unit tests; #117 CI passed on covered checkpoint `e607ff7`, on the docs-only `2850907`/`16247f8`/`d353113`/`988a7cd`, on `e4a9e29` (later completed green after an in-progress observation), and on `7bf5419`, the latest verified all-green checkpoint before the subsequent docs-only updates (75.0% new-code coverage, 0 new issues, 0 hotspots); a later dated snapshot of `f5f04d1` (docs-only) was **not yet green** — only Check Code Quality had concluded at 16:53:24Z — so the status of any later head is read from GitHub; the user-run staging `/jda-walkthrough` interaction succeeded on 2026-11-13. Local QA/reviewer agents satisfied the independent review criterion; no GitHub PR review submitted, and the root runtime is still Kord-based. The pass is unaffected by the #116 ancestry merge |
| **2C** | Voice/session/player-message narrow ports; JDA `AudioSendHandler`/DAVE and lifecycle stay in the adapter | Branch `feature/114-phase-2c-voice-ports`, created from the fetched epic head `255893a` — the regular merge commit that contains PR #117's Phase 2B pass. PR #118 (base: `feature/114-migrate-to-jda-jda-ktx`) was **merged** into the epic branch by the regular merge commit `5fa9c63` (PR head `3e50248`) | **PASS — gate closed (2026-11-14).** Acceptance criteria, scope limits and the user-run staging playback evidence are recorded in the Phase 2C section — sanitized facts only. All four checks succeeded on the implementation head `8eb0349` and on the merged head `3e50248`; the Sonar Quality Gate passed with 90.1% new-code coverage, 11 new issues (10 major, 1 critical), 0 security hotspots and 0.0% duplication, recorded as-is with no zero-issue claim. Latest **local** run on the formatted tree: 685 cases (680 executed + 5 pre-existing skips), all pass with 0 failures/errors; local JaCoCo module line coverage `:core` 207/219 = 94.52% (branch 108/115 = 93.91%), `:jda-runtime` 870/948 = 91.77% (branch 342/418 = 81.82%) — local module JaCoCo only, not Sonar/new-code CI evidence. Only outgoing playback was proven; receive-audio, root runtime wiring and Phase 6 artifact validation remain out of scope |
| **2D** | Channel/message/locale edge adapters; neutral locale port/use cases/catalog in `:core`; shared JSON persistence module; shared unchanged language resources; minimal `/locale` command/message boundaries | Branch `feature/114-phase-2d-locale-edge-adapters`, created from the **live remote epic tip verified at branch creation** (`1304aa6`, `Clarified Phase 2C staging provenance`), not from `master` (`4badb92`) or a hardcoded SHA. Tested code/test snapshot `4ff8167` is pushed; **PR #119 is MERGED** into the epic branch by the regular merge commit `f56d225` (merge recorded 2026-11-14; head `0b365aa`, base `1304aa6`) | **PASS — gate closed (2026-11-14).** Implementation checkpoint complete; final local QA passed **758 test cases: 753 executed, 5 skipped (pre-existing), 0 failures/errors** with clean build, local module JaCoCo readings and local changed-file ktlint **0 findings**. The `a4bc1ae` time-qualified green snapshot has all four checks green; Sonar Quality Gate OK with 91.5% new-code coverage, 0.0% new duplication, 100% hotspots reviewed, 0 bugs/vulnerabilities/security hotspots, and 1 new MAJOR maintainability code smell `kotlin:S6524` disclosed as tracked follow-up debt. Sanitized user-run staging/restart evidence is recorded in the Phase 2D section. PR #119 is merged at `f56d225`; the root runtime stays Kord-based through Phase 5 and the Phase 6 root code/build switch remains OPEN |

**Phase 2 exit criterion 4 — scoped architectural import rule: PASS — gate closed (2026-11-15).** This
is an **exit gate, not a subphase** (no Phase 2E exists). PR #120 added the **root JUnit
import-boundary scanner** as **test-source-only** work (new files under `src/test/kotlin/architecture/`;
no production source, dependency, build-script or CI-workflow change, and no new Gradle task), and it is
**MERGED** into the epic branch by the regular merge commit `96e5fbb` on **2026-11-15 08:03:45Z** (head
`66f7783`, base `955ac49`, parents `955ac49` + `66f7783`). All four checks succeeded on `66f7783` —
Check Code Quality (2026-11-14 21:52:37Z), SonarCloud Code Analysis (21:56:59Z), Build and analyze
(21:57:10Z) and SonarCloud (21:57:06Z). Local evidence: **191** architecture tests (ImportBoundaryPolicy
**148**, KotlinFileHeaderParser **21**, SourceTreeScanner **22**) and, in its historical pre-merge
snapshot, **949 cases = 944 executed + 5 pre-existing skips, 0 failures/errors** with local ktlint 1.8.0
**0 findings** on the changed Kotlin test files. No new Sonar issue or coverage metric is claimed for this
PR beyond the four checks passing. Criteria 1–3 remain satisfied by the 2A/2B/2C/2D isolated-JDA evidence;
the root cutover is **not** required here and stays in Phase 6.

**Phase 2D locale acceptance note (accepted scope, PASS — evidence recorded).** 2D keeps the
**guild default locale** distinct from the **invoking user/client locale**. Legacy behaviour is that
a non-Community guild's default language is always `en_US`; only a Discord **Community** guild can
have its default language changed. The user/client locale describes the invoking client and must
never be persisted as, or conflated with, the guild default. Locale conversion and fallback —
including `unknown`, invalid or unsupported values — are validated at the Discord adapter boundary.
The **canonical Discord tags are the neutral values**, including the real JDA 6.7.0 `ar` and `he`
tags; JDA locales map through their canonical `.locale` string and `UNKNOWN` maps to no locale.
Persisted values stay raw; malformed stored codes fall back to the guild default, and valid but
untranslated locales fall back per key to English. No data migration is performed and no translation
authoring is part of 2D. **Phase 2C did not exercise locale selection** and returned no locale value;
its acceptance only preserves this guild-default versus user/client-locale distinction for 2D, and
the recorded `locale=en-US` observation comes from Phase 2B's `/jda-walkthrough`, not from 2C.
The **root Kord** bot's localization behaviour is **not** migrated by 2D: the root
`LocalizationService` remains a Kord-facing adapter over the neutral core service, while the isolated
JDA `/locale` uses the neutral/shared contracts and passed in 2D. `/locale` stays guild-scoped in the
isolated JDA runtime, and global command-registration parity remains an explicit **Phase 6** production
requirement. 2D is **PASS — gate closed (2026-11-14)**: final local automated
tests/build/JaCoCo/ktlint-diff checks passed, the `a4bc1ae` time-qualified green snapshot has all four checks
green with a passing Sonar Quality Gate, and the sanitized user-run staging/restart checks in the
Phase 2D section are recorded.

**Phase 2 exit status (updated 2026-11-15 — PASS, gate closed):** Phase 2A, 2B, 2C and 2D are all
**PASS**, and the last remaining gate — criterion 4, the scoped architectural import-rule CI gate (exact
scanner in the Phase 2 section) — was closed by **PR #120** on **2026-11-15**. PR #120 merged into the
epic branch at **2026-11-15 08:03:45Z** by the regular merge commit `96e5fbb` (head `66f7783`, base
`955ac49`, parents `955ac49` + `66f7783`), and all four checks succeeded on `66f7783`: Check Code
Quality 2026-11-14 21:52:37Z, SonarCloud Code Analysis 21:56:59Z, Build and analyze 21:57:10Z and
SonarCloud 21:57:06Z. The earlier **local test-source working-tree snapshot taken 2026-11-14 on base
`955ac49`, before the scanner branch was committed or pushed** — where the scanner existed as
uncommitted test-source changes and no PR/CI had run — is **historical evidence only**, not a current
state. Therefore **Phase 2 is PASS — gate closed (2026-11-15)**. Phase 3 is **IN PROGRESS — implementation landed**: its design was subsequently approved and its implementation is present in the local working tree, but the acceptance gate remains **open/not passed** pending PR CI/Sonar and staging.
Root Kord production is **not cut over** and no Kord removal happens before Phase 6; that is not a
Phase 2 blocker.

Stacking rules (current policy, owner decision 2026-11-14):

- For any future phase or subphase work: **fetch and verify the live epic tip first**, then branch
  **from that tip** and open the PR **against the epic integration branch**
  `feature/114-migrate-to-jda-jda-ktx`. Base your branch on the fetched SHA, not on a hardcoded or
  stale one (`4badb92` is `master`; `255893a`, `5fa9c63`, `1304aa6` and `269acc1` are historical
  bases). Phase 2B branched from Phase 1B and 2C/2D each branched from the live epic tip; that pattern
  is now the rule for every later phase.
- Landing is by **regular merge commit** — no squash and no rebase — so the branch's own commits stay
  in the epic history.
- **Historical exceptions:** PR #115 and PR #116 were **squash-merged** into the epic branch (`5aad7c7`
  and `c00bdf1`). Those are recorded as historical exceptions, not as precedent: they are not
  restructured, and later work is not pushed into them.
- Subphase branches are **not** squashed into an already-merged earlier PR and **not** rebased onto
  `master` mid-flight.
- The preserved ancestry is historical fact: #117 merged at `255893a` (parents `c00bdf1` + `7d8661d`),
  #118 at `5fa9c63` (parents `255893a` + `3e50248`), #119 at `f56d225` (parents `1304aa6` +
  `0b365aa`), #120 at `96e5fbb` (parents `955ac49` + `66f7783`). Do not rewrite it.
- A subphase is recorded complete only when its own gate evidence exists. **Open subphases are
  never marked complete** because an earlier checkpoint passed.

#### Phase 2A — recorded behavior shifts (tested)

- Raw invalid locale codes now pass through the neutral repository unchanged; parsing and fallback
  happen at the Kord edge via `LocaleCode.toKordLocaleOrNull()`. For the root `LocalizationService`
  this is externally equivalent: an invalid stored code still falls back to the supplied Discord
  locale, then to `en-US`.
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
read the live checks for any newer commit. **At 2B closure** broader Phase 2 was still in progress — the
root runtime migration and the architectural import rule were still open, and the isolated runtime
is **not** wired into the root application. *(Under the owner decision of 2026-11-14 the root runtime
migration is **Phase 6** scope, so the only remaining Phase 2 item became the scoped import-rule CI
gate.)* Phase 2C and Phase 2D closed on 2026-11-14;
PR #118 is merged into the epic branch at `5fa9c63`, and Phase 2D PR #119 is **merged** into the epic branch by the regular merge commit `f56d225` (2026-11-14). The last gate closed when **PR #120 merged on 2026-11-15** by the regular merge commit `96e5fbb` (head `66f7783`, base `955ac49`, four checks green), so **Phase 2 is PASS — gate closed (2026-11-15)**.

What the branch contained at the Phase 2B snapshot (historical layout):

- **Isolated Gradle modules at 2B.** `settings.gradle.kts` included `:core` and `:jda-runtime`. At that
  snapshot the root project added only `implementation(project(":core"))`; it remained Kord-based
  (local `libs/kord-*.jar`) and declared **no** JDA dependency or JDA runtime wiring. JDA 6.7.0 and
  coroutines were declared in the version catalog and consumed only by `:jda-runtime`. **Phase 2D
  later added the shared `:locale-persistence` module to both the root project and `:jda-runtime`**;
  this paragraph does not describe the current dependency graph.
- **`:core` is gateway-free.** At that snapshot and currently, it declares no Kord or JDA dependency
  and contains the neutral
  `CommandContext` (`guildId`, `channelId`, `userId`, `locale`, all domain values),
  `CommandHandler`, `CommandResponder` (at most one `defer` and one `respond`; after `defer` the
  response edits the original and visibility stays fixed by the defer), `CommandResponse`, the pure
  `WalkthroughCommand` handler, and the domain-owned `GuildId`/`LocaleCode` values moved out of the
  root source set alongside the new `ChannelId` and `UserId` values introduced by `:core`.
- **`:jda-runtime` is the only gateway adapter.** At that snapshot it depended on `:core` and JDA
  6.7.0 and contained
  `JdaBootstrap`, `JdaCommandRouter` (the gateway callback only identifies the handler; handler and
  REST work run off the JDA event thread), `JdaCommandContextMapper`, the state-machine
  `JdaCommandResponder` (a failed REST action restores the previous state so the router can fall
  back safely) and the coroutine `RestActionBridge`. Phase 2D later added `:locale-persistence` to
  its dependencies; the gateway-adapter role remains true.
- **Guild-only walkthrough command (historical at 2B).** At that snapshot, the only registered
  command was `/jda-walkthrough`, upserted
  guild-scoped from `DISCORD_DEV_GUILD_ID`; `DISCORD_BOT_TOKEN` and `DISCORD_DEV_GUILD_ID` were
  mandatory and the isolated runtime failed fast without them. No global command was published, no
  gateway intents were enabled, and the isolated JDA entry point was **not** wired into the root
  runtime. Later phases added the 2C/2D diagnostic and `/locale` commands.
- **Deliberately out of scope for 2B (historical):** the **root Kord** `/locale` and root
  `LocalizationService` stayed Kord-facing at 2B closure; Phase 2D later completed the neutral/shared
  locale contracts and isolated JDA `/locale` (passed 2026-11-14). The root Kord service/command
  remains Phase 6 scope. Voice/DAVE and session/player ports were 2C, and root bootstrap/fat-JAR
  wiring stays Phase 6.

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
  guild-scoped command registration and one command round-trip. It did not validate the root
  application, root bootstrap wiring, `/locale`, voice/DAVE or any other command family. Voice/DAVE
  was validated separately by **Phase 2C on 2026-11-14** (see the Phase 2C section); the **root**
  Kord wiring and **root** `/locale` command remain for Phase 6, while the isolated JDA `/locale`
  adapter was completed and passed in **Phase 2D (2026-11-14)**. Phase 6 artifacts remain open.
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
which were **2D** work and are **not** migrated by Phase 2B; Phase 2D later passed on 2026-11-14.

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
  full scoped import rule (exact custom scanner matrix under Phase 2) was then the Phase 2 exit/CI gate
  and is **not** closed by it; that gate has since been satisfied by **PR #120** (2026-11-15).
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
  is therefore **PASS / closed**; broader Phase 2 has since become **PASS — gate closed (2026-11-15)**
  through PR #120, which satisfied the scoped import-rule gate.

### Phase 2C — voice/session/player-message ports and adapters — PASS — gate closed (2026-11-14)

**Status: PASS — gate closed (2026-11-14).** This section records the agreed acceptance criteria,
scope limits, branch/base posture, the dated CI checkpoints and the sanitized user-run staging
evidence for Phase 2C. The implementation, unit tests and tone resource were committed and pushed on
`feature/114-phase-2c-voice-ports`, and PR #118 (base: `feature/114-migrate-to-jda-jda-ktx`) was
**merged** into the epic branch by the regular merge commit
`5fa9c63bc85ad3d5df1ed9c43c05f3e9b7d72e85` (parents `255893a` and PR head `3e50248`), so **no open
Phase 2C PR remains**. The implementation head is
`8eb0349c4f65997d0dd69ffdc2b245268c207074` (`Fixed ktlint violations in Phase 2C`), the docs-only
head is `9fd1826731916111f47517b574a33b98d81c8880` (`Recorded Phase 2C CI checkpoint`) and the final
merged head is `3e50248446a5ce65ea2a1f648576523fb5b25c6a` (`Recorded passing Phase 2C CI checkpoint`).
All four checks on the implementation head **succeeded** — Check Code Quality, Build and analyze,
SonarCloud Code Analysis and SonarCloud — and the Sonar Quality Gate **passed** with **90.1% Coverage
on New Code**, **11 new issues** (10 major, 1 critical), **0 security hotspots** and **0.0%
duplication**. The 11 new issues are recorded as-is; no zero-issue claim is made, and they are carried
forward as **tracked follow-up debt** — under the **owner-approved 2026-11-14 interpretation** the
repository's configured Quality Gate is the criterion, and it passed. The earlier head `c893567` failed
only Check Code Quality, on 25 formatting findings, and the formatting-only repairs were committed and
pushed as `8eb0349`. All four
checks also **succeeded on the docs-only `9fd1826`** and on the merged head `3e50248` on
**2026-11-14** — on `3e50248` they concluded at Check Code Quality 08:38:15Z, SonarCloud Code
Analysis 08:40:08Z, Build and analyze 08:40:13Z and SonarCloud 08:40:13Z. Those are **dated
historical checkpoints** only; read the live GitHub check runs for whatever head is current. The
accepted user-run playback gate was closed by the **2026-11-14 staging run recorded below**, not by CI
alone. No human GitHub review is recorded here; read the PR for its live review state. Passing 2C did
**not** close broader Phase 2 at that closure: at 2C closure on 2026-11-14 broader Phase 2 remained
open, and it subsequently closed **PASS — gate closed (2026-11-15)** after the scoped import rule
passed CI and merged. Criterion 6 is recorded **PASS** under the **owner-approved
interpretation of 2026-11-14**: the repository's **configured Sonar Quality Gate is the criterion**,
and it passed. The **11 new maintainability issues** (10 major, 1 critical-severity code smell; 0
bugs, 0 vulnerabilities, 0 security hotspots) are recorded transparently and carried forward
collectively as **tracked follow-up debt**; **per-issue dispositions and owners are not recorded in
this document yet**. They are not a failed gate, not silently waived, and never described as zero.

#### Branch and base posture

- Phase 2B was **merged** into the epic branch `feature/114-migrate-to-jda-jda-ktx` by a **regular
  merge commit** — not a squash — at
  `255893abcef31554e13ae1713086a9b7c1217c60`. Its parents are the epic tip `c00bdf1` and the Phase 2B
  head `7d8661d`, and its tree is identical to `7d8661d`, so the epic head carries the Phase 2B pass
  unchanged.
- The correct 2C base is therefore the **fetched remote epic head `255893a`**. It is **not** the stale
  local `4badb92` (that is `master`), and it is not the old Phase 2B branch.
- Phase 2C branch: **`feature/114-phase-2c-voice-ports`**, created from the fetched
  `origin/feature/114-migrate-to-jda-jda-ktx` at `255893a`. The plan commit, implementation, unit
  tests, tone resource and the formatting-only ktlint repairs were **committed and pushed** on head
  `8eb0349c4f65997d0dd69ffdc2b245268c207074` (`Fixed ktlint violations in Phase 2C`); the docs-only
  checkpoint `9fd1826` (`Recorded Phase 2C CI checkpoint`) followed, and the final PR head was
  `3e50248446a5ce65ea2a1f648576523fb5b25c6a` (`Recorded passing Phase 2C CI checkpoint`). **PR #118
  was merged** into the **epic branch** by the regular merge commit `5fa9c63` (parents `255893a` and
  `3e50248`), so **no open Phase 2C PR remains**. All four checks on `8eb0349` and on `3e50248`
  succeeded, and the Sonar Quality Gate passed with 90.1% new-code coverage, 11 new issues (10 major,
  1 critical), 0 security hotspots and 0.0% duplication — recorded as-is, not zero. The earlier head
  `c893567` failed only Check Code Quality, on 25 formatting findings. `8eb0349`, `9fd1826` and
  `3e50248` are dated historical checkpoints only; read the live GitHub check runs for any later epic
  head.
- PR #117 stays **closed / merged** and is not modified. `master` stays untouched, and the root
  runtime stayed Kord-based and JDA-free during 2C.

#### Implementation checkpoint — merged in `5fa9c63` (final PR head `3e50248`); tested locally

- `:core` gains neutral outgoing-audio values/ports (`AudioFrame`, `AudioFrameFormat`,
  `AudioFrameSource`, `VoiceSessionPort` and its result types), a text-only `PlayerMessagePort`, and a
  neutral `/jda-voice-walkthrough` handler that depends only on those ports.
- `:jda-runtime` gains the JDA `AudioSendHandler`, the voice connection/lifecycle adapter, the
  text-only player-message adapter, DAVE 0.2.0 configuration and the pre-encoded 20 ms Opus tone
  source. The tone is a generated bare-packet resource in
  `jda-runtime/src/main/resources` — committed and pushed with the rest of the implementation —
  produced by `jda-runtime/tools/generate-walkthrough-tone.py` (Python stdlib + macOS
  `afconvert` only), with exactly 75 packets, every packet's 20 ms duration derived from its Opus TOC
  byte and every packet at or below the 1275-byte limit. The `api`, `impl-jni`, `adapter-jda`,
  `natives-darwin` and `natives-linux-x86-64` 0.2.0 modules are confined to this module; the root
  catalog's existing `libdave` / `libdave-natives` aliases and root dependency graph are unchanged.
- The isolated bootstrap enables only `GUILD_VOICE_STATES` plus `CacheFlag.VOICE_STATE`, performs the
  DAVE native/protocol preflight before gateway login, and registers both guild-scoped diagnostics
  through the existing registrar. No global command and no privileged intent is introduced.
- **Latest local result (local only, not CI):** on the formatted tree,
  `:core:test` **81** tests and `:jda-runtime:test` **224** tests passed with 0 failures and 0 skips,
  and the root suite was **380** tests (5 pre-existing skips) with 0 failures/errors — **685 cases
  total (680 executed + 5 skips)**. Local JaCoCo **module** line coverage is `:core`
  **207/219 = 94.52%** (branch **108/115 = 93.91%**) and `:jda-runtime` **870/948 = 91.77%** (branch
  **342/418 = 81.82%**). Those are **local module JaCoCo report readings only** — independently
  reproducible per module, **not** Sonar's new-code metric and not CI evidence; no aggregate
  changed-lines coverage figure is claimed for Phase 2C. The clean full `./gradlew build` passed
  locally on the same tree.
- **CI on the implementation head `8eb0349`, the docs-only `9fd1826` and the final merged head
  `3e50248` is green.** All four checks succeeded on all three heads — **Check Code Quality**,
  **Build and analyze**, **SonarCloud Code Analysis** and **SonarCloud** — and the Sonar Quality Gate
  **passed** with **90.1% Coverage on New Code**, **11 new issues** (10 major, 1 critical), **0
  security hotspots** and **0.0% duplication**; no zero-issue claim is made for 2C. On `9fd1826` the
  four checks concluded on **2026-11-14** at Check Code Quality **08:28:21Z**, SonarCloud Code
  Analysis **08:31:00Z**, Build and analyze **08:31:04Z** and SonarCloud **08:31:05Z**; on `3e50248`
  they concluded at Check Code Quality **08:38:15Z**, SonarCloud Code Analysis **08:40:08Z**, Build
  and analyze **08:40:13Z** and SonarCloud **08:40:13Z**. These are dated historical checkpoints only;
  read the live GitHub check runs for any later epic head.
- **Prior head `c893567` — kept as history.** There **Check Code Quality FAILED** in its ktlint step
  with 25 formatting findings (`blank-line-between-when-conditions` plus one
  `function-expression-body`), while **Build and analyze**, **SonarCloud Code Analysis** (90.1%
  new-code coverage, 11 new issues, 0 hotspots) and **SonarCloud** succeeded. The formatting-only
  repairs for the six affected files were committed and pushed as `8eb0349`; they change no behaviour
  and address exactly the 24 `standard:blank-line-between-when-conditions` and 1
  `standard:function-expression-body` diagnostics. That earlier failure is historical and does not
  keep the gate open: the 2C gate was closed by the recorded staging run below.
- **Local QA/reviewer agents (not GitHub review).** The local Code Quality and reviewer agents report
  **PASS** on the formatted tree. That satisfies the internal review criterion only; no human GitHub
  review is recorded here — read PR #118 for the live review state.

#### Phase 2C CI checkpoints (dated history — read live GitHub checks for newer heads)

These are **dated historical checkpoint records** for PR #118, which is now **merged** into the epic
branch by `5fa9c63`. None of them is a standing guarantee, and none is a claim about a later head:
read the live GitHub check runs for whatever head is current.

| Head | Commit subject | Recorded checks | Sonar report |
| --- | --- | --- | --- |
| `c893567` | `Added DAVE-backed JDA voice adapters` | **Check Code Quality FAILED** on 25 ktlint formatting findings (24 `blank-line-between-when-conditions`, 1 `function-expression-body`); **Build and analyze**, **SonarCloud Code Analysis** and **SonarCloud** succeeded | 90.1% Coverage on New Code, 11 new issues, 0 security hotspots, 0.0% duplication |
| `8eb0349` | `Fixed ktlint violations in Phase 2C` | All four **succeeded**: Check Code Quality, Build and analyze, SonarCloud Code Analysis and SonarCloud | 90.1% Coverage on New Code, 11 new issues (10 major, 1 critical), 0 security hotspots, 0.0% duplication |
| `9fd1826` | `Recorded Phase 2C CI checkpoint` (docs-only) | All four **succeeded on 2026-11-14**: Check Code Quality **08:28:21Z**, SonarCloud Code Analysis **08:31:00Z**, Build and analyze **08:31:04Z**, SonarCloud **08:31:05Z** | Quality Gate **passed**, 90.1% Coverage on New Code, 11 new issues, 0 security hotspots, 0.0% duplication |
| `3e50248` | `Recorded passing Phase 2C CI checkpoint` (docs-only; final PR head, merged by `5fa9c63`) | All four **succeeded on 2026-11-14**: Check Code Quality **08:38:15Z**, SonarCloud Code Analysis **08:40:08Z**, Build and analyze **08:40:13Z**, SonarCloud **08:40:13Z** | Check runs green on this docs-only head; per-issue Sonar figures are those recorded for the covered heads above, not re-read for `3e50248` |

- `c893567` is retained as history: the formatting-only repairs for the six affected files were
  committed and pushed as `8eb0349`, and they change no behaviour.
- `9fd1826` and `3e50248` changed only `docs/migrations/kord-to-jda.md`. Each was the head at its own
  checkpoint; PR #118 was then merged by `5fa9c63`, so GitHub is authoritative for any later epic head
  and its checks.
- The **11 Sonar new issues** are recorded as-is on every head above — **no zero-issue claim** is
  made. All 11 are maintainability code smells (10 major, 1 critical-severity), with 0 bugs, 0
  vulnerabilities and 0 security hotspots. Under the **owner-approved 2026-11-14 interpretation** the
  configured Quality Gate is the criterion and it passed, so these do not fail the gate; they are
  carried forward collectively as **tracked follow-up debt** — **per-issue dispositions and owners
  are not recorded here yet** — not silently waived, and closing the 2C gate did not resolve them.
- The figures above are **CI/Sonar results for those heads**. The local test counts and JaCoCo
  readings elsewhere in this section are **local-only** and are not Sonar or new-code evidence.
- Staging evidence is recorded separately in the subsection below: it is **user-reported and
  sanitized**, not CI output, and no CI run is claimed as playback proof.

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
   the repository's **configured Sonar Quality Gate passing**. That gate requires **ratings A**,
   **new-code coverage >= 50%**, **duplication <= 3%** and **100% hotspot review**; it defines **no
   zero-new-issue threshold**. The **>= 75% coverage figure is an internal target, not a configured
   gate condition**: the coverage threshold the configured gate enforces is **>= 50%**. The other
   gate conditions — A ratings, duplication <= 3% and 100% hotspot review — apply exactly as recorded.
   Local status: the clean full `./gradlew build` passed
   locally on the formatted tree, and local module JaCoCo reports `:core` line coverage 207/219 =
   94.52% (branch 108/115 = 93.91%) and `:jda-runtime` line coverage 870/948 = 91.77% (branch
   342/418 = 81.82%); those are **local module JaCoCo reports only, not Sonar/new-code coverage or CI
   evidence**. CI **has run** on the implementation head `8eb0349`, on the docs-only follow-up
   `9fd1826` and on the final PR head `3e50248`: all four checks are green on all three. Sonar reports
   a **PASSING Quality Gate** with **90.1% Coverage on New Code** — above the configured **>= 50%**
   threshold and above the internal **>= 75%** target — **0 security hotspots**, **0.0% duplication**,
   **0 bugs** and **0 vulnerabilities**, plus **11 new issues** (10 major, 1 critical-severity
   **code smell**). All 11
   findings are **maintainability code smells**; none is a bug, vulnerability, security hotspot or
   duplication failure. **Owner-approved interpretation (2026-11-14):** the repository's **configured
   Quality Gate is this criterion**, so a passing gate with all its thresholds met satisfies criterion
   6. The 11 issues do **not** fail the gate and are **not silently waived** — they are recorded
   transparently and carried forward collectively as **tracked follow-up debt**; **per-issue
   dispositions and owners are not recorded here yet** (see the CI checkpoint notes below), and **no
   zero-issue claim** is made. The earlier `c893567` ktlint failure is fixed by the formatting-only
   repairs in `8eb0349`. The green heads are dated historical checkpoints only; read the live GitHub
   check runs for any later epic head.
7. **User-run staging gate (sanitized evidence only).** Bot login and ready state; the walkthrough
   command is available **guild-scoped only**; DAVE native and protocol initialize; **all 75 20 ms
   Opus frames are supplied to JDA's `AudioSendHandler`/send loop** — the reported frame count proves
   hand-off only, **not** confirmed network delivery; the **invoking user's own Discord client**,
   separate from the bot, **reports hearing the tone**, which is the **actual delivery proof** and is
   a distinct observation from the 75-frame hand-off; the bot **disconnects cleanly**; and **no receive
   path** is exercised. Record sanitized facts only — no token, raw log, or
   guild/channel/user/application identifier. **Status: PASS (2026-11-14)** — the user ran the isolated
   runtime on their local macOS arm64 machine against the staging bot, their own Discord client
   reported hearing the beep, and they reported the completion path's disconnect cleanup; the
   sanitized record is in the staging evidence subsection
   below. The invoking user's client-side audible confirmation is treated as the delivery evidence;
   the record does **not** identify or claim another, separately verified account. The `75/75` frame
   count is **not** recorded as network-delivery proof.
8. **Phase boundary (historical at 2C closure).** At 2C closure on 2026-11-14, broader Phase 2
   remained open: passing 2C did **not** close it then — **2D** (channel/message/locale edge
   adapters) and the **architectural import rule** remained open, and the **root runtime migration**
   was recorded as open under the older placement wording; it is now **Phase 6** scope under the
   owner decision of 2026-11-14. **Phase 2D subsequently closed PASS on 2026-11-14**, and
   **Phase 2 itself subsequently closed on 2026-11-15** after the scoped import rule passed CI and
   merged; this criterion preserves the 2C-closure perspective only. **Phase 6** JDK 21 / fat-JAR /
   native artifact validation also remains open.

#### Phase 2C staging evidence — PASS (2026-11-14)

User-run evidence recorded on **2026-11-14**. It is **user-reported and sanitized**: no token, raw
log, or guild/channel/user/application identifier is reproduced here or in the repository.

| Check | Recorded result |
| --- | --- |
| Host / native | User's **local macOS arm64** machine; the DAVE **0.2.0** native loaded successfully — `natives-darwin` supports this host (the 2C module declares both `natives-darwin` and `natives-linux-x86-64` as `runtimeOnly`) |
| DAVE protocol | Native loaded; maximum supported protocol version 1 and negotiated protocol version 1 |
| JDA login | Completed; WebSocket connected; client reached ready |
| Command registration | Guild-scoped `/jda-walkthrough` and `/jda-voice-walkthrough` registered |
| Gateway intents | Only **`GUILD_VOICE_STATES`** |
| Voice | `/jda-voice-walkthrough` connected to the invoking user's regular voice channel |
| DAVE init | DAVE protocol 1 initialized on the connection |
| Command reply | `/jda-voice-walkthrough` replied `Voice walkthrough OK \| handed 75/75 frames to JDA's send loop (delivery not confirmed)` — the **Phase 2C** response, distinct from Phase 2B's `JDA walkthrough OK` |
| Frame hand-off | That reply reports `handed 75/75 frames to JDA's send loop` — a **hand-off count only**, and the reply itself states delivery was not confirmed |
| Audible playback | The **invoking user's own Discord client**, separate from the bot, reported hearing the beep — **this is the delivery evidence**, a distinct observation from the frame hand-off |
| Cleanup | The command's completion path confirms disconnect cleanup |
| Locale selection | **Not exercised** by `/jda-voice-walkthrough`, which returned no locale value; the `locale=en-US` observation belongs to the Phase 2B `/jda-walkthrough` run on 2026-11-13, not to 2C |
| Shutdown | The user stopped the still-running JDA process with Ctrl+C; no failure reported |
| Receive audio | Not exercised |

Scope of this evidence:

- It validates the **isolated `:jda-runtime` outgoing path only**: DAVE native load and protocol
  initialization, guild-scoped registration with only `GUILD_VOICE_STATES`, voice connection,
  the 75-frame hand-off and audible output, and the completion path's disconnect cleanup.
- The `75/75` frame count is **not** network-delivery proof. The **audible beep, heard on the
  invoking user's own Discord client separate from the bot, is the delivery evidence**; that audible
  observation is distinct from the 75-frame hand-off, and no other account is claimed as independently
  verified. The `Voice walkthrough OK` prefix is the 2C diagnostic's own reply and does **not** itself
  confirm delivery — the reply carries `(delivery not confirmed)`, and the audible beep remains the
  delivery evidence. This Phase 2C reply is **not** the Phase 2B `JDA walkthrough OK` response.
- It does **not** validate receive-audio, the assistant voice feature, the root application, root
  bootstrap wiring, `/locale` or any other command family.
- It did **not** exercise locale selection: `/jda-voice-walkthrough` returned no locale value, and the
  recorded `locale=en-US` observation belongs to the Phase 2B `/jda-walkthrough` run on 2026-11-13.
  2C only preserves the guild-default versus user/client-locale distinction for **2D**.
- The run was on **local macOS arm64**, not on the Linux x86_64 staging container, and it used **no
  Phase 6 artifact**: root runtime wiring and Phase 6 packaging/artifact/digest validation remain
  **OPEN**.
- Only the sanitized facts above are recorded.

#### Deferred to Phase 3 — pause/resume preservation (user decision)

The user explicitly chose to **defer pause/resume preservation to Phase 3 real-player integration**.
It remains an **overall migration requirement** — it is carried by the Phase 3 exit criteria and the
architecture guardrail below — but it is **not a Phase 2C acceptance gate**. The 2C diagnostic sends
one controlled tone and has no pause control, so 2C makes no pause-semantics claim. Do not record 2C
as covering pause/resume, and do not treat the deferral as dropping the requirement.

#### Inherited caveat from Phase 2B

The Phase 2B locale caveat still applies: a non-Community guild's default language is `en_US`, and the
`locale=en-US` observation recorded in the Phase 2B `/jda-walkthrough` evidence is that guild default,
not an explicit or user-selected locale. **That observation came from the Phase 2B `/jda-walkthrough`
response, not from Phase 2C.** The Phase 2C `/jda-voice-walkthrough` did not exercise locale selection
and returned no locale value; its acceptance only preserves the distinction between the guild default
locale and the invoking user/client locale for **2D**, which owns that behaviour. No localization
behaviour is migrated here, and no locale selection was exercised or recorded by 2C.

#### Recording rules

Only sanitized evidence is recorded in this plan. No secrets, bot/guild/channel/user identifiers,
tokens or raw logs are added. The earlier plan checkpoint changed **only this migration plan**. The
2C implementation, test, resource and formatting-repair files plus the module build/catalog edits
recorded above were merged into the epic branch with PR #118 at `5fa9c63`. This document update
itself modifies **no source, build, test or asset file and no PR metadata**. The local test, coverage
and build numbers above are **local module results**, not Sonar or CI evidence; the CI figures come
from the completed check runs on `8eb0349`, `9fd1826` and the final PR head `3e50248` (all four checks
succeeded on each). They are dated historical checkpoints, so read the live GitHub check runs for any
later epic head. The staging facts are **user-reported** and sanitized. No human GitHub
review is recorded here. Any **detekt** migration is a future separate tooling task, not part of 2C:
2C uses the existing ktlint quality gate only, and no detekt configuration or dependency is added on
this branch.

### Phase 2D — channel/message/locale edge adapters — PASS — gate closed (2026-11-14)

**Status: PASS — gate closed (2026-11-14).** Phase 2C remains **PASS — gate closed (2026-11-14)**. Phase 2
overall is now **PASS — gate closed (2026-11-15)**: 2A–2D are **PASS**, the isolated-runtime evidence is
recorded, and the scoped import-rule CI gate was closed by **PR #120** (merged 2026-11-15 by the regular
merge commit `96e5fbb`; head `66f7783`, base `955ac49`). The earlier **local test-source working-tree
snapshot taken 2026-11-14 on base `955ac49`, before the scanner branch was committed or pushed** — where
the scanner implementation existed with no PR/CI result — is **historical evidence only**;
**Phase 6** — root code/build switch, Kord removal plus packaging/artifact validation — remains **OPEN**;
issue #114 remains open. Pause/resume preservation remains
deferred to Phase 3 and is a required Phase 3 outcome with semantics approved before implementation;
detekt remains a separate future tooling task.

#### Branch and base posture

- The live remote epic tip was fetched and verified at the start of 2D as
  `1304aa6` (`Clarified Phase 2C staging provenance`) on `origin/feature/114-migrate-to-jda-jda-ktx`.
- The worktree was clean and on `feature/114-migrate-to-jda-jda-ktx` before branching. No local or
  remote `phase-2d` branch existed. `master` was `4badb92` and is untouched.
- The 2D branch is **`feature/114-phase-2d-locale-edge-adapters`**, created from the verified epic
  tip `1304aa6` — not from `master`, not from a hardcoded `5fa9c63`/`269acc1`, and not from a stale
  local commit. The tested code/test snapshot is **`4ff8167`** (parent `1304aa6`); the
  **`a4bc1ae` time-qualified green snapshot** is a documentation-only child of `4ff8167`. At the
  recorded 2026-11-14 snapshot, live PR #119 head was the later docs-only `8c20a1f`, fully green at
  that check time; **PR #119 is MERGED into
  `feature/114-migrate-to-jda-jda-ktx`** by the regular merge commit
  `f56d2252506617ae282c50a3cc95fb830c644424` on 2026-11-14 — its two parents are the base `1304aa6`
  and the final head `0b365aa14514c358dd9b31e94b8c032e68cf0bf9`. PR #119 is the stacked Phase 2D PR,
  and the epic branch advanced to `f56d225` at that recorded snapshot; **`f56d225` was the epic tip
  before this documentation update**. The live tip is read from fetched Git/GitHub.

#### Accepted architecture and scope

- **Neutral locale model/catalog in `:core`.** The locale repository port, `GetGuildLocaleUseCase`,
  `SetGuildLocaleUseCase`, the canonical Discord locale catalog and the neutral localization catalog
  live in `:core`. `:core` gains no Kord or JDA dependency.
- **Shared JSON persistence.** The existing `LocalJsonLocaleRepository` and its
  `GuildLocalesContainer` DTO are extracted into the small reusable `:locale-persistence` module,
  which both the root Kord project and `:jda-runtime` depend on. There is one implementation, not a
  copy per runtime. The production path remains `./data/guild_locales.json`.
- **Unchanged legacy JSON contract.** The persisted shape remains exactly
  `{ "guild_locales": { "<decimal-id>": "<canonical-tag>" } }`. Existing `LocaleCode(String)` values
  and raw JSON fields already represent the neutral tags, so **no data migration** is performed.
  Raw stored values are returned unchanged; the repository keeps its null/raw/exception behaviour.
  Malformed values are not silently sanitized on read.
- **Shared language resources.** `lang.yml` (default `en-US`) and `lang_es-ES.yml` are moved without
  content or filename changes from the root resources to `:core/src/main/resources/lang`, so both
  runtimes load the same catalog from the shared runtime classpath. `ar`/`he` translations are **not**
  fabricated; selectable locales without a translation file fall back per key to English.
- **Canonical neutral values.** The catalog contains the real JDA 6.7.0 canonical tags, including
  `ar` and `he`, and excludes the synthetic `unknown`. JDA enums map through their canonical `.locale`
  string; `UNKNOWN` maps to no locale. Enum names, localized names, aliases and custom tags are never
  persisted. Canonical command input remains exact-case, while `reset` remains case-insensitive.
- **Format-valid but unsupported tags.** The Kord edge no longer treats Kord's format-only
  `Locale.fromString` result as a supported Discord locale: codes are validated against the canonical
  catalog. Format-valid non-Discord tags such as `xx-YY` therefore fall back instead of being treated
  as supported.
- **Locale separation.** The neutral command context keeps the **guild default locale**, the
  **persisted guild locale** and the **user/client locale** distinct. A user/client locale is never
  persisted as, or conflated with, the guild default. Non-Community guilds keep the `en_US` default;
  a Community guild's configurable default is supplied by the Discord adapter, not hardcoded.
- **Fallback semantics.** Resolution is persisted valid guild locale → guild default → English
  default. A malformed stored code falls back to the guild default. A valid but untranslated locale
  falls back per key to the English `en-US` string. Existing `/locale` validation behaviour is
  preserved, with the canonical catalog expanded to the JDA locale set.
- **Minimal adapters.** The root `LocalizationService` remains a Kord-facing adapter over the neutral
  localization service and keeps its existing public signatures. `/locale` remains admin-only with
  the same success/reset/invalid responses, set/reset persistence and persist-before-confirmation
  order. The JDA `/locale` command is **guild-scoped only** for 2D staging; global
  command-registration parity is an explicit **Phase 6** production requirement. There is no generic
  JDA facade and no generic channel/message facade; only the narrow command/responder/autocomplete
  boundaries needed by `/locale` are added.
- **Root gateway/runtime not cut over.** The root runtime stays Kord-based and JDA-free, even though
  2D refactored the root Kord-facing locale service/command, resources, DI and module dependencies.
  No Phase 6 wiring or artifact changes are part of 2D.
- **Test ownership.** Production files and resources are changed in this implementation pass. The
  **Test Agent has added/moved the Phase 2D test sources**; this pass did not modify any test source
  or test resource.

#### Implementation checkpoint (PASS)

- `:core` now owns `LocaleRepository`, `GetGuildLocaleUseCase`, `SetGuildLocaleUseCase`,
  `DiscordLocaleCatalog`, `LocalizationCatalog`, `GuildLocalizationService` and the neutral
  `LocaleCommandHandler`.
- `:locale-persistence` now owns `LocalJsonLocaleRepository`, `LocaleJson` and
  `GuildLocalesContainer`; the root `RepositoryModule` and the isolated JDA bootstrap both use it.
- The root `LocalizationService` delegates to `GuildLocalizationService` and converts only at the
  Kord edge. `DISCORD_LOCALE_MAP` and `LocaleCode` conversions are catalog-driven, so `ar`/`he` are
  safe in both directions and unsupported format-valid tags are rejected.
- `:jda-runtime` adds a guild-scoped `/locale` registration (admin-only, one required autocomplete
  string option), a JDA autocomplete listener, and context mapping that keeps the guild default and
  user locale separate. A neutral `/locale` handler applies the legacy set/reset/invalid order and
  persists before responding. Its no-guild and missing-option responses stay **public**, matching
  the legacy Kord `ErrorHandlerService` path rather than making them ephemeral.
- `/locale` command and option descriptions carry the catalog's **actual available translations**
  (`en-US` and `es-ES`) through neutral `CommandDefinition` localization maps. The JDA registrar
  maps those canonical tags to JDA locales at the registration edge; no `ar`/`he` or other
  translations are fabricated, and `:core` stays free of Kord/JDA types.
- Root Kord `/locale` registration now selects the catalog's `en-US` description deterministically
  and reports a missing default instead of relying on map iteration order, while still attaching
  the full translated description maps.
- The reset marker has a single canonical value in `DiscordLocaleCatalog.RESET_INPUT`. The public
  top-level `RESET_LOCALE_VALUE` remains a source-compatible alias to it, so existing imports keep
  working and reset matching behavior is unchanged.
- The shared language resources moved to `:core` with unchanged filenames and content; the root
  `generateLangClass` task now reads the shared copy. The root test resources are untouched.

#### Phase 2D CI snapshot — commit 4ff8167 (2026-11-14)

This is a **historical, time-qualified CI snapshot for the tested implementation commit `4ff8167`**
on PR #119. PR #119 was **open/draft when these checks ran**; it was marked **ready for review later
that day at 15:19:27Z**, so it was not draft as of the later 2026-11-14 snapshot. Base `1304aa6`. All
four GitHub checks completed successfully on 2026-11-14:

| Check | Result |
| --- | --- |
| Check Code Quality | **success**, 14:21:33Z |
| SonarCloud Code Analysis | **success**, 14:25:29Z |
| Build and analyze | **success**, 14:25:35Z |
| SonarCloud | **success**, 14:25:35Z |

Those passes apply only to implementation snapshot `4ff8167`. The later `a4bc1ae` time-qualified
green snapshot is recorded separately below. PR #119 has since been **merged** into the epic branch
by the regular merge commit `f56d225` on 2026-11-14.

#### Phase 2D a4bc1ae green-snapshot CI/Sonar — commit a4bc1ae (2026-11-14)

`a4bc1ae` is a documentation-only child of `4ff8167`. PR #119 was **open/draft when these checks ran**
and was marked **ready for review later that day at 15:19:27Z**. All four
GitHub checks completed successfully on 2026-11-14:

| Check | Result |
| --- | --- |
| Check Code Quality | **success**, 14:35:53Z |
| SonarCloud Code Analysis | **success**, 14:38:30Z |
| Build and analyze | **success**, 14:38:33Z |
| SonarCloud | **success**, 14:38:36Z |

Sonar Quality Gate: **OK**. New-code coverage **91.5%**, new duplication **0.0%**, hotspots reviewed
**100%**, **0 bugs**, **0 vulnerabilities**, **0 security hotspots**, and **1 new MAJOR maintainability
code smell** — `kotlin:S6524` (“Make this collection immutable”) in
`core/.../LocalizationCatalog.kt`. The finding is disclosed as **tracked follow-up debt** under the
approved configured Sonar Quality Gate interpretation; it is **SCM auto-assigned to Wikijito7** and
has no explicit per-issue disposition recorded yet. The configured gate passes. This snapshot is
time-qualified to `a4bc1ae`; a later documentation-only commit requires fresh checks and is not
covered here. Readers should consult live PR #119 checks for current status. PR #119 has since been
**merged** into the epic branch by the regular merge commit `f56d225` on 2026-11-14 (head `0b365aa`).

#### Phase 2D PR-head CI snapshot recorded 2026-11-14 — commit 8c20a1f

At the recorded 2026-11-14 snapshot, live PR #119 head was the later docs-only child `8c20a1f` of
`a4bc1ae`; it contains no source/test changes. All four GitHub checks had completed successfully:

| Check | Result |
| --- | --- |
| Check Code Quality | **success**, 15:58:06Z |
| SonarCloud Code Analysis | **success**, 16:00:19Z |
| Build and analyze | **success**, 16:00:23Z |
| SonarCloud | **success**, 16:00:25Z |

PR #119 was open and ready for review (not draft) at that time-qualified snapshot; it has since been
**merged** into the epic branch by the regular merge commit `f56d225` on 2026-11-14. Phase 2D PASS
and the staging evidence rest on the tested code/test snapshot `4ff8167` and the fully
green `a4bc1ae` snapshot; `8c20a1f` is documentation-only. The recorded CI snapshot applies to
`8c20a1f`; the later merged head `0b365aa` is recorded in the next subsection, and live GitHub is
authoritative for any newer head.

#### Phase 2D merged-head CI snapshot — commit 0b365aa (2026-11-14)

`0b365aa` (`Finalized Phase 2D gate record`, documentation-only) was the final PR #119 head and is the
second parent of the regular merge commit `f56d225`. Live GitHub check runs for `0b365aa` were read
after the merge and all four had completed successfully on 2026-11-14:

| Check | Result |
| --- | --- |
| Check Code Quality | **success**, 16:14:50Z |
| SonarCloud Code Analysis | **success**, 16:17:23Z |
| Build and analyze | **success**, 16:17:25Z |
| SonarCloud | **success**, 16:17:29Z |

These figures are the live GitHub check runs for `0b365aa`, the exact head that was merged; they are
a dated CI record, not a standing guarantee for any later epic head. No Sonar report figures are
re-read for this documentation-only head, so they are not restated here. PR #119 merged at
**2026-11-14 16:38:21Z**.

#### 2D acceptance and gate — PASS — gate closed (2026-11-14)

All Phase 2D acceptance criteria have recorded evidence; the gate is **closed**.

1. **Automated tests/CI.** The **Test Agent has added/moved** the neutral core locale boundaries,
   JSON legacy compatibility and raw values, catalog drift and JDA/Kord tag mapping,
   guild-default/persisted/user-locale separation, per-key English fallback, `/locale`
   success/reset/invalid and persist-before-confirmation behaviour, and guild-scoped
   registration/admin-only options tests. Final local QA on **2026-11-14** recorded a **clean full
   build passing** and **758 test cases: 753 executed, 5 skipped (pre-existing), 0 failures/errors**.
   Module breakdown: root **366** (**361 executed + 5 skipped**), `:core` **134**,
   `:locale-persistence` **21** and `:jda-runtime` **237**. The local JaCoCo evidence was measured on
   the **committed code/test snapshot `4ff8167` (parent `1304aa6`) on
   `feature/114-phase-2d-locale-edge-adapters`**; no source/test changes have occurred since that
   snapshot, and later documentation-only updates do not change it. Verified module-local line counts
   and report paths:
   - root `build/reports/jacoco/test-results/jacocoTestReport.xml` — **1657/2866 (57.82%)**
   - `:core` `core/build/reports/jacoco/test/jacocoTestReport.xml` — **389/419 (92.84%)**
   - `:locale-persistence` `locale-persistence/build/reports/jacoco/test/jacocoTestReport.xml` —
     **60/67 (89.55%)**
   - `:jda-runtime` `jda-runtime/build/reports/jacoco/test/jacocoTestReport.xml` —
     **919/997 (92.18%)**

   **Working-tree input manifest** (not an immutable Git commit): SHA-256
   `0815c66bba1cc70fe75d032f959a3924bfbeeb3e93c9a8b1c3f57ff17d49b30d` over **55** paths. Algorithm:
   from Phase 2D base `1304aa6`, collect tracked modified/deleted paths and untracked non-ignored
   paths (`git diff --no-renames --name-only 1304aa6` plus
   `git ls-files --others --exclude-standard`), exclude only this migration document
   (`docs/migrations/kord-to-jda.md`) and ignored/generated build outputs, sort repository-relative
   paths lexicographically, form each row as `path<NUL>sha256(file-bytes)` or `path<NUL>DELETED`,
   where each per-file SHA-256 digest is encoded as **64-character lowercase hexadecimal ASCII**
   (not raw digest bytes); concatenate the rows with no extra separators, and SHA-256 the
   concatenated bytes, encoding the final manifest digest as **64-character lowercase hexadecimal
   ASCII**. Later document edits and build outputs therefore do not change this digest.

   These are **local JaCoCo report readings only, not Sonar or GitHub CI results**. The local ktlint
   check scoped to changed files reports **0 findings**; the repo-wide CLI reports **21 pre-existing
   findings in untouched files**, which PR CI's added-line mode filters. The `a4bc1ae`
   time-qualified green snapshot has all four checks green (Check Code Quality, SonarCloud Code
   Analysis, Build and analyze, SonarCloud), and the Sonar Quality Gate is OK: new-code coverage
   **91.5%**, new duplication **0.0%**, hotspots reviewed **100%**, 0 bugs/vulnerabilities/security
   hotspots, and **1 new MAJOR maintainability code smell** `kotlin:S6524` in
   `core/.../LocalizationCatalog.kt`, disclosed as tracked follow-up debt under the configured gate
   criterion. At the recorded 2026-11-14 snapshot, live head `8c20a1f` was fully green (all four
   checks); see the PR-head CI snapshot recorded 2026-11-14 above. The final merged head `0b365aa`
   also had all four checks green — see the merged-head CI snapshot above.
2. **User-run staging — PASS (2026-11-14).** Sanitized, user-reported evidence is recorded in the
   Phase 2D staging evidence subsection: a translated guild locale yielded a localized confirmation
   and persisted after restart; `/locale reset` returned to the Discord guild default and stayed
   reset after restart; changing the Discord client language did not mutate the guild default;
   selecting canonical, untranslated `ar` produced the English fallback; and the staging guild was
   restored to default afterward.
3. **CI/quality gate — PASS.** The `a4bc1ae` time-qualified green snapshot has all four checks green and the
   configured Sonar Quality Gate is OK; the disclosed `kotlin:S6524` finding is tracked follow-up
   debt, not a failed gate. The gate snapshot is time-qualified to `a4bc1ae`; later documentation-only
   changes require fresh checks, and the final merged head `0b365aa` was separately checked and is
   green (2026-11-14). **Phase 2D is PASS — gate closed (2026-11-14)**.

The Phase 2D gate evidence is recorded above. At 2D closure on 2026-11-14, broader Phase 2 remained
open — 2D does **not** close it — and Phase 2 subsequently closed **PASS — gate closed (2026-11-15)**
after the scoped import rule passed CI and merged. The root Kord
runtime's localization behaviour is not migrated in 2D — it is part of the **Phase 6** root code/build switch.
The 2C/2D PASS records, the Phase 6 root code/build switch and artifact gates, and issue #114 remain governed by
their own gates.

#### Phase 2D staging evidence — PASS (2026-11-14)

This is **user-reported, sanitized evidence**. No guild/channel/user/application identifiers, tokens,
raw logs, or platform details are recorded. No specific translated locale code is claimed beyond the
explicitly reported `ar` test. The evidence applies to the JDA code on PR #119. The green
`a4bc1ae` docs-only snapshot differs from tested implementation snapshot `4ff8167` only by
documentation; the later live-head snapshot is `8c20a1f` as of its recorded check time, and PR #119
is now merged at `f56d225`.

| Check | Recorded result |
| --- | --- |
| Translated guild locale | Selecting a translated guild locale produced a localized confirmation |
| Persistence | The selected locale remained after restarting the runtime |
| Reset | `/locale reset` returned to the Discord guild default |
| Reset persistence | The reset state persisted across restart |
| Client locale separation | Changing the Discord client language did not mutate the guild default |
| Untranslated fallback | Selecting canonical, selectable `ar` (no Arabic translation resource) produced the English fallback |
| Cleanup | The staging guild was restored to the Discord default after the `ar` test |

The evidence covers the user-run set/localize/reset/restart gate and the client-locale separation and
untranslated-fallback checks. It does not claim a specific translated tag beyond the reported `ar`
test, and it does not validate the root runtime, Phase 6 artifacts, or any receive-audio path.

### Checkpoint state

- The epic integration branch `feature/114-migrate-to-jda-jda-ktx` contains
  `5fa9c63bc85ad3d5df1ed9c43c05f3e9b7d72e85`, the **regular merge commit of PR #118** whose parents are
  `255893abcef31554e13ae1713086a9b7c1217c60` (the regular merge commit of PR #117) and
  `3e50248446a5ce65ea2a1f648576523fb5b25c6a` (the final Phase 2C PR head), so that merge carries
  both the Phase 2B and Phase 2C passes. The Phase 2C staging evidence was then recorded by the
  documentation-only commit `269acc1`, which was the epic tip when the earlier documentation update
  was written; that statement is **historical**. The Phase 2D child branch was based on `1304aa6`;
  its tested implementation snapshot `4ff8167` and the `a4bc1ae` time-qualified green snapshot were pushed;
  PR #119 was **merged** into the epic branch by the regular merge commit `f56d225` on 2026-11-14
  (head `0b365aa`, base `1304aa6`), and the epic branch advanced to `f56d225` at that recorded
  snapshot. **`f56d225` was the epic tip before the earlier documentation update**, and the later
  documentation-only `38bdba1` (`Recorded Phase 2D merge and next steps`) was the epic tip **before
  this documentation-only edit**. **The live epic tip is read from fetched Git/GitHub** rather than
  from any SHA recorded here. The earlier #117 merge had parents `c00bdf1`
  (squash merge of PR #116 on top of `5aad7c7`, itself the squash merge of PR #115 on base `4badb92`)
  and `7d8661d` (the Phase 2B head), with a tree identical to `7d8661d`. PRs #115, #116, #117, #118 and #119
  are all **closed/merged**, and the remote `feature/114-kord-to-jda-migration` branch was deleted (a stale local ref may remain).
- Phase 1B sat on `feature/114-native-failfast-check` as PR #116, which had been retargeted to the
  **epic branch** at `5aad7c7` because the former parent branch was squash-merged and its remote ref was deleted (a stale local ref may remain). It
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
- **Phase 2C is PASS — gate closed (2026-11-14)** and is now **merged** into the epic branch. It ran on
  `feature/114-phase-2c-voice-ports`, created from the fetched epic head `255893a` — the correct base,
  not the stale local `4badb92` (that is `master`) and not the old Phase 2B branch. Its first pushed
  commit was **plan-only** (this document); the neutral ports, JDA adapters, DAVE 0.2.0 wiring, tone
  resource, unit tests and formatting-only ktlint repairs were pushed on head
  `8eb0349c4f65997d0dd69ffdc2b245268c207074`, the docs-only `9fd1826` followed, and the final PR head
  was `3e50248`. PR #118 was **merged** into the epic branch by the regular merge commit `5fa9c63`
  (parents `255893a` and `3e50248`), so **no open Phase 2C PR remains**. All four checks succeeded on
  `8eb0349`, on `9fd1826` and on `3e50248` — Check Code Quality, Build and analyze, SonarCloud Code
  Analysis and SonarCloud — and the Sonar Quality Gate passed with 90.1% new-code coverage, 11 new
  issues (10 major, 1 critical), 0 security hotspots and 0.0% duplication; those 11 issues are recorded
  as-is and carried forward as **tracked follow-up debt** under the owner-approved 2026-11-14
  interpretation (the configured Quality Gate is the criterion), not as zero. The earlier `c893567`
  failed only Check Code
  Quality, on 25 formatting findings. Latest local results on the formatted tree: 685 cases (`:core` 81
  + `:jda-runtime` 224 + root 380 with 5 pre-existing skips = 680 executed + 5 skips, 0 failures/errors;
  local module JaCoCo line coverage `:core` 207/219 = 94.52% (branch 108/115 = 93.91%) and
  `:jda-runtime` 870/948 = 91.77% (branch 342/418 = 81.82%) — local module reports only, not
  Sonar/new-code CI evidence; the clean full `./gradlew build` passed locally). The gate was closed by
  the **user-run staging evidence recorded on 2026-11-14** in the Phase 2C section: local macOS arm64,
  DAVE 0.2.0 native loaded with protocol version 1, JDA login/WebSocket/ready, guild-scoped
  `/jda-walkthrough` and `/jda-voice-walkthrough` registered with only `GUILD_VOICE_STATES`,
  `/jda-voice-walkthrough` connected to the invoking user's regular voice channel and replied
  `Voice walkthrough OK | handed 75/75 frames to JDA's send loop (delivery not confirmed)` — a
  hand-off count only, not delivery proof — then the invoking user's own Discord
  client — separate from the bot — reported hearing the beep (the delivery evidence, distinct from the
  hand-off), completion-path disconnect cleanup, and a deliberate Ctrl+C stop. No
  receive-audio, no root wiring and no Phase 6 artifact validation were exercised. `master` is
  untouched.
- The PR #117 conflict with the epic branch was resolved by a **normal ancestry merge** of `c00bdf1`
  into `feature/114-phase-2b-command-port`. Only `docs/migrations/kord-to-jda.md` conflicted; the
  Phase 2B document was kept, and every non-doc path's merged tree equals the prior Phase 2B head
  `e61f301`. **No code changed** in that merge — only this current-stack/handoff record.
- `.opencode/opencode.json`, this plan and `spikes/jda-dave/**` are **tracked** in the epic branch
  history: `8ca6242` recorded them on the former working branch, `5aad7c7` brought them into the
  epic branch and `c00bdf1` carried them forward with the #116 squash merge. They are part of the
  migration record and are updated as the migration proceeds.
- This plan is maintained on the epic branch `feature/114-migrate-to-jda-jda-ktx`: `5fa9c63` is the
  PR #118 merge commit that brought the Phase 2C plan into it, and the documentation-only `269acc1`
  then recorded the Phase 2C staging pass — it was the epic tip when that earlier documentation update
  was written, which is **historical**. The Phase 2D child branch was based on the later epic
  tip `1304aa6`; its tested implementation snapshot `4ff8167` and the `a4bc1ae` time-qualified green snapshot were
  pushed, and PR #119 is **merged** into the epic branch by the regular merge commit `f56d225` on
  2026-11-14 (head `0b365aa`, base `1304aa6`), so the epic branch advanced to `f56d225` at that
  recorded snapshot. **`f56d225` was the epic tip before the earlier documentation update**; the
  later documentation-only `38bdba1` was the epic tip before this documentation-only edit; the live
  tip is read from Git and the pull requests rather than asserted here.
  The earlier epic head `255893a` contained the Phase 2B version of this document through the #117
  merge.
- PR #115 was the **agreed combined Phase 1/2A checkpoint** and is **not restructured**; it was
  squash-merged as `5aad7c7`. PR #116 was then squash-merged as `c00bdf1`, PR #117 was merged as the
  regular merge commit `255893a`, PR #118 as `5fa9c63`, and PR #119 as `f56d225`. **Historical
  exceptions:** those two squash merges (#115/#116) are recorded as history, not precedent. The
  current **stacking policy (owner decision 2026-11-14)** is: fetch and verify the **live epic tip**,
  branch **from that tip**, open the PR **against the epic integration branch**, and land with a
  **regular merge commit** (no squash, no rebase). 2C and 2D already followed the branch-from-live-tip
  part of that rule. The epic branch remains the **final integration target**; `master` is merged
  only at migration completion.
- No secrets, bot/guild/channel/user identifiers, tokens or raw logs are recorded in this plan or
  in the spike.

### Handoff snapshot — 2026-11-08 (historical, superseded by 2026-11-15)

> Historical snapshot only. It accurately reflected the state on 2026-11-08, when Phase 2B was
> **IN PROGRESS** and staging was pending. It is retained for the record and is **superseded** by
> the post-merge 2026-11-15 handoff snapshot below. The branch/PR posture recorded here is itself
> out of date: `feature/114-kord-to-jda-migration` was later squash-merged by #115 and its remote ref deleted (a stale local ref may remain), and
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
> 2A branch. It is retained for the record and is **superseded** by the **2026-11-15 post-merge**
> snapshot below.

Branch, commit and PR facts below were read from Git and GitHub; they are **not** gate evidence
beyond what is recorded above.

| Branch / PR | Handoff state |
| --- | --- |
| `feature/114-migrate-to-jda-jda-ktx` (epic baseline) | Was at `4badb92` at that time; it has since advanced to `5aad7c7`, the squash merge of PR #115 |
| `feature/114-kord-to-jda-migration` → draft PR #115 (base: epic) | Carried the combined Phase 1/2A checkpoint: the plan/spike record and the neutral locale repository/use-case boundary. Phase 1 is **PASS** (1A + 1B recorded), Phase 2A is **partial/IN PROGRESS** *(as at 2026-11-13; superseded by the owner scope decision of 2026-11-14, which closed 2A as PASS for its delivered scope)*, Phase 2B is **PASS on its child branch**. PR #115 has since been **squash-merged** as `5aad7c7` and this branch's remote ref **deleted** (a stale local ref may remain); the merge did not complete Phase 2 then — Phase 2 subsequently closed on 2026-11-15 |
| `feature/114-native-failfast-check` → draft PR #116 (base: the 2A feature branch at that time, since retargeted to the epic branch) | Artifact, runbook and the pass record live here. **Phase 1B PASS — gate closed on 2026-11-08**. Read the actual head from Git and PR #116 |
| `feature/114-phase-2b-command-port` → draft PR #117 (base: the Phase 1B branch, PR #116) | Latest verified all-green CI checkpoint before the subsequent docs-only updates: `7bf5419` (`Corrected Phase 2B CI status record`, docs-only), where all four checks passed on 2026-11-13 — Check Code Quality 16:47:15Z, SonarCloud Code Analysis 16:48:52Z, Build and analyze 16:48:56Z, SonarCloud 16:48:58Z — with 75.0% new-code coverage, 0 new issues and 0 hotspots; it was the head at that point, not afterwards. The earlier docs-only `988a7cd` is also green (all four checks, last completed 16:38:44Z), and `e4a9e29` completed all four checks successfully after an in-progress observation at 16:41:06Z. A later dated snapshot of `f5f04d1` (docs-only) was **not green** — only Check Code Quality had concluded at 16:53:24Z, with Build and analyze running and the SonarCloud checks unlisted. These are dated checkpoints only; the current head is whatever this follow-up produced, so GitHub's live check runs on PR #117 are authoritative for it. Carries the isolated `:core`/`:jda-runtime` modules, the neutral command boundary, the JDA 6.7.0 adapter and the guild-only `/jda-walkthrough`, with unit tests, plus JaCoCo 0.8.12 coverage for both modules wired into root `sonar`. **Phase 2B PASS — gate closed 2026-11-13**: all four #117 checks passed on `7bf5419`, `e4a9e29` and `988a7cd` (and earlier on `cd9d6db`, `e607ff7`, `2850907`, `16247f8`, `d353113`; 75.0% new-code coverage, 0 new issues, 0 hotspots), local QA and reviewer agents satisfied the independent review criterion, and the user-run staging `/jda-walkthrough` interaction succeeded. No GitHub PR review is submitted; that is not itself a required gate. **Historical instruction at that date: keep #117 draft and do not merge it ahead of parent PR #116.** It was later marked ready and **merged** into the epic branch by the regular merge commit `255893a` (Phase 2B PASS) |

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
- **Gate posture (at that date).** Phase 2B is **PASS / closed** on 2026-11-13. Broader **Phase 2
  remained IN PROGRESS then**: 2C and 2D were **OPEN**, root Kord removal and bootstrap wiring were not
  done, and the Phase 2 exit criteria (including the architectural import rule) were still open. Issue
  #114 stays **open**. 2C has since closed on **2026-11-14**, and **Phase 2D is PASS — gate closed
  (2026-11-14)** on the `a4bc1ae` time-qualified green snapshot and the sanitized staging evidence;
  PR #119 is **merged** into the epic branch by the regular merge commit `f56d225` (2026-11-14). At that
  date the root wiring, the import rule and Phase 6 all remained open. *(Superseded on 2026-11-14: 2C/2D
  are PASS, and the **scoped import-rule** gate was the last Phase 2 item; it closed on **2026-11-15**
  through PR #120, so Phase 2 is PASS; the root code/build
  switch is Phase 6, and production deployment is Phase 7.)*
- **No root wiring.** The root application remains Kord-based and JDA-free; only the isolated
  `:jda-runtime` module touches JDA. Phase 2B does **not** mean JDA is wired into the root app.
- **Test counts.** **18 `:core` / 62 `:jda-runtime` / 380 root with 5 pre-existing skips = 460.**

Next actions at that time (**superseded** by the post-merge next actions below):

1. **Historical instruction (2026-11-13):** keep PR #117 draft and do not merge it ahead of parent
   PR #116; the coordinator updates the PR #117 body. Leave #115 as the combined Phase 1/2A
   checkpoint. This was later superseded: #117 was marked ready and **merged** into the epic branch at
   `255893a`, and #115 was squash-merged as `5aad7c7`.
2. Start Phase 2C (voice/session/player-message narrow ports) on a new child branch based on the
   Phase 2B branch head, then Phase 2D on a child of 2C.
3. Keep the root Kord removal, root bootstrap wiring, the architectural import rule and issue #114
   open until their own gates are satisfied.

### Handoff snapshot — 2026-11-15 post-merge (current)

Branch, commit and PR facts below were read from Git and GitHub, and the ancestry and tree-equality
statements were verified against the repository; they are **not** gate evidence beyond what is
recorded above. The #117, #118, #119 and #120 merges are recorded by commit SHAs and verified parents; no
live PR base SHA is asserted. This handoff update itself changes **only**
`docs/migrations/kord-to-jda.md` — no source, test, build or asset file and no PR metadata. The
earlier **2026-11-08** and **2026-11-13 pre-merge** historical snapshots are retained only
as history; they are **superseded** by this snapshot. Phase 2C, 2D and the scoped import-rule gate have
since closed **PASS** (2026-11-14 / 2026-11-15), so **Phase 2 is PASS — gate closed (2026-11-15)**. The
**owner decision of 2026-11-14** recorded in §1 and §3 is reflected here: root Kord
production is preserved through Phase 5, Phase 6 switches the root code/build and removes Kord, and Phase 7
deploys the exact Phase 6-verified artifact.

| Branch / PR | Handoff state |
| --- | --- |
| `feature/114-migrate-to-jda-jda-ktx` (epic baseline) | Contains **`5fa9c63bc85ad3d5df1ed9c43c05f3e9b7d72e85`**, the **regular merge commit of PR #118**. Parents: `255893abcef31554e13ae1713086a9b7c1217c60` (the regular merge commit of PR #117) and `3e50248446a5ce65ea2a1f648576523fb5b25c6a` (the final Phase 2C PR head), so that merge carries both the Phase 2B and Phase 2C passes. The Phase 2C staging pass was then recorded by the documentation-only `269acc1` (`Recorded Phase 2C staging pass and gate decision`); it was the epic tip when that earlier documentation update was written, which is **historical**. The Phase 2D child branch was based on the later epic tip `1304aa6`; its tested implementation snapshot `4ff8167` and the `a4bc1ae` time-qualified green snapshot were pushed, and PR #119 is **MERGED** into the epic branch by the regular merge commit `f56d2252506617ae282c50a3cc95fb830c644424`, whose parents are `1304aa6` (base) and `0b365aa` (final head). The merge was recorded on 2026-11-14 and the epic branch advanced to `f56d225` at that recorded snapshot; **`f56d225` was the epic tip before the earlier documentation update**, and the documentation-only `38bdba1` (`Recorded Phase 2D merge and next steps`) was followed by `955ac49` (`Clarified migration phase gates and adapter boundaries`). **PR #120 is MERGED** into the epic branch on **2026-11-15 08:03:45Z** by the regular merge commit `96e5fbbd7c3660b53414f4cc67033060749fcbe1`, whose parents are `955ac49` (base) and `66f7783` (scanner head); **the live epic tip before this documentation-only update was `96e5fbb`**, time-qualified — this doc-only edit makes no claim about the resulting head. **The live tip is read from fetched Git/GitHub**, and no SHA recorded here is asserted as the current head. The earlier #117 merge had parents `c00bdf1` (the **squash merge of PR #116** on top of `5aad7c7`, itself the **squash merge of PR #115** on base `4badb92`; `5aad7c7`'s tree equals the former Phase 2A head `67802d1`) and `7d8661d` (the Phase 2B head), with a merge tree **identical** to `7d8661d` |
| PR #115 (former `feature/114-kord-to-jda-migration` → epic) | **CLOSED / MERGED by squash** as `5aad7c7`; the remote branch `feature/114-kord-to-jda-migration` was **deleted**. Phase 2A is **PASS — gate closed (owner scope decision 2026-11-14)** for the neutral-value/locale-persistence scope it delivered; it did not close Phase 2 at that time (Phase 2 subsequently closed on 2026-11-15) and does not perform the Phase 6 root code/build switch |
| `feature/114-native-failfast-check` → PR #116 (base: epic) | Head **`1b753b9`**, an **ancestry-only merge** committing `5aad7c7` into `49b4bc1`. Four add/add conflicts — `docs/migrations/kord-to-jda.md`, `spikes/jda-dave/.gitignore`, `spikes/jda-dave/README.md`, `spikes/jda-dave/build.gradle.kts` — were resolved by retaining the Phase 1B versions, so the resolved tree is **byte-identical** to `49b4bc1` and **no content changed**. All four checks on `1b753b9` passed on 2026-11-13 — Check Code Quality 17:20:31Z, SonarCloud Code Analysis 17:22:32Z, Build and analyze 17:22:35Z, SonarCloud 17:22:40Z. **Phase 1B PASS — gate closed 2026-11-08.** PR #116 is **CLOSED / MERGED by squash** into the epic branch as `c00bdf1` |
| `feature/114-phase-2b-command-port` → PR #117 (base: epic `feature/114-migrate-to-jda-jda-ktx`) | **Phase 2B PASS — gate closed 2026-11-13**, unaffected by the #116 merge. Carried the isolated `:core`/`:jda-runtime` modules, the neutral command boundary, the JDA 6.7.0 adapter and the guild-only `/jda-walkthrough`, with unit tests, plus JaCoCo 0.8.12 coverage for both modules wired into root `sonar`. CI history is recorded in the Phase 2B section; those are dated checkpoints, not a statement about any later head. After #116 merged, the base was **auto-retargeted to the epic branch**; the PR was later **CLOSED / MERGED** into the epic branch by the regular merge commit `255893a`. Its one conflict — this plan — had been resolved earlier by a **normal ancestry merge** of `c00bdf1` that kept the Phase 2B document; every non-doc path's tree equalled `e61f301` and **no code changed** |
| `feature/114-phase-2c-voice-ports` → PR #118 (base: epic) | **PASS — gate closed (2026-11-14); CLOSED / MERGED.** Created from the fetched epic head `255893a` — the correct 2C base, not the stale local `4badb92` (`master`) and not the old Phase 2B branch. The plan, neutral voice/audio/player-message ports, their JDA adapters, DAVE 0.2.0 wiring, tone resource, unit tests and formatting-only ktlint repairs were merged into the epic branch by the regular merge commit `5fa9c63` (parents `255893a` and PR head `3e50248`); the implementation head is `8eb0349c4f65997d0dd69ffdc2b245268c207074` (`Fixed ktlint violations in Phase 2C`). All four checks on `8eb0349` **succeeded** — Check Code Quality, Build and analyze, SonarCloud Code Analysis and SonarCloud — and the Sonar Quality Gate **passed** with 90.1% new-code coverage, 11 new issues (10 major, 1 critical), 0 security hotspots and 0.0% duplication. Under the **owner-approved 2026-11-14 interpretation** the repository's configured Quality Gate is the criterion, so those 11 maintainability issues are **tracked follow-up debt**, not a failed gate. The earlier `c893567` is kept as history: **Check Code Quality FAILED** there on 25 formatting findings while the other three checks succeeded with the same Sonar figures; the formatting-only repairs were committed and pushed as `8eb0349`. The docs-only `9fd1826` (`Recorded Phase 2C CI checkpoint`) and the final PR head `3e50248` (`Recorded passing Phase 2C CI checkpoint`) also passed all four checks on 2026-11-14. Local results on the formatted tree remain local-only: 685 cases (680 executed + 5 pre-existing skips, 0 failures/errors) all passing; local module JaCoCo line coverage `:core` 207/219 = 94.52% (branch 108/115 = 93.91%) and `:jda-runtime` 870/948 = 91.77% (branch 342/418 = 81.82%) — local module reports only, not Sonar/new-code CI evidence — and the clean full `./gradlew build` passed locally. Local QA/reviewer agents report PASS; that is internal review, not a GitHub review, and no human review is recorded here. The gate was closed by the **sanitized 2026-11-14 staging run recorded in the Phase 2C section** — local macOS arm64 with the DAVE 0.2.0 native loaded and protocol version 1, only `GUILD_VOICE_STATES`, guild-scoped `/jda-walkthrough` and `/jda-voice-walkthrough`, voice connect to the invoking user's channel, the Phase 2C reply `Voice walkthrough OK \| handed 75/75 frames to JDA's send loop (delivery not confirmed)` (a hand-off count, not delivery proof), an audible beep reported by the invoking user's own Discord client, separate from the bot (the delivery evidence, distinct from the hand-off), completion-path disconnect cleanup and a deliberate Ctrl+C stop; no receive path, root wiring or Phase 6 artifact was exercised. `master` is untouched |
| `feature/114-phase-2d-locale-edge-adapters` → PR #119 (base: epic) | **PASS — gate closed (2026-11-14); CLOSED / MERGED.** Created from the fetched epic tip `1304aa6`; the tested code/test snapshot `4ff8167`, the `a4bc1ae` time-qualified green snapshot, the docs-only `8c20a1f` and the final head `0b365aa` (`Finalized Phase 2D gate record`) were pushed. PR #119 is **merged** into the epic branch by the regular merge commit `f56d225` (parents `1304aa6` and `0b365aa`; merge recorded 2026-11-14 16:38:21Z). All four checks on `0b365aa` were green on 2026-11-14 — Check Code Quality 16:14:50Z, SonarCloud Code Analysis 16:17:23Z, Build and analyze 16:17:25Z, SonarCloud 16:17:29Z; the `a4bc1ae` snapshot's Sonar Quality Gate was OK with 91.5% new-code coverage, 0.0% duplication, 100% hotspots reviewed and **1 new MAJOR `kotlin:S6524`** code smell recorded as tracked follow-up debt. Final local QA: **758 cases (753 executed + 5 pre-existing skips), 0 failures/errors**, with local changed-file ktlint **0 findings**. The remote 2D branch was **deleted** after the merge; a local ref remains at `0b365aa`. The root runtime stays Kord-based through Phase 5 and the root code/build switch (Phase 6) and production deployment (Phase 7) remain **OPEN** |
| `feature/114-import-declaration-scanner` → PR #120 (base: epic) | **Phase 2 exit criterion 4 — scoped import-rule gate: PASS — gate closed (2026-11-15); CLOSED / MERGED.** Branch created from the verified live epic tip `955ac49` (`Clarified migration phase gates and adapter boundaries`), not from `master`. Head `66f7783` (`Added Phase 2 import boundary scanner`, one commit, 8 files, +2460/−31) carried the **test-source-only** root JUnit scanner (`src/test/kotlin/architecture/`: `ImportBoundaryConfig`, `ImportBoundaryPolicy`, `KotlinFileHeaderParser`, `SourceTreeScanner` plus three test classes) and the plan update; it changed **no production source, dependency, build script or CI workflow**, and needed **no new Gradle task**. PR #120 is **MERGED** into the epic branch by the **regular merge commit** `96e5fbb` on **2026-11-15 08:03:45Z** (parents `955ac49` + `66f7783`; not a squash or rebase). All four checks on `66f7783` succeeded — Check Code Quality **2026-11-14 21:52:37Z**, SonarCloud Code Analysis **21:56:59Z**, Build and analyze **21:57:10Z**, SonarCloud **21:57:06Z**; no Sonar issue-count or coverage figure is asserted here for this PR. Local evidence (pre-merge working tree, historical): **949 cases = 944 executed + 5 pre-existing skips, 0 failures/errors** (root 557, `:core` 134, `:locale-persistence` 21, `:jda-runtime` 237), **191** architecture tests passing (policy 148, parser 21, source-tree 22), local ktlint **1.8.0** **0 findings** on the changed Kotlin test files, and because it is tests-only no production coverage change is claimed. The remote scanner branch was deleted after the merge; a local ref remains at `66f7783` |

- **Phase 2B staging evidence** remains the sanitized 2026-11-13 record in the Phase 2B section:
  isolated `:jda-runtime` login, ready state, guild-scoped `/jda-walkthrough` registration, the
  `JDA walkthrough OK` reply with the non-Community guild default `locale=en-US`, no intents enabled,
  no global command, and a deliberate Ctrl+C shutdown. No token, raw log or identifier is recorded.
- **Gate posture.** Phase 2B and Phase 2C are **PASS / closed**; **Phase 2D is PASS — gate closed
  (2026-11-14)** on the `a4bc1ae` time-qualified green snapshot and the sanitized staging/restart evidence;
  PR #119 is **merged** into the epic branch by the regular merge commit `f56d225` (2026-11-14;
  head `0b365aa`, base `1304aa6`), and the recorded final head `0b365aa` had all four checks green.
  **Phase 2 is PASS — gate closed (2026-11-15)**: 2A–2D are **PASS** with their isolated-runtime
  evidence recorded, and the scoped import-rule CI gate closed with **PR #120**, merged on 2026-11-15 by
  the regular merge commit `96e5fbb` (head `66f7783`, base `955ac49`), all four checks green. The
  scanner's earlier **local test-source working-tree snapshot taken 2026-11-14 on base `955ac49`,
  before the branch was committed or pushed** — with passing local tests but no PR/CI verdict — is
  **historical evidence only**, not a current state. **Phase 6**
  packaging/artifact validation is still OPEN. The root gateway/runtime is **not cut over** through
  Phase 5; the root code/build switch, Kord removal and the repo-wide import rule belong to **Phase 6**; Phase 7 deploys the artifact.
  **Phase 2A is PASS — gate closed (owner scope decision 2026-11-14)** for its delivered
  neutral-value/locale-persistence scope, so 2A never was what kept Phase 2 open. The 2C acceptance
  criteria, scope limits and sanitized staging evidence are recorded in the Phase 2C section; the
  recorded Sonar result is 90.1% new-code coverage, 11 new issues (10 major, 1 critical), 0 hotspots
  and 0.0% duplication, and those 11 maintainability issues remain recorded **tracked follow-up
  debt** — under the **owner-approved 2026-11-14 interpretation** the repository's configured Quality
  Gate is the criterion, so they are not a failed gate, not zero and not silently waived.
  Local results on the formatted tree are 685 cases (680 executed + 5 pre-existing skips, 0
  failures/errors; local module JaCoCo line coverage `:core` 207/219 = 94.52% with branch 108/115 =
  93.91%, `:jda-runtime` 870/948 = 91.77% with branch 342/418 = 81.82% — local module reports only,
  not Sonar/new-code CI evidence). Root Kord removal, root bootstrap wiring and the root code/build switch are
  **Phase 6** work and are not done; the scoped import-rule CI gate is **closed (2026-11-15)**, so Phase 2
  has no open gate, while issue #114 stays **open** for Phases 3–7.
- **No root JDA wiring.** The root application remains Kord-based and JDA-free **through Phase 5**;
  only the isolated `:jda-runtime` module touches JDA. 2C kept the root Kord runtime, root dependency
  graph and `fatJar` untouched; 2D later added the shared `:locale-persistence` module dependency to
  the root project and refactored the root Kord locale service while keeping Kord as the root gateway.
- **Test counts.** Phase 2B's recorded baseline is **18 `:core` / 62 `:jda-runtime` / 380 root with 5
  pre-existing skips = 460** (kept in the Phase 2B coverage section). The earlier **local 2C**
  results are higher: `:core` **81** (0 skips), `:jda-runtime` **224** (0 skips) and root **380**
  (5 pre-existing skips) = **685 cases, 680 executed + 5 skips, all passing with 0
  failures/errors**; those and the **758** 2D figure (753 executed + 5 skips) remain **historical**
  local snapshots of their own worktrees. The **local import-scanner** snapshot is the **local
  test-source working-tree snapshot taken on 2026-11-14, based on `955ac49`, before the scanner branch
  was committed or pushed** — **historical, pre-merge evidence**; it recorded **949 cases =
  944 executed + 5 pre-existing skips, 0 failures/errors**: root **557** (552 executed + 5 skips),
  `:core` **134**, `:locale-persistence` **21** and `:jda-runtime` **237**, with the **191**
  architecture scanner tests (ImportBoundaryPolicy **148**, KotlinFileHeaderParser **21**,
  SourceTreeScanner **22**) all passing; local ktlint **1.8.0** reported **0 findings** on the 7
  changed Kotlin test files. That snapshot predates the PR; **PR #120 has since merged (2026-11-15)**
  and its four CI checks are green on head `66f7783`, so the snapshot's "no PR/CI" state is dated
  2026-11-14 history, not a current claim. The change is tests-only, so production coverage is
  unchanged and **no new coverage percentage is claimed** for it. The 2C
  source, tests, tone resource and formatting repairs were merged at `5fa9c63` (heads `8eb0349` and
  `3e50248`); all four
  checks are green on the recorded heads (dated historical checkpoints, so read the live GitHub
  checks). The 11 Sonar new issues remain recorded as **tracked follow-up debt** under the
  owner-approved 2026-11-14 interpretation (the configured Quality Gate is the criterion), and the
  user-run staging gate is closed by the sanitized 2026-11-14 record above.

Next actions, in order:

1. **Phase 2 closed — PASS (2026-11-15).** **PR #120 is MERGED** into the epic branch by the regular
   merge commit `96e5fbb` on 2026-11-15 (head `66f7783`, base `955ac49`, parents `955ac49` + `66f7783`),
   closing the scoped import-rule CI gate; its four checks are green. 2A–2D were already **PASS**. The
   former placement conflict stays **settled (owner decision 2026-11-14)**: root production stays
   **Kord through Phase 5**; **Phase 6** performs the root code/build switch and Kord removal; **Phase 7**
   deploys the exact Phase 6-verified artifact. **No Phase 2E is defined**, and none should be invented.
   The owner-approved scanner decision stands: the custom, **no-dependency** Phase 2 gate is now
   **passed**, and **Phase 6 extends the same scanner repo-wide on the Kord-free tree**.
2. **Phase 3 is IN PROGRESS — implementation landed locally; the acceptance gate remains open (not
   PASS).** Commit/push
   `feature/114-phase-3-player-engine` and open a **stacked Phase 3 child PR** against the epic branch.
   Then wait for CI/Sonar and run the Phase 3 staging matrix. The local 2026-11-15 snapshot
   (**1,024 cases, 1,020 executed + 4 pre-existing skips, 0 failures/errors**) is working-tree evidence
   only, not CI or staging; its per-module counts, 42-task build and changed-file ktlint result are
   recorded in full in the Phase 3 local verification snapshot, the authoritative detailed breakdown
   (summaries elsewhere may repeat the headline totals). Pause/resume,
   `:player` placement and the continuous-stream contract are already decided; no Phase 3 design
   decision is unresolved.
3. **Phase 3 gate items before exit:** real player/source providers; per-guild ordering; Kord-parity
   pause/resume including paused silence and `provide(0)` keepalive; reconnect/disconnect; no blocking
   send path; root Kord regression tests. Receive-audio stays out of scope. Phase 4 starts only after
   Phase 3 exit.
4. **Phase 4** starts after Phase 3 and ports the remaining command groups and component IDs into
   neutral `:core` contracts plus JDA adapters in the isolated `:jda-runtime`; root Kord stays
   production. **Phase 5** (after Phases 3–4) tests and stages the full isolated JDA implementation,
   **retaining the root Kord app and its Kord-root tests** until Phase 6 — it is **not** the root
   production cutover.
5. Record explicit per-issue dispositions for the **11 Phase 2C Sonar new issues** (10 major, 1
   critical-severity code smell) plus the **single Phase 2D `kotlin:S6524`** MAJOR maintainability
   code smell. The Phase 2D finding is **SCM auto-assigned to Wikijito7** but has **no explicit
   disposition recorded**; do not describe it as unassigned or silently waived. All remain
   **tracked follow-up debt** under the owner-approved 2026-11-14 interpretation: the repository's
   configured Quality Gate is the criterion and it passed, so they do not keep 2C/2D open, but they
   must not be described as zero or dropped. Individual dispositions are not recorded in this
   document yet.
6. Keep the **root gateway/runtime on Kord through Phase 5** — no root code/build switch and no Kord removal before
   Phase 6. Keep Phase 6 packaging/artifact validation and issue **#114 open** until their own gates
   are satisfied; no root wiring, receive-audio or Phase 6 artifact validation is claimed by the 2C or
   2D pass.
7. For every future phase/subphase: fetch and verify the **live epic tip**, branch from it, PR
   against the epic integration branch, and land with a **regular merge commit**.
8. **Detekt sequencing (user instruction 2026-11-15):** after the Phase 3 PR is created, migrate
   from ktlint to Detekt in a **separate tooling PR** and remove ktlint there. Do not bundle the
   Detekt migration into Phase 3; Phase 3's existing ktlint CI gate must still pass. No Detekt
   dependency or workflow change is part of Phase 3. The import scanner is **not** Detekt and must
   not pull it in.

---

## 1. Context and settled decisions

### Baseline

| Item | State |
| --- | --- |
| Baseline commit | Clean `master` @ `4badb92` |
| Epic integration branch | `feature/114-migrate-to-jda-jda-ktx`, based on `master` @ `4badb92`; it contains `5fa9c63bc85ad3d5df1ed9c43c05f3e9b7d72e85`, the regular merge commit of PR #118 whose parents are `255893a` (the regular merge commit of PR #117, whose parents are `c00bdf1` — squash merge of PR #116 on top of `5aad7c7`, the squash merge of PR #115 — and `7d8661d`, the Phase 2B head) and `3e50248` (the final Phase 2C PR head), plus the documentation-only `269acc1` that recorded the Phase 2C staging pass and was the epic tip when the earlier documentation update was written. That merge carries the Phase 2B and Phase 2C passes; the `269acc1` tip statement is **historical**. The Phase 2D child branch was based on `1304aa6`; its tested implementation snapshot `4ff8167` and the `a4bc1ae` time-qualified green snapshot were pushed, and PR #119 is **MERGED** into the epic branch by the regular merge commit `f56d2252506617ae282c50a3cc95fb830c644424` (parents `1304aa6` and `0b365aa`) on 2026-11-14, so the epic branch advanced to `f56d225` at that recorded snapshot; **`f56d225` was the epic tip before that earlier documentation update**. A later documentation-only commit `38bdba1` (`Recorded Phase 2D merge and next steps`) was followed by `955ac49f6e397d5d59198c5fa4c6e3e14e94c634` (`Clarified migration phase gates and adapter boundaries`), the base of the import-scanner child branch. **PR #120 is MERGED** into the epic branch on **2026-11-15 08:03:45Z** by the regular merge commit `96e5fbbd7c3660b53414f4cc67033060749fcbe1` (parents `955ac49` and `66f7783`), and the epic branch later advanced by the documentation-only `6d14167` (`Recorded Phase 2 import-rule closure`), which was the **verified live epic tip at Phase 3 branch creation** (the `96e5fbb` tip statement is historical); `master` remains `4badb92`. The live tip is read from fetched Git/GitHub, and no SHA recorded here is asserted as the current head |
| Working branch (current) | At the **time-qualified 2026-11-15 snapshot** the working branch is the **Phase 3 child branch** `feature/114-phase-3-player-engine`, created from the epic tip `6d14167` (`Recorded Phase 2 import-rule closure`), with uncommitted Phase 3 source, test, build and documentation working-tree changes; **at that snapshot it had not been pushed and no Phase 3 PR existed**. That is a dated record and does **not** assert that the child branch is the epic head. The **epic integration branch** `feature/114-migrate-to-jda-jda-ktx` had advanced to **`96e5fbbd7c3660b53414f4cc67033060749fcbe1`** (the regular merge commit of **PR #120**) and then to `6d14167`; `master` remains **`4badb92`** and is untouched. **PR #120 is MERGED** on **2026-11-15 08:03:45Z** (head `66f77831f9b1271f030969acd27933b7049f94ad`, base `955ac49f6e397d5d59198c5fa4c6e3e14e94c634`, parents `955ac49` + `66f7783`; regular merge, no squash/rebase); all four checks succeeded on `66f7783` (Check Code Quality 2026-11-14 21:52:37Z, SonarCloud Code Analysis 21:56:59Z, Build and analyze 21:57:10Z, SonarCloud 21:57:06Z). The scanner branch `feature/114-import-declaration-scanner` is preserved locally at `66f7783`; its remote ref was deleted by the merge. **Phase 2 is PASS — gate closed (2026-11-15)**: the scanner was **test-source-only** under `src/test/kotlin/architecture/` (no production source, dependency, build-script or CI-workflow change, no new Gradle task), and its earlier **local test-source working-tree snapshot of 2026-11-14 based on `955ac49`, before the branch was committed or pushed,** recorded **949 cases (944 executed + 5 pre-existing skips, 0 failures/errors)** with the **191** scanner tests all passing and local ktlint 1.8.0 at **0 findings** on the 7 changed Kotlin test files — that snapshot is **historical** (at it no PR/CI existed), since PR #120 later merged with green CI. **2D is PASS — gate closed (2026-11-14)**; PR #119 is **merged** by the regular merge commit `f56d225` (head `0b365aa`, base `1304aa6`), and the Phase 2D branch `feature/114-phase-2d-locale-edge-adapters` is preserved locally at `0b365aa` with its remote ref deleted after the merge. Final local 2D QA on 2026-11-14 recorded **758 test cases: 753 executed, 5 skipped (pre-existing), 0 failures/errors**, a clean local build, local module JaCoCo readings and local changed-file ktlint **0 findings**; the merged head `0b365aa` had all four checks green. 2C remains **PASS — gate closed (2026-11-14)** and merged into the epic branch by PR #118 at `5fa9c63`; no open 2C PR remains. The remote `feature/114-kord-to-jda-migration` branch was deleted (a stale local ref may remain); PRs #115, #116, #117, #118, #119 and #120 are merged. The root gateway/runtime stays Kord-based through Phase 5; the root code/build switch is Phase 6 and production deployment is Phase 7, both still open |
| Assistant receive-audio | Stays separately stashed; **out of scope** for this migration |
| Parked PRs | #106, #109, #111 remain **parked and untouched**; Phase 8 (post-Phase 7 follow-up) ports each individually from a new child branch off the verified live epic tip, without rebasing or rewriting the parked branches. Not a prerequisite for Phase 2, Phase 6 or issue #114 closure |
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
| Parked PRs | #106, #109, #111 stay **parked**, ported individually in Phase 8 after Phase 7 via new child branches from the verified live epic tip (reviewed cherry-pick or reimplementation, regular merge commits; no rebase/rewrite). Not a prerequisite for Phase 2, Phase 6 or issue #114 closure |
| Artifact | Linux x86_64 **fat JAR**; no `Dockerfile` is added to this repo |
| Staging | Separate **staging bot/token** and the **full staging matrix** before production |
| Concurrency | **Per-guild serialization** via a narrow actor/mailbox |
| jda-ktx | **Optional**; if 0.14.2 + JDA 6.7.0 fails, fall back to direct JDA + coroutine bridge |
| **Phase ownership (owner decision 2026-11-14)** | **Preserve root Kord through Phase 5; Phase 6 root code/build switch; Phase 7 production deployment.** Phases 2–5 build and prove an isolated JDA implementation while root production stays Kord. Phase 6 switches the root code/build, stages the JDA artifact and removes Kord; Phase 7 deploys the exact Phase 6-verified artifact |
| Root refactors before Phase 6 (owner decision 2026-11-14) | **No root gateway/runtime cutover (no root code/build switch that replaces Kord) and no Kord removal until Phase 6.** Non-cutover root refactors that preserve Kord are permitted where already accepted — Phase 2D already refactored the root Kord-facing `LocalizationService`/root `/locale`, shared language resources, DI and module dependencies |
| Phase 2A scope (owner decision 2026-11-14) | **PASS** for neutral `GuildId`/`LocaleCode`, locale repository/use cases and JSON compatibility. Root Kord `LocalizationService`/`/locale` are **Phase 6** scope, not a 2A blocker |
| Phase 5 vs cutover | Phase 5 is **test/staging only**; it is **not** the root production cutover, and it retains the root Kord app and its Kord tests |
| Phase dependency chain | Phase 3 after Phase 2 exit; Phase 4 after Phase 3; Phase 5 after Phases 3–4; Phase 6 after Phase 5; Phase 7 after Phase 6 |
| Import rule (owner decision 2026-11-14) | **Custom import-declaration scanner using only existing JDK/Kotlin/Gradle APIs — no Konsist, ArchUnit, Detekt or new dependency.** Phase 2 matrix forbids Kord/JDA/jda-ktx/libdave in `:core`/`:locale-persistence`, forbids JDA/jda-ktx/libdave in the root while allowing Kord as grandfathered legacy, and allows JDA/jda-ktx/libdave in `:jda-runtime` only within the declared adapter package boundaries. Phase 6 removes the root Kord exception and applies it repo-wide on the Kord-free tree. **There is no separate reflection-based public-signature gate or requirement to add one (owner decision 2026-11-14)**: the scanner is import-declaration-only and does not detect fully qualified references without imports or inspect API signatures; `VoiceBoundaryReflectionTest`/neutral-port reflection tests are complementary limited tests, not a repo-wide signature guarantee. Exact definition under Phase 2 |
| Branching and landing (owner decision 2026-11-14) | Future phase/subphase work fetches and verifies the **live epic tip**, branches from it, and PRs **against the epic integration branch**; landing uses **regular merge commits** (no squash/rebase). #115/#116 squash merges are **historical exceptions** |

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
- **`adapter-jda` is not yet part of the root production dependency set.** The version catalog does
  contain `libdaveV2` / `adapter-jda` aliases, but that alias is currently **consumed by the isolated
  `:jda-runtime`**; the **root production dependency set does not include `adapter-jda`**, and the root
  still carries the **Kord-era `libdave` / `libdave-natives` dependencies**. Adding `adapter-jda` to
  the root production dependency set and bumping the root `libdave` / `libdave-natives` references to
  `0.2.0` is **deferred to the final root dependency replacement stage (Phase 6)**; Phase 1 does
  **not** change the root production dependency set.
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

**Enforcement gate (two scopes; exact rule defined under Phase 2).** An **architectural test/CI
rule** forbids the vendor imports `dev.kord.`, `net.dv8tion.`, `dev.minn.jda.ktx.` and
`moe.kyokobot.libdave.` outside the declared adapter packages. It is a **custom import-declaration
scanner built only on existing JDK/Kotlin/Gradle APIs — no Konsist, ArchUnit, Detekt or new
dependency** (exact mechanism, scope, matrix and package-boundary match in
"Phase 2 scoped architectural import rule — exact scanner definition").

- **Phase 2 scope:** the rule runs in CI and requires `:core` and `:locale-persistence` to be
  **gateway-free**, the root's **source imports** free of JDA/jda-ktx/libdave, and JDA/jda-ktx/libdave confined to the
  declared `:jda-runtime` adapter packages. Root Kord packages are **grandfathered legacy until
  Phase 6** and are outside this scoped rule; this is why Phase 2 does not switch the root runtime.
- **Phase 6 scope:** the same scanner becomes **repo-wide on the Kord-free tree**, once the root Kord
  code and dependency are gone.

Tests and core code must mock the **neutral ports**, not Kord/JDA types. Without the scoped Phase 2
rule in place and **passing**, Phase 2 cannot exit; without the repo-wide Phase 6 rule passing, the
replaceability criterion is **not** met. An import-declaration scanner is not a full Kotlin type-use
parser; the existing reflection-based neutrality tests remain **complementary only** and do not
establish a public-signature guarantee.

### Architecture guardrail

Kord and KTX both dispatch events concurrently. The JDA design must add a **narrow per-guild
actor/mailbox** that serializes:

- player creation
- queue and track transitions
- voice lifecycle (connect / reconnect / disconnect)

Rules that follow from this:

- Lavaplayer callbacks may only **enqueue** work; they must never block.
- JDA's `AudioSendHandler` must consume **exactly one Opus frame per send cycle**, preserve the
  **approved** pause behaviour, and never block the audio thread. Pause/resume preservation is
  verified in **Phase 3** real-player integration against semantics approved before implementation —
  it was explicitly deferred from 2C and remains a migration requirement.

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

**Phase dependency chain (owner decision 2026-11-14).** Phases are sequential:
**Phase 3 starts after Phase 2 exit → Phase 4 after Phase 3 → Phase 5 after Phases 3–4 →
Phase 6 after Phase 5 → Phase 7 after Phase 6 → Phase 8 after Phase 7.** Phase 8 is post-migration
follow-up and is **not** a prerequisite for Phase 2, Phase 6 or issue #114 closure. Phases 2–5 validate
the **isolated JDA runtime** only; root Kord production remains in service through Phase 5, and only
Phase 6 switches the root code/build and removes Kord (Phase 7 deploys the verified artifact).

### Phase 0 — Baseline and scope

- Freeze the baseline at `master` @ `4badb92`; record the epic integration branch and the separate
  child working branch/PR that carries migration commits.
- Re-derive the scope inventory above and record it in the issue.
- Confirm parked PRs and the stashed assistant work stay untouched.

**Exit criteria**
- The epic integration branch `feature/114-migrate-to-jda-jda-ktx` is based on `master` @
  `4badb92`, and migration work landed on the child working branch
  `feature/114-kord-to-jda-migration` through draft PR #115, which targeted the epic branch and not
  `master`. #115 has since been **squash-merged** as `5aad7c7` and that working branch's remote ref deleted (a stale local ref may remain).
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
  dependency graph there. **jda-ktx remains optional:** the spike records this candidate, and the
  direct-JDA fallback stays permitted; no later phase is required to keep jda-ktx.
- Compile a throwaway slice that registers one slash command and answers one interaction.
- Align DAVE at 0.2.0 across **all** modules (`api`, `impl-jni`, `adapter-jda`, Linux x86_64
  natives) **in the isolated spike**. The root production dependency set still carries the Kord-era
  `libdave` / `libdave-natives` dependencies and is aligned only at the final root dependency
  replacement stage (Phase 6); Phase 1 does not change it. Version-catalog aliases already used by
  isolated modules (e.g. `libdaveV2` / `adapter-jda` for `:jda-runtime`) are additive and not part of
  the root production set.
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
longer defers the isolated JDA runtime/bootstrap work of Phase 2; root production integration and the
root code/build switch remain **Phase 6** scope (production deployment is Phase 7; see the resolved phase placement in Phase 2).

#### Phase 1 subphase plan and stacked branches

Phase 1 followed a branch-per-subphase strategy: each subphase got its **own child
branch and PR based on the immediately preceding unmerged branch**, never on `master`, and it was
never folded into an already-open PR. *(Historical posture; future work follows the live-epic-tip
stacking policy recorded under Phase 2's "Stacking rules".)*

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
has since closed **PASS — gate closed (2026-11-15)** through PR #120.

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

### Phase 2 — isolated JDA bootstrap, event and command-response foundation

**Status: PASS — gate closed (2026-11-15).** All four exit criteria are satisfied: criterion 1 and
criteria 2–3 have their recorded 2B/2C/2D isolated-runtime evidence, and criterion 4 — the scoped
architectural import rule — was closed in CI by **PR #120** (merged 2026-11-15 08:03:45Z by the regular
merge commit `96e5fbb`; head `66f7783`, base `955ac49`; all four checks green). The earlier scanner
observation in the **local test-source working-tree snapshot taken 2026-11-14 on base `955ac49`, before
the branch was committed or pushed**, is **historical**: at that snapshot the scanner existed locally
with no PR or CI run because the branch was not pushed. **Root cutover is not a Phase 2 criterion** and
remains Phase 6.
Phase 2A is implemented, **squash-merged into the epic branch** (`5aad7c7`), and **PASS — gate closed
(owner scope decision 2026-11-14)** for its delivered neutral-value/locale-persistence scope. The
**root Kord** `LocalizationService`, **root Kord** `/locale` command, resources, DI wiring and module
dependencies were already refactored in **Phase 2D** while remaining Kord-based; those are **not**
Phase 2 blockers and are not a cutover. Phase 2B is **PASS — gate closed
2026-11-13**: its neutral command boundary and isolated JDA adapter are implemented with unit tests,
green CI on its verified checkpoints and a recorded staging `/jda-walkthrough` interaction. The
earlier handoff head `e4a9e29` later completed all four checks successfully, `7bf5419` is the latest
verified all-green checkpoint before the subsequent docs-only updates (it was the head at that point,
not afterwards), and CI state for any later head is read live. A later dated snapshot of `f5f04d1` was
**not green**. The bootstrap
and command layer live in the **isolated** `:jda-runtime` and are deliberately **not** wired into the
root runtime. Phase 2A, 2B, 2C and 2D are all **PASS**: criterion 1 has recorded staging evidence,
criteria 2–3 are design-reviewed and tested (complementary, not an automated signature guarantee), and
criterion 4 — the scoped import-rule CI gate (exact definition in "Phase 2 scoped architectural import
rule" below) — **passed in CI through PR #120**. Phase 2 is therefore **PASS — gate closed
(2026-11-15)**; the root code/build switch is Phase 6 and was never part of this exit.
**2D is PASS — gate closed (2026-11-14)** on the `a4bc1ae` time-qualified green snapshot (all four checks green,
Sonar gate OK) and the sanitized staging evidence in the Phase 2D section; PR #119 is **merged** into
the epic branch by the regular merge commit `f56d225` on 2026-11-14 (head `0b365aa`, base `1304aa6`).
**The scoped import rule is closed too:** PR #120's root JUnit import-boundary scanner is merged, and
no Phase 2 work item remains to be implemented here. Its scanner was committed, pushed and opened as a
child PR against the epic branch, and the four checks went green — which is how criterion 4 closed.
**2C is PASS — gate closed
(2026-11-14)**: its acceptance criteria and sanitized staging evidence are recorded in the Phase 2C
section, and its branch `feature/114-phase-2c-voice-ports` (created from the fetched epic head
`255893a`) was **merged** into the epic branch by the regular merge commit `5fa9c63` (PR head
`3e50248`), so **no open Phase 2C PR remains**. All four checks succeeded on the implementation head
`8eb0349` and on `3e50248`, and the Sonar Quality Gate passed with 90.1% new-code coverage, 11 new
issues (10 major, 1 critical), 0 security hotspots and 0.0% duplication — the 11 issues are recorded
as **tracked follow-up debt** under the owner-approved 2026-11-14 interpretation (the configured
Quality Gate is the criterion), not zero. The earlier `c893567` failed only Check Code Quality, on 25
formatting findings. The staging run was user-run on **local macOS arm64** with the DAVE 0.2.0
`natives-darwin`
native, protocol version 1, only `GUILD_VOICE_STATES`, guild-scoped `/jda-walkthrough` and
`/jda-voice-walkthrough`, voice connect, the reply
`Voice walkthrough OK | handed 75/75 frames to JDA's send loop (delivery not confirmed)` (a hand-off
count, not delivery proof), an audible beep reported by the invoking user's own Discord client, separate from
the bot (the delivery evidence, a distinct observation from the hand-off), completion-path disconnect
cleanup and a deliberate Ctrl+C stop; no receive path, root wiring or Phase 6 artifact validation was
exercised. Local results on the formatted tree are 685 cases (680 executed +
5 pre-existing skips; local module JaCoCo line coverage `:core` 207/219 = 94.52% with branch
108/115 = 93.91% and `:jda-runtime` 870/948 = 91.77% with branch 342/418 = 81.82% — local module
reports only, not Sonar/new-code CI evidence). The recorded checkpoints are dated historical evidence;
read the live GitHub checks for any later epic head. **Phase 2D is PASS — gate closed (2026-11-14)**
on `feature/114-phase-2d-locale-edge-adapters`, created from the verified live epic tip `1304aa6`;
the `a4bc1ae` time-qualified green snapshot has all four checks green and the Sonar Quality Gate OK, and the
sanitized user-run set/localize/reset/restart evidence is recorded in the Phase 2D section. Final local
QA on 2026-11-14 recorded **758 test cases: 753 executed, 5 skipped (pre-existing), 0 failures/errors**,
a clean local build, local module JaCoCo readings and local changed-file ktlint **0 findings**. The
CI/Sonar snapshot is time-qualified to `a4bc1ae`; PR #119 is **merged** into the epic branch by the
regular merge commit `f56d225` on 2026-11-14 (head `0b365aa`, base `1304aa6`).

**Resolved phase placement (owner decision 2026-11-14).** Phase 2 targets an **isolated** JDA
implementation in `:core` + `:jda-runtime`. The root runtime is **not** switched in Phase 2: root
production stays on Kord through Phases 2–5, and **Phase 6** owns the root code/build switch to the JDA lifecycle
and the Kord dependency/JAR removal. Phase **7** deploys the exact Phase 6-verified artifact.
The earlier "replace the root Kord bootstrap in Phase 2" wording is superseded; no Phase 2E is defined,
and none should be invented to carry the root code/build switch.

**Root refactors are not forbidden before Phase 6.** The constraint is **no root gateway/runtime
cutover and no removal of Kord until Phase 6** — not "no root code/build changes". Non-cutover root
refactors that preserve Kord are permitted where already accepted; Phase 2D in fact refactored the
root Kord-facing `LocalizationService` and root `/locale`, moved the shared language resources, and
adjusted DI and module dependencies while keeping Kord as the root gateway. Such refactors must keep
the root runtime Kord-based and must not add root JDA wiring.

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
whatever head is current. For Phase 2B, local QA and reviewer agents satisfied the independent review
criterion and no human GitHub review was submitted, which is not itself a required gate; for 2C the
local QA/reviewer agents also report PASS, and no human GitHub review is recorded here. **Phase 2C is
PASS — gate closed (2026-11-14)** and its PR #118 is **merged** into the epic branch (regular merge
commit `5fa9c63`); the sanitized user-run staging record is in the Phase 2C section. **Phase 2 is
PASS — gate closed (2026-11-15)** — the scoped architectural import rule closed in CI through **PR
#120** (merged 2026-11-15 by the regular merge commit `96e5fbb`; head `66f7783`, base `955ac49`; four
checks green) — its other isolated-runtime exit criteria are satisfied by the recorded 2B/2C/2D evidence, Phase 2A is **PASS**
for its delivered scope, and the root code/build switch is **Phase 6** (deployment is Phase 7), not a Phase 2 item. **Phase 2D is PASS — gate closed (2026-11-14)** and is not
listed as open. **Phase 6** remains separately **OPEN**. The 11 new Sonar issues stay
recorded as **tracked follow-up debt** under the owner-approved 2026-11-14 interpretation — the
repository's configured Quality Gate is the criterion — so they are not a failed gate and not zero.

- Deliver the JDA bootstrap lifecycle (login, shard/event wiring, shutdown) **inside `:jda-runtime`**.
  Root Kord/bootstrap replacement is **Phase 6**, not Phase 2.
- Introduce neutral abstractions for IDs and locales so command code stops speaking Kord types.
  **Done in the neutral boundary** (Phase 2A + 2D): domain-owned `GuildId`/`LocaleCode` plus a neutral
  locale repository and use cases, and neutral/shared locale contracts in `:core`. Phase 2B moves those
  values into `:core` and adds neutral command ports; the **root** Kord command code still speaks Kord
  types and is converted in **Phase 6**.
- Establish a single command dispatch/response layer with correct deferred/replied semantics.
  **Implemented and unit-tested in the isolated `:jda-runtime`** (Phase 2B/2C/2D); root dispatch stays
  Kord until **Phase 6**.
- Apply the framework-replaceability goal from §1 to the **neutral contracts and the isolated JDA
  adapter**: commands/handlers take a neutral command context and responder/response port; JDA types
  stay in declared `:jda-runtime` adapter packages. **Root Kord packages are explicitly grandfathered
  legacy until Phase 6** and are outside this phase's rule scope.
- Do **not** add root JDA bootstrap, the root code/build switch or a Kord-dependency removal to Phase 2.

**Exit criteria**
1. **Isolated staging bot connects and its commands respond — SATISFIED by 2B/2C/2D evidence.** The
   criterion is about the **isolated `:jda-runtime`** bot only: connect on staging with the staging
   token and register/respond with explicit, tested deferred-vs-immediate semantics. Recorded:
   `/jda-walkthrough` (2026-11-13), `/jda-voice-walkthrough` (2026-11-14) and the isolated `/locale`
   (Phase 2D, 2026-11-14). **Root staging behaviour, root command behaviour and root Kord removal are
   not Phase 2 criteria** — they belong to Phase 6.
2. **Neutral contracts do not expose Discord-library types — design principle, reviewed (not
   independently automated).** As a design principle, `:core` and `:locale-persistence` neutral ports
   and contracts must not expose `dev.kord.*`, `net.dv8tion.*`, jda-ktx or libdave types, and the neutral
   ports stay gateway-free. Phase 2B/2C/2D review and the existing neutral-boundary/reflection tests are
   **complementary evidence only**. The **automated Phase 2 gate for imports is criterion 4**; it checks
   import declarations only and does not detect fully qualified references or inspect compiled public
   signatures. **Root Kord packages are explicitly grandfathered legacy until Phase 6** — they are not a
   leak and do not fail this criterion. Tests are only isolated-runtime/neutral-boundary tests; no
   root-stage test is required here.
3. **Adapter containment — design principle, reviewed (not independently automated).** As a design
   principle, JDA (`net.dv8tion.*`), jda-ktx, libdave and DAVE implementation belongs only in
   `:jda-runtime` and its declared adapter packages. Root Kord usage is grandfathered legacy until
   Phase 6 and is excluded from this scoped rule. The **automated Phase 2 gate is criterion 4's
   import-declaration matrix**, which checks source imports only and does not prove that no vendor type
   appears in compiled signatures or via fully qualified references.
4. **Scoped architectural import rule passes in CI — SATISFIED; gate CLOSED (2026-11-15).** The
   CI boundary rule enforces the exact matrix defined in
   the next subsection: `:core`
   and `:locale-persistence` gateway-free, the root's **source imports** free of JDA/jda-ktx/libdave, and JDA/jda-ktx/
   libdave confined to the declared `:jda-runtime` adapter packages. Written but not green does not
   pass — and it **is** green: **PR #120** (merged 2026-11-15 08:03:45Z by the regular merge commit
   `96e5fbb`; head `66f7783`, base `955ac49`) runs the scanner as a root JUnit gate through the existing
   `./gradlew build`/CI lifecycle, with all four checks green on `66f7783` — Check Code Quality
   (2026-11-14 21:52:37Z), SonarCloud Code Analysis (21:56:59Z), Build and analyze (21:57:10Z) and
   SonarCloud (21:57:06Z). The earlier **local test-source working-tree snapshot taken 2026-11-14 on
   base `955ac49`, before the scanner branch was committed or pushed** — where the scanner existed as
   uncommitted test-source changes with no PR/CI — is **historical, pre-merge evidence only**. **Phase 6 later
   extends the same scanner repo-wide on the Kord-free tree.** The earlier
   Phase 2B reflection tests remain **complementary only**; they are not the CI gate and do not
   establish a public-signature guarantee.

#### Phase 2 scoped architectural import rule — exact scanner definition (owner decision 2026-11-14)

This is the rule Phase 2 exit criterion 4 required and Phase 6 reuses. It is **implemented and merged**:
PR #120 added the scanner, its configuration and its tests as **test-source-only** changes on
`feature/114-import-declaration-scanner`, merged into the epic branch on **2026-11-15** by the regular
merge commit `96e5fbb` (head `66f7783`, base `955ac49`). Its earlier **local test-source working-tree
snapshot taken 2026-11-14 on base `955ac49`, before the branch was committed or pushed** — where it
existed as uncommitted changes with no PR/CI — is **historical evidence only**. This
subsection remains the normative definition against which that implementation and
the Phase 6 reuse are checked.

**Mechanism — no new dependency.** A Gradle/Kotlin test or verification task implemented using only
the **existing JDK, Kotlin and Gradle APIs**. No Konsist, ArchUnit, Detekt, annotation processor,
extra plugin or any other new dependency is added. The task fails the build on any violation and is
wired into the repository's existing check/CI gate (the ktlint/Sonar jobs or the build task), so it is
green in CI, not merely described.

**Scan scope (Phase 2).** The scanner **parses imports only in the explicitly configured source roots**:
`<root>/src/main/kotlin/**/*.kt`, `<root>/src/test/kotlin/**/*.kt`,
`core/src/main/kotlin/**/*.kt`, `core/src/test/kotlin/**/*.kt`,
`locale-persistence/src/main/kotlin/**/*.kt`, `locale-persistence/src/test/kotlin/**/*.kt`,
`jda-runtime/src/main/kotlin/**/*.kt` and `jda-runtime/src/test/kotlin/**/*.kt`.
A **separate source-root completeness check** (below) independently enumerates the Kotlin **source
files ending in `.kt`** under the Gradle Kotlin source sets of the configured projects (and the optional
spike source sets when retained), and fails on unclassified roots; it does not parse imports in build
output. **Gradle Kotlin DSL/build scripts (`*.gradle.kts` and any other `*.kts`) are not part of this
import scanner's source-file universe**; if build-script imports ever need enforcement, that would be a
separate rule. The Phase 2 scanner configuration explicitly **excludes** the separately built
`spikes/jda-dave`, all `**/build/**` outputs and `.gradle/**`. There is **no unspecified
generated-directory glob**: any new Kotlin source root must be **explicitly added to the scanner
configuration** before its imports are checked.

**What is scanned — actual top-level Kotlin file-header imports only.** The scanner must not
string-match arbitrary lines. A **candidate import** is only a real top-level Kotlin **file-header
import declaration**: at the start of the file after an optional UTF-8 BOM, optional whitespace and
comments, optional `@file:` annotations (including annotation arguments with balanced parentheses,
brackets, braces and nested annotations), and an optional `package` directive, **before the first
top-level declaration**. Both **aliased imports** (`import a.b.C as D`) and **wildcard imports**
(`import a.b.*`) are candidates. This definition is **deterministic**: the lexer recognizes header
structure purely from the lexical states below, ignoring comments and literals, and tracking balanced
delimiters **only in code state**; there is no heuristic mode or line-shape matching.

**Lexical rule — no compiler parser, PSI or new dependency.** The scanner must use a small
lexer/state machine built only on the existing JDK/Kotlin/Gradle APIs; a full Kotlin PSI/compiler
parser is **not** permitted as a dependency. The lexer must track at least:

- **code** state,
- **line comment** (`// ...`),
- **block comment** (`/* ... */`) with **nested block comments**,
- **regular string literal** (`"..."`, including escapes),
- **character literal** (`'...'`, including escaped quote/backslash chars),
- **raw/triple-quoted string** (`"""..."""`),
- **balanced delimiters** inside `@file:` annotation arguments and the header regions.

Only an `import` keyword encountered in **code state while still inside the file-header import region**
counts as a candidate. Text inside line comments, block/nested block comments, string literals,
character literals or raw strings is never treated as an import, even when it occupies its own line and
begins with `import`. The lexer must not fall back to matching arbitrary lines.

**Deterministic gate condition — SATISFIED (2026-11-15).** Phase 2 exit stayed **open unless the
scanner passed all specified acceptance fixtures and the source-root completeness check below**; it did,
and the gate closed. The rule must not be weakened to
line-based or regex-only matching, and no parser dependency may be added to compensate; had a candidate
implementation failed the fixtures, scanner design would have been deferred and the Phase 2 gate would
have stayed open.

**Implementation status — MERGED via PR #120 (2026-11-15); gate CLOSED.** The scanner and its
acceptance tests are **test-source-only** changes (new files under
`src/test/kotlin/architecture/`: `ImportBoundaryConfig`, `ImportBoundaryPolicy`,
`KotlinFileHeaderParser`, `SourceTreeScanner` plus their tests) on
`feature/114-import-declaration-scanner`; no production source, build script,
dependency, or CI wiring is changed, and the scanner tests run through the existing root `build`
(no new Gradle task). PR #120 merged on **2026-11-15 08:03:45Z** by the regular merge commit `96e5fbb`
(head `66f7783`, base `955ac49`; parents `955ac49` + `66f7783`), and **all four checks succeeded on
`66f7783`** — Check Code Quality (2026-11-14 21:52:37Z), SonarCloud Code Analysis (21:56:59Z), Build
and analyze (21:57:10Z) and SonarCloud (21:57:06Z). Local evidence from the **pre-merge working-tree
snapshot of 2026-11-14** (historical): a clean full `./gradlew build`
passed (36 tasks); the full local suite recorded **949 cases = 944 executed + 5 pre-existing skips,
0 failures/errors** (root **557** = 552 executed + 5 skips, `:core` **134**, `:locale-persistence`
**21**, `:jda-runtime` **237**); the **191** architecture scanner tests (ImportBoundaryPolicy **148**,
KotlinFileHeaderParser **21**, SourceTreeScanner **22**) all pass; and a scan of the current Phase 2
tree reports **0 violations and 0 completeness issues**. Local ktlint **1.8.0** reported **0
findings** on the 7 changed Kotlin test files. Because the change is tests-only, production coverage is unchanged
and **no new coverage percentage is claimed**; no Sonar issue count is asserted for this PR. The
snapshot's "no PR/CI" state was dated 2026-11-14 history: the branch was later pushed, the PR opened,
and the checks above went green.

**Scanner acceptance tests.** The scanner's own tests must include **all** of these fixtures:

- **False-positive fixtures (must not be flagged):** fake forbidden-import-looking text inside
  **line comments**, **normal and escaped regular string literals**, **triple-quoted raw strings**,
  **block comments** and **nested block comments**. In regular escaped strings, `\n` denotes two source
  characters (backslash + `n`), not an actual new line, and must be handled as a string escape. A
  character literal cannot contain a full fake import line; character-literal lexing is covered by the
  escaped-character annotation fixture below.
- **Header-termination fixture:** once a top-level declaration (class/function/property/etc.) has begun,
  text resembling a forbidden import later in the file — including in an invalid fixture or inside any
  literal/comment — is **not** treated as a header import.
- **Source-root completeness fixture:** introducing a Kotlin source file under an unclassified source
  root causes the gate to **fail** until that root is explicitly categorized in the active scanner mode.
- **Delimiter robustness fixtures:** delimiter-looking `)`, `]` and `}` inside comments and inside
  regular/raw strings must **not** terminate an annotation or the header region.
- **Annotation-header fixtures:** nested/balanced `@file:` annotation arguments (parentheses, brackets,
  braces and nested annotations), plus comments between the `package` directive and imports and between
  imports, must not break header recognition.
- **Real-import fixtures (must be flagged):** real header imports after an `@file:` annotation and a
  `package` directive, **aliased imports** and **wildcard imports**.
- **Escaped-character annotation fixture:** a synthetic `@file:` annotation argument containing a
  **valid escaped character literal** (for example an escaped quote or escaped backslash, such as
  `'\''` or `'\\'` in scanner-input form), followed by a real vendor import, must be lexed without
  losing header state and the following import must be flagged. The fixture is **scanner input** and
  need not compile if it uses a test-only annotation.

No comment, string or character-literal content may produce a false positive. A fake import cannot be
placed inside a character literal because a `Char` cannot contain one; the escaped-character annotation
fixture above covers character-literal lexing in the header instead. This remains an
**import-declaration scanner, not a full Kotlin type-use or fully-qualified-reference parser**:
fully-qualified references used without an `import`, reflection string targets, and any import-like
text outside the lexical rules above remain outside its guarantee unless this plan is explicitly
extended. Per the **owner decision (2026-11-14)**, there is **no separate reflection-based
public-signature gate and no requirement to add one**. The existing `VoiceBoundaryReflectionTest` and
neutral-port reflection tests stay in place as **complementary, limited tests only**; they do **not**
fulfill or establish a repo-wide public-signature policy or guarantee.

**Forbidden vendor prefixes** (matched against the imported name): `dev.kord.`, `net.dv8tion.`,
`dev.minn.jda.ktx.`, `moe.kyokobot.libdave.`

**Import-path normalization.** Each candidate import must be parsed into **identifier segments**: separate
any `as Alias` clause from the target, preserve a trailing wildcard marker (`*`), and **remove Kotlin
backticks from identifier segments** before matching. Compare at **package-segment boundaries**, never
by raw-string prefix. Fixtures such as ``import dev.`kord`.core.SomeType as Alias`` and
`import net.dv8tion.jda.api.*` must be caught; a raw prefix check that misses backticked segments is not
acceptable.

**Phase 2 matrix**

| Source set | Kord (`dev.kord.*`) | JDA (`net.dv8tion.*`) | jda-ktx | libdave |
| --- | --- | --- | --- | --- |
| root main + test | **allowed** (grandfathered legacy until Phase 6) | **forbidden** | **forbidden** | **forbidden** |
| `:core` main + test | **forbidden** | **forbidden** | **forbidden** | **forbidden** |
| `:locale-persistence` main + test | **forbidden** | **forbidden** | **forbidden** | **forbidden** |
| `:jda-runtime` main + test | **forbidden** | **allowed only in declared adapter packages** | **allowed only in declared adapter packages** | **allowed only in declared adapter packages** |

**Matrix scope is imports, not dependencies.** The root row forbids **source imports** of JDA, jda-ktx and
libdave; it does **not** clear the root dependency graph. The root **production dependency graph still
uses the Kord-era `libdave` / `libdave-natives` dependencies** until the Phase 6 dependency
replacement, and a catalog or build dependency entry alone does not violate this import scanner. Root
JDA dependencies and root JDA wiring remain forbidden throughout Phase 2.

**Declared JDA adapter packages (package-boundary match).** The allowance for `:jda-runtime` applies
only when the **file's `package` declaration** is exactly one of
`es.wokis.jda`, `es.wokis.jda.command`, `es.wokis.jda.player`, `es.wokis.jda.voice`, **or a
subpackage of one of them**. The match is a **package boundary**, i.e.
`pkg == allowed || pkg.startsWith("$allowed.")`, never a plain string prefix — so `es.wokis.jdaextra`
is **not** an allowed package. A file with no `package` declaration is not in an allowed adapter
package. The allowance never applies to Kord: `dev.kord.*` stays forbidden in `:jda-runtime`
main and test.

**Phase 6 matrix — same scanner on the Kord-free tree.** Remove the **root Kord exception** and extend
the scanner to **all projects included in the root Gradle build** — the current root project, `:core`,
`:locale-persistence`, `:jda-runtime`, and any future projects included by the root build —
`src/main/kotlin/**/*.kt` and `src/test/kotlin/**/*.kt` in each. It still excludes all `**/build/**`
outputs and `.gradle/**`; there is **no unspecified generated-directory glob**, and any new source root
must be **explicitly added to the scanner configuration**. Inclusion in the root Gradle build does
**not** grant vendor-import permission; the module-specific allowlist is the Phase 6 matrix below.
**`spikes/jda-dave` is a separate build, not
part of the root Gradle build**: if retained, it appears only in the separate optional row below
(`spikes/jda-dave/src/main/kotlin/**/*.kt` and `spikes/jda-dave/src/test/kotlin/**/*.kt`), and the
production adapter allowlist never applies to it.

**Phase 6 matrix**

| Scan root | Kord (`dev.kord.*`) | JDA (`net.dv8tion.*`) | jda-ktx (`dev.minn.jda.ktx.*`) | libdave (`moe.kyokobot.libdave.*`) |
| --- | --- | --- | --- | --- |
| Root project source (main + test) | **forbidden** | **forbidden** | **forbidden** | **forbidden** |
| `:core` / `:locale-persistence` (main + test) | **forbidden** | **forbidden** | **forbidden** | **forbidden** |
| `:jda-runtime` (main + test) | **forbidden** | **allowed only in declared `es.wokis.jda.*` adapter packages** | **allowed only in declared `es.wokis.jda.*` adapter packages** | **allowed only in declared `es.wokis.jda.*` adapter packages** |
| `spikes/jda-dave` (separate build; main + test) **only if retained** | **forbidden** | **allowed only in non-production package `spike.jdadave` and its subpackages** | **allowed only in non-production package `spike.jdadave` and its subpackages** | **allowed only in non-production package `spike.jdadave` and its subpackages** |

**Root imports are denied regardless of package.** The root project source forbids all four vendor
prefixes even when a root file declares `package es.wokis.jda` (or any other declared-adapter-looking
package); **a package name alone never authorizes root imports**. Only `:jda-runtime` may import
JDA/jda-ktx/libdave, and only inside the declared `es.wokis.jda...` adapter packages. The
`spikes/jda-dave` row keeps its separate non-production `spike.jdadave` exception.

The `spikes/jda-dave` row is a **non-production spike exception**, not part of the production adapter
allowlist; its package-boundary rule is `spike.jdadave` and its subpackages only, and the production
adapter packages (`es.wokis.jda`, `.command`, `.player`, `.voice`) never authorize spike imports.
**If the spike is deleted before Phase 6, that row disappears.** All other vendor imports are denied
everywhere. The gate must be green **repo-wide** on the Kord-free tree before the Phase 6 artifact is
produced (see Phase 6).

**`spikes/jda-dave` is non-production:** before Phase 6 it is either **deleted**, or retained and
covered by the explicit `spike.jdadave`-only row above. It is never treated as production source, and
its allowance must not admit vendor imports into production modules. That spike choice is deliberately
not fixed here; it must be decided before the Phase 6 import gate runs.

**Root-to-adapter boundary (Phase 6).** Root `Main`/application code may call a **narrow
lifecycle/entrypoint exposed by `:jda-runtime`**, but **root source must not import JDA/DAVE types**
(import gate). As a **design principle**, root-facing runtime API must avoid exposing JDA/DAVE types,
and JDA/DAVE implementation stays confined to the `es.wokis.jda.*` adapter packages. This does not
prescribe a generic facade and does not fix the final entrypoint/API name; the package matrix above
remains authoritative.

**Import-scanner limitation for that boundary.** The source-import scanner is **import-declaration-only**:
it does **not** detect fully qualified JDA/DAVE type references used without an import, and it does
**not** inspect public API signatures. **Owner decision (2026-11-14): no separate reflection-based
public-signature gate is required** for Phase 6 or architecture enforcement, and there is **no
requirement to add one**; the custom source-import scanner is the enforcement mechanism. Root-facing
runtime API is designed to avoid vendor types, but
that design property is **not automatically verified** and no automated signature guarantee is claimed.
The existing `VoiceBoundaryReflectionTest`/neutral-port reflection tests are complementary limited
tests, not a public-signature policy.

**Table-driven allowlist acceptance fixtures.** These fixtures test the allowlist matrix itself, not
only lexing. Each cell is an asserted expected outcome for an import resolved to that row:

| Mode | Source / package | Kord (`dev.kord.*`) | JDA (`net.dv8tion.*`) | jda-ktx (`dev.minn.jda.ktx.*`) | libdave (`moe.kyokobot.libdave.*`) |
| --- | --- | --- | --- | --- | --- |
| Phase 2 | Root project main/test | **allowed** (grandfathered) | **denied** | **denied** | **denied** |
| Phase 2 | `:core` main/test | **denied** | **denied** | **denied** | **denied** |
| Phase 2 | `:locale-persistence` main/test | **denied** | **denied** | **denied** | **denied** |
| Phase 2 | `:jda-runtime` in `es.wokis.jda` or a declared subpackage (`es.wokis.jda.command`, `es.wokis.jda.player`, `es.wokis.jda.voice` and their subpackages) | **denied** | **allowed** | **allowed** | **allowed** |
| Phase 2 | `:jda-runtime` outside declared packages, including `es.wokis.jdaextra` | **denied** | **denied** | **denied** | **denied** |
| Phase 2 | `:jda-runtime` with no `package` declaration | **denied** (fail closed) | **denied** (fail closed) | **denied** (fail closed) | **denied** (fail closed) |
| Phase 2 | `spikes/jda-dave` path | **excluded** | **excluded** | **excluded** | **excluded** |
| Phase 6 | Root project source (main/test), **any package including `es.wokis.jda`** | **denied** | **denied** | **denied** | **denied** |
| Phase 6 | `:core` / `:locale-persistence` (main/test) | **denied** | **denied** | **denied** | **denied** |
| Phase 6 | `:jda-runtime` (main/test) in a declared `es.wokis.jda.*` adapter package | **denied** | **allowed** | **allowed** | **allowed** |
| Phase 6 | `:jda-runtime` (main/test) outside declared packages or with no `package` declaration | **denied** | **denied** | **denied** | **denied** |
| Phase 6 | Retained `spikes/jda-dave` in non-production `spike.jdadave` or its subpackages | **denied** | **allowed** | **allowed** | **allowed** |
| Phase 6 | Retained `spikes/jda-dave` outside `spike.jdadave` | **denied** | **denied** | **denied** | **denied** |

These fixtures must also exercise **aliased imports**, **wildcard imports** and **backtick-normalized
package segments**, each resolving into the rows above. For example
``import dev.`kord`.core.SomeType as Alias`` is a Kord import (allowed in the Phase 2 root row, denied
elsewhere), and `import net.dv8tion.jda.api.*` is a JDA import (denied in the root row, allowed only in
declared adapter packages). The Phase 2 spike path remains **excluded**, not denied-by-policy, because
it is not scanned in Phase 2 mode. **Phase 6 must include a root-project fixture whose file declares
`package es.wokis.jda` and imports `net.dv8tion...`; the expected outcome is denied** — package name
alone never grants root imports.

**Source-root completeness (both phases — active-mode classification).** The scanner runs in exactly one
**active mode** at a time: **Phase 2 mode** or **Phase 6 mode**. In the active mode, every Kotlin
**source file ending in `.kt`** under the configured Gradle Kotlin source sets (and the optional spike
source sets when retained) must be classified **exactly once in that mode's scanner configuration**;
otherwise the gate fails. **`*.gradle.kts` and any other `*.kts` build scripts are outside this
scanner's source-file universe** and are not enumerated by this rule; build-script import enforcement
would be a separate rule.

- **Phase 2 mode** uses the Phase 2 project roots listed above (`<root>`, `core`,
  `locale-persistence`, `jda-runtime` main/test Kotlin roots) and explicitly **excludes**
  `spikes/jda-dave`.
- **Phase 6 mode** uses the Kotlin roots of all projects included in the root Gradle build, plus the
  explicit optional `spikes/jda-dave` row when retained.

A shared physical root appears in the Phase 2 and Phase 6 mode lists, but within one active mode it
belongs to **only one category** — it cannot be both scanned and excluded in the same mode. **Unknown or
unclassified roots fail closed:** the gate fails until the root is explicitly categorized in the active
mode. There is **no vague generated-directory glob**; only explicit output exclusions (`**/build/**`,
`.gradle/**`, or a reviewed explicit addition) are permitted.

### Phase 3 — Per-guild serialized voice (isolated JDA real-player slice)

**Ownership:** root production remains on **Kord** throughout Phase 3. This phase targets an
**isolated JDA real-player slice** (`:core` neutral ports, the gateway-free `:player` engine and
`:jda-runtime` adapters); it does **not** switch root production or remove Kord.

**Depends on:** Phase 2 exit — **satisfied on 2026-11-15**. Phase 2 is **PASS — gate closed (2026-11-15)**
(isolated staging bot/commands plus the scoped CI boundary rule closed by PR #120). The owner approved
both design decisions on 2026-11-15; implementation is in progress and the gate remains open.

- Implement the per-guild actor/mailbox for player creation, queue/track transitions, and voice
  lifecycle.
- Integrate the **real Lavaplayer player** behind the neutral voice/player ports for the isolated JDA
  slice.
- **Reuse the existing `JdaAudioSendHandler`** delivered in **Phase 2C**. Do not reimplement it and do
  not add a second handler. Phase 3 owns **real player integration and per-guild serialization**,
  verified through that existing handler and the real-player path.
- Make Lavaplayer callbacks **enqueue-only**; they may never block.
- **Module placement for the real player — resolved by owner approval (2026-11-15):** a new
  gateway-free `:player` module shared by the root Kord adapter and `:jda-runtime`. No root gateway
  switch occurs in Phase 3.

**Phase 3 design review — RESOLVED (owner approval 2026-11-15).** Both former blockers are now
closed by explicit owner approval:

- **(a) Testable pause/resume semantics — approved.** Pause/resume preservation remains required and
  the Phase 2C `JdaAudioSendHandler` is reused (no second handler, no reimplementation). After the
  serialized pause acknowledgement the track position stops advancing; resume continues the same
  track at that same position; skip while paused remains paused. JDA sends valid 20 ms Opus silence
  while paused, never `null`/EOF, and keeps polling `provide(0)`.
- **(b) Real-player / Lavaplayer module placement — approved.** The real player lives in the new
  gateway-free `:player` module (`es.wokis.player.engine`), shared by the root Kord adapter and the
  isolated `:jda-runtime`; Lavaplayer types stay internal to that module. Root production remains on
  Kord through Phase 5.

**Pause/resume semantics now fixed.** The approved rule above is the testable contract Phase 3
implements and the Test Agent asserts: paused playback does not advance position, resume uses the
same track and position, paused JDA output is 20 ms Opus silence that neither advances the position
nor exhausts the stream, and skip-while-paused stays paused. The `provide(0)` poll remains active
while paused so Lavaplayer's cleanup liveness clock keeps refreshing.

**Exit criteria**
- Concurrent play/queue/skip/disconnect per guild produce a single deterministic state order.
- **Pause/resume** follows the approved testable semantics, and reconnect and disconnect behave
  correctly under load. Pause/resume preservation was explicitly deferred from Phase 2C to this phase
  and is a **required** migration outcome, not optional.
- No blocking call observed on the audio send path.
- The slice runs on the isolated JDA runtime while root production continues on Kord.

#### Phase 3 implementation scope — IN PROGRESS, gate open (2026-11-15)

**Status: Phase 3 is IN PROGRESS — implementation landed, gate remains open/not passed.**
Implementation and tests are present locally; Phase 3 is NOT PASS. The owner approved the
two design decisions (module placement and pause/resume semantics) on 2026-11-15. Production source,
the `:player` tests and the scanner's `:player` classification exist on
`feature/114-phase-3-player-engine` at base `6d14167`. The Phase 3 gate stays **open** until the exit
criteria above are evidenced in CI/Sonar and staging; no PR had been opened at the local snapshot.

#### Phase 3 local verification snapshot — time-qualified working tree, not CI (2026-11-15)

This is a **local working-tree snapshot** of `feature/114-phase-3-player-engine` (base `6d14167`)
taken on 2026-11-15, before the Phase 3 child PR was pushed/opened. It is **not** GitHub CI,
SonarCloud, staging or a Phase 3 PASS claim; after push, read the live PR checks for current status.
The "no push/no PR" state belongs to this **dated snapshot only** and is not a standing claim.
**This subsection is the authoritative detailed breakdown of the Phase 3 local validation snapshot**
— its case totals, per-module counts, build task count, JaCoCo readings and ktlint scope are recorded
here in full. Summary sections elsewhere may repeat the headline totals (the 1,024-case total, the
42-task build result and the changed-file ktlint result); where a summary and this subsection differ,
this subsection is authoritative.

- Clean full `./gradlew build` passed (**42 tasks**).
- **1,024 test cases: 1,020 executed + 4 pre-existing skips, 0 failures/errors.** Module breakdown:
  root **531** (527 executed + 4 skips), `:core` **134**, `:locale-persistence` **21**, `:player` **85**,
  `:jda-runtime` **253**.
- Local module JaCoCo readings (**local module reports only — not Sonar, new-code or CI evidence**):
  `:player` line **692/796 = 86.93%**, branch **219/353 = 62.04%**; `:jda-runtime` line
  **1,087/1,178 = 92.28%**; root line **1,596/2,612 = 61.10%**; `:core` line **389/419 = 92.84%**;
  `:locale-persistence` line **60/67 = 89.55%**. No aggregate new-code coverage is claimed.
- Changed-file ktlint with the existing local ktlint **1.8.0**: **0 findings** across the **53 Kotlin
  files present in the working tree** — 25 tracked modifications plus 28 **untracked** Phase 3
  source/test files, **excluding deleted paths** (6 deletions are not counted). That is a **local
  ktlint 1.8.0 result only, not CI**: the pushed PR's Check Code Quality ktlint gate remains pending.
- **No new external dependency or version was introduced.** `:player` reuses the existing catalog
  versions — Lavaplayer 2.2.6, YouTube 1.17.0 and LavaSrc 4.8.1 — plus `kotlinx-coroutines-core`,
  `slf4j-api` and `:core`; no Discord library and no new artifact.
- **`:player` Sonar JaCoCo reporting is wired.** The module emits its JaCoCo report into Sonar, and
  the `GuildLavaPlayerService.kt` Sonar exclusion was **removed** so the file is measured. Its local
  JaCoCo line coverage is exactly **72/242 lines = 29.75%**, read from the root report
  `build/reports/jacoco/test-results/jacocoTestReport.xml`; that reading is **local JaCoCo only, not
  Sonar or new-code coverage**. The **Sonar new-code coverage gate is pending live PR CI** and must
  not be hidden or waived if CI fails; no Sonar gate outcome is claimed here.
- TestAgent coverage now includes engine ordering/cancellation/pause/provider-failure/manager
  lifecycle, JDA continuous stream/adapter/cleanup and the scanner's `:player` classification; all
  pass locally. The Test Agent found **no deterministic direct unit-test seam** for the Kord
  `connectToVoiceChannel`/leave-timer path at this snapshot. That is a **local coverage limitation
  and follow-up**, not a code failure and not evidence that any Phase 3 gate passed.

**Pending gates (not yet evidenced).** **CI/Sonar on the pushed Phase 3 child PR** — including the
Sonar new-code coverage gate on the now-unexcluded `GuildLavaPlayerService.kt` — and the **Phase 3
staging matrix**: real player with preserved source-provider parity; serialized per-guild queue
ordering under concurrent load; Kord-compatible pause/resume including paused Opus silence and the
`provide(0)` liveness poll; reconnect/disconnect under load; no blocking call on the audio send path;
and the untouched root Kord regression suite. Receive-audio remains out of scope. Detekt migration is
**not** a Phase 3 acceptance item.

**Approved decisions implemented.**

- **New shared module `:player`**, package `es.wokis.player.engine` (the neutral `es.wokis.player`
  package already belongs to `:core`, so no split package is introduced). It depends on `:core`,
  Lavaplayer 2.2.6, the existing YouTube v2 1.17.0 and LavaSrc 4.8.1 catalog entries,
  `kotlinx-coroutines-core` and `slf4j-api`; it has **no Discord library**. `slf4j-api` stays in the
  library module; bindings remain in app/test modules.
- **One engine implementation, two gateway adapters.** `PlayerEngines.fromConfig` builds a
  `LavaPlayerEngine`; the root Kord adapter and `:jda-runtime` both use that implementation and the
  same source-manager graph. The root bootstrap/gateway remains **Kord**; no JDA bootstrap switch,
  Kord dependency removal or root JDA wiring was made.
- **Per-guild mailbox/actor.** Every public command is sent to a single-consumer `Channel`; the
  actor applies pause/resume/skip/stop/shuffle/load/queue transitions in arrival order. Lavaplayer's
  `AudioEventAdapter` callbacks only `trySend` into that mailbox. No Lavaplayer thread mutates queue
  state or blocks on presentation code.
- **Neutral snapshots only.** `PlayerTrack`/`PlayerSnapshot`, `PlayerEvent`, `ContinuousPlayerAudioStream`
  and `PlayerAudioState` contain no Kord, JDA, libdave or Lavaplayer type. `TrackBO` is now a root
  typealias for `PlayerTrack`, so command/player-embed code keeps its previous names without exposing
  the audio library.
- **Source providers preserved.** YouTube, Deezer, Spotify, Tidal, Kokoro/TTS, local files and the
  generic remote fallback are registered through `LavaAudioPlayerManagers` with the existing options
  (OAuth2, PoToken/visitor data, remote cipher, Spotify custom endpoint, Kokoro voice/speed/lang).
  Root `Config` is mapped onto `PlayerSourcesConfig`; no provider was silently dropped.
- **Pause/resume semantics (approved).** `pause` is serialized and acknowledged; Lavaplayer
  `isPaused` becomes true and the track position stops advancing. `resume` reuses the same track at
  the same position. `skip` while paused advances the queue and leaves the engine paused. The Kord
  adapter keeps the previous user-visible pause state and player embed.
- **Continuous stream contract, not the finite one.** `ContinuousPlayerAudioStream.next()` has three
  explicit states: `Frame` (20 ms Opus), `PausedSilence` and `Ended`. The Phase 2C finite
  `AudioFrameSource` contract is unchanged.
- **`JdaAudioSendHandler` extended, not duplicated.** The existing handler now accepts either the
  finite `AudioFrameSource` path or the continuous stream through an internal frame supply. Paused
  playback produces a valid 20 ms Opus silence packet (`FC FF FE`), never `null`/EOF, and every send
  tick still polls Lavaplayer `provide(0)`, which refreshes the ~60 s cleanup liveness clock without
  advancing the position or exhausting the stream.
- **Root Kord adapter preserved.** `GuildLavaPlayerService` keeps its Kord voice connection,
  player-channel embeds, track/playlist/no-match/failure messages, leave timer, reconnect and locale
  presentation, now driven by engine events. `GuildQueueService` creates the shared engine through
  `AudioPlayerManagerProvider`; TTS/radio and retry behaviour remain. Retry uses a neutral linear
  backoff (250 ms linear steps capped at 2 s, 5 attempts) inside the engine, with retried starts
  suppressed from the now-playing message, matching the previous `LinearRetry` intent without a
  gateway type.
- **JDA runtime manual wiring.** `JdaPlayerRuntime` owns one long-lived engine and one
  `JdaGuildPlayerVoiceAdapter` per guild (no Koin). The adapter reuses `JdaAudioSendHandler.continuous`
  and maps the continuous stream to one 20 ms frame per send tick. `JdaBootstrap` creates the runtime
  and the existing cleanup pipeline closes it before JDA shutdown. The Phase 2C finite walkthrough
  remains on its own `AudioFrameSource` path.

**Production review hardening (2026-11-15).**

- Blocking TTS/radio `loadItemSync` calls no longer run on the per-guild mailbox actor. A
  dedicated IO worker performs them and returns results to the actor; load-epoch IDs discard any
  result that arrives after stop/close, and `stop` drains pending sync-load acknowledgements.
- `LavaPlayerEngine.close()` and the JDA runtime/provider/factory closes are atomic and idempotent,
  so concurrent callers cannot double-destroy the player or manager.
- JDA voice connect/disconnect/close are serialized on a single-coroutine dispatcher (no JVM lock
  around JDA operations); `close` sets its flag before waiting and a queued connect cleans its own
  stale session, so a losing connect cannot leak a stream.
- `PlayerSnapshot.connected` was replaced by `hasActiveTrack`; voice connection ownership is
  reported by the gateway adapter, not by the neutral player snapshot.
- A provider exception or malformed provider packet is reported once through
  `PlayerEvent.PlaybackFailed` and terminates the stream, allowing the engine to advance the queue
  instead of hiding the failure as permanent paused silence.
- `PlayerEngine.events` is explicitly **single-subscriber** (one gateway adapter per engine) and
  throws on a second subscriber instead of silently load-balancing events.
- Root uses one Lavaplayer manager for all per-guild engines via a single provider; `GuildQueueService`
  closes every guild engine and then the manager once. `Bot` registers a shutdown hook and also
  closes in `finally`. The leave timer is daemon. The isolated JDA runtime uses the same shared
  factory model.

**Module graph (Phase 3).**

```
root (Kord production, unchanged gateway)
  -> :core, :locale-persistence, :player
:jda-runtime (isolated)
  -> :core, :locale-persistence, :player
:player
  -> :core, Lavaplayer 2.2.6, YouTube 1.17.0, LavaSrc 4.8.1,
     kotlinx-coroutines-core, slf4j-api
:core / :locale-persistence
  -> no gateway library
```

**Deferred / still open.**

- Phase 4 owns the JDA command surface; Phase 3 exposes the engine and voice adapter but adds no
  production JDA player command.
- Phase 5 owns the full staged JDA matrix and any JDA-side leave/retry parity evidence.
- Phase 6 owns the root gateway/build switch and Kord removal. Root stays Kord.
- TestAgent has already added `:player` to the import-scanner source-root classification and added
  engine/continuous-stream/handler/lifecycle coverage locally; Quality Agent evidence remains
  CI/Sonar/staging only and is not claimed yet. No Phase 3 PASS is claimed.
- The Kord `connectToVoiceChannel`/leave-timer paths lack a deterministic direct test seam; the Test
  Agent recorded that as a **local coverage limitation and follow-up**, not as a Phase 3 gate that
  passed and not as a code failure.
- After the Phase 3 PR is created, migrate from ktlint to Detekt in a **separate tooling PR**; do not
  bundle that migration into Phase 3. Phase 3's existing ktlint CI gate must still pass, and this PR
  makes no Detekt dependency/workflow change.

### Phase 4 — Commands, components, messages, channels, locales (isolated JDA adapters)

**Ownership:** root production remains on **Kord** throughout Phase 4. The port lands in neutral
`:core` contracts plus JDA adapters in the isolated `:jda-runtime`; the **root Kord implementation
stays the production implementation until Phase 6**.

**Depends on:** Phase 3 exit.

- Port the remaining command groups and all 18 component IDs into **neutral `:core` contracts** with
  **JDA adapters in `:jda-runtime`**.
- Port message sending/editing, player-channel management, and locale persistence behind the same
  neutral ports (locale persistence contracts and isolated JDA `/locale` already landed in Phase 2D).
- Land the three button-routing fixes with tests.

**Exit criteria**
- Every command family and every component ID is reachable and behaves as on `master` **in the
  isolated JDA implementation**; root Kord parity remains the production baseline until Phase 6.
- Hidden/obfuscated channels work end-to-end in the isolated implementation.
- Locale selection persists across restarts and is applied per guild.
- Routing-fix tests pass and would fail against the pre-fix routing.

### Phase 5 — Test and staging gate (full isolated JDA implementation)

**Ownership:** Phase 5 tests and stages the **full isolated JDA implementation** while the Kord
dependency, the **root Kord application** and the **Kord-root test suite** remain in place. It is
**not** the root production cutover; the root code/build switch and Kord removal are **Phase 6**.

**Depends on:** Phases 3–4 exit.

- Test and stage the **isolated JDA runtime**. Do **not** rewrite, disable or delete the **root Kord
  tests that exercise the production Kord runtime** before the cutover — they remain the production
  regression suite until Phase 6.
- Only **new** neutral/JDA tests must avoid Kord APIs. Existing root Kord tests stay Kord-based and
  keep running as-is.
- Do **not** retire Kord-only test scaffolding or the Kord dependency at this phase; that is Phase 6
  work, not Phase 5 work.
- Execute the full staging matrix on the separate staging bot/token, against the isolated JDA
  implementation.

**Exit criteria**
- Full suite green on the JDA implementation **with the Kord dependency and the untouched root Kord
  suite still present** — the Phase 5 test gate is allowed to pass while Kord remains on the
  dependency graph.
- New neutral/JDA tests assert no Kord-only APIs; no root production-runtime Kord test was rewritten
  or disabled before Phase 6.
- Staging matrix covers: hidden/obfuscated channels, deferred interactions, all command
  families, all components, message and player-channel management, locale persistence, voice
  pause/reconnect/disconnect, DAVE-enabled real playback.
- Known-issue list signed off as "acceptable for cutover" **only** for items that are not gate
  criteria. Any parity gap or any matrix/DAVE failure is a stop condition, not an accepted risk.
- **Not a cutover gate:** a Phase 5 pass is staging evidence for the isolated JDA implementation.
  Root production remains Kord; the root code/build switch happens in Phase 6 and deployment in Phase 7.

### Phase 6 — Root Kord→JDA code/build switch, Kord removal and artifact (pre-deployment)

Phase 6 owns the **root code/build switch**: this is where the root gateway/runtime is replaced in code
and build, and Kord is removed. Root Kord production keeps running through Phases 2–5; Phase 6 does
**not** deploy to production. Phase 6 **produces, tests, stages and verifies** the JDA artifact, and the
actual **production cutover/deployment** of that exact verified artifact is **Phase 7**. Non-cutover
root refactors that preserve Kord were already permitted earlier (for example Phase 2D); Phase 6 is the
point where the root runtime code/build itself changes.

**Depends on:** Phase 5 exit.

- Switch the **root runtime code/build** to the tested JDA lifecycle (login, shard/event wiring, shutdown) and
  replace the root Kord-facing services and commands. Root `Main`/application code may call a narrow
  `:jda-runtime` lifecycle/entrypoint but must not import JDA/DAVE types. Per the owner decision
  (2026-11-14) the custom import scanner is the only architecture gate; no separate reflection or
  public-signature gate is required, and the root-facing API is designed to avoid vendor types without
  claiming an automated signature guarantee.
- Remove Kord dependencies, local `libs/kord-*.jar`, Kord-only dependencies, and Kord-specific
  documentation/CI references. Only Kord-specific docs and references are removed or updated; this
  migration plan (`docs/migrations/kord-to-jda.md`) is **not** Kord-specific documentation and must
  be kept. Note the current setup: the SonarCloud build job sets up **JDK 17**
  (`actions/setup-java`, zulu) despite the Java 21 toolchain, and the ktlint workflow does not set
  up a JDK at all.
- Align the root **production** dependency set to the tested JDA/DAVE stack at this point: add
  `adapter-jda` to the root production set and bump root `libdave` / `libdave-natives` to the 0.2.0
  set proven by the spike. Phase 1 leaves the root production set on its current Kord-era `libdave`
  versions; the root production dependency replacement happens here, not through spike-local
  overrides. Existing catalog aliases consumed only by isolated modules remain additive until used by
  the root production set.
- Extend the **Phase 2 import scanner** to the Phase 6 matrix: remove the root Kord exception and
  apply the module split above — root project source and `:core` / `:locale-persistence` forbid all four
  prefixes, and only `:jda-runtime` may import JDA/jda-ktx/libdave, within the declared adapter packages
  (`es.wokis.jda`, `.command`, `.player`, `.voice` and subpackages). `spikes/jda-dave`
  is non-production: delete it before this gate, or retain it under the explicit non-production
  `spike.jdadave`-package row in the Phase 6 matrix (see the Phase 2 scanner subsection for the exact
  rule).
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
- The root runtime code/build runs the JDA lifecycle and the root Kord bootstrap/services are replaced,
  verified on the staged artifact; production cutover to that artifact is **Phase 7**.
- Root `Main`/application code may call only the **narrow lifecycle/entrypoint exposed by
  `:jda-runtime`** and must **not import JDA/DAVE types**; JDA and DAVE types stay in the
  `es.wokis.jda.*` adapter packages. No generic facade or final entrypoint name is required. **No
  separate reflection-based public-signature gate is required (owner decision 2026-11-14), and there is
  no requirement to add one**: the custom import scanner is the enforcement mechanism; the root-facing
  API is designed to avoid vendor types, and existing reflection tests are complementary only — no
  automated signature guarantee is claimed.
- The build tree contains no Kord source, Kord dependency, or local Kord JAR.
- The **same import scanner** from Phase 2 passes **repo-wide on the Kord-free tree** with the Phase 6
  matrix: the root project source and `:core` / `:locale-persistence` forbid all four vendor prefixes
  (`dev.kord.`, `net.dv8tion.`, `dev.minn.jda.ktx.`, `moe.kyokobot.libdave.`); only `:jda-runtime` may
  import JDA/jda-ktx/libdave, and only inside the declared `es.wokis.jda.*` adapter packages. This is an
  **import-declaration gate only**; it does not inspect public signatures or fully qualified references,
  and no separate reflection gate is approved. Core
  tests mock neutral ports, not library types. `spikes/jda-dave` is either deleted or covered by the
  explicit non-production `spike.jdadave`-package matrix row; it is never production source, and that
  exception is not part of the production adapter allowlist.
- The root version catalog resolves the tested stack: **JDA 6.7.0**, **libdave 0.2.0** including
  `adapter-jda` and the Linux x86_64 natives, with no spike-local force or override carrying the
  runtime. **jda-ktx is conditional**: if jda-ktx is the selected path, record and verify the exact
  candidate and its JDA pairing (currently `jda-ktx 0.14.2` + JDA 6.7.0 as the candidate under test);
  if **direct JDA + coroutine bridge** is selected instead, jda-ktx is **not required** and no
  jda-ktx resolution gate applies. This plan does not force either path.
- `./gradlew clean build` passes on the Kord-free tree.
- A clean build produces the fat JAR with no Kord classes — verified by inspecting the artifact,
  not by trusting the build file — and with no packaging or native defects.
- The build/CI gate passes with an explicit **JDK 21** setup in the SonarCloud build job; the
  ktlint workflow is out of scope unless it is shown to need a JDK. **R3 is closed here.**
- The exact fat JAR being promoted passes the full staging matrix in `eclipse-temurin:21` on
  Linux x86_64, including DAVE-enabled real voice playback.
- Record in migration issue #114 (or the release record), against the staged artifact, all of:
  the **resolved dependency versions**; the **JDK 21 CI pass**; the exact fat JAR **SHA-256
  digest**; the **full staging matrix result**; and the **staging-container digest verification**.
  The **production-server digest verification** is recorded at the Phase 7 cutover. No separate
  evidence file is created.

**Stop condition:** a failed artifact inspection, a digest mismatch, or a failed staging run
blocks cutover. Because the user rejected a Kord rollback, this gate cannot be waived, downgraded,
or replaced by "roll forward".

### Phase 7 — Production deployment of the Phase 6-verified artifact

**Depends on:** Phase 6 exit.

- Stop Kord and start the **exact artifact verified in Phase 6** in production during an announced
  window. The user deploys the JAR through their **external `eclipse-temurin:21` Dockerfile**; this
  repo adds no Docker files and assumes no artifact registry.
- Before start, compute the SHA-256 digest of the production JAR **on the server** and require it
  to equal the digest recorded in Phase 6 **and** verified on staging. Any mismatch is a stop
  condition. Do not rebuild or substitute a different artifact. Same-digest verification applies
  here exactly as in Phase 6.
- Run post-start smoke checks only. The no-rebuild/no-substitution rule applies through cutover
  and after it.

**Exit criteria**
- Production runs the **same** fat JAR that passed Phase 6, proven by matching SHA-256 digest.
- No Kord artifacts on the production classpath.
- Production healthy; rollback path is deliberately absent per decision above.

### Phase 8 — Port parked PRs (post-migration follow-up, after Phase 7)

**This phase is not a prerequisite** for Phase 2, Phase 6, or issue #114 closure unless the owner later
makes it one. It is post-migration follow-up work that runs **after Phase 7**.

- Port #106, #109, #111 individually. **Do not rebase, rewrite or amend the parked branches.** For each
  one, fetch and verify the **live epic tip**, create a **new child branch** from it, then port the
  change as a reviewed **cherry-pick or reimplementation** with its own tests, and land it with a
  **regular merge commit** against the epic integration branch.
- Leave the parked PRs/branches untouched as history; re-scope or close them only when the replacement
  lands.

**Exit criteria**
- Each parked PR is merged/replaced or explicitly re-scoped, with its own tests, through a new child
  branch and regular merge commit.
- No parked branch is rebased or rewritten, and no ported replacement retains Kord-only APIs.
- Phase 8 is follow-up only: it does **not** gate Phase 2, Phase 6 or issue #114 closure.

---

## 4. Risks and open gates

| # | Risk / gate | Status |
| --- | --- | --- |
| R1 | No official `jda-ktx 0.14.2` + JDA 6.7 combination exists | **IN PROGRESS — transport subgate PASS only (2026-10-30)** — resolved and exercised for voice transport. **Conditional:** only applies if jda-ktx is the selected path; if direct JDA + coroutine bridge is selected, no jda-ktx pairing gate applies. Residual for the jda-ktx path: the pairing is still undeclared upstream and must be re-verified against the final Phase 6 artifact |
| R2 | `adapter-jda` 0.2.0: source build catalog declares `compileOnly` JDA 6.3.1; published metadata does not declare/test 6.7.0 | **IN PROGRESS — transport subgate PASS only (2026-10-30)** — DAVE negotiated and played through JDA. Residual: inferred, not certified, and vendor metadata still targets 6.3.1; re-verify on the Phase 6 artifact |
| R3 | The SonarCloud build job explicitly sets up **JDK 17** (`actions/setup-java`, zulu) despite the build's **Java 21** toolchain; the ktlint workflow does **not** set up a JDK at all | Open — **go/no-go**; closed only when the SonarCloud build job sets up JDK 21 explicitly and that build/CI gate passes (Phase 6 exit criterion) |
| R4 | `fatJar` uses `DuplicatesStrategy.EXCLUDE`; duplicate handling, OkHttp, JNA and `META-INF/services` entries are unverified for the JDA stack | Open — Phase 6 artifact gate; the Phase 1 evidence came from the isolated spike build, not the root fat JAR |
| R5 | `docker/docker-compose.yml` references build contexts that do not exist in this repo; the real Dockerfile is external | Fact — no action in repo, confirm image contract with owner |
| R6 | Base image immutability/pinning is unconfirmed; `eclipse-temurin:21` is currently glibc/Ubuntu, and `/tmp` `noexec` would break extracted JNI natives | Open — Phase 6 staging gate; verify or pin |
| R7 | Existing Kord build does not support channel obfuscation at all | Fact — this is the reason for the migration, not a regression |
| R8 | Per-guild serialization regressions when porting concurrent Kord handlers | Open — Phase 3 go/no-go (isolated JDA real-player slice; root production stays Kord) |
| R9 | Schedule pressure: Nov 16 is a readiness target; the 17–19 day estimate is unvalidated | Open — re-estimate now that the DAVE transport subgate and Phase 1's missing-native fail-fast check both have recorded evidence (2026-11-08) |
| R10 | Domain/command code can silently re-couple to Kord/JDA during the port, defeating library replaceability | **Open — Phase 6 scope only**, enforced in two scopes by the **custom import-declaration scanner** (no new dependency): Phase 2 forbids Kord/JDA/jda-ktx/libdave in `:core`/`:locale-persistence`, forbids JDA/jda-ktx/libdave in the root (Kord grandfathered), and allows JDA/jda-ktx/libdave in `:jda-runtime` only inside declared adapter package boundaries — **the Phase 2 scope is now green in CI via PR #120 (2026-11-15)**; Phase 6 removes the root Kord exception and applies it **repo-wide on the Kord-free tree**, which remains open. Core tests mock neutral ports |
| R11 | Ordering hazard: root JDA code/build switch attempted before the isolated implementation is proven, or Kord removed before the exact artifact is verified | Open — Phase 6/7 ownership: the root code/build switch and Kord removal happen in **Phase 6** on the Kord-free tree, and production deployment of the exact verified artifact in **Phase 7** |
| R12 | Import-rule scanner blind spots: import-declaration scanning does not catch fully-qualified references used without an import, reflection string targets, or unusual comment layouts | Accepted limitation — documented in the Phase 2 scanner subsection. The `VoiceBoundaryReflectionTest`/neutral-port reflection tests remain complementary evidence, not a public-signature policy. **No separate reflection/API-signature gate is required (owner decision 2026-11-14)**; extending to full Kotlin type-use/FQ reference analysis or signature proof is not required by this plan |

Open entries marked as gates are **stop conditions** per §3. They are never downgraded to
accepted risk, and a documented blocker does not close a gate.

## 5. Tracked and ignored local artifacts

- The epic integration branch `feature/114-migrate-to-jda-jda-ktx` contains
  `5fa9c63bc85ad3d5df1ed9c43c05f3e9b7d72e85`, the **regular merge commit of PR #118** (parents
  `255893a`, the regular merge commit of PR #117, and `3e50248`, the final Phase 2C PR head), plus the
  documentation-only `269acc1` that recorded the Phase 2C staging pass and was the epic tip when the
  earlier documentation update was written; that tip statement is **historical**. The Phase 2D
  child branch was based on `1304aa6`; its tested implementation snapshot `4ff8167`, the `a4bc1ae`
  time-qualified green snapshot, and the later docs-only heads `8c20a1f` (fully green at the
  recorded 2026-11-14 snapshot) and `0b365aa` (`Finalized Phase 2D gate record`, all four checks
  green) were pushed. PR #119 is **merged** into the epic branch by the regular merge commit
  `f56d225` (2026-11-14; parents `1304aa6` and `0b365aa`), so the epic branch advanced to `f56d225`
  at that recorded snapshot; **`f56d225` was the epic tip before the earlier documentation update**,
  and the later documentation-only `38bdba1` (`Recorded Phase 2D merge and next steps`) was the epic
  tip **before this documentation-only edit**. The live tip is read from fetched Git/GitHub. PRs #115,
  #116, #117, #118 and #119 are **closed/merged** and the former `feature/114-kord-to-jda-migration` child
  branch's remote ref is deleted (a stale local ref may remain); no open Phase 2C or 2D branch/PR remains. The merged 2C work (implementation,
  tests, tone resource and formatting-only ktlint repairs) was pushed at `8eb0349`, followed by the
  docs-only `9fd1826` and the final PR head `3e50248`; all four checks succeeded on the recorded heads
  and the Sonar Quality Gate passed with 90.1% new-code coverage, 11 new issues, 0 security hotspots
  and 0.0% duplication (the 11 issues are recorded as **tracked follow-up debt** under the
  owner-approved 2026-11-14 interpretation — the configured Quality Gate is the criterion — not
  zero). Those are dated
  historical checkpoints; read the live GitHub check runs for any later epic head. **Phase 2C is
  PASS — gate closed (2026-11-14)** on the sanitized user-run staging evidence in the Phase 2C
  section. **Phase 2D is PASS — gate closed (2026-11-14)** on the `a4bc1ae` time-qualified green snapshot (all
  four checks green, Sonar Quality Gate OK) and the sanitized staging/restart evidence in the Phase 2D
  section; PR #119 is **merged** into the epic branch by the regular merge commit `f56d225` (2026-11-14).
- **Phase 3 working branch** `feature/114-phase-3-player-engine` is based on the epic tip `6d14167`
  (`Recorded Phase 2 import-rule closure`) and contains the uncommitted Phase 3 implementation, test,
  scanner-classification, build and documentation working-tree changes. The `:player` tests, JDA
  continuous-stream/lifecycle tests and the scanner's `:player` classification are implemented and
  pass locally; **no new external dependency or version** was added. At the **time-qualified
  2026-11-15 snapshot** the branch had **not been pushed and no PR existed**; the local
  **1,024-case (1,020 executed + 4 pre-existing skips, 0 failures/errors)** snapshot is **local
  working-tree evidence only**, and its 42-task build and changed-file ktlint **0 findings** are
  recorded in full, with the exact per-module counts, in the Phase 3 local verification snapshot, the
  authoritative detailed breakdown (summaries elsewhere may repeat the headline totals). Phase 3 is
  **IN PROGRESS — implementation landed, gate remains open/not passed**; the
  `GuildLavaPlayerService.kt` Sonar exclusion was removed but its
  Sonar new-code coverage gate is **pending live PR CI** and must not be hidden or waived if CI
  fails. Read the live PR checks after push; the stacked child PR and later heads are not asserted
  here.
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
  into the epic branch** by PR #115 as `5aad7c7`; the remote branch is deleted (a stale local ref may remain). Phase 2A is
  **PASS — gate closed (owner scope decision 2026-11-14)** for its delivered neutral-value/locale-persistence
  scope, and the root Kord locale service/command remain **Phase 6** scope. Current Git and PR
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
- Phase 2C is **PASS — gate closed (2026-11-14)** and **merged** into the epic branch: the plan was
  committed first on `feature/114-phase-2c-voice-ports`, created from the fetched epic head
  `255893a`; the neutral ports, JDA adapters, DAVE 0.2.0 wiring, tone resource, unit tests and
  formatting-only ktlint repairs were pushed on head `8eb0349`, followed by the docs-only `9fd1826`
  and the final PR head `3e50248`, and PR #118 was merged by the regular merge commit `5fa9c63`
  (parents `255893a` and `3e50248`). **No open Phase 2C PR remains.** All four checks succeeded on
  the recorded heads — Check Code Quality, Build and analyze, SonarCloud Code Analysis and
  SonarCloud — and the Sonar Quality Gate passed with 90.1% new-code coverage, 11 new issues (10
  major, 1 critical), 0 security hotspots and 0.0% duplication; the 11 issues are recorded as
  **tracked follow-up debt** under the owner-approved 2026-11-14 interpretation (the configured
  Quality Gate is the criterion), not zero. The earlier `c893567` failed only Check Code Quality, on 25 formatting
  findings. Local results on the formatted tree: `:core` 81, `:jda-runtime` 224 and root 380 tests
  (5 pre-existing skips) — 685 cases, 680 executed + 5 skips, 0 failures/errors, all passing; local
  module JaCoCo line coverage `:core` 207/219 = 94.52% (branch 108/115 = 93.91%) and
  `:jda-runtime` 870/948 = 91.77% (branch 342/418 = 81.82%) — local module reports only, not
  Sonar/new-code CI evidence — and the clean full `./gradlew build` passed locally. The recorded
  checkpoints are dated historical evidence; read the live GitHub check runs for any later epic head.
  The gate was closed by the sanitized **user-run 2026-11-14 staging record** in the Phase 2C section
  (local macOS arm64 with the `natives-darwin` DAVE 0.2.0 native, only `GUILD_VOICE_STATES`, the reply
  `Voice walkthrough OK | handed 75/75 frames to JDA's send loop (delivery not confirmed)` with the
  frame count as a hand-off only, an audible beep reported by the
  invoking user's own Discord client, separate from the bot, as the delivery evidence (a distinct
  observation from the hand-off), completion-path disconnect cleanup, deliberate Ctrl+C, no receive
  path). There is
  **no root wiring and no Phase 6 artifact validation**, and `master` is untouched.
- `stash@{0}` — housekeeping WIP; preserved, untouched.
- `stash@{1}` — assistant voice feature WIP (out of scope); preserved, untouched.
- Ignored caches and user data (for example `.gradle/`, `build/`, `data/`, `.idea/`, `.memory/`,
  `.opencode/node_modules/`) are **gitignored**, untouched, and stay that way.
