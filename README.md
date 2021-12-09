This is an experimental implementation of [LSP](https://microsoft.github.io/language-server-protocol/) based project intended to provide support for a [DRL](https://docs.jboss.org/drools/release/latest/drools-docs/html_single/#drl-rules-con_drl-rules) text editor.

It is composed of 2 parts: the **server** containing the actual LSP implementation and the **client** which is a VSCode extension consuming the services provided by the server.

The server is a plain Java/Maven project. Executing a `mvn clean package` in its folder will generate a jar file that will be automatically linked and consumed by the client when executed as a VSCode extension.

