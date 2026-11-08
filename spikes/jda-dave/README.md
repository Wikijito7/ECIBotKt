# JDA 6.7 / jda-ktx / libdave staging runtime probe

Isolated **Phase 1** spike for issue #114. This project is standalone: it has its own
`settings.gradle.kts`, its own `fatJar` task, and is **not** included by the root build. It
does not change the root production build, root version catalog, root settings, production
source, or the production `runtimeClasspath`. It does not use Kord.

## What this validates — and what it does not

This is a **test-only staging transport probe**. It validates:

- JDA 6.7.0 can log in with the minimal gateway intent.
- JDA accepts `AudioModuleConfig.withDaveSessionFactory(LDJDADaveSessionFactory(NativeDaveFactory()))`.
- The libdave `0.2.0` Linux x86-64 native loads and reports a non-zero DAVE protocol version.
- A guild-scoped `/voice-test` command can connect the bot to the invoking user's voice
  channel, negotiate DAVE, send a generated 440 Hz tone, and reach the requested
  disconnected state.

It **does not** validate Lavaplayer integration, real track decoding, the production audio
pipeline, or full application playback. Those remain a later gate. A successful probe is
transport evidence only, not approval of the full migration. The public JDA API also cannot
attribute the cause of a `NOT_CONNECTED`; the probe reports connection state and treats the
audible tone as the decisive transport evidence.

The probe has since been run on the Linux x86-64 staging target and the transport claims above
were observed; the sanitized result is recorded in
`docs/migrations/kord-to-jda.md` (Phase 1 staging result). That run is transport evidence only:
it does not validate Lavaplayer integration or production playback.

## Candidate matrix

| Component | Coordinate | Version |
| --- | --- | --- |
| Java runtime | `eclipse-temurin:21` (Linux x86-64) | 21 |
| Kotlin | `org.jetbrains.kotlin.jvm` Gradle plugin | 2.3.10 |
| Gradle | repository wrapper | 8.13 |
| JDA | `net.dv8tion:JDA` | 6.7.0 (explicit, forced in this project) |
| jda-ktx | `club.minnced:jda-ktx` | 0.14.2 |
| DAVE API | `moe.kyokobot.libdave:api` | 0.2.0 |
| DAVE JNI impl | `moe.kyokobot.libdave:impl-jni` | 0.2.0 |
| DAVE JDA adapter | `moe.kyokobot.libdave:adapter-jda` | 0.2.0 |
| DAVE Linux natives | `moe.kyokobot.libdave:natives-linux-x86-64` | 0.2.0 |
| Logging (spike only) | `org.slf4j:slf4j-simple` | 2.0.19 |

`jda-ktx:0.14.2` requests `JDA:6.3.2` transitively. This project deliberately forces
`JDA:6.7.0` on its own compile/runtime classpaths only. The `adapter-jda:0.2.0` POM does not
declare a JDA dependency; its compatibility with 6.7.0 is exactly what the staging run must
prove.

## Environment variables

The probe reads **only** these names. There are no defaults for the token or guild, and no
secret is stored in this repository.

| Variable | Required | Meaning |
| --- | --- | --- |
| `DISCORD_BOT_TOKEN` | yes | Token of the **separate staging bot**. Never print, commit, or pass it on the command line. |
| `DISCORD_TEST_GUILD_ID` | yes | Positive numeric ID of the throwaway staging guild (validated at startup; non-numeric, zero and negative values are rejected). |
| `SPIKE_KEEP_ONLINE` | no | One-shot mode (exit after the first completed probe) unless the value is exactly `true`, `1`, or `yes` after trimming surrounding whitespace. Those three values are accepted case-insensitively. `false`, `0`, `no`, an empty value, an absent variable, or any other value keeps one-shot mode. |

Create a local env file outside version control, or materialize it from your deployment
secret store for the duration of the run:

```text
# spikes/jda-dave/spike.env  (git-ignored)
DISCORD_BOT_TOKEN=<staging-bot-token>
DISCORD_TEST_GUILD_ID=<numeric-test-guild-id>
# SPIKE_KEEP_ONLINE=false   # true/1/yes (case-insensitive) keeps the bot online
```

Protect and exclude the file:

- `chmod 600 spikes/jda-dave/spike.env` and keep it outside version control. The spike
  `.gitignore` already covers `.env`, `*.env` and `spike.env`; confirm with
  `git check-ignore -v spikes/jda-dave/spike.env`.
- Never print the token (`cat`, `env`, `set -x`, CI logs) and never put it in a command
  line: `docker run -e DISCORD_BOT_TOKEN=...` lands in shell history and process listings.
  Never commit the file; if it is ever committed, rotate the token first.
- `docker --env-file` only moves the problem: the values become ordinary environment
  entries inside the container, and a privileged user on the Docker host can still read
  them with `docker inspect` and from `/proc/<pid>/environ` of the container process.
  Prefer a protected/root-owned `600` env file, a mounted secret file consumed through the
  platform's secret mechanism, or a dedicated secret manager where the host provides one.
  Keep the token in one host only, and delete the materialized env file after the run
  (`rm -f spikes/jda-dave/spike.env`).

The token is never logged. `SpikeConfig.toString()` deliberately omits it.

## Staging bot setup

1. Create a **separate staging application/bot**. Do not reuse the production bot.
2. Invite it to the test guild with OAuth2 scopes `bot` and `applications.commands`.
3. Grant it these channel/guild permissions: **View Channel**, **Connect**, **Speak**.
   `Connect` is required to join; `Speak` is required for Discord to carry the tone.
4. Gateway intents: only **`GUILD_VOICE_STATES`** is enabled by the probe. It is not a
   privileged intent and needs no Developer Portal toggle. Do **not** enable Message
   Content or Server Members for this probe.
