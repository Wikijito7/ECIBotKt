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

```bash
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

The process prints no token. For evidence, redirect the run to a file (for example append
`> spike.log 2>&1` to the `docker run` command) and keep that log with the staging notes.
`spike.log` is git-ignored: never add it to Git or attach it unredacted. It contains the
staging guild and channel names/IDs (and the bot's own ID), so redact those identifiers
before pasting the log into an issue, PR, or chat.

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
grep -E '\[dave\]|\[voice\]|\[tone\]|\[command\]|DAVE Init|Transition executed|MLS Failure|Native library dave-jvm' spike.log
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
