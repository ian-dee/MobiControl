# MobiControl 01BD experimental prerelease

This is a public development snapshot requested by the repository owner on October 6, 2026.
It includes the modified app source and a debug APK, with the modified PumpX2 dependency available
at the exact pinned revision. No pump behavior is changed as part of this publication.

## Important boundaries

- For development and isolated bench testing only. Do not use this unvalidated build with a pump
  providing therapy. This is not an official Tandem app or a validated clinical release.
- ControlX2 repository-wide licensing is still awaiting clarification in
  [upstream issue 165](https://github.com/jwoglom/controlX2/issues/165). Publication is not a grant of
  upstream rights. Attribution and existing notices are preserved; no blanket app license is added.
- PumpX2 is MIT licensed. The pin is `9e40cf029e84bdb38168c976a41f69434275acb4`, based on
  `dad3eea2a3f6ae1bb1a6fdc6b3eac37f3ac7132b`, not the latest upstream main.
- Dependency testing has two known failures: `DailyStatusHistoryLogTest` and
  `VersionsAHistoryLogTest`. Both also reproduce on the untouched upstream base. They remain
  unresolved, not suppressed; app test success is not a wholly passing dependency suite.
- The APK is debuggable, package `local.controlx21`, version `1.0.0-01BD-20261002` (code 51).
  Debug signing is not production signing. An existing installation with a different signer cannot
  be updated in place. Do not uninstall an app managing a therapy pump merely to try this build.
- Watch control remains disabled. Developer-preview profiles and README screenshots are synthetic.
- Private development history, phone/pump captures, personal profiles, credentials and private
  signing keys are not publication inputs. The already-public upstream development keystore remains
  in upstream ancestry and the source tree; it is not a private signing identity.

## Build and review

Follow [BUILDING.md](BUILDING.md) to reproduce a debug build from the source branch. The GitHub
release identifies the exact app commit and contains the APK SHA-256 checksum. Local app unit-test
and build evidence is reported in the release notes; it is not bench or clinical validation.

Publication uses development branches and a prerelease tag. Required review/check protections on
`main` remain in place. Automatic APK publication is not enabled. See
[PUBLICATION_CHECKLIST.md](PUBLICATION_CHECKLIST.md) for unresolved gates.
