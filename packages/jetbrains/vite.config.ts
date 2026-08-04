import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig(({ mode }) => ({
  root: path.resolve(__dirname, 'webview'),
  base: './',
  plugins: [
    react({
      babel: {
        plugins: ['babel-plugin-react-compiler'],
      },
    }),
  ],
  resolve: {
    alias: [
      { find: '@openchamber/ui', replacement: path.resolve(__dirname, '../ui/src') },
      { find: '@vscode', replacement: path.resolve(__dirname, './webview') },
      { find: '@', replacement: path.resolve(__dirname, '../ui/src') },
      { find: 'node:child_process', replacement: path.resolve(__dirname, './webview/node-process-shim.ts') },
    ],
  },
  worker: {
    format: 'es',
  },
  define: {
    'process.env.NODE_ENV': JSON.stringify(mode === 'production' ? 'production' : 'development'),
    'global': 'globalThis',
    '__OPENCHAMBER_WEBVIEW_BUILD_TIME__': JSON.stringify(new Date().toISOString()),
  },
  envPrefix: ['VITE_'],
  server: {
    host: 'localhost',
    port: 5174,
    strictPort: true,
    cors: true,
    headers: {
      'Access-Control-Allow-Origin': '*',
    },
    hmr: {
      host: 'localhost',
      protocol: 'ws',
      port: 5174,
    },
  },
  optimizeDeps: {
    include: ['@opencode-ai/sdk/v2'],
  },
  build: {
    outDir: path.resolve(__dirname, 'dist/webview'),
    emptyOutDir: true,
    rollupOptions: {
      input: path.resolve(__dirname, 'webview/index.html'),
      output: {
        entryFileNames: 'assets/[name].js',
        chunkFileNames: 'assets/[name].js',
        assetFileNames: 'assets/[name].[ext]',
      },
    },
  },
}));