5. The bot must already be a member of `DISCORD_TEST_GUILD_ID`.

`/voice-test` is registered **guild-scoped** with `Guild.upsertCommand`, so it appears
immediately and does not affect other guilds or other commands in the test guild.

## Build the probe JAR (macOS or Linux)

From the repository root, using the repository wrapper:

```bash
./gradlew -p spikes/jda-dave clean compileKotlin
./gradlew -p spikes/jda-dave fatJar
```

The artifact is:

```text
spikes/jda-dave/build/libs/jda-dave-spike-0.1.0-all.jar
```

`compileKotlin` also compiles `DaveJdaCompileProbe`, a compile-only API canary for
`LDJDADaveSessionFactory` and jda-ktx. Building on macOS is fine; **running** the probe is
not supported on macOS or arm64 because the DAVE native in this candidate is Linux x86-64
only.

Inspect the resolved graph and the artifact:

```bash
./gradlew -p spikes/jda-dave dependencies --configuration runtimeClasspath
./gradlew -p spikes/jda-dave dependencyInsight \
  --dependency JDA --configuration runtimeClasspath

jar tf spikes/jda-dave/build/libs/jda-dave-spike-0.1.0-all.jar \
  | grep -E 'natives/linux-x86-64/(libdave-jvm|libopus)\.so|com/sun/jna/linux-x86-64/libjnidispatch\.so'
unzip -p spikes/jda-dave/build/libs/jda-dave-spike-0.1.0-all.jar META-INF/MANIFEST.MF
```

Required native entries in the fat JAR:

```text
natives/linux-x86-64/libdave-jvm.so
natives/linux-x86-64/libopus.so
com/sun/jna/linux-x86-64/libjnidispatch.so
```

The Opus and JNA natives are needed because the tone is supplied as PCM and JDA encodes it
with its Opus encoder before DAVE encryption. If any of these entries is missing, stop and
fix packaging before deploying.

## Negative test: missing DAVE native must fail fast (Phase 1B fail-fast gate)

This is a **separate, safe artifact** used only to prove the startup guard. It is not a
voice test and it never contacts Discord. The normal staging jar above is left untouched.

### Build and inspect

```bash
./gradlew -p spikes/jda-dave verifyNegativeTestJar
```

That task builds both jars and fails unless all of the following are true:

- `jda-dave-spike-0.1.0-all.jar` **contains** `natives/linux-x86-64/libdave-jvm.so`.
- `jda-dave-spike-negative-no-dave-native-0.1.0-all.jar` **does not contain** it.
- Both jars still contain `natives/linux-x86-64/libopus.so`,
  `com/sun/jna/linux-x86-64/libjnidispatch.so`, `spike/jdadave/MainKt.class`,
  `moe/kyokobot/libdave/NativeDaveFactory.class` and the known JDA class
  `net/dv8tion/jda/api/JDA.class`.
- Every entry present in both jars has identical bytes (SHA-256 compared); a shared entry
  that differs fails the task.
- The negative jar differs from the normal jar **only** by the DAVE native entry.
- `Main-Class` is `spike.jdadave.MainKt` in both jars.

Artifacts:

```text
spikes/jda-dave/build/libs/jda-dave-spike-0.1.0-all.jar                                  (normal, unchanged)
spikes/jda-dave/build/libs/jda-dave-spike-negative-no-dave-native-0.1.0-all.jar           (negative test only)
```

The negative task uses `negativeTestRuntimeClasspath`, which extends the normal
`runtimeClasspath` and excludes only `moe.kyokobot.libdave:natives-linux-x86-64:0.2.0`.
It is reproducible (`isPreserveFileTimestamps = false`, `isReproducibleFileOrder = true`)
and declares its own manifest with the same attributes as the normal jar (`Main-Class`,
`Implementation-Title`, `Implementation-Version`, `Multi-Release`); it does not reuse the
normal manifest file. The verifier checks that equality: both jars must report the same
`Main-Class`, and the shared `META-INF/MANIFEST.MF` entry is covered by the byte-identical
shared-entry comparison.

On network access, be precise:

- The **runtime** negative container makes **no Discord connection at all**: it runs with
  `--network none`, so no gateway/HTTP path exists **inside the running container**.
- `--network none` applies to the container only, and only once it exists. Image acquisition
  happens earlier, on the **Docker host**: if `eclipse-temurin:21` is not already cached locally,
  `docker run` asks the daemon to pull it over the **host's** network, which `--network none`
  does not restrict. A fully offline run therefore requires the image to be pre-pulled on the
  daemon host (for example `docker pull eclipse-temurin:21` while the host still has network
  access, or `docker load` from a saved archive). If the host is offline and the image is absent,
  `docker run` fails before any container starts; that is a "test not run" outcome, not a pass.
- The verifier's **inspection action** makes **no Discord and no network call**: once the JARs
  exist it only reads their entries and compares SHA-256 hashes of the entries they share.
  That inspection step does not itself write files.
- Invoking `./gradlew verifyNegativeTestJar` is **not** only that inspection. The task depends
  on the `fatJar` tasks, so Gradle may re-run them, write compilation output and JARs under
  `spikes/jda-dave/build/`, and resolve Maven dependencies or plugins over the network when
  they are not already in the local Gradle cache. Do not claim that running the Gradle task
  writes nothing.
- The surrounding **Gradle build is not offline**. Gradle dependency and plugin resolution may
  still require network access unless those artifacts are already in the local Gradle cache.
  Do not describe the task or the build as "offline" or "offline-safe"; the correct claim is
  "packaging-only, with dependency resolution potentially requiring network".

