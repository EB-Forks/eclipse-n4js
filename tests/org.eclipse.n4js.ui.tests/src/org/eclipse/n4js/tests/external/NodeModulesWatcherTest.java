/**
 * Copyright (c) 2019 NumberFour AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   NumberFour AG - Initial API and implementation
 */
package org.eclipse.n4js.tests.external;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import org.eclipse.n4js.ui.external.NodeModulesListener.NodeModulesChange;
import org.eclipse.n4js.ui.external.NodeModulesWatcher;
import org.eclipse.n4js.utils.io.FileDeleter;
import org.eclipse.swt.widgets.Display;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Testing {@link NodeModulesWatcher}.
 */
@SuppressWarnings("javadoc")
public class NodeModulesWatcherTest {

	private static Display display;
	private NodeModulesWatcher watcher = null;
	private File projectFolder = null;
	private File nodeModulesFolder = null;

	private final Queue<List<NodeModulesChange>> eventQueue = new ConcurrentLinkedQueue<>();

	/**
	 * In case of failures, the tests in this class may well wait for an event that never arrives. Thus we use this
	 * timeout for all test methods.
	 */
	// FIXME !!!!!!!
	// @Rule
	// public Timeout globalTimeout = Timeout.seconds(20);

	@BeforeClass
	public static void beforeClass() {
		display = Display.getDefault();
	}

	@AfterClass
	public static void afterClass() {
		display.dispose();
		display = null;
	}

	@Before
	public void before() throws Exception {
		watcher = new NodeModulesWatcher(display);
		watcher.addListener(changes -> {
			eventQueue.add(changes);
		});

		String rootName = "target" + File.separator + NodeModulesWatcherTest.class.getSimpleName();
		projectFolder = new File(rootName).getAbsoluteFile();
		FileDeleter.delete(projectFolder);
		projectFolder.mkdirs();
		nodeModulesFolder = new File(projectFolder, "node_modules");
		nodeModulesFolder.mkdir();
	}

	@After
	public void after() {
		try {
			FileDeleter.delete(projectFolder);
		} catch (IOException e) {
			// ignore
		}
		projectFolder = null;
		nodeModulesFolder = null;

		watcher.close();
		watcher = null;
	}

	@Test
	public void testAddPackage() throws Exception {
		watcher.addProjectFolder(projectFolder.toPath());

		Thread.sleep(1000);

		File lodash = new File(nodeModulesFolder, "lodash");
		lodash.mkdir();

		List<NodeModulesChange> actualChanges = waitForChanges();
		assertChanges(actualChanges, "CREATE: lodash in " + projectFolder);
	}

	@Test
	public void testRemovePackage() throws Exception {
		File lodash = new File(nodeModulesFolder, "lodash");
		lodash.mkdir();

		watcher.addProjectFolder(projectFolder.toPath());

		Thread.sleep(1000);

		lodash.delete();

		List<NodeModulesChange> actualChanges = waitForChanges();
		assertChanges(actualChanges, "DELETE: lodash in " + projectFolder);
	}

	@Test
	public void testAddModifyDeletePackageJsonInExistingPackage() throws Exception {

		// create folder for lodash *before* adding the node_modules folder to the watcher
		File lodash = new File(nodeModulesFolder, "lodash");
		lodash.mkdir();

		watcher.addProjectFolder(projectFolder.toPath());
		Thread.sleep(1000);

		testAddModifyDeletePackageJson(lodash);
	}

	@Test
	public void testAddModifyDeletePackageJsonInNewPackage() throws Exception {

		watcher.addProjectFolder(projectFolder.toPath());
		Thread.sleep(1000);

		// create folder for lodash *after* adding the node_modules folder to the watcher
		File lodash = new File(nodeModulesFolder, "lodash");
		lodash.mkdir();

		waitForChanges(); // package creation event is tested by another method (we only need to consume it)

		testAddModifyDeletePackageJson(lodash);
	}

	private void testAddModifyDeletePackageJson(File lodash) throws Exception {

		File packageJson = new File(lodash, "package.json");
		packageJson.createNewFile();

		assertChanges(waitForChanges(), "MODIFY: lodash in " + projectFolder);

		Files.write(packageJson.toPath(), Collections.singletonList("{}"));

		assertChanges(waitForChanges(), "MODIFY: lodash in " + projectFolder);

		packageJson.delete();

		assertChanges(waitForChanges(), "MODIFY: lodash in " + projectFolder);
	}

