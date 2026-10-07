# Known limitations and validation boundaries

- Licensing permission for ControlX2-derived code is awaiting clarification. The repository owner
  requested an experimental public prerelease while this remains unresolved; that does not grant
  upstream rights or establish a repository-wide license. No new app license has been assigned.
- The preserved PumpX2 base is intentionally older than current upstream. Do not assume newer
  upstream fixes are included. Dependency updates require review and targeted validation.
- Initial clean dependency testing reports failures in `DailyStatusHistoryLogTest` and
  `VersionsAHistoryLogTest`. These are not suppressed or converted into passing CI results.
  Both were reproduced on the pristine upstream base. Protocol changes to resolve them require
  a separate scoped review; source preparation does not silently fix or suppress them.
- Emulator previews are synthetic UI demonstrations, not protocol or medical validation.
- Private historical screenshots were excluded. Screenshot verification needs newly generated,
  reviewed synthetic baselines before it can become a required check.
- Watch control remains disabled. A successful mobile build does not validate retained watch code.
- Bench evidence is maintained privately. Public bug reports must use sanitized reproduction
  descriptions; never attach pairing codes, serial numbers, full support bundles or health histories.
- The published APK is a debuggable development build, not a production-signed or clinically
  validated release. CI has no signing/deployment credentials and does not publish APKs.
