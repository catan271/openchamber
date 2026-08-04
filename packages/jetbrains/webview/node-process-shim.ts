// The SDK exports a Node-only process helper alongside its browser API client.
// The JCEF webview never invokes it, but Rollup must be able to resolve it.
export function spawnSync(): never {
  throw new Error('Node process APIs are unavailable in the JetBrains webview.');
}