Manual equivalent of the guard:

```bash
jar tf spikes/jda-dave/build/libs/jda-dave-spike-0.1.0-all.jar \
  | grep -F 'natives/linux-x86-64/libdave-jvm.so'
jar tf spikes/jda-dave/build/libs/jda-dave-spike-negative-no-dave-native-0.1.0-all.jar \
  | grep -E 'natives/linux-x86-64/(libdave-jvm|libopus)\.so'   # only libopus may match
```

### Run the negative test (`--network none` container)

**Token rule:** the command below uses
`DISCORD_BOT_TOKEN=negative-test-placeholder-not-a-real-token`. That string is a **fake
placeholder**, not a credential. It must **never** be replaced with a real staging or
production token for this negative test. No real token is needed and none may be used: the
native failure must happen **before** any JDA login, so a real token would muddy the
evidence — the run must show that the native gate trips, not that a login would have failed.
The config parser does read the placeholder value to validate that required configuration is
non-empty; the value is never used for a JDA login, because the process fails fast at the
DAVE native load.

Run the block exactly as written. The whole run — the `docker run` call, the `rc` capture, the
`check` helper, every assertion, and the final PASS/FAIL verdict — is wrapped in a POSIX
subshell `( ... )`. The fake `DISCORD_BOT_TOKEN` and `DISCORD_TEST_GUILD_ID` values, the
`NEGATIVE_JAR` default, `LOG` and `failed` are assigned **only inside that subshell** and never
alter the caller's environment, even when names such as `DISCORD_BOT_TOKEN` were already
exported in the invoking shell. An externally exported `NEGATIVE_JAR` is only read as the
override. The parent shell's variables keep their previous values.

### Absolute host paths: why an earlier attempt failed with `No such file or directory`

An earlier attempt redirected the run to the **relative** path
`spikes/jda-dave/spike-negative.log` while the invoking shell's current directory was the
user's server directory, where no `spikes/jda-dave/` exists. A shell sets up output
redirection **before** it executes the command, so that redirection failed with
`No such file or directory` and `docker run` never started — no container was created and no
image was pulled. This is a current-directory/path problem, **not** a zsh-vs-Bash
incompatibility: both shells perform redirection before `exec`, and both fail the same way
when the target directory is absent.

The same rule applies to the jar bind mount. `-v` takes a **host** path that must exist on the
machine where the Docker daemon runs — not a path inside the container, and not a path
relative to some other working directory. With a remote daemon or a Docker Desktop VM, only
the configured shared paths are visible, so copy the jar there or point `NEGATIVE_JAR` at a
shared absolute path.

The block below therefore uses two absolute host paths:

- `NEGATIVE_JAR` — absolute host path of
  `jda-dave-spike-negative-no-dave-native-0.1.0-all.jar`. When the block runs from the
  repository root it defaults to the `realpath` of
  `$PWD/spikes/jda-dave/build/libs/jda-dave-spike-negative-no-dave-native-0.1.0-all.jar`
  (`realpath` resolves symlinks in the whole path, because the Docker daemon resolves the bind
  source, not this shell). The fallback used when `realpath` is unavailable is **weaker**:
  `pwd -P` makes the *containing checkout directory* physical and absolute, but it does **not**
  resolve a symlink on the jar file itself, so a symlinked jar would remain a symlink in
  `NEGATIVE_JAR`. The default artifact here is a regular Gradle-built file under `build/libs/`,
  not a symlink, so the fallback is adequate for the documented default; if you swap the jar for
  a symlink, use `realpath` or pass a canonical absolute `NEGATIVE_JAR` instead.
  Replace it with the actual file location when you run from anywhere else or when the Docker
  daemon is remote: either `export NEGATIVE_JAR=/abs/path/to/...-all.jar` before the block or
  edit the assignment. An explicit `NEGATIVE_JAR` is never rewritten or resolved, so it must
  already be the absolute path **as seen by the Docker daemon host**. A preflight always
  rejects a relative value and exits before Docker with a clear message; a missing default jar
  is also a hard stop, while a missing override only warns, because a remote daemon may hold
  the jar at that path even when this shell cannot see it.
- `LOG=/tmp/spike-negative.log` — absolute host path for the process output, so host
  redirection works regardless of the current directory. It is written by your local shell,
  not by the container. Pick another absolute writable path (for example under `/var/tmp`)
  if `/tmp` is unsuitable.

Inside the subshell the expected-failure `docker run` is wrapped in
`if ...; then rc=0; else rc=$?; fi`, so the non-zero Docker exit status is captured instead of
aborting the block — this holds **even if the caller has `set -e` enabled**. All assertions
run against `rc`; the variable is deliberately `rc` and not `status`, because `status` is a
read-only special variable in zsh. Log capture goes to the absolute path
`/tmp/spike-negative.log`, outside the repository, so it does not depend on the current
directory and needs no `.gitignore` entry.

The subshell uses no relative paths. The only working-directory assumption is the default
value of `NEGATIVE_JAR`: it is built from `$PWD/spikes/...` and then passed through `realpath`
(falling back to `pwd -P`), so it is correct only from the repository root; set `NEGATIVE_JAR`
to an absolute path in every other case. Note that the `pwd -P` fallback only makes the
containing directory physical and absolute — it does not resolve a symlink on the jar file
itself. That is sufficient for the default regular Gradle-built artifact; for a symlinked jar,
use `realpath` or supply a canonical absolute `NEGATIVE_JAR`. If the Docker host is the server
and that server holds only the JAR, set `NEGATIVE_JAR` to the jar's absolute path **on the
server** — from the build machine that path need not exist, and the missing-file preflight only
warns for an explicit override. The Docker values stay inside the subshell, are passed to the
container with `-e`, and are never `unset` — a parent shell's own values are left untouched. The
subshell's exit status is non-zero only when a check fails, so a failing run is still reported
to the caller without the expected `docker run` status `3` ever aborting the block.

