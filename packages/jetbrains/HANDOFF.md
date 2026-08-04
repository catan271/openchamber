# JetBrains Plugin Handoff

## Latest debugging handoff (2026-07-10)

### Current artifact

- Installable ZIP: `packages/jetbrains/build/distributions/openchamber-1.14.1.zip`
- Last successful validation: `node scripts/gradle.mjs buildPlugin`
- The generated descriptor has `<idea-version since-build="243" />` with no `until-build`, so it installs on current 261-series IDEs.

### Completed fixes

- Moved IntelliJ Platform Gradle plugin versions into `settings.gradle.kts` `pluginManagement`; the former `org.jetbrains.intellij.platform.settings` setup caused a duplicate classpath plugin failure.
- Fixed the Kotlin `jvmToolchain` property conversion to use an integer.
- Corrected Kotlin compilation errors in the JCEF wrapper, action prompts, bridge imports, settings imports, and panel visibility/lifecycle.
- Replaced `Map<String, Any>` JSON serialization in `OpenChamberPanel` with explicit `JsonObject`/`JsonArray` builders. The previous code caused `Serializer for class Any not found` before the webview HTML could load.
- Changed editor action payloads to `JsonObject`, matching `OpenChamberPanel.postCommand`'s typed `JsonElement` payload.
- Corrected host-to-webview message delivery in `OpenChamberPanel.postRaw`: it now dispatches a browser `message` event rather than passing a JavaScript object to code that calls `JSON.parse`.
- Seeded the webview configuration's `connectionStatus` from `OpenCodeManager.getStatus()` when the tool window is opened, avoiding a permanently stale `connecting` state when OpenCode started before the panel.

### Remaining issue: black/empty tool window

The user still reports a black panel after installing the latest artifact. The next likely issue is a JCEF bridge startup race:

- `JcefBrowserWrapper` currently installs `window.__OPENCHAMBER_JCEF_POST__` in `CefLoadHandler.onLoadEnd`.
- The Vite module at `webview/assets/index.js` can send bridge requests during module initialization, before `onLoadEnd` runs.
- The in-page bridge currently drops messages while `window.__OPENCHAMBER_JCEF_POST__` is absent, so initial settings/bootstrap requests can time out silently.

Recommended next change:

1. Add a `JcefBrowserWrapper` method that returns `jsQuery.inject("JSON.stringify(message)")`.
2. During `OpenChamberPanel.buildWebviewHtml()`, inject the generated call directly into the initial `<script>` that defines `window.__OPENCHAMBER_JCEF_POST__`.
3. Remove the `CefLoadHandler` injection path to avoid a late/racy bridge installation.
4. Package with `node scripts/gradle.mjs buildPlugin` and verify the panel in a real IDE.

The browser automation CLI (`agent-browser`) is not installed in this environment, and it only supports Chromium/Electron CDP targets, not JetBrains JCEF directly. No live IDE interaction has been performed.

## What was built

A JetBrains plugin port of the OpenChamber VS Code extension, located in `packages/jetbrains/`.

It reuses the existing shared webview from `packages/vscode/webview/main.tsx` by providing a small JetBrains-specific shim (`packages/jetbrains/webview/main.tsx`) that injects the VS Code API globals (`acquireVsCodeApi()`, `__VSCODE_CONFIG__`) expected by the VS Code webview code. The plugin hosts the webview inside a JCEF (JetBrains Chromium Embedded Framework) browser panel in a tool window.

The Kotlin side implements the same request/response bridge protocol used by the VS Code extension so the shared TypeScript webview code can talk to the JetBrains host with minimal changes.

## Current state

- Project scaffolding is complete:
  - Gradle + IntelliJ Platform Plugin (`build.gradle.kts`)
  - Plugin descriptor (`src/main/resources/META-INF/plugin.xml`)
  - Tool window, JCEF panel, settings page, editor context actions
  - JS bridge and message handlers
  - OpenCode CLI / external API lifecycle manager
  - Vite webview build with `@vscode` path alias to `packages/vscode/webview`
  - `scripts/gradle.mjs` cross-platform Gradle runner
  - Root `package.json` scripts: `jetbrains:dev`, `jetbrains:build`, `jetbrains:build:webview`, `jetbrains:type-check`

- Webview type-check passes:
  - `bun run --cwd packages/jetbrains type-check`

- Webview production build passes:
  - `bun run --cwd packages/jetbrains build:webview`

- Kotlin/Gradle build has **not yet been run** because Java 21 is not installed on this machine.

## What's blocked

