# Product direction

- This is a private, single-user Android app intended to run only on the owner's device.
- Keep it local-first: no accounts, analytics, ads, cloud backend, or network access unless explicitly requested.
- Firebase App Distribution is the explicit exception: debug APKs are distributed through its Gradle plugin. Keep tester addresses in the uncommitted `firebaseTesters` Gradle property.
- After a debug build passes its required verification, upload it to Firebase App Distribution with concise release notes describing the tested changes.
- Favor useful day-to-day tools over multi-user, monetization, or scalability features.

# Interaction design

- The home screen is a scrollable three-column grid of tool icons.
- Keep each tool fully usable from its grid tile whenever possible. Do not add separate tool landing screens, setup flows, decorative pages, or extra controls unless they are strictly required for the tool to work or by Android.
- Each grid tile should make its current state and primary action immediately clear. Use Android system permission dialogs only when required.
- Avoid bloat. Every visible control, permission, and background behavior must directly support the owner's use of a tool.

# Planned first tool: HFP Streamer

- Create a private functional equivalent of Bluetooth Streamer Pro without copying its name, branding, artwork, or exact UI.
- Place it in the first grid position: row 0, column 0.
- Its grid tile is the complete interface: one state-aware button. When streaming is inactive, pressing it starts streaming; when active, it shows that streaming is on and pressing it stops streaming.
- It routes permitted phone audio to Bluetooth devices that support call audio (HFP/SCO) but not normal music streaming (A2DP).
- Route audio directly through Android's HFP/SCO communication path without capturing or replaying it. Never invoke MediaProjection or show a screen-capture prompt for this tool.
- Once started, keep streaming in a foreground service when the activity is closed or its task is removed. Stop only from the tile or notification action, or when a call, Bluetooth disconnect, route loss, or error requires cleanup.
- Do not add warnings, explanations, or extra setup UI for the owner.
- First validate the feature end-to-end on the owner's physical Android phone and intended car, headset, or hearing aid. Do not rely on an emulator for audio routing verification.
- The tool must never interfere with real phone calls and must restore normal audio routing whenever streaming stops, Bluetooth disconnects, an error occurs, or a call begins.
