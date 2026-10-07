# UI Revision 2

Status: approved by the user. App version remains V1.0.0; V2 refers only to the design revision.

## User Direction

Increase the glass effect and faithfully reproduce https://github.com/Oliverrr2424/webgl-apple-liquid-glass. Use iOS-style typography and redesign the UI accordingly. Preserve all original functional requirements and the UI approval gate before app development.

## Review Files

- 03-v2-liquid-library.png: collection, detail, reverse-prompt result.
- 04-v2-liquid-settings.png: appearance/text, glass settings, dark model connection.

These are built-in image-generation concepts, not screenshots of implemented WebGL controls. They cannot establish exact shader fidelity, real typeface use, animation or performance.

## Material Direction

- Reuse the upstream rendering and control logic, retaining its MIT attribution. Pin and verify the chosen source revision during implementation.
- Clear convex lens bodies, meniscus edge refraction, fine chromatic dispersion, inner reflection and specular highlights.
- Reduce milky tint and excessive frosting so the lens visibly transmits its backdrop.
- Clear capsule actions with blue icon/text replace previous opaque blue action buttons.
- Navigation uses a clear moving selection lens. Circular tools and slider thumbs share the material.
- Gallery entries remain cover plus name only. Prompt paragraphs and model input fields prioritize readability.
- On actual implementation, ONLY real underlying content should refract. Some generated buttons contain illustrative scenic reflections; these are not permission to bake fake scenery into controls. Controls over neutral backgrounds refract neutral backgrounds.
- Preserve the user's data and search requirements; do not add decorative content to expose the glass.

## Upstream Evidence

Inspected local README, src/controls.js, playground/press-effects.js and repository baseline images tab-bar.png / shape-set-lake.png. Also opened the specified live GitHub repository.

Use LiquidGlass / LiquidGlassNavbar / LiquidGlassSwitch or the underlying LiquidGlassWebGL as appropriate after Android integration inspection. Real backdrop binding is essential. Group surfaces to avoid unnecessary WebGL contexts. Static surfaces should not continuously repaint.

Interaction requirements: selected lens follows dragging, expands under pressure, brightens under press and springs back on release. Source defaults and behavior are the baseline; do not substitute an unrelated generic scale tween and call it an exact replica.

Common controls show refraction, dispersion, frost, tint and highlight; remaining source parameters are available in More. V2 concept frost 0.12 and tint 0.08 are proposals, not upstream default claims. Check readable contrast on both themes and all font sizes.

## Typography

User requested actual iOS system fonts. Apple's public San Francisco font license at https://developer.apple.com/fonts/ restricts use and embedding, and does not grant Android embedding rights. No Apple font has been downloaded, installed or bundled. No separate licensed font assets were provided.

Proposed implementation fallback: distributable neutral sans-serif families selected after checking their licenses; match iOS-like size hierarchy, weight, line height and spacing. Exact Apple font use remains unfulfilled and must not be claimed. Concept images only illustrate the typography direction.

Typography targets in logical pixels: page titles 28/600, compact subpage titles 24/600, body 16/400 with 1.5 line height, controls 15-16/500, captions 13/400. Letter spacing 0. Five global font scales must reflow, not clip text. Physical device system insets and font rendering need actual testing.

## Prompt Briefs

Both boards generated using the built-in image-generation tool, with previous boards as layout references and actual upstream baseline screenshots as glass-material references.

Main board brief: three legible full portrait screens for the collection/detail/reverse workflows, stronger clear refractive lenses, subtle spectral edges, lighter iOS-like neutral sans-serif typography, photographic covers, original/current segmented toggle and transparent copy/save controls. Preserve functional hierarchy and avoid solid blue button fills, decorative wallpaper, metallic outlines and unrelated widgets.

Settings board brief: same stronger glass language applied to system/light/dark segmented selection, five-step font control, full glass parameter preview and sliders, dark API form with service address/key/two model names and untested capability states. Keep actual field text and long-form reading clear.

## Verification Still Required After Approval

Compare runtime optical effects and pressure/drag/release behavior against the upstream playground; static concepts are not a fidelity test. Verify mobile safe areas, largest fonts, dark/light contrast, images passing beneath fixed controls, API and persistence behavior, and frame pacing on vivo X300. No APK or interactive preview was created during this design revision.