Gradle requires **Java 21 JDK**. This machine currently has Java 17 only (`C:\Program Files\Java\jdk-17`, `JAVA_HOME` points there). The next step is installing Java 21 and running `bun run jetbrains:build`.

## Next steps

1. Install Java 21 JDK.
   - Option A (recommended): `winget install EclipseAdoptium.Temurin.21.JDK`
   - Option B: Download from https://adoptium.net and install manually.
   - Option C: Point the build to an existing Java 21 install.
2. Set `JAVA_HOME` to the Java 21 install and ensure `java -version` reports 21.
3. Run the build:
   ```bash
   bun run jetbrains:build
   ```
4. Fix any Kotlin compilation errors that surface.
5. Run the plugin in the sandbox IDE:
   ```bash
   bun run jetbrains:dev
   ```
6. Verify the webview loads, the bridge is active, and core flows work:
   - Bootstrap / API proxy
   - Sending a message (`api:session:message`)
   - SSE streaming events
   - Opening files / file picker
   - Settings persistence

## Key commands

| Command | Purpose |
| --- | --- |
| `bun run jetbrains:dev` | Start webview dev server + launch sandbox IDE |
| `bun run jetbrains:build` | Build the webview + Kotlin plugin package |
| `bun run --cwd packages/jetbrains type-check` | Type-check the webview shim |
| `bun run --cwd packages/jetbrains build:webview` | Build just the webview assets |
| `bun run --cwd packages/jetbrains gradlew --tasks` | List available Gradle tasks |
| `bun run --cwd packages/jetbrains gradlew buildPlugin` | Build the plugin distribution ZIP |

## Important files

| File | Purpose |
| --- | --- |
| `build.gradle.kts` | Gradle build, plugin packaging, webview asset copy |
| `src/main/resources/META-INF/plugin.xml` | Plugin descriptor |
| `src/main/kotlin/com/openchamber/jetbrains/OpenChamberPanel.kt` | JCEF panel, generated HTML, bridge injection |
| `src/main/kotlin/com/openchamber/jetbrains/JcefBrowserWrapper.kt` | JCEF browser setup and JS query channel |
| `src/main/kotlin/com/openchamber/jetbrains/bridge/BridgeMessageHandler.kt` | Webview message dispatch |
| `src/main/kotlin/com/openchamber/jetbrains/opencode/OpenCodeManager.kt` | OpenCode CLI / external API lifecycle |
| `src/main/kotlin/com/openchamber/jetbrains/settings/OpenChamberSettingsConfigurable.kt` | Plugin settings UI |
| `src/main/kotlin/com/openchamber/jetbrains/actions/OpenChamberEditorActions.kt` | Editor context-menu actions |
| `webview/main.tsx` | JetBrains bridge shim that reuses VS Code webview entry |
| `webview/jetbrains.d.ts` | Global types including Vite client reference |
| `vite.config.ts` | Vite build config with `@vscode` alias |
| `README.md` | Setup and build instructions |

## Known implementation notes / risks

- **Resource loading**: The plugin writes the built `index.html` into a resolved webview directory and loads it via `file://`. A fallback extraction-from-JAR path is implemented but not runtime-tested.
- **External OpenCode server**: Like the VS Code extension, the plugin can run against an existing OpenCode server or rely on the user to start one. The `OpenCodeManager` currently prefers using an external server if configured; if none is set, it prompts the user to start one manually.
- **JCEF availability**: JCEF is bundled with recent JetBrains IDEs, but it can be disabled or unavailable in some environments. The plugin should surface a clear error if JCEF is not available.
- **Bridge parity**: The implemented handlers mirror the VS Code extension JSON protocol. If the VS Code extension adds new handlers, they need to be added to `BridgeMessageHandler.kt` as well.
- **VS Code webview changes**: Because the JetBrains webview imports `packages/vscode/webview/main.tsx`, breaking changes in the VS Code webview's expected globals or bridge contract will affect this plugin.

## Environment prerequisites

- Java 21 JDK (not yet installed)
- `JAVA_HOME` pointing to the Java 21 install
- Bun (already used by the monorepo)
- IntelliJ IDEA with Plugin DevKit (recommended for development)

## Validation checklist for next owner

- [ ] Java 21 installed and `java -version` reports 21
- [ ] `bun run jetbrains:build` succeeds
- [ ] Sandbox IDE launches with `bun run jetbrains:dev`
- [ ] Tool window shows the OpenChamber webview
- [ ] Bridge handshake works (no `acquireVsCodeApi` errors in JCEF devtools)
- [ ] Can send a message and receive SSE streamed response
- [ ] File picker / open-file bridge calls work
- [ ] Settings changes persist
