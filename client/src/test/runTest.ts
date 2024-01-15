import * as path from 'path';

import { runTests } from '@vscode/test-electron';

async function main() {
	try {
		// The folder containing the Extension Manifest package.json
		// Passed to `--extensionDevelopmentPath`
		const extensionDevelopmentPath = path.resolve(__dirname, '../../');

		// The path to test runner
		// Passed to --extensionTestsPath
		const extensionTestsPath = path.resolve(__dirname, './suite/index');

		// Use win64 instead of win32 for testing Windows
		const platform = process.platform === 'win32' ? 'win32-x64-archive' : undefined;

		// Download VS Code, unzip it and run the integration test
		await runTests({ extensionDevelopmentPath, extensionTestsPath, platform });
	} catch (err) {
		console.error('Failed to run tests');
		process.exit(1);
	}
}

main();