```bash
# Run from the repository root: NEGATIVE_JAR then defaults to the resolved absolute path of
# the in-repo artifact. From anywhere else, pass NEGATIVE_JAR as an absolute host path.

# POSIX subshell: the fake values and all helper state live only in this block.
# Nothing here is exported into, unset in, or otherwise written to the caller's shell.
(
  jar_from_override=0
  if [ -n "${NEGATIVE_JAR:-}" ]; then
    # Use the override verbatim: it names a path on the Docker daemon host, which may be a
    # different machine, so it must not be rewritten or resolved here.
    jar_from_override=1
  else
    # Repository-root default. Resolve it to a real absolute path with symlinks removed,
    # because the bind-mount source is resolved by the Docker daemon, not by this shell.
    default_jar="$PWD/spikes/jda-dave/build/libs/jda-dave-spike-negative-no-dave-native-0.1.0-all.jar"
    NEGATIVE_JAR=$(realpath "$default_jar" 2>/dev/null \
      || printf '%s\n' "$(pwd -P)/spikes/jda-dave/build/libs/jda-dave-spike-negative-no-dave-native-0.1.0-all.jar")
  fi

  # Fake placeholder only. Never substitute a real token for this negative test.
  DISCORD_BOT_TOKEN=negative-test-placeholder-not-a-real-token
  DISCORD_TEST_GUILD_ID=123456789012345678

  # Absolute host path, written by the local shell (not by the container), so host
  # redirection works regardless of the current directory.
  LOG=/tmp/spike-negative.log

  # Preflight before Docker: reject a relative path and stop clearly when the default jar
  # is missing. Otherwise the failure would surface later as a confusing "No such file or
  # directory", or as an empty directory created by `-v` for an absent bind source.
  case "$NEGATIVE_JAR" in
    /*) ;;
    *)
      printf 'FAIL  NEGATIVE_JAR must be an absolute host path, got: %s\n' "$NEGATIVE_JAR" >&2
      echo "NEGATIVE TEST NOT RUN - Phase 1B gate stays OPEN" >&2
      exit 1
      ;;
  esac
  if [ ! -f "$NEGATIVE_JAR" ]; then
    if [ "$jar_from_override" -eq 1 ]; then
      # An explicit override is not required to be readable from this shell: a remote
      # Docker daemon resolves the bind-mount source on the daemon host.
      printf 'WARN  NEGATIVE_JAR is not readable from this shell: %s\n' "$NEGATIVE_JAR" >&2
      echo "Continuing: the Docker daemon host must hold the jar at exactly that absolute path." >&2
    else
      printf 'FAIL  negative jar not found: %s\n' "$NEGATIVE_JAR" >&2
      echo "Point NEGATIVE_JAR at the actual jar on the Docker host and re-run; Docker was not started." >&2
      echo "NEGATIVE TEST NOT RUN - Phase 1B gate stays OPEN" >&2
      exit 1
    fi
  fi

  # The expected non-zero exit is captured, never raised: `docker run` is the whole
  # `if` condition, so this block stays safe even when the caller has `set -e` enabled.
  # `rc` (not `status`) avoids zsh's read-only special variable.
  # No --restart flag here: Docker's default restart policy is already `no`, and `--rm`
  # cannot be combined with an explicit restart policy (Docker rejects the pair).
  if docker run --rm \
    --name jda-dave-spike-negative \
    --platform linux/amd64 \
    --network none \
    -e DISCORD_BOT_TOKEN="$DISCORD_BOT_TOKEN" \
    -e DISCORD_TEST_GUILD_ID="$DISCORD_TEST_GUILD_ID" \
    -v "$NEGATIVE_JAR:/app/jda-dave-spike-negative.jar:ro" \
    eclipse-temurin:21 \
    java -jar /app/jda-dave-spike-negative.jar > "$LOG" 2>&1
  then
    rc=0
  else
    rc=$?
  fi

  failed=0
  check() {
    label="$1"
    shift
    if "$@"; then
      printf 'PASS  %s\n' "$label"
    else
      printf 'FAIL  %s\n' "$label"
      failed=1
    fi
  }

  check "exit code is exactly 3 (EXIT_NATIVE), got $rc" test "$rc" -eq 3
  check "[dave] native library is unavailable" grep -qF '[dave] native library is unavailable' "$LOG"
  check "native load error names UnsatisfiedLinkError or Required library was not found" \
    grep -qE 'UnsatisfiedLinkError|Required library was not found' "$LOG"
  check "zero [gateway] logging in lines" test "$(grep -cF '[gateway] logging in' "$LOG")" -eq 0
  check "zero [gateway] ready lines" test "$(grep -cF '[gateway] ready' "$LOG")" -eq 0
  check "placeholder token never printed" test "$(grep -cF 'negative-test-placeholder-not-a-real-token' "$LOG")" -eq 0

  if [ "$failed" -ne 0 ]; then
    echo "NEGATIVE TEST FAILED - Phase 1B gate stays OPEN; inspect $LOG"
    exit 1
  fi
  echo "NEGATIVE TEST PASSED - exit=3 with zero gateway lines; keep $LOG as Phase 1B evidence"
)
```

- `--network none` guarantees no gateway/HTTP path exists **for the running container**. It
  does not cover image acquisition: pulling `eclipse-temurin:21` when it is not cached uses
  the Docker **host's** network before the container starts. Keep the host disconnected from
  Discord for this test, but note that a fully offline run requires the image to be pre-pulled.