	@Test
	public void testAddPackageAndPackageJsonInOneGo() throws Exception {

		watcher.addProjectFolder(projectFolder.toPath());
		Thread.sleep(1000);

		File lodash = new File(nodeModulesFolder, "lodash");
		lodash.mkdir();
		File packageJson = new File(lodash, "package.json");
		packageJson.createNewFile();

		assertChanges(waitForChanges(), "CREATE: lodash in " + projectFolder);

		Files.write(packageJson.toPath(), Collections.singletonList("AA"));

		assertChanges(waitForChanges(), "MODIFY: lodash in " + projectFolder);

		Files.write(packageJson.toPath(), Collections.singletonList("BB"));

		assertChanges(waitForChanges(), "MODIFY: lodash in " + projectFolder);
	}

	@Test
	public void testBurstOfChanges() throws Exception {

		watcher.addProjectFolder(projectFolder.toPath());
		Thread.sleep(1000);

		new File(nodeModulesFolder, "package0").mkdir();
		new File(nodeModulesFolder, "package1").mkdir();
		Thread.sleep(100);
		new File(nodeModulesFolder, "package2").mkdir();
		Thread.sleep(300);
		new File(nodeModulesFolder, "package3").mkdir();
		new File(nodeModulesFolder, "package4").mkdir();
		Thread.sleep(1000);
		new File(nodeModulesFolder, "package5").mkdir();
		Thread.sleep(100);
		new File(nodeModulesFolder, "package6").mkdir();
		new File(nodeModulesFolder, "package7").mkdir();

		assertChanges(waitForChanges(),
				"CREATE: package0 in " + projectFolder,
				"CREATE: package1 in " + projectFolder,
				"CREATE: package2 in " + projectFolder,
				"CREATE: package3 in " + projectFolder,
				"CREATE: package4 in " + projectFolder,
				"CREATE: package5 in " + projectFolder,
				"CREATE: package6 in " + projectFolder,
				"CREATE: package7 in " + projectFolder);

		new File(nodeModulesFolder, "package0").delete();
		Thread.sleep(1000);
		new File(nodeModulesFolder, "package1").delete();
		new File(nodeModulesFolder, "package2").delete();
		Thread.sleep(300);
		new File(nodeModulesFolder, "package3").delete();
		Thread.sleep(100);
		new File(nodeModulesFolder, "package4").delete();
		new File(nodeModulesFolder, "package5").delete();
		new File(nodeModulesFolder, "package6").delete();
		Thread.sleep(500);
		new File(nodeModulesFolder, "package7").delete();

		assertChanges(waitForChanges(),
				"DELETE: package0 in " + projectFolder,
				"DELETE: package1 in " + projectFolder,
				"DELETE: package2 in " + projectFolder,
				"DELETE: package3 in " + projectFolder,
				"DELETE: package4 in " + projectFolder,
				"DELETE: package5 in " + projectFolder,
				"DELETE: package6 in " + projectFolder,
				"DELETE: package7 in " + projectFolder);
	}

	@Test
	public void testCreateNodeModulesFolderAfterRegistering() throws Exception {

		nodeModulesFolder.delete();

		watcher.addProjectFolder(projectFolder.toPath());
		Thread.sleep(1000);

		nodeModulesFolder.mkdir();
		new File(nodeModulesFolder, "lodash").mkdir();

		// note: next line also asserts that the creation of the node_modules folder did not trigger its own event
		assertChanges(waitForChanges(), "CREATE: lodash in " + projectFolder);

		new File(nodeModulesFolder, "immutable").mkdir();

		assertChanges(waitForChanges(), "CREATE: immutable in " + projectFolder);
	}

	@Test
	public void testDeleteRecreateNodeModulesFolderAfterRegistering() throws Exception {

		watcher.addProjectFolder(projectFolder.toPath());
		Thread.sleep(1000);

		nodeModulesFolder.delete(); // should not trigger an event

		Thread.sleep(1000);

		nodeModulesFolder.mkdir(); // should not trigger an event

		Thread.sleep(1000);

		new File(nodeModulesFolder, "lodash").mkdir();

		assertChanges(waitForChanges(), "CREATE: lodash in " + projectFolder);
	}

	@Test
	public void testDeleteNodeModulesFolder() throws Exception {

		new File(nodeModulesFolder, "package1a").mkdir();
		new File(nodeModulesFolder, "package1b").mkdir();

		watcher.addProjectFolder(projectFolder.toPath());
		Thread.sleep(1000);

		new File(nodeModulesFolder, "package2a").mkdir();
		new File(nodeModulesFolder, "package2b").mkdir();

		assertChanges(waitForChanges(),
				"CREATE: package2a in " + projectFolder,
				"CREATE: package2b in " + projectFolder);

		new File(nodeModulesFolder, "package1b").delete();
		new File(nodeModulesFolder, "package2b").delete();

		assertChanges(waitForChanges(),
				"DELETE: package1b in " + projectFolder,
				"DELETE: package2b in " + projectFolder);

		Runtime.getRuntime().exec("rm -rf " + nodeModulesFolder.getAbsolutePath());

		assertChanges(waitForChanges(),
				"DELETE: package1a in " + projectFolder,
				"DELETE: package2a in " + projectFolder);
	}

