# Field Kit presentation module

Field Kit is a Kotlin source module inside the existing `mobile` Compose build. It adds no Gradle
project, binary dependency, runtime network request, image asset or permission. Open fonts are bundled in app resources. A separate
Android library would create a circular dependency on the host's `SkinTokens`/`AppSkin`; this
package keeps the existing app architecture and a small explicit integration boundary.

- `FieldKitSkin.kt`: stable `field-kit` ID, display metadata, colors, typography, shapes, tokens.
- `FieldKitMaterials.kt`: cached enamel, graphite, rubber and oxide faces; recessed LCD/chart glass;
  localized edge wear and reflections.
  Drawing modifiers do not measure, add padding, receive input, or change semantics.
- `FieldKitIcons.kt`: original vectors. Missing names return null so the host retains its icon.
- `FieldKitControls.kt`: navigation plate drawing within the existing native indicator footprint.
- Host `SkinSwitch.kt`: native switches with Field Kit colors and decorative thumb grip; checked,
  enabled and callback semantics pass through unchanged.
- Host `SkinIcon.kt`: keeps source descriptions, tint, modifier and hit targets; chooses glyphs.
- Host theme/card/menu/shell helpers: opt into the module only when Field Kit is selected.

The host owns navigation, lists, data, chart layers and gestures, commands, authentication,
confirmation, persistence, widgets, app name, package ID, and release numbering. The skin must not
import a screen, DataStore, Prefs, pump type, or service. The `usesRefinedChrome` token controls
presentation only and must never be a data/workflow gate.

POR retains persisted ID `current`; Instrument retains `instrument`. Restoring `field-kit`
re-enables this explicit selection; `bauhaus` and unknown IDs still resolve to POR. The package
must not change the user's current selection on integration.

## Current host contract

Based on MobiControl01AN: the extra inspection panel beneath the graph is removed. The native
in-chart scrub leader and glucose/basal labels remain. The simplified reconnect banner, original
graduated battery/insulin bars, Carb Capture, direct Sleep/Exercise toggles and dashboard-only
Bolus entry remain host-owned. Android widgets retain their existing renderers and geometry;
propagating selected palettes to RemoteViews/Glance is a separate host feature.

Use `tools/skins/package_field_kit.py` with an unchanged target checkout to produce the reviewable,
hash-guarded delivery archive. Repackage against a new host baseline after review instead of
forcing an old archive onto changed application files. See `harness/build/field-kit-package.md`.

## Second material pass

Barlow Semi Condensed supplies printed labels and body text; Rajdhani supplies tabular numeric
readouts. Original TTFs, source hashes and OFL notices are retained; see FONTS.md. Readout type has
an optical 10% size adjustment inside its existing bounds to compensate for Rajdhani's smaller
ink height, with the original line height and card layout. The CHART panel variant adds the
optional Field Kit glass frame; other skins map it to the previous STANDARD appearance. No plot
layer, value, color meaning, gesture or time calculation changes. Existing materials and new
controls preserve all measured touch-target and menu containers.

Before another app-workspace integration, regenerate the package against that workspace's then
current source. It has independent widget work; never copy this isolated checkout wholesale.