- There is no `--restart` flag. Docker's **default** restart policy is `no`, which is exactly
  the behaviour this test needs: the expected failure must not be retried. `--rm` cannot be
  combined with an explicit restart policy — `docker run --rm --restart=no` is rejected with
  `Conflicting options: --restart and --rm` — so the flag is omitted rather than restated.
- `--platform linux/amd64` and `eclipse-temurin:21` pin the supported Linux x86-64 runtime;
  the DAVE native in this candidate is Linux x86-64 only.
- `NEGATIVE_JAR` is an absolute **host** path, mounted **read-only** (`:ro`) at
  `/app/jda-dave-spike-negative.jar`; the negative jar writes no data. When unset, it is the
  `realpath` of
  `$PWD/spikes/jda-dave/build/libs/jda-dave-spike-negative-no-dave-native-0.1.0-all.jar`
  (or, if `realpath` is unavailable, `$(pwd -P)/spikes/...`, which makes the containing
  directory physical but does not resolve a symlink on the jar itself), so the default is
  correct only from the repository root. The default artifact is a regular Gradle-built file,
  not a symlink; if you substitute a symlinked jar, use `realpath` or pass a canonical
  absolute `NEGATIVE_JAR`. A relative value stops the preflight with `NEGATIVE TEST NOT RUN`
  before Docker is invoked — that outcome neither passes nor fails the gate; it means the test
  did not run. A missing default jar is also a hard stop; a missing explicit override only
  warns, because the jar may live on a remote daemon host.
- `LOG=/tmp/spike-negative.log` is written by the local shell on the host. It is not inside
  the repo, so no `.gitignore` entry is needed, and it is absolute, so the current directory
  cannot break redirection.
- The numeric `DISCORD_TEST_GUILD_ID` is a placeholder too; it only has to pass config
  validation, because the process exits before guild lookup.

Expected result: **exit code exactly `3`** (`EXIT_NATIVE`) and output that stops at the DAVE
load. The sample below is illustrative — the loader/JDK wording varies — but the structure
(exit `3`, no `[gateway]` line) must hold:

```text
JDA/DAVE staging transport probe (test-only, not production)
[env] java=... jda=6.7.0 os=Linux arch=amd64
[config] SpikeConfig(testGuildId=<numeric-id>, keepOnline=false, ...)
[dave] loading native library for linux-x86-64
Native library dave-jvm: loading with filter null
Native library dave-jvm: loading failed.
java.lang.UnsatisfiedLinkError: Required library was not found
...
[dave] native library is unavailable: java.lang.UnsatisfiedLinkError: Required library was not found
...
```

The exact loader wording may vary by lava-common/JDK/slf4j version, so the assertion does
**not** require the literal phrase `Required library was not found`. The required signals are:

- `[dave] native library is unavailable` (exact text, required), and
- a native-load error matching **either** `UnsatisfiedLinkError` **or**
  `Required library was not found` — asserted as
  `grep -qE 'UnsatisfiedLinkError|Required library was not found' "$LOG"`
  (ordinary `grep`, no bash-only `[[ ... ]]`, so it runs in both bash and zsh), plus
- exit `3`, and **zero** `[gateway] logging in` and **zero** `[gateway] ready` lines.

The token is never logged; `[config]` omits it.

If any check prints `FAIL`, the run is a **failure**: the negative artifact still had a
usable DAVE native, the wrong jar/command was used, or a login was attempted. Exit `2` is
also a failure for this test (config or platform rejection, not the native gate); exit `0`
or `1` is never expected.

### Scope of this evidence

**Building or verifying the JARs does not close Phase 1B.** `verifyNegativeTestJar` proves
packaging only: it reads JAR entries and shared-entry hashes, and it makes no network or
Discord call of its own (running the Gradle task can still write build output and resolve
dependencies over the network — see above). The gate remains **OPEN** until the **user** runs
the exact negative path documented above — the Linux amd64 `eclipse-temurin:21` container with
`--network none`, Docker's default restart policy (`no`, so no `--restart` flag alongside
`--rm`), the fake placeholder token, `NEGATIVE_JAR` set to the negative jar's absolute host
path (the resolved in-repo artifact or an explicit absolute path on the Docker daemon host),
and the assertion block that
requires exit `3` (`EXIT_NATIVE`), the `[dave] native library is unavailable` line together
with an `UnsatisfiedLinkError` **or** `Required library was not found` native-load error, and
**zero** `[gateway] logging in` **and zero** `[gateway] ready` lines — matching the migration
plan's Phase 1B evidence requirements.
Nothing in this README records that run as executed or passed; do not report the gate as
closed until the user has produced that evidence.

A successful negative test proves only that the probe **fails fast when the DAVE native is
absent**, before any gateway login. It does **not** replace the successful audible DAVE
voice test recorded in `docs/migrations/kord-to-jda.md`. The audible run remains the
transport evidence; this negative artifact is a guard check only.

## Deploy and run on the external Temurin 21 container

The target must be Linux **x86_64/amd64** with glibc (`eclipse-temurin:21` is suitable).
Do not use an Alpine/musl image unless you have separately verified the natives. If the
Docker host is separate from the build host, copy the jar and the env file there first.

```bash
docker run --rm -it \
  --name jda-dave-spike \
  --platform linux/amd64 \
  --env-file "$PWD/spikes/jda-dave/spike.env" \
  -v "$PWD/spikes/jda-dave/build/libs/jda-dave-spike-0.1.0-all.jar:/app/jda-dave-spike.jar:ro" \
  eclipse-temurin:21 \
  java -jar /app/jda-dave-spike.jar
```

