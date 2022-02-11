This is an experimental implementation of [LSP](https://microsoft.github.io/language-server-protocol/) based project intended to provide support for a [DRL](https://docs.jboss.org/drools/release/latest/drools-docs/html_single/#drl-rules-con_drl-rules) text editor.

It is composed of 2 parts: the **server** containing the actual LSP implementation and the **client** which is a VSCode extension consuming the services provided by the server.

The server is a plain Java/Maven project. Executing a `mvn clean package` in its folder will generate a jar file that will be automatically linked and consumed by the client when executed as a VSCode extension.


Server Architecture
===================

The server part is composed of three modules:

1. drools-parser
2. drools-completion
3. drools-lsp-server

drools-parser is responsible for actual drl-syntax parsing, eventually invoking a JAVA-LSP engine to read the `RHS` content (that is plain Java code); it depends on `org.drools:drools-drl-ast`

drools-completion is used to provide completion suggestion using the C3 engine; it depends on `com.vmware.antlr4-c3:antlr4-c3` and on `drools-parser`

drools-lsp-server is the "gateway" between the client and the parsing/completion logic; by itself it should not implement any business logic, but should be concerned only with communication; it depends directly on `drools-completion`



Usage
=====

Precompiled-server - no debug
_____________________________

1. package server side code with `mvn clean package`
2. goto `client` directory
3. issue `npm install`
4. issue `code .` to start VSCODE in that directory
5. inside VSCODE, select `Run and Debug` (Ctrl+Shift+D) and then start `Run Extension`
6. a new `Extension Development Host` window will appear, with `drl` extension enabled
7. to "debug" server-side event, add `server.getClient().showMessage(new MessageParams(MessageType.Info, {text}));` in server-side code


Connected remote server - debug
_______________________________

1. package server side code with `mvn clean package`
2. start server with `DroolsLspTCPLauncher` from IDE on debug mode; this will start the LSP-server listening on port `9925`
3. goto `client` directory
4. issue `npm install`
5. issue `code .` to start VSCODE in that directory
6. inside VSCODE, select `Run and Debug` (Ctrl+Shift+D) and then start `Debug Extension`
7. the extensions will establish a connection to the server running at port `9925`
8. a new `Extension Development Host` window will appear, with `drl` extension enabled
9. to "debug" server-side event, add breakpoints in server-side code


