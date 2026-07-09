# Contributing to drools-lsp

## Prerequisites

- JDK 17+
- Maven
- Node.js 20+
- npm

## Building

### Server (Java)

From the repository root:

```bash
mvn clean install
```

This builds all Java modules and copies the server uber-JAR into `client/lib/`.

### VS Code Extension

```bash
cd client
npm ci
npm run compile
```

To package a `.vsix` file (generated in `client/dist/`):

```bash
npm run pack:dev
```

## Running in Development

### Precompiled server (no debug)

1. Build the server: `mvn clean install`
2. Go to `client/` and run `npm ci`
3. Open the `client/` directory in VS Code: `code .`
4. Select `Run and Debug` (Ctrl+Shift+D) and start `Run Extension`
5. A new Extension Development Host window will appear with the DRL extension enabled
6. Server-side log messages appear in the VS Code Output channel (select "DRL Language Server" from the dropdown). Use `logger.info(msg)` or `logger.log(Level.FINE, msg)` in server-side code. The log level is configurable via the `drools.lsp.logLevel` extension setting.

### Connected remote server (debug)

1. Build the server: `mvn clean install`
2. Start the server with `DroolsLspTCPLauncher` from your IDE in debug mode — this starts the LSP server listening on port `9925`
3. Go to `client/` and run `npm ci`
4. Open the `client/` directory in VS Code: `code .`
5. Select `Run and Debug` (Ctrl+Shift+D) and start `Debug Extension`
6. The extension will connect to the server running on port `9925`
7. A new Extension Development Host window will appear with the DRL extension enabled
8. Add breakpoints in server-side code to debug

## Releasing

Releases are driven by git tags. To publish a new version (e.g. `1.0.1`):

1. Update `pom.xml` version from `X.Y.Z-SNAPSHOT` to `1.0.1`
2. Commit and push to `main`:
   ```bash
   git commit -am "Release 1.0.1"
   git push origin main
   ```
3. Tag the commit and push the tag:
   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```
4. The `release.yml` workflow will:
   - Build the server JAR
   - Set the extension version from the tag
   - Package and publish the `.vsix` to the VS Code Marketplace
   - Create a GitHub Release with the JAR and VSIX attached
5. After the release, bump `pom.xml` to the next snapshot (e.g. `1.0.2-SNAPSHOT`) and push to `main`

The `VSCE_PAT` repository secret must be configured for Marketplace publishing.