`--env-file` is read by the Docker CLI on the host; the values end up as container
environment variables and stay readable to privileged users through `docker inspect` and
`/proc/<pid>/environ`. Treat the env file as a secret (owner-only permissions, one host,
deleted after the run) and prefer a secret manager where one is available.

If `/tmp` is mounted `noexec` on the host/container, give the JVM an executable temp
directory instead:

```bash
docker run --rm -it \
  --name jda-dave-spike \
  --platform linux/amd64 \
  --env-file "$PWD/spikes/jda-dave/spike.env" \
  --tmpfs /tmp:rw,exec,size=64m \
  -v "$PWD/spikes/jda-dave/build/libs/jda-dave-spike-0.1.0-all.jar:/app/jda-dave-spike.jar:ro" \
  eclipse-temurin:21 \
  java -jar /app/jda-dave-spike.jar
```

The libdave loader extracts `libdave-jvm.so` under `java.io.tmpdir/lava-jni-natives/...`.
The Opus/JNA loaders also extract under `java.io.tmpdir`. The directory must be writable
and executable, not `noexec`.

Expected startup sequence:

```text
JDA/DAVE staging transport probe (test-only, not production)
[config] SpikeConfig(testGuildId=..., keepOnline=false, ...)
[dave] loading native library for linux-x86-64
Native library dave-jvm: successfully loaded.
[dave] native loaded; maxSupportedProtocolVersion=1
[audio] loading JDA Opus encoder
Audio System successfully setup!
[audio] JDA Opus encoder is available
[gateway] logging in with intents=[GUILD_VOICE_STATES], cache=[VOICE_STATE], ...
[gateway] ready as <bot> (id=...) in 1 guild(s)
[command] guild-scoped /voice-test is ready (guild=..., commandId=...)
[probe] waiting for /voice-test ...
```

The process prints no token. For evidence, redirect the run to a file — for example append
`> /tmp/spike.log 2>&1` (an absolute path) or `> spikes/jda-dave/spike.log 2>&1` when the
current directory is the repository root — and keep that log with the staging notes.
`spikes/jda-dave/spike.log` is git-ignored; the negative test writes
`/tmp/spike-negative.log` outside the repo. Never add either log to Git or attach it
unredacted. They contain the staging guild and channel names/IDs (and the bot's own ID), so
redact those identifiers before pasting a log into an issue, PR, or chat. A relative redirect
target fails with `No such file or directory` when its parent directory does not exist in the
current directory, and then Docker never runs.

## Run `/voice-test`

1. With a second account, join a **regular voice channel** in the test guild. Stage
   channels are rejected by the probe.
2. In any text channel of that guild, run `/voice-test`.
3. The bot joins your current voice channel, plays a short low-volume 440 Hz tone
   (1.5 seconds, 75 frames of 20 ms), then disconnects automatically.
4. The ephemeral reply reports success or a short failure reason.

Audio implementation details:

- `ToneSendHandler` implements JDA's `AudioSendHandler`; `DefaultSendFactory` (JDA's
  `IAudioSendFactory` implementation) drives the send loop.
- Format: 48 kHz, 16-bit, stereo, signed **big-endian PCM**, as required by
  `AudioSendHandler.INPUT_FORMAT`.
- Exactly one 20 ms frame (3840 bytes) is returned per `provide20MsAudio()` call. Frames
  are pre-rendered in the constructor, so the callback does no I/O and cannot block.
- `isOpus()` returns `false`, so JDA performs the Opus encode; DAVE encrypts the resulting
  frame. The probe calls `AudioNatives.ensureOpus()` at startup and fails fast if JDA's
  encoder cannot load, so a missing Opus native cannot masquerade as a successful tone.
- Amplitude is `0.10` of full scale (about -20 dBFS), with 10 ms fades to avoid clicks.
- After `CONNECTED`, the probe waits a fixed 1.5 seconds before starting the tone so the
  DAVE MLS transition is not competing with the first media packets.

## Connection state model and disconnect-cause limitation

The probe owns a `ProbeConnectionState` that is **not** derived from JDA internals and is
not exported as a claim about Discord's view. It records what the probe intended at each
point:

| Probe state | Meaning |
| --- | --- |
| `IDLE` | No voice connection has been requested in this run. A `NOT_CONNECTED` here is background noise. |
| `CONNECTING` | `openAudioConnection` was issued and JDA has not reported `CONNECTED` yet. |
| `CONNECTED` | JDA reported `CONNECTED`; the DAVE settle window is running. |
| `PLAYING` | The tone send handler is installed and frames are being handed to JDA. |
| `DISCONNECTING` | The probe recorded local disconnect intent immediately before calling `AudioManager.closeAudioConnection()`. |
| `DISCONNECTED` | A `NOT_CONNECTED` was observed after `DISCONNECTING`. The requested disconnected state was reached; the cause is not attributable via the public API. |

The `(desiredState=...)` field in a `[voice] status=...` log line is the snapshot taken at
callback **entry**, before the callback applies its own transition. A `CONNECTED` line
therefore normally reports `desiredState=CONNECTING`, and a `NOT_CONNECTED` that ends a
disconnect reports `desiredState=DISCONNECTING`. Treat these lines as descriptive state
records, not as proof that the connection or the transport worked.

In `CONNECTING`, `CONNECTED` or `PLAYING`, a `NOT_CONNECTED` status with no local disconnect
request is an **unexpected voice loss** and fails the run. That is the only safe reading of
the public status: the probe still wanted a connection at that point.

