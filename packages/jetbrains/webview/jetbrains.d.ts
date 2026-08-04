/// <reference types="vite/client" />

declare module '@vscode/main' {
  // The VS Code webview main module runs side effects only.
}

declare interface VSCodeAPI {
  postMessage(message: unknown): void;
  getState(): unknown;
  setState(state: unknown): void;
}

type JetBrainsConfig = {
  apiUrl?: string;
  workspaceFolder: string;
  workspaceFolders?: Array<{ name: string; path: string }>;
  theme: string;
  connectionStatus: string;
  cliAvailable?: boolean;
  extensionVersion?: string;
  platform?: string;
  arch?: string;
  panelType?: 'chat' | 'agentManager';
  viewMode?: 'sidebar' | 'editor';
  initialSessionId?: string | null;
};

declare global {
  interface Window {
    __JETBRAINS_CONFIG__?: JetBrainsConfig;
    __VSCODE_CONFIG__?: JetBrainsConfig;
    __OPENCHAMBER_JETBRAINS_BRIDGE__?: VSCodeAPI;
    __OPENCHAMBER_LOCAL_ORIGIN__?: string;
    __OPENCHAMBER_ELECTRON__?: {
      macVibrancySupported?: boolean;
      macVibrancy?: boolean;
      runtime?: unknown;
    };
    __OPENCHAMBER_PLATFORM__?: string;
  }
}

export {};
