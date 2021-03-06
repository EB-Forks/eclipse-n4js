/**
 * Copyright (c) 2016 NumberFour AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   NumberFour AG - Initial API and implementation
 */
package org.eclipse.n4js.runner.nodejs;

import static com.google.common.base.CharMatcher.breakingWhitespace;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.n4js.N4JSGlobals;
import org.eclipse.n4js.binaries.nodejs.NodeJsBinary;
import org.eclipse.n4js.runner.SystemLoaderInfo;
import org.eclipse.n4js.utils.ProjectDescriptionUtils;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * Builds command line command for executing the node.js engine. Clients must call command on their own. Clients must
 * configure runtime environment on their own.
 */
public class NodeEngineCommandBuilder {

	/** Command line option to signal COMMON_JS */
	private static final String CJS_COMMAND = "cjs";

	@Inject
	private Provider<NodeJsBinary> nodeJsBinary;

	/**
	 * Creates commands for calling Node.js on command line. Data wrapped in passed parameter is used to configure node
	 * itself, and to generate file that will be executed by Node.
	 */
	public String[] createCmds(NodeRunOptions nodeRunOptions, Path workDir) throws IOException {

		final ArrayList<String> commands = new ArrayList<>();

		commands.add(nodeJsBinary.get().getBinaryAbsolutePath());

		// allow user flags
		final String nodeOptions = nodeRunOptions.getEngineOptions();
		if (nodeOptions != null) {
			for (String nodeOption : Splitter.on(breakingWhitespace()).omitEmptyStrings().split(nodeOptions)) {
				commands.add(nodeOption);
			}
		}

		final String bootScript = generateBootCode(nodeRunOptions, workDir);
		commands.add(bootScript);

		if (nodeRunOptions.getSystemLoader() == SystemLoaderInfo.COMMON_JS) {
			commands.add(CJS_COMMAND);
		}

		return commands.toArray(new String[] {});
	}

	/**
	 * Generates JS code to the this that will be configured with data for execution.
	 *
	 * This method uses provided working dir to transform it into nodejs-project-like structure, to allow proper
	 * configuration of the executions. In particular:
	 *
	 * <pre>
	 * <ol>
	 * <li> add node module to the folder</li>
	 * <li> generate startup script</li>
	 * <li> return path to the startup script</li>
	 * </ol>
	 * </pre>
	 *
	 * Note that some configuration will be performed by the startup script when it is executed.
	 *
	 * @param nodeRunOptions
	 *            options used to generate boot code
	 * @return path to the script that has to be called by node
	 * @throws IOException
	 *             for IO operations
	 */
	private String generateBootCode(NodeRunOptions nodeRunOptions, Path workDir) throws IOException {
		// 1 generate fake node project
		Path projectRootPath = workDir;
		// 2 create 'node_modules' to the #1
		final File node_modules = new File(projectRootPath.toFile(), N4JSGlobals.NODE_MODULES);
		node_modules.mkdirs();
		addNodeModulesDeleteHook(node_modules);
		// 3 generate elf script in #1
		final File elf = Files.createTempFile(projectRootPath, "N4JSNodeELF",
				"." + N4JSGlobals.JS_FILE_EXTENSION).toFile();
		elf.deleteOnExit();
		Map<Path, String> path2name = nodeRunOptions.getCoreProjectPaths();
		// early throw, to prevent debugging runtime processes
		String execModule = nodeRunOptions.getExecModule();
		if (Strings.isNullOrEmpty(execModule))
			throw new RuntimeException("Execution module not provided.");
		List<String> initModules = nodeRunOptions.getInitModules();
		// TODO-1932 redesign of the runners and testers pending
		// some runtime environments (the N4JS projects) bootstrap execution
		// is against Runners/Testers design. We want those to fix those runtime
		// projects, but also there was redesign of the whole concept pending.
		// For the time being we just patch data generated by the IDE to work
		// with the user projects.
		initModules = Arrays.asList();
		String eflCode = getELFCode(nodeRunOptions, node_modules, path2name, execModule, initModules);
		writeContentToFile(eflCode, elf);

		return elf.getAbsolutePath();
	}

	/**
	 * Creates the ELF code for the given parameters.
	 *
	 * @param nodeRunOptions
	 *            The run options of this execution
	 * @param node_modules
	 *            The 'node_modules' folder to use
	 * @param path2name
	 *            Mapping between runtime library projects and their path
	 * @param execModule
	 *            The module to execute
	 * @param initModules
	 *            The init modules to be loaded before the actual module execution
	 */
	protected String getELFCode(NodeRunOptions nodeRunOptions, final File node_modules,
			Map<Path, String> path2name, String execModule, List<String> initModules) throws IOException {
		Set<String> scopeNames = path2name.values().stream()
				.filter(name -> ProjectDescriptionUtils.isProjectNameWithScope(name))
				.map(name -> name.substring(0, name.indexOf('/')))
				.collect(Collectors.toSet());
		return NodeBootScriptTemplate.getRunScriptCore(
				node_modules.getCanonicalPath(),
				nodeRunOptions.getExecutionData(),
				initModules,
				execModule,
				scopeNames,
				path2name);
	}

	/** Writes given content to a given file. */
	private static void writeContentToFile(String content, File file) throws IOException {
		try (final BufferedWriter writer = new BufferedWriter(new FileWriter(file));) {
			writer.write(content);
		}
	}

	/**
	 * Since {@code node_modules} are linked at runtime, the links don't exist yet. We add shutdown hook to schedule
	 * them for deletion, after JS was executed.
	 *
	 * Since we assume that contents are symlinks to other locations, we are not walking deep, just schedule to delete
	 * immediate children.
	 */
	private static void addNodeModulesDeleteHook(File file) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				file.deleteOnExit();
				if (file.isDirectory()) {
					File[] childFildes = file.listFiles();
					for (int i = 0; i < childFildes.length; i++) {
						childFildes[i].deleteOnExit();
					}
				}
			}
		});
	}

}