Before cleanup calls the public `AudioManager.closeAudioConnection()`, the probe records
`DISCONNECTING` and atomically creates a **fresh disconnect acknowledgement for that
request**. Cleanup waits only on that request's acknowledgement, never on a shared or
global status future, so a `NOT_CONNECTED` observed earlier (for example in `IDLE`) cannot
confirm a later cleanup. The acknowledgement is satisfied only by a `NOT_CONNECTED`
processed while that exact request is still active; stale or duplicate statuses cannot
confirm a newer request. When it is accepted, the probe records `DISCONNECTED` *before*
waking the waiter. A `NOT_CONNECTED` observed after that point means the requested
disconnected state was reached. It does **not** prove that JDA's close call caused the
status, and the probe must not claim that it did. The public JDA API exposes connection
status, not the causal source of a disconnect; a network drop racing the cleanup produces
the same observable status. This is an unavoidable public-API limitation of the spike.

For the same reason, the state is an application-owned desired-state record, not a causal
proof. `ERROR_*`, authentication failure, region change, deleted channel and other terminal
`ConnectionStatus` values remain failures. The first terminal reason is preserved and cannot
be overwritten by a later cleanup status.

A success therefore still requires all of the following, and none of them is replaced by the
`NOT_CONNECTED` cleanup status:

1. DAVE protocol evidence in the logs.
2. All tone frames handed to JDA (`framesProvided == totalFrames`).
3. A second account actually hearing the tone. The audible tone is the **decisive transport
   evidence**; `NOT_CONNECTED` only reports the requested end state.

## Success evidence

Capture the full container stdout/stderr. A pass requires **all** of the following:

1. **Native loaded**: `[dave] native loaded; maxSupportedProtocolVersion=1` and
   `Native library dave-jvm: successfully loaded.` A version of `0` is a failure. Also
   confirm `[audio] JDA Opus encoder is available` (and JDA's
   `Audio System successfully setup!`).
2. **Voice connected**: logs show a `[voice] status=CONNECTING_... (desiredState=CONNECTING)`
   line followed by `[voice] connected to '<channel>' (<id>)`. The `CONNECTED` status line is
   normally printed as `[voice] status=CONNECTED (desiredState=CONNECTING)`: the callback logs
   the state snapshot taken at callback *entry*, and the transition to `CONNECTED` happens
   inside that same callback, so `desiredState=CONNECTED` on that line is **not** expected and
   its absence is not a failure. These lines are descriptive only — a status log is not proof
   of a working transport. The decisive evidence remains the DAVE negotiation (item 3), the
   full tone frame count (item 4) and the audible tone (item 5).
3. **DAVE negotiated**: the libdave adapter logs `DAVE Init - Protocol version=1`
   (any value greater than `0` is acceptable; `0` or an absent line is a failure). This is
   emitted by `DAVEManager` at DEBUG through the bundled `simplelogger.properties`.
   `Transition executed: ID=..., Protocol version=...` is additional evidence that the MLS
   transition completed. `MLS Failure` must **not** appear.
4. **Tone provided to JDA**: `[tone] finished: framesProvided=75/75` (produced by
   `[tone] started: 440.0 Hz for 1500 ms (75 frames ...)`). The probe also fails the run if
   `framesProvided != totalFrames`. `framesProvided` counts frames handed to JDA's send
   loop; it is **not** proof of network transmission, DAVE encryption or audible output.
   Audible audio is still required (item 5).
5. **Audible**: a second account in the same voice channel hears a short, quiet 440 Hz
   tone. This is the **decisive transport evidence**. Logs alone are not audible proof, and a
   `NOT_CONNECTED` status is not delivery proof.
6. **Requested disconnected state observed**: after cleanup records `DISCONNECTING` and
   creates the fresh per-request acknowledgement, the log shows
   `[voice] status=NOT_CONNECTED (desiredState=DISCONNECTING)` and
   `[voice] NOT_CONNECTED observed after the local disconnect request;`
   `requested disconnected state reached (cause not attributable via public JDA API)`.
   This confirms the requested
   state, **not** that JDA's `closeAudioConnection()` caused it. The public API cannot
   distinguish a close-driven disconnect from a network drop that raced cleanup; do not
   report a causal source. The acknowledgement is scoped to this cleanup: an earlier
   `NOT_CONNECTED` (for example in `IDLE`) or a stale/duplicate status cannot satisfy it.
   A connection attempt that never produced a JDA audio session is
   cancelled and logged as `[voice] pending voice connection cancelled`; if that cancellation
   cannot be confirmed, the probe reports a cleanup failure instead of claiming a clean
   disconnect. The container exits with status `0` only for a successful run.

Useful grep for evidence collection:

```bash
grep -E '\[dave\]|\[voice\]|\[tone\]|\[command\]|DAVE Init|Transition executed|MLS Failure|Native library dave-jvm' spikes/jda-dave/spike.log
```

Also record `docker inspect --format '{{.State.ExitCode}}' jda-dave-spike` when the
container is not run with `--rm`, and keep the full log with the staging run notes.

## Stop and fail criteria

Stop the container with `Ctrl-C` (or `docker stop jda-dave-spike`). In one-shot mode the
process exits by itself after the first completed probe; user errors such as “join a voice
channel first” do not consume the one-shot run.

Phase 1 **fails** if any of the following happens:

- The process exits non-zero at startup: missing config (`2`), unsupported platform (`2`),
  DAVE/Opus native failure (`3`), or synchronous JDA construction failure (`4`).
- JDA login/readiness fails or times out; this is reported as a probe failure (`1`).
- `maxSupportedProtocolVersion` is `0`, the native cannot be extracted or loaded, or the
  JVM reports an `UnsatisfiedLinkError`.
- `AudioNatives.ensureOpus()` returns false or JDA logs that its audio system encountered
  problems; PCM frames would then be silent.
