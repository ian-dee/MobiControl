# MobiControl

Experimental Android application derived from [ControlX2](https://github.com/jwoglom/controlX2),
using [PumpX2](https://github.com/jwoglom/pumpX2) for pump communication.
Maintained by [ian-dee](https://github.com/ian-dee). Not an official Tandem product.

This is development and bench-test software, not a validated replacement for the official app.
Automated tests do not establish safety or clinical suitability. Do not use developer-preview
values as treatment settings. Do not connect an unvalidated build to a pump providing therapy.

## Publication status

This source preparation is local pending licensing clarification and the final privacy review.
ControlX2's repository-wide license is unresolved; see [the maintainer inquiry](https://github.com/jwoglom/controlX2/issues/165).
No license grant for ControlX2-derived code is implied by this README. PumpX2 is MIT licensed.
The exact dependency commit must be published before the default bootstrap can fetch it.

## Development

See [build instructions](docs/BUILDING.md), [architecture](docs/ARCHITECTURE.md),
[known limitations](docs/KNOWN_LIMITATIONS.md), and [contribution requirements](CONTRIBUTING.md).
The mobile app includes dashboard/history, pump setup and settings, CGM workflows, widgets,
cover-screen UI, and device-authenticated workflows. The watch-control integration remains disabled.

Developer preview is for emulator UI testing and uses synthetic fixtures. It is not a pump
simulator and does not prove pump behavior. No real profiles, captures, or screenshots are
intended publication inputs. Report bugs using synthetic reproduction steps, not raw pump logs.

## Credits

MobiControl builds on James Woglom's ControlX2 and PumpX2. Original upstream history and notices
are retained. See [third-party notices](THIRD_PARTY_NOTICES.md).
