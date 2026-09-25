# Layoutly Bridge

Layoutly Bridge reads the selected Figma node through the Figma Plugin API and sends design data and assets to the Java backend. Bridge mode does not require a Figma Personal Access Token and does not call the Figma REST API.

## Build

```bash
npm install
npm run build
```

## Load in Figma Desktop

1. Start the Layoutly backend at `http://localhost:8080`.
2. Optionally start the frontend at `http://localhost:5173`.
3. Open Figma Desktop.
4. Open **Plugins > Development > Import plugin from manifest**.
5. Select `layoutly-figma-bridge/manifest.json`.
6. Open a Figma file and select a Frame or node.
7. Run **Plugins > Development > Layoutly Bridge**.
8. Keep **Selected Frame / Selected Node** selected, or explicitly choose **Current Page**.
9. Click **Sync to Layoutly**.
10. After the snapshot is created, click **Open Layoutly**.

The backend URL is editable for development. The plugin uses Figma's `JSON_REST_V1` Plugin API export for a selected node, with a compatibility serializer for Current Page or graceful fallback. It exports only the selected subtree by default, stores a root reference image when possible, deduplicates assets by node ID, and reports non-fatal export failures as snapshot warnings.
