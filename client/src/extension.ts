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

// Debug mode is selected by the LSDEBUG env var; it switches the server transport
// to a socket (below) and enables log.debug output.
const DEBUG_MODE = process.env.LSDEBUG === 'true';

// Single output channel shared by the extension's own logging and the language
// client's server/trace output, so everything lands in one "DRL Language Server" panel.
let channel: vscode.OutputChannel | undefined;
const log = {
    info: (msg: string) => channel?.appendLine('INFO: ' + msg),
    warn: (msg: string) => channel?.appendLine('WARNING: ' + msg),
    error: (msg: string) => channel?.appendLine('ERROR: ' + msg),
    debug: (msg: string) => { if (DEBUG_MODE) { channel?.appendLine('DEBUG: ' + msg); } },
};

export function activate(context: vscode.ExtensionContext) {
    channel = vscode.window.createOutputChannel('DRL Language Server');
    context.subscriptions.push(channel);

    log.info('Activating extension "DRL Language Server"....');
    let serverOptions: ServerOptions | undefined = undefined;

    if (DEBUG_MODE) {
        log.debug('Starting in debug mode');
        let connectionInfo = {
            port: 9925,
            host: "127.0.0.1"
        };
        log.debug('connectionInfo ' + JSON.stringify(connectionInfo));
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
        const javaHome = getJavaHome();

        let executable: string = `java`;

        if (javaHome) {
            // If java home is available, compose a path
            executable = path.join(javaHome, 'bin', 'java');
            log.debug('java executable path : ' + executable);
        } else {
            log.warn('java home is not found. Invoking java without path.');
        }

        // path to the launcher.jar
        let serverJar = path.join(__dirname, "..", 'lib', 'drools-lsp-server-jar-with-dependencies.jar');
        if (fs.existsSync(serverJar)) {
            log.debug('server jar path : ' + serverJar);
        } else {
            log.error(`${serverJar} not found`);
            return;
        }

        const config = vscode.workspace.getConfiguration();
        const args: string[] = [];

        const logLevel: string | undefined = config.get('drools.lsp.logLevel');
        if (logLevel) {
            args.push(`-Ddrools.lsp.logLevel=${logLevel}`);
        }

        const lintProps = [
            'drools.lsp.lint.missingEnd',
            'drools.lsp.lint.missingSeparator',
            'drools.lsp.lint.missingSemicolon',
            'drools.lsp.lint.unbalancedParens',
            'drools.lsp.lint.mvelPropertyAccess',
        ];
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

        // Custom Maven POM(s) for classpath resolution — a single path or a list,
        // each absolute or workspace-relative. Joined with the OS path separator
        // into one JVM arg; empty means pom.xml at the workspace root.
        const pomPathSetting = config.get<string | string[]>('drools.lsp.maven.pomPath');
        const pomPaths = (Array.isArray(pomPathSetting) ? pomPathSetting : (pomPathSetting ? [pomPathSetting] : []))
            .map(p => p.trim())
            .filter(p => p.length > 0);
        if (pomPaths.length > 0) {
            const workspaceRoot = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
            const resolved = pomPaths.map(p =>
                (workspaceRoot && !path.isAbsolute(p)) ? path.join(workspaceRoot, p) : p);
            args.push(`-Ddrools.lsp.maven.pomPath=${resolved.join(path.delimiter)}`);
        }

        args.push('-jar', serverJar);

        serverOptions = {
            command: executable,
            args: [...args],
            options: {}
        };
    }

    if (serverOptions) {
        log.info('Starting language client');
        let clientOptions: LanguageClientOptions = {
            documentSelector: [{scheme: 'file', language: 'drools'}],
            synchronize: {
                fileEvents: vscode.workspace.createFileSystemWatcher('**/target/classes/**/*.class')
            },
            outputChannel: channel
        };
        languageClient = new LanguageClient('Drools', 'DRL Language Server', serverOptions, clientOptions);
        languageClient.start();

        log.info('DRL Language Server activated.');
    }
}

// this method is called when your extension is deactivated. Returning the
// stop() promise lets VSCode await a clean language-server shutdown.
export function deactivate(): Thenable<void> | undefined {
	log.info('DRL Language Server deactivated.');
	if (!languageClient) {
		return undefined;
	}
	return languageClient.stop();
}

function getJavaHome() : string | undefined {

    let javaHome: string | undefined;

    javaHome = vscode.workspace.getConfiguration().get('java.home');
    if (javaHome) {
        log.debug('java.home from workspace configuration : ' + javaHome);
        return javaHome;
    }

    // GHA_JAVA_HOME is to specify JAVA_HOME for Github Action (MacOS changes JAVA_HOME internally)
    javaHome = process.env.GHA_JAVA_HOME;
    if (javaHome) {
        log.debug('GHA_JAVA_HOME from process env : ' + javaHome);
        return javaHome;
    }

    javaHome = process.env.JAVA_HOME;
    if (javaHome) {
        log.debug('JAVA_HOME from process env : ' + javaHome);
        return javaHome;
    }

    log.warn('JAVA_HOME not found');
    return javaHome; // undefined
}