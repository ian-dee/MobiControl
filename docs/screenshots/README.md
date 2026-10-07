# Screenshot provenance

This gallery was generated locally from the native MobiControl UI and widget renderer, using
Robolectric API 35, native graphics and Roborazzi. It is not AI-generated artwork and does not
reuse private emulator baselines, phone screenshots, pump logs, databases or camera photos.

## Coverage

Each of POR, Instrument and Field Kit has the same 16 captures:

- Dashboard, Notifications, Actions and Settings.
- Carb Capture and the compact cover-screen dashboard.
- Six bolus stages: correction choice, calculation, review, hold-to-deliver, simulated progress
  and stop confirmation.
- Compact/history widgets and compact/wide cover widgets.

Main-screen viewport: 400 × 900 dp. Compact-cover viewport: 399 × 441 dp. Captures use mdpi,
light mode and English. Widget dimensions are specified in the widget test. They show the widget
itself, not a Samsung launcher, device bezel or an Android Auto surface.

## Synthetic fixtures and limits

The test starts with cleared preferences and the publication copy's synthetic developer-preview
profile. Demonstration values include glucose 150 mg/dL, IOB 1.50 u, battery 82%, cartridge 100 u,
and a 30 g carbohydrate entry. The graph uses the app's existing synthetic history generator;
widgets use a mathematical sample curve. No values are treatment recommendations.

The Notifications screen contains a fabricated site-change reminder. The pump display name is
`Tandem Mobi (demo)`; no real pump identifier or pairing details are loaded. All transport callbacks
are disconnected. A call to the real service-bolus callback fails the capture test immediately.
Dashboard captures are scrolled past the developer-only profile/demo controls, using the app's
normal scroll behavior. No production layout is modified for this gallery.

Bolus progression uses the existing in-process developer simulator and a test authentication
success callback. Native biometric/passcode UI is not depicted. The cancellation image stops
at the confirmation dialog; it is not evidence that a pump accepted cancellation.

Carb Capture shows the real ChatGPT-handoff UI with camera permission off. A test package entry
represents ChatGPT being installed, but no image is captured, no app is launched and no request
is sent. No simulated carbohydrate estimate is presented as an actual service response.

Some app labels use the test runtime's clock, so regenerating may change displayed dates or times.
The widget clock is fixed to a synthetic date. No screenshots establish clinical safety, hardware
compatibility or complete visual/accessibility coverage.

## Regenerate

Follow [the build prerequisites](../BUILDING.md) and bootstrap the pinned PumpX2 dependency first.
From the repository root:

```sh
./gradlew --no-daemon --console=plain :mobile:recordRoborazziDebug --tests '*Readme*'
```

Fixtures and capture instructions live in:

- `mobile/src/test/java/com/jwoglom/controlx2/test/snapshots/ReadmeGalleryTest.kt`
- `mobile/src/test/java/com/jwoglom/controlx2/test/snapshots/ReadmeWidgetsTest.kt`

The command overwrites only the named gallery PNGs. These recording tests intentionally live
outside the ordinary unit-test suite. Review every regenerated image for legibility, unexpected
identifiers, stale/blank graphs and unexpected dialogs before committing. Check PNG metadata too.
Do not substitute screenshots from a paired phone or import private historical baselines.

## Capture review

The initial gallery was reviewed on October 2, 2026:

- App source: `fe1d05b`; only documentation, gallery images and the capture tests were added.
- PumpX2 source pin: `9e40cf029e84bdb38168c976a41f69434275acb4` (unchanged).
- Recording run: 13 tests passed, 0 failed; all 48 expected PNGs generated and linked.
- All images visually reviewed; PNGs contain only `IHDR`, `IDAT` and `IEND` chunks, with no
  embedded text, EXIF, identifiers or local paths.
- No phone installation, pump communication, clinical validation or public upload was performed.

This is a gallery-specific check, not a rerun of all application or dependency tests. Existing
publication and validation limitations remain as described in the main README.
