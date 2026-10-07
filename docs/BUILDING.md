# Build from source

Requirements: Git, Ruby, JDK 21, Android SDK platforms 34 and 36, build-tools 35.0.0 and 36.0.0,
and platform-tools. Accept Android SDK licenses yourself. Set `JAVA_HOME` and `ANDROID_HOME`.
The Gradle wrapper downloads Gradle 9.2.1. No Tandem account, signing secret, device, or local
Maven cache is required for a debug build. A network connection is needed for public dependencies.

```sh
git clone --branch codex/publication-preparation https://github.com/ian-dee/MobiControl.git
cd MobiControl
bash tools/build-dependency.sh
./gradlew --no-daemon :mobile:assembleDebug testDebugUnitTest
```

The bootstrap reads `dependencies/pumpx2.json`, checks out that exact commit, and builds its
libraries into `.dependencies/maven`. The app uses only that location; it does not silently
substitute current upstream or the developer's `~/.m2` contents. To use a local source mirror,
set `PUMPX2_SOURCE` to a clean clone containing that exact commit before bootstrapping.
The override changes where Git obtains the commit, not which commit is accepted.

Dependency verification (separate from artifact creation):

```sh
cd .dependencies/pumpX2
./gradlew --no-daemon :androidLib:testDebugUnitTest :messages:test :shared:test
```

The APK is `mobile/build/outputs/apk/debug/mobile-debug.apk`. Debug signing is not release
signing. This workflow does not install it, connect a pump, publish an APK, or create a release.
The owner approved one experimental public prerelease on October 6, 2026, despite unresolved
licensing clarification and the documented dependency-test failures. That is not permission for
automatic releases, stable distribution, or use with a pump providing therapy. Private data and
private signing keys remain excluded. The inherited `debug.keystore` is byte-identical to the
already-public upstream development key, not a private signing identity; never use it for secure
production signing. The debug APK uses Android's local debug-signing configuration.

Robolectric downloads its public Android framework dependencies automatically. Offline mode
is opt-in with `-Drobolectric.offline=true` and requires pre-populated `~/.robolectric` files.
Private screenshot baselines were deliberately excluded; regenerate and review synthetic
baselines before enabling screenshot checks. Ordinary unit tests do not include those baselines.

## Updating the dependency

Never change the pin just to make a build pass. Review the dependency diff and affected command
paths, obtain the scoped workflow approval, run regression tests, and perform the relevant bench
validation. Update the JSON pin in a dedicated pull request with provenance and evidence.
