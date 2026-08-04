# OpenChamber JetBrains Plugin

A JetBrains IDE plugin (IntelliJ IDEA, WebStorm, PyCharm, etc.) that ports the VS Code extension experience to the JetBrains platform. It reuses the same shared React UI (`@openchamber/ui`) and OpenCode runtime by hosting it in a JCEF webview.

## Architecture

- **Kotlin plugin host** (`src/main/kotlin/`) — tool window, project service, settings, editor actions, OpenCode CLI management.
- **JCEF webview** — renders the same shared UI used by the web app and VS Code extension.
- **Bridge protocol** — webview ↔ host messages use the same JSON request/response format as the VS Code extension, so most UI code is reused verbatim.
- **Thin TS wrapper** (`webview/main.tsx`) — injects `acquireVsCodeApi()` and `__VSCODE_CONFIG__` globals that the existing VS Code webview bootstrap expects, then imports `packages/vscode/webview/main.tsx`.

## Required Runtime Environment

Before building or running the plugin, install:

1. **Java 21 JDK** (required by Gradle and the IntelliJ Platform)
   - Set `JAVA_HOME` to the JDK root.
   - Verify with `java -version`.
2. **Bun** (already used by the OpenChamber monorepo)
   - Verify with `bun --version`.
3. **IntelliJ IDEA** with the **Plugin DevKit** plugin (for local development / `dev`).

> **Note:** The first Gradle build downloads the IntelliJ Platform SDK (~1 GB). Ensure a stable connection.

## Build

```bash
# From the monorepo root
bun run jetbrains:build
```

This builds the webview with Vite and then packages the plugin with Gradle. The output `.zip` is in:

```
packages/jetbrains/build/distributions/openchamber-<version>.zip
```

### Manual steps

```bash
cd packages/jetbrains

# Build webview only
bun run build:webview

# Build plugin only (requires Java)
./gradlew buildPlugin        # macOS/Linux
.\gradlew.bat buildPlugin    # Windows PowerShell
```

## Development

```bash
# Start the webview dev server and a JetBrains sandbox IDE
bun run jetbrains:dev
```

Or separately:

```bash
bun run dev:webview   # Vite HMR on localhost:5174
./gradlew runIde      # JetBrains sandbox IDE
```

The plugin HTML loads the production bundle by default. To use the dev server, set the `OPENCHAMBER_JETBRAINS_DEV_SERVER` environment variable or update `OpenChamberPanel.kt` to inject `devServerUrl`.

## Project Structure

```
packages/jetbrains/
├── build.gradle.kts                 # Gradle + IntelliJ Platform plugin config
├── settings.gradle.kts
├── gradle.properties
├── webview/
│   ├── index.html                   # Dev entry
│   ├── main.tsx                     # JetBrains bridge shim + imports VS Code main.tsx
│   └── jetbrains.d.ts               # Global type declarations
├── src/main/kotlin/com/openchamber/jetbrains/
│   ├── OpenChamberPlugin.kt
│   ├── OpenChamberToolWindowFactory.kt
│   ├── OpenChamberProjectService.kt
│   ├── OpenChamberPanel.kt          # JCEF browser + HTML injection
│   ├── JcefBrowserWrapper.kt        # JCEF setup + JS bridge
│   ├── OpenChamberStartupActivity.kt
│   ├── opencode/OpenCodeManager.kt  # CLI / external API lifecycle
│   ├── bridge/
│   │   ├── BridgeModels.kt
│   │   └── BridgeMessageHandler.kt  # Webview message handlers
│   ├── actions/                     # Editor context actions
│   ├── settings/                    # Plugin settings page
│   └── util/                        # Theme, persistence helpers
└── src/main/resources/
    ├── META-INF/plugin.xml          # Plugin descriptor
    └── icons/openchamber.svg
```

## What Is Reused from VS Code

- `packages/vscode/webview/main.tsx` — full shared UI bootstrap.
- `packages/vscode/webview/api/bridge.ts` — request/response bridge client.
- `packages/vscode/webview/api/files.ts`, `settings.ts`, `editor.ts`, etc. — runtime API implementations.
- `packages/ui` — all shared React components, stores, sync layer, and theme system.

The only JetBrains-specific additions are:

- `webview/main.tsx` — provides the `acquireVsCodeApi()` global.
- Kotlin host code — replaces the VS Code extension host for filesystem, proxy, settings, and editor actions.

## Implemented Bridge Handlers

| Type | Behavior |
|------|----------|
| `api:proxy` | Forward generic OpenCode API requests. |
| `api:session:message` | Forward session message POST. |
| `api:sse:start` / `api:sse:stop` | Proxy server-sent events via OkHttp SSE. |
| `api:config/settings:get` / `:save` | Plugin settings persistence. |
| `api:config/reload` | Restart OpenCode connection. |
| `api:fs:*` | Local filesystem operations. |
| `api:files/pick` | Native file chooser. |
| `api:opencode/version` | OpenCode version probe. |
| `api:opencode/directory` | Working directory hint. |
| `api:system/info` | Runtime identity. |
| `editor:openFile` | Open file in JetBrains editor. |
| `vscode:openExternalUrl` | Open URL in system browser. |
| `api:github/*` | Disabled — returns unsupported. |

## Known Limitations

- **Terminal**: stubbed (JetBrains terminal integration not yet implemented).
- **Git**: filesystem/git operations use direct file access; deep Git4Idea integration can be added later.
- **GitHub**: disabled in the host; users can use JetBrains' native GitHub integration.
- **TTS / dictation / remote tunnels**: reported as unsupported, matching VS Code runtime behavior.
- **Theme**: uses the IDE background/foreground colors; full VS Code-style theme adapter not yet implemented.

## Next Steps

1. Install Java 21 and set `JAVA_HOME`.
2. Run `bun run jetbrains:build`.
3. Install the produced `.zip` in IntelliJ IDEA via **Settings → Plugins → Install from Disk**.
4. Open the **OpenChamber** tool window from the right sidebar.
