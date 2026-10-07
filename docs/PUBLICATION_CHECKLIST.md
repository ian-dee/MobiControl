# Publication handoff checklist

Local source preparation is not permission to release an APK or a statement of medical validation.

On October 6, 2026, the repository owner explicitly requested publication of the modified source
and APK, rather than a documentation-only preview. This authorizes a single experimental prerelease
with the unresolved licensing and test failures disclosed. It does not resolve the unchecked gates
below, waive privacy review, or authorize merging protected `main` without its required checks/review.

- [x] Preserve public upstream ancestry without importing private working history/tags.
- [x] Replace captured preview profiles and pump-name examples with synthetic fixtures.
- [x] Exclude private logs, screenshots, archives, machine configuration and bench notes.
- [x] Pin exact modified PumpX2 source; do not silently upgrade to newer upstream.
- [x] Compile dependency and app from a fresh checkout with empty local artifact outputs.
- [x] Pass 1,013 app unit tests and debug assembly. Public dependency-download caches and an
  installed JDK/SDK were reused; no developer Maven artifacts or local.properties were required.
- [x] Scan new modification history for secrets; verify no-reply owner commit identity.
- [x] Prepare read-only build/test CI, contributor guidance, CODEOWNERS and issue/PR templates.
- [x] Configure remote main-branch review/check protections and private vulnerability reporting.
- [ ] Resolve ControlX2 repository-wide licensing with the maintainer (issue #165).
- [x] Review outgoing source/test fixtures, synthetic screenshots and binary assets; rerun secret
  and publication-boundary checks. The inherited development keystore matches public upstream;
  no private signing key is an input. No scanner can guarantee absence of all sensitive information.
- [ ] Resolve the two documented PumpX2 baseline test failures under a separately approved scope.
- [ ] Publish only reviewed dependency/app branches and verify remote tree/metadata.
- [ ] Observe both GitHub checks pass; resolve failures rather than bypassing them.

Actions are currently disabled on the forks to prevent inherited publishing workflows from
running. Native secret scanning and push protection are enabled. New CI contains no release keys,
APK uploads, automatic merges or device installs. Required checks intentionally block merging
until available and passing. The single repository owner retains an administrative override;
automation must not use it to bypass review.

PumpX2's suite reported 643 tests, 2 failures and 43 skips. Both failing history-parser tests
reproduce on the pristine upstream base. They are not suppressed. App test success must not be
misrepresented as a completely green dependency suite or bench validation.
