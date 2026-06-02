/* --------------------------------------------------------------------------------------------
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 * ------------------------------------------------------------------------------------------ */

import * as vscode from 'vscode';
import * as assert from 'assert';
import { getDocUri, activate } from './helper';

suite('Completion tests', () => {
	const docUri = getDocUri('empty.drl');

	test('Completes "package" at the beginning', async () => {
		await testCompletion(docUri, new vscode.Position(0, 0), {
			items: [
				{ label: 'package', kind: vscode.CompletionItemKind.Keyword }
			]
		});
	}).timeout(20000); // increase timeout from the default 2000ms because helper.activate waits 2000ms for server startup
});

suite('Class completion tests', () => {
	const docUri = getDocUri('sample.drl');

	test('Suggests class names in pattern position', async () => {
		await activate(docUri);

		// Line 13: "$p :" in the "greeting" rule — caret after the colon
		const position = new vscode.Position(13, 7);

		const actualCompletionList = (await vscode.commands.executeCommand(
			'vscode.executeCompletionItemProvider',
			docUri,
			position
		)) as vscode.CompletionList;

		// Person and Address are compiled domain classes in testFixture/target/classes
		const classLabels = actualCompletionList.items
			.filter(item => item.kind === vscode.CompletionItemKind.Class)
			.map(item => item.label);

		assert.ok(classLabels.includes('Person'), 'Should suggest Person class');
		assert.ok(classLabels.includes('Address'), 'Should suggest Address class');

		// Person is imported, so should have sortText starting with "0_"
		const personItem = actualCompletionList.items.find(
			item => item.label === 'Person' && item.kind === vscode.CompletionItemKind.Class
		);
		assert.ok(personItem, 'Person completion item should exist');
		assert.ok(personItem!.sortText?.startsWith('0_'), 'Imported Person should be ranked first');

		// Address is not imported, so should have sortText starting with "1_"
		const addressItem = actualCompletionList.items.find(
			item => item.label === 'Address' && item.kind === vscode.CompletionItemKind.Class
		);
		assert.ok(addressItem, 'Address completion item should exist');
		assert.ok(addressItem!.sortText?.startsWith('1_'), 'Unimported Address should be ranked after imported classes');
	}).timeout(30000); // classpath resolution via mvn adds time
});

async function testCompletion(
	docUri: vscode.Uri,
	position: vscode.Position,
	expectedCompletionList: vscode.CompletionList
) {
	await activate(docUri);

	// Executing the command `vscode.executeCompletionItemProvider` to simulate triggering completion
	const actualCompletionList = (await vscode.commands.executeCommand(
		'vscode.executeCompletionItemProvider',
		docUri,
		position
	)) as vscode.CompletionList;

	assert.ok(actualCompletionList.items.length >= expectedCompletionList.items.length);

	expectedCompletionList.items.forEach((expectedItem) => {
		const actualItem = actualCompletionList.items.find(item => item.label === expectedItem.label);
		assert(actualItem);
		assert.strictEqual(actualItem.kind, expectedItem.kind);
	});
}
