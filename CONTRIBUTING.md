# Contributing

Please read the licensing status in the README before contributing or redistributing.
Use a focused branch and pull request; do not push directly to `main`. Prefer small changes that
reduce complexity without changing established pump behavior. Do not include unrelated cleanups.

## Protected workflows

Bolus, cartridge/fill, pump pairing, and transport behavior require explicit, scoped maintainer
approval before implementation. A UI task is not permission to change pump commands, device
authentication, dose calculations, permission ownership, cancellation, retries or reconciliation.
Document the approved scope in the issue/PR, affected states and failure cases, tests, and rollback.
Re-lock the workflow at handoff. Ask before broadening the scope.

## Verification

Follow [BUILDING.md](docs/BUILDING.md). Include the exact app/dependency commits, commands run,
test counts/failures, and what was not tested. Separate unit/emulator evidence from bench evidence.
Do not represent simulation as proof of clinical safety or protocol equivalence.
CI failures are blockers, including pre-existing failures until explicitly resolved.

## Privacy and security

Use synthetic fixtures. Never commit logs, Bluetooth captures, pump identifiers, patient profiles,
database exports, credentials, signing keys, or private screenshots. Check image metadata and Git
author/committer metadata too. Use GitHub's no-reply address if you do not want your email public.
A zero-result secret scan does not prove absence of private health data.

For a potential vulnerability, use GitHub's private vulnerability-reporting interface if available.
Do not post secrets or exploit details in public issues. See [SECURITY.md](SECURITY.md).

## Review

`main` is intended to require `build-and-test` and `privacy-check`, an approving review, and
resolved conversations. Force pushes/deletion are disabled. The repository owner retains an
administrative override while there is only one maintainer; it is not permission for automation
to bypass review. No automatic signing, publishing, merging, or device installation is configured.