	@Test
	public void testRenameNodeModulesFolder() throws Exception {

		new File(nodeModulesFolder, "lodash").mkdir();

		watcher.addProjectFolder(projectFolder.toPath());
		Thread.sleep(1000);

		Path dummy = nodeModulesFolder.toPath().getParent().resolve("dummy");
		Runtime.getRuntime().exec("mv " + nodeModulesFolder.toPath() + " " + dummy);

		assertChanges(waitForChanges(), "DELETE: lodash in " + projectFolder);

		Runtime.getRuntime().exec("mv " + dummy + " " + nodeModulesFolder.toPath());

		assertChanges(waitForChanges(), "CREATE: lodash in " + projectFolder);
	}

	@Test
	public void testRenamePackage() throws Exception {

		new File(nodeModulesFolder, "lodash").mkdir();

		watcher.addProjectFolder(projectFolder.toPath());
		Thread.sleep(1000);

		Runtime.getRuntime().exec("mv "
				+ nodeModulesFolder.toPath().resolve("lodash") + " "
				+ nodeModulesFolder.toPath().resolve("express"));

		assertChanges(waitForChanges(),
				"DELETE: lodash in " + projectFolder,
				"CREATE: express in " + projectFolder);
	}

	@Test
	public void testRenamePackageJson() throws Exception {

		File lodash = new File(nodeModulesFolder, "lodash");
		lodash.mkdir();
		File packageJson = new File(lodash, "package.json");
		packageJson.createNewFile();

		watcher.addProjectFolder(projectFolder.toPath());
		Thread.sleep(1000);

		Path dummy = packageJson.toPath().getParent().resolve("dummy");
		Runtime.getRuntime().exec("mv " + packageJson.toPath() + " " + dummy);

		assertChanges(waitForChanges(), "MODIFY: lodash in " + projectFolder);

		Runtime.getRuntime().exec("mv " + dummy + " " + packageJson.toPath());

		assertChanges(waitForChanges(), "MODIFY: lodash in " + projectFolder);
	}

	@Test
	public void testIgnoredChanges() throws Exception {

		File lodash = new File(nodeModulesFolder, "lodash");
		lodash.mkdir();

		watcher.addProjectFolder(projectFolder.toPath());
		Thread.sleep(1000);

		new File(projectFolder, "someFile.txt").createNewFile();
		new File(projectFolder, "someFolder").mkdir();
		new File(nodeModulesFolder, "someFile.txt").createNewFile();
		new File(lodash, "someFile.txt").createNewFile();
		new File(lodash, "someFolder").mkdir();

		new File(lodash, "package.json").createNewFile(); // dummy event

		// now, the dummy event should be the *only* event triggered:
		assertChanges(waitForChanges(), "MODIFY: lodash in " + projectFolder);

		new File(projectFolder, "someFile.txt").delete();
		new File(projectFolder, "someFolder").delete();
		new File(nodeModulesFolder, "someFile.txt").delete();
		new File(lodash, "someFile.txt").delete();
		new File(lodash, "someFolder").delete();

		new File(lodash, "package.json").delete(); // dummy event

		// again, the dummy event should be the *only* event triggered:
		assertChanges(waitForChanges(), "MODIFY: lodash in " + projectFolder);
	}

	private List<NodeModulesChange> waitForChanges() {
		List<NodeModulesChange> result;
		while ((result = eventQueue.poll()) == null) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		dumpChanges(result);
		return result;
	}

	private void assertChanges(List<NodeModulesChange> actualChanges, String... expectedChanges) {
		List<String> actualChangesAsStrings = actualChanges.stream()
				.map(ch -> ch.toString())
				.collect(Collectors.toList());
		List<String> expectedChangesAsStrings = Arrays.asList(expectedChanges);
		expectedChangesAsStrings.sort(null);
		actualChangesAsStrings.sort(null);
		assertEquals("incorrect changes received", expectedChangesAsStrings, actualChangesAsStrings);
	}

	private void dumpChanges(List<NodeModulesChange> changes) {
		System.out.println("RECEIVED:\n    "
				+ changes.stream().map(ch -> ch.toString()).collect(Collectors.joining("\n    ")));
	}
}
