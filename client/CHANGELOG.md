# Change Log

## 1.0.0

Initial release.

### Code Editing
- Syntax highlighting for DRL files
- Code completion for grammar keywords, Java class names in LHS patterns, fields/properties inside pattern constraints, and DRL `declare` types (including cross-file within the workspace)
- Inlay hints for bound variables
- Live class index refresh on recompile

### Navigation
- Go-to definition for DRL and Java types
- Find references for DRL types and bound variables
- Rename for DRL declared types and bound variables
- Document symbols (outline view)
- Type hierarchy for DRL types
- Folding ranges for DRL blocks and comments

### Diagnostics
- Syntax error diagnostics
- Lint diagnostics (missing `end`, missing constraint separator, missing semicolon, unbalanced parentheses, MVEL property-access style)
- Unknown-type lint with typo quick-fix for DRL-declared types
- Configurable severity for each lint rule

### Information
- Hover tooltips for DRL/Java types with doc-comment rendering
- Reference-count code lens for DRL declared types

### Configuration
- Configurable log level, lint severities, inlay hints toggle, and Maven POM path for classpath resolution
