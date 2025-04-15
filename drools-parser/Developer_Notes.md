## drools-parser

Parser development has been done on drools main branch, so this module just takes the antlr4 grammar files from drools.

At the moment, the antlr4 grammar files are the same as the ones in drools main branch except for cleaning up unused java codes. In the future, we may need to customize the grammar files and/or add some drools dependencies to support requirements from lsp clients (DRL Editor) .

### Development tips
- IntelliJ IDEA has an ANTLR4 plugin, which "ANTLR Preview" window displays a parse tree. It is very useful to debug the parser rules.

### Resources
[The Definitive ANTLR 4 Reference](https://pragprog.com/titles/tpantlr2/the-definitive-antlr-4-reference/)