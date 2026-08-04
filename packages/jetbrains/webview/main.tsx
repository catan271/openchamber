interface VSCodeAPI {
  postMessage(message: unknown): void;
  getState(): unknown;
  setState(state: unknown): void;
}

function createDevBridge(): VSCodeAPI {
  return {
    postMessage(message: unknown) {
      console.log('[JetBrainsBridge:dev:post]', message);
      // In dev without a host, echo back a stub success for bridge requests.
      const req = message as { id?: string; type?: string } | undefined;
      if (req?.id) {
        setTimeout(() => {
          window.dispatchEvent(new MessageEvent('message', {
            data: { id: req.id, type: req.type, success: true, data: {} },
          }));
        }, 10);
      }
    },
    getState: () => null,
    setState: () => {},
  };
}

function getDevConfig(): Window['__JETBRAINS_CONFIG__'] {
  return {
    workspaceFolder: '/',
    workspaceFolders: [{ name: 'Dev', path: '/' }],
    theme: 'dark',
    connectionStatus: 'connecting',
    cliAvailable: false,
    extensionVersion: 'dev',
    platform: 'unknown',
    arch: 'unknown',
    panelType: 'chat',
    viewMode: 'sidebar',
  };
}

window.__OPENCHAMBER_JETBRAINS_BRIDGE__ ??= createDevBridge();
window.__VSCODE_CONFIG__ = window.__JETBRAINS_CONFIG__ ?? getDevConfig();

// Reuse the VS Code webview bootstrap; it reads window.__VSCODE_CONFIG__ and
// acquireVsCodeApi() which we have aliased to the JetBrains bridge.
await import('../../vscode/webview/main');

export {};
