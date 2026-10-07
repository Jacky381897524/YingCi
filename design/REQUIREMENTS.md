# YingCi V1.0.0

Status: User approved the revision-2 UI with "不错，就这样吧". Android implementation and APK packaging are authorized. App name: 映词.

Latest revision: user requests substantially stronger glass, faithful reuse of the specified upstream project, and iOS-style typography. See V2-DESIGN.md. The first two boards are superseded proposals. V2 is approved; actual Apple font embedding remains unavailable under the public license.

## Deliverable

- Installable Android APK for user testing on vivo X300.
- Prioritize smooth scrolling, gestures, press feedback and page transitions. Actual device performance must be measured; no verified frame-rate claim yet.
- No application server. Store the collection and settings on the phone.
- AI requests go directly to the user's configured provider. Reverse prompting sends the selected reference image to that provider; local storage does not imply offline AI.

## Prompt Collection

- Gallery entries show ONLY a cover and a custom name.
- Fuzzy search over names and prompt text.
- Create entries, edit names, upload and replace covers.
- Detail shows the full cover and full prompt with one-tap copy.
- Persist immutable original prompt and one replaceable edited prompt. Editing again replaces only the edited version.
- Toggle original/current text; copy exactly the currently displayed version.
- Editing, cancel, save, empty state, validation and deletion confirmation must be designed consistently.

## Appearance

- Minimal iOS-inspired visual language on Android.
- System/light/dark appearance modes.
- Five global font-size steps, with preview. All screens and dialogs must adapt without clipping at the largest step.
- Glass controls with restrained refraction; readable solid text and intact cover images.
- Preview and save changes to appearance and glass settings.
- Press/release spring feedback, moving navigation selection and coordinated detail transitions. Respect system animation/accessibility preferences.

## Liquid Glass Reference

Local source: C:/人工智能图片视频/codex/Huitu skill/reference/webgl-apple-liquid-glass

Upstream: https://github.com/Oliverrr2424/webgl-apple-liquid-glass

Previously recorded source commit: f84657b6ecd77248808515de4ce9ca7629d7aef8. MIT attribution must be retained if reused.

Confirmed parameters from the local README: refraction, edgeReach, edgeWidth, dispersion, frost, backdropBlur, body, absorption, tint, rim, reflection, highlight, lightAngle, echo, hairline, hairWidth, roundness. Also consider surface opacity and light/dark tint tone.

Expose common parameters on the main display screen and remaining material parameters under More. Use real renderer parameters, not disconnected sliders. Preview changes before Save applies the global persisted configuration; include restore-default behavior. The preview values in concept images are proposals, not assertions of upstream defaults.

Choose Android rendering/integration strategy after design approval and local build-tool inspection. Do not assume WebGL effects can be copied into native UI without integration work. Avoid a separate graphics context per gallery entry and unnecessary continuous redraw when idle.

## Model Connections

- Editable API protocol, service base URL, secret API key and exact model names.
- Distinguish image-understanding model for reverse prompting from image-generation model.
- Allow separate provider configurations where needed; a single shared service is only the compact concept state.
- Start with a verified protocol implementation; do not claim arbitrary-provider compatibility.
- Test actual required capabilities, not merely whether a server or model list is reachable.
- Show untested/testing/success/error states, actionable authentication/model/endpoint/timeout errors and cancellation.
- A generation test may incur provider charges and must make its purpose clear when invoked.
- Store secrets with Android-backed protection; exclude keys from logs and ordinary exports.
- No credentials have been supplied and no provider connection has been tested.

## Style Reverse Prompting

- Input: a style reference image.
- Output: a reusable instruction that can be paired with ANY new content photo.
- Separate transferable style, medium, texture, lighting, palette, layout and abstraction rules from the reference's specific subject/identity/objects/background.
- Anchor content, identity, subject count, pose and relationships to the NEW uploaded image.
- Do not force unrelated reference characters, accessories, environments, logos or watermarks into later results.
- For the first user example: transferable 3:4 composition, 50:50 split, photography above and selective hand-drawn paper collage below. Person, chair, hill and flowers are not mandatory future subjects.
- For the second example: transfer drawing language and atmosphere without forcing the original character, outfit, cave or branding onto every image.
- Support edit/copy/save-to-collection of extracted prompts; preserve original model output on initial save.
- Include picking/replacing the reference, running/cancelling extraction, errors, retry and success states.
- Do not promise perfect reconstruction or identical output. Results depend on the configured vision and generation models.

## Storage Proposal

- App-private database: entry metadata, original/current prompts and media references.
- App-private files/covers: imported full covers with stable IDs.
- App-private files/thumbnails: generated size-limited previews.
- App-private cache: disposable request and image-processing intermediates.
- Settings stored separately; secrets protected separately.
- No loose files dumped into shared photo/download directories. User-chosen export is the exception.
- Implement atomic updates, cleanup of unreferenced media and bounded cache usage.
- Plan backup/export and restore because local-only data must have an explicit recovery route; define its final scope before release.

## Review Artifacts

- 01-library-detail-reverse.png: collection, prompt detail, style-extraction result.
- 02-appearance-glass-api.png: appearance/font preview, glass preview/sliders, dark API settings.
- Generated using the built-in image-generation tool as static UI concepts. Not an interactive prototype, functional screenshot or APK.
- The images use illustrative covers and abbreviated sample prompt text. User reference files remain unchanged.
- Proposed colors: neutral white #FAFAFC, ink #18191D, secondary #83858D, blue accent #3478F6; dark settings #151618.
- Proposed structure: collection / reverse / settings bottom navigation. Gallery images have restrained corners; glass navigation uses capsule geometry.
- Additional states to implement after approval: settings index, add/edit entry, original toggle, empty/error/loading states, reverse upload state, advanced material controls and unsaved-setting handling.

## Next Gate

Obtain user feedback on the two UI boards. Then build an interactive preview to verify motion and navigation, followed by Android implementation, real persistence/API integration, build verification and installable V1.0.0 APK. Performance on the physical vivo X300 remains unverified until tested there.
