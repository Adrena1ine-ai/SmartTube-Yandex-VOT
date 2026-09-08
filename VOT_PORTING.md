# SmartTube Yandex VOT maintenance

## Canonical checkpoint

- SmartTube upstream base tag: `32.38s`
- SmartTube upstream base commit: `26076c93237172af8e09656d2cfe06ab0d9eb872`
- Initial 32.38 VOT port commit: `7b0b3e22b08f8c74180793b077a4880c9f5db633`
- Canonical private release tag: `v32.38-vot-r1`
- Stable package: `org.smarttube.stable`
- Build variant: `ststableDebug`, universal APK (`armeabi-v7a` + `arm64-v8a`)
- Verified signing certificate SHA-256: `7b53c97a65361d2f48a3ad941ba40a429ef6e6fd7c1408b4054e2c3763524e89`
- Public package: `org.smarttube.vot`
- Public display name: `SmartTube VOT`
- Public build variant: `stvotRelease`, universal APK (`armeabi-v7a` + `arm64-v8a`)
- Public signing certificate SHA-256: `438644d831f64b92b2174ed616f83e690a3e62aabbf9d3f9169e055d1c026a7c`

The tag is the complete maintained SmartTube 32.38 + Yandex VOT source checkpoint. Do not reconstruct the implementation from runtime logs or old experiments. The no-op VOT status implementation in `EmbedPlayerView` is required while that class implements `PlaybackView` without exposing full playback controls.

## Maintained behavior

- VOT supports ordinary EN→RU voice-over and optional OAuth lively voice.
- OAuth is entered by each user in the application UI and is stored only in private app preferences.
- The source and APK contain no user OAuth token or personal Yandex ClientSecret/ClientID.
- Server status and `remainingTimeSec` are decoded from protobuf. There is no real percentage, so none is synthesized.
- Player states are `OFF`, `PENDING`, `ACTIVE`, and brief `ERROR`; four separate full-color drawables and state-specific tooltips are used.
- Pending ETA is displayed as `MM:SS` under the VOT icon without moving the stock 90x64 control geometry.
- `ACTIVE` is entered only after `TranslationAudioPlayer` reaches `STATE_READY`.
- Polling follows the latest server ETA, caps an initial ETA over 180 seconds at 120 seconds, then polls every 30 seconds.
- `FAILED + shouldRetry > 0` permits one fresh non-lively retry per translation operation.
- A non-protocol HTTP 400 from a subsequent lively poll also permits the same one fresh non-lively retry. The shared guard prevents retry loops.
- Translation audio follows play/pause, seek, and playback speed; drift over 800 ms is corrected.
- VOT diagnostics redact URLs and long token-like values.

## Important implementation areas

- `common/.../controllers/VoiceTranslateController.java`: state machine, ETA ticks, synchronization, audio ducking, sanitized errors.
- `common/.../vot/VotClient.java`: request/poll flow, protocol error decoding, audio-request flow, one-shot fallback.
- `common/.../vot/VotPollingPolicy.java`: server ETA cadence and one-shot retry policy.
- `common/.../vot/VotProgress.java` and `VotTranslationResponse.java`: truthful stage/status model.
- `common/.../vot/TranslationAudioPlayer.java`: ready/error callbacks and playback synchronization.
- `smarttubetv/.../VoiceTranslateAction.java`, presenter/layout/resources: four icons, tooltips, and timer.
- `common/src/test/.../vot/`: protobuf and polling/fallback regression tests.

## Future SmartTube upgrade workflow

1. Fetch and verify the exact new upstream SmartTube release tag and commit.
2. Create a dedicated branch/worktree from that upstream commit and initialize its pinned submodules.
3. Port the canonical private VOT delta represented by `32.38s..v32.38-vot-r1`. Prefer replaying the two private VOT commits (`7b0b3e22` and the commit referenced by `v32.38-vot-r1`) with three-way conflict handling.
4. Resolve actual integration conflicts file by file. Never replace newer SmartTube files wholesale with 32.38 files.
5. Re-check every `PlaybackView` implementation when the `PlayerUI` contract changes; retain the `EmbedPlayerView` no-op only when required.
6. Preserve server-derived status/ETA semantics and the operation-scoped one-shot fallback. Do not add fake progress percentages or arbitrary timeout failures.
7. Run the VOT unit tests and the full stable debug build with JDK 11:

   ```text
   gradlew.bat :common:testStstableDebugUnitTest --tests com.liskovsoft.smartyoutubetv2.common.vot.* --no-daemon
   gradlew.bat :smarttubetv:assembleStstableDebug --no-daemon
   ```

8. Verify package, version, ABI, SHA-256, signing certificate, and absence of secrets in tracked files and the APK.
9. Confirm exactly one intended ADB server and device. If package and certificate match, use only `adb install -r`; never uninstall or clear app data during an upgrade.
10. Smoke-test launch, normal video playback, VOT button/UI, ordinary translation, lively translation, long-wait ETA, and automatic fallback.
11. Commit the resolved port, push only to the private canonical repository, and tag the verified checkpoint.

Concise flow: new SmartTube tag → port canonical VOT delta → resolve real conflicts → tests/build → `adb install -r` → VOT smoke test → commit/push private checkpoint.

## Build and signing notes

- Use JDK 11. The legacy D8/R8 stack fails under JDK 21.
- `keystore.properties` is optional, local, and must remain untracked. Without it, the debug variant uses the local Android debug key.
- The public `stvot` flavor has `applicationId org.smarttube.vot`, the `SmartTube VOT` label, package-specific search/provider authorities, and an empty update URL list. A runtime package guard also prevents it from invoking the official SmartTube updater.
- `stvot` uses `matchingFallbacks = ["ststable"]` for library modules that expose only the existing SmartTube flavors. Do not add duplicate public flavors to every library.
- Public release signing is loaded from `SMARTTUBE_VOT_SIGNING_PROPERTIES` or `~/.smarttube-vot/signing.properties`. The properties file, keystore, and passwords must remain outside Git and public artifacts.
- Preserve the public signing identity for all future `org.smarttube.vot` updates. Verify the certificate SHA-256 above before publication.
- Build `ststable` and `stvot` in separate Gradle invocations. Requesting both together activates the upstream Google Services plugin for the entire app project, while the public package intentionally has no Firebase client entry.
- `stvotVotAudit` is a debuggable verification-only build signed with the public key. It exists only to inspect fresh-install private preferences; publish only `stvotRelease`.
- Never copy a keystore, password, OAuth token, app preferences, runtime log, device identifier, or local path into source or publication artifacts.
- `org.smarttube.vot` coexists with `org.smarttube.stable`: Android assigns separate UID, data directory, preferences, provider authorities, and signing identity.

## Runtime verification checklist

1. Confirm one intended ADB target and one ADB server on the standard port.
2. Install with `adb install -r` only after package and certificate checks.
3. Verify app launch and normal video playback without crash or ANR.
4. Confirm the VOT button, four states, tooltips, alignment, and ETA timer.
5. Verify ordinary translation and OAuth lively voice reach actual translated-audio `STATE_READY`.
6. Verify a long-wait request remains pending according to server ETA and a transient lively-poll HTTP 400 falls back automatically without showing a terminal error.
7. Confirm OAuth and user preferences are unchanged using boolean/hash comparison only; never print their values.

Cross-device history/position synchronization requires a second device signed into the same account and must not be marked as verified from one device alone.