/* --------------------------------------------------------------------------------------------
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 * ------------------------------------------------------------------------------------------ */

import * as path from 'path';
import * as vscode from 'vscode';

// Import the language client, language client options and server options from VSCode language client.
import {LanguageClient, LanguageClientOptions, ServerOptions, StreamInfo} from 'vscode-languageclient';
import * as net from "net";

export function activate(context: vscode.ExtensionContext) {
    console.log('on activate, your extension "drl"....');
    let serverOptions: ServerOptions  | undefined = undefined;

    const DEBUG_MODE = process.env.LSDEBUG;

    console.log('DEBUG_MODE ' + DEBUG_MODE);

    if (DEBUG_MODE === 'true') {
        console.log('Starting in debug mode');
        let connectionInfo = {
            port: 9925,
            host: "127.0.0.1"
        };
        console.log('connectionInfo ' + connectionInfo);
        serverOptions = () => {
            // Connect to language server via socket
            let socket = net.connect(connectionInfo);
            let result: StreamInfo = {
                writer: socket,
                reader: socket
            };
            return Promise.resolve(result);

        };
    } else {
        console.log('Starting without debug');
        // Name of the launcher class which contains the main.
        const main: string = 'org.drools.lsp.server.DroolsLspLauncher';

        const {JAVA_HOME} = process.env;

        // If java home is available continue.
        if (JAVA_HOME) {
            // If java home is available continue.
            console.log(`Using java from JAVA_HOME: ${JAVA_HOME}`);
            let excecutable: string = path.join(JAVA_HOME, 'bin', 'java');

            // path to the launcher.jar
            let classPath = path.join(__dirname, '..', '..', 'drools-lsp-server', 'target', 'drools-lsp-server-jar-with-dependencies.jar');
            const args: string[] = ['-cp', classPath];

            serverOptions = {
                command: excecutable,
                args: [...args, main],
                options: {}
            };
        }
    }

    if (serverOptions) {
        console.log('serverOptions ' + serverOptions);
        // Options to control the language client
        let clientOptions: LanguageClientOptions = {
            // Register the server for plain text documents
            documentSelector: [{scheme: 'file', language: 'drools'}]
        };
        // Create the language client and start the client.
        let languageClient: LanguageClient = new LanguageClient('Drools', 'DRL Language Server', serverOptions, clientOptions);
        let disposable = languageClient.start();

        // Disposables to remove on deactivation.
        context.subscriptions.push(disposable);

        console.log('Congratulations, your extension "drl" is now active!');
    }
}

// this method is called when your extension is deactivated
export function deactivate() { 
	console.log('Your extension "drl" is now deactivated!');
}
