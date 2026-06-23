/* --------------------------------------------------------------------------------------------
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 * ------------------------------------------------------------------------------------------ */

import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';

// Import the language client, language client options and server options from VSCode language client.
import {LanguageClient, LanguageClientOptions, ServerOptions, StreamInfo} from 'vscode-languageclient/node';
import * as net from "net";

let languageClient: LanguageClient | undefined;

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

        const javaHome = getJavaHome();

        let executable: string = `java`;

        if (javaHome) {
            // If java home is available, compose a path
            executable = path.join(javaHome, 'bin', 'java');
        } else {
            console.warn('java home is not found. Invoking java without path.');
        }

        // path to the launcher.jar
        let serverJar = path.join(__dirname, "..", 'lib', 'drools-lsp-server-jar-with-dependencies.jar');
        if (fs.existsSync(serverJar)) {
            console.log(`${serverJar} exists`);
        } else {
            console.error(`${serverJar} does not exist : The extension won't work`);
            return;
        }
        const args: string[] = [];

        const lintProps = [
            'drools.lsp.lint.missingEnd',
            'drools.lsp.lint.missingSeparator',
            'drools.lsp.lint.missingSemicolon',
            'drools.lsp.lint.unbalancedParens',
            'drools.lsp.lint.mvelPropertyAccess',
        ];
        const config = vscode.workspace.getConfiguration();
        for (const prop of lintProps) {
            const value: string | undefined = config.get(prop);
            if (value !== undefined) {
                args.push(`-D${prop}=${value}`);
            }
        }

        // Inlay hints: boolean toggle, passed through so an explicit `false`
        // reaches the server (it defaults to enabled). VSCode's global
        // editor.inlayHints.enabled still gates display independently.
        const inlayHintsEnabled: boolean | undefined = config.get('drools.lsp.inlayHints.enabled');
        if (inlayHintsEnabled !== undefined) {
            args.push(`-Ddrools.lsp.inlayHints.enabled=${inlayHintsEnabled}`);
        }

        args.push('-jar', serverJar);

        serverOptions = {
            command: executable,
            args: [...args],
            options: {}
        };
    }

    if (serverOptions) {
        console.log('serverOptions ' + serverOptions);
        // Options to control the language client
        let clientOptions: LanguageClientOptions = {
            documentSelector: [{scheme: 'file', language: 'drools'}],
            synchronize: {
                fileEvents: vscode.workspace.createFileSystemWatcher('**/target/classes/**/*.class')
            }
        };
        // Create and start the language client. In vscode-languageclient v8+,
        // start() returns a Promise<void> (not a Disposable); the client is torn
        // down via stop() in deactivate() below, which keeps it out of
        // context.subscriptions so it isn't disposed twice.
        languageClient = new LanguageClient('Drools', 'DRL Language Server', serverOptions, clientOptions);
        languageClient.start();

        console.log('Congratulations, your extension "drl" is now active!');
    }
}

// this method is called when your extension is deactivated. Returning the
// stop() promise lets VSCode await a clean language-server shutdown.
export function deactivate(): Thenable<void> | undefined {
	console.log('Your extension "drl" is now deactivated!');
	if (!languageClient) {
		return undefined;
	}
	return languageClient.stop();
}

function getJavaHome() : string | undefined {

    let javaHome: string | undefined;

    javaHome = vscode.workspace.getConfiguration().get('java.home');
    if (javaHome) {
        console.log('java.home from workspace configuration : ' + javaHome);
        return javaHome;
    }

    // GHA_JAVA_HOME is to specify JAVA_HOME for Github Action (MacOS changes JAVA_HOME internally)
    javaHome = process.env.GHA_JAVA_HOME;
    if (javaHome) {
        console.log('GHA_JAVA_HOME from process env : ' + javaHome);
        return javaHome;
    }

    javaHome = process.env.JAVA_HOME;
    if (javaHome) {
        console.log('JAVA_HOME from process env : ' + javaHome);
        return javaHome;
    }

    console.log('java home is not found');
    return javaHome; // undefined
}