- JDA never reaches `CONNECTED` for the voice channel within the configured connect timeout
  (20 seconds by default from `SpikeConfig.voiceConnectTimeoutMillis`), or the connection
  reports an `ERROR_*` status. `DISCONNECTED_AUTHENTICATION_FAILURE`,
  `DISCONNECTED_CHANNEL_DELETED` and `AUDIO_REGION_CHANGE` are terminal: the probe fails
  immediately with a specific reason instead of waiting out the connect timeout.
- A `NOT_CONNECTED` is observed while the probe's application-owned state is `CONNECTING`,
  `CONNECTED` or `PLAYING` and no local disconnect was requested. That is an unexpected
  voice loss and must fail; it is never treated as success.
- `DAVE Init - Protocol version=0`, no `DAVE Init` line at all, or any `MLS Failure` line.
- `[tone] finished` reports fewer than 75 frames provided to JDA, the tone times out, or no
  participant hears the tone. A full frame count is not delivery proof, so a full count
  with silence is still a failure.
- The requested disconnected state is not observed, a pending connection attempt cannot be
  cancelled, the disconnect is not confirmed within 5 seconds, or the process exits non-zero.
  Cleanup confirms the disconnect only through the fresh acknowledgement created for its own
  `DISCONNECTING` request; a `NOT_CONNECTED` seen earlier or processed by a stale/duplicate
  callback cannot confirm it. A `NOT_CONNECTED` after `DISCONNECTING` only proves the
  requested state; it does not prove JDA's close caused it, and it is never used as evidence
  of clean transport on its own.
- The jar lacks `natives/linux-x86-64/libdave-jvm.so`,
  `natives/linux-x86-64/libopus.so`, or
  `com/sun/jna/linux-x86-64/libjnidispatch.so`.

Do **not** silently change the approved versions, substitute a different JDA/Kotlin pair, or
treat a successful compile as runtime success. Report the failing log and exit code to the
Lead Coordinator.

## Troubleshooting

- `Required library was not found` / `UnsatisfiedLinkError`: the fat JAR was built without
  the Linux native, or the process is not Linux x86-64. Re-run the native inspection
  command above.
- `Failed to create directory for unpacked native library` or a JNA `tmp` error:
  `java.io.tmpdir` is missing, read-only, or `noexec`. Use the `--tmpfs /tmp:rw,exec`
  variant.
- `Maximum Dave Protocol Version is 0`: the DAVE factory was not wired or the native is
  unusable. Do not continue.
- `The bot is missing permission VOICE_CONNECT`: grant **Connect** (and **Speak**) to the
  staging bot in that channel.
- `[voice] terminal status=DISCONNECTED_AUTHENTICATION_FAILURE`: Discord rejected the voice
  authentication. The token may have been rotated or the bot may lack voice access in the
  guild. The probe fails immediately with this reason; do not wait for the connect timeout.
- `[voice] terminal status=AUDIO_REGION_CHANGE`: Discord moved the voice region and JDA
  aborted the connection. This probe treats a region change as terminal; re-run
  `/voice-test`. A repeated region change means the network path is unstable.
- `[voice] terminal status=DISCONNECTED_CHANNEL_DELETED`: the voice channel was deleted while
  the probe was connected. The run is a failure; re-create/join a regular channel and re-run.
- `[voice] status=NOT_CONNECTED (desiredState=CONNECTING|CONNECTED|PLAYING)`: the voice
  connection was lost while the probe still wanted it. The probe records a terminal failure
  and does not report success. Check the audio websocket/UDP path and re-run.
- `[voice] status=NOT_CONNECTED (desiredState=DISCONNECTING)` or
  `[voice] NOT_CONNECTED observed after the local disconnect request`: the requested
  disconnected state was reached and this cleanup's fresh per-request acknowledgement was
  satisfied. A `NOT_CONNECTED` observed before the request (for example in `IDLE`) or a
  stale/duplicate callback does not satisfy it. This does **not** prove JDA's
  `closeAudioConnection()` caused the disconnect; the public API only reports status. Use the
  DAVE logs plus the audible tone as the transport evidence, and do not describe this line as
  causal proof.
- `The probe worker could not be scheduled` or `Discord did not acknowledge /voice-test`:
  the one-shot run now exits with a failure instead of hanging. Check the gateway/HTTP
  logs, then re-run the container.
- `[voice] pending voice connection cancellation was not confirmed`: JDA queued a voice
  state update before the probe gave up, and the staging guild never showed the bot joining
  and then leaving within 5 seconds. Do not reuse the run; check the staging guild for a
  stuck bot and disconnect it before retrying.
- No `DAVE Init` line: confirm the `simplelogger.properties` resource is inside the JAR and
  that no external logging config overrides `moe.kyokobot.libdave.jda.internal.DAVEManager`.
- `/voice-test` not visible: the guild command is registered only in
  `DISCORD_TEST_GUILD_ID`, and the bot needs the `applications.commands` scope.

## Isolation guarantees

- The probe code, its build and this README live under `spikes/jda-dave/`. The migration
  plan and agent/tooling configuration live outside that directory (for example
  `docs/migrations/` and `.opencode/`); they are not part of the probe module.
- The root build, root `settings.gradle.kts`, root version catalog, root
  `runtimeClasspath`, and production sources are untouched by the probe.
- No Kord dependency is present.
- The production spike sources use only public `net.dv8tion.jda.api.*` APIs. They do not
  import or reach into `net.dv8tion.jda.internal.*`. The connection state used to distinguish
  an unexpected loss from a requested cleanup is application-owned, not a JDA internal.
- No production bot token is used; the staging token comes only from the environment.
