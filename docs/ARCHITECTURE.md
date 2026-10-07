# Architecture

| Component | Responsibility |
| --- | --- |
| `mobile` | Compose UI, service lifecycle, workflow state, notifications, widgets and cover-screen UI |
| `pumpcomm` | Pump-session communication integration |
| `shared` | Shared models, workflow policies, enums and serializers |
| `db` | Room pump-history persistence and sync helpers |
| `clientcomm` | Client communication interfaces |
| `wear` | Retained Wear OS code; watch pump-control integration disabled |
| pinned PumpX2 | Bluetooth transport, protocol messages, authentication and message parsing |

UI requests are coordinated by the phone service and serialized through the pump communication
layer. Pump responses and history feed the app's observed state. Cached UI state is not proof
that a pump command completed. Preserve correlation, authorization, unit conversion, timeout,
and reconciliation behavior when modifying any pump-facing path.

The pump is the source of truth for its profile and delivery state. Emulator previews use
synthetic state and must remain separate from real pump communication. They are not a complete
pump simulator. Authentication dialogs, platform lifecycle, reconnects and radio conditions
require device/bench validation beyond unit tests.
