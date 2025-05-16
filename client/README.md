# DRL (Drools Rule Language) Editor Extension

This extension provides syntax highlighting and code completion for DRL files.

## Features

- Syntax highlighting
- Code completion

## How to build

Under `client` directory, run:

```bash
npm install
npm run pack:dev
```

vsix file will be generated in `dist` directory.

## Known Issues

- Code completion may suggest words that are not valid in the current context
- This is alpha version. If you find any issues, please report them in [github issues](https://github.com/kiegroup/drools-lsp/issues)
