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
package org.eclipse.n4js.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.util.URI;
import org.eclipse.n4js.N4JSGlobals;
import org.eclipse.n4js.preferences.ExternalLibraryPreferenceStore;

import com.google.common.base.Optional;
import com.google.inject.Inject;

/**
 * Helper for finding all node_modules folders for a given set of N4JS projects, including support for yarn workspaces.
 *
 * For more details on yarn workspaces, see: https://yarnpkg.com/lang/en/docs/workspaces/
 */
public class NodeModulesDiscoveryHelper {

	@Inject
	private ProjectDescriptionLoader projectDescriptionLoader;

	/**
	 * For the given N4JS projects, this method will search and return all <code>node_modules</code> folders. The
	 * returned list is ordered by priority, such that elements with a higher index have higher priority than those with
	 * a lower index. This is in line with shadowing between <code>node_modules</code> locations in the library manager
	 * (as configured in {@link ExternalLibraryPreferenceStore}), where the contents of locations with a higher index
	 * shadow those of locations with a lower index.
	 *
	 * NOTE: this method is accessing the file system, so it should be deemed expensive!
	 *
	 * @param n4jsProjects
	 *            paths to the root folder of N4JS projects. If a path points to a folder not containing a valid N4JS
	 *            project, the behavior is undefined (will not be checked by this method).
	 */
	public List<Path> findNodeModulesFolders(Collection<Path> n4jsProjects) {
		List<Path> result = new ArrayList<>();

		final Map<File, List<String>> workspacesCache = new HashMap<>();
		final Set<File> matchingWorkspaceRoots = new LinkedHashSet<>();

		for (Path projectFolderPath : n4jsProjects) {
			final File projectFolder = projectFolderPath.toFile();
			if (projectFolder.isDirectory()) {
				final Path nodeModulesPath = projectFolderPath.resolve(N4JSGlobals.NODE_MODULES);
				result.add(nodeModulesPath);

				final Optional<File> workspaceRoot = getYarnWorkspaceRoot(projectFolder, workspacesCache);
				if (workspaceRoot.isPresent()) {
					matchingWorkspaceRoots.add(workspaceRoot.get());
				}
			}
		}

		// ORDER IMPORTANT: global node_modules folders from yarn workspaces should be added at the end!
		// Rationale: in case of multiple versions of the same npm package, only one can be "active" in the library
		// manager (with the current implementation of the library manager) that will be seen by all projects.
		// Having all projects see the version installed in the global node_modules folder is less harmful than
		// having all projects see the version installed in a local node_modules folder. Thus the global
		// node_modules folder should have priority and should therefore come last in list 'result'.
		// (NOTE: both "solutions" are actually broken, but this is a limitation of the library manager.)
		for (File workspaceRoot : matchingWorkspaceRoots) {
			final Path globalNodeModulesFolderPath = workspaceRoot.toPath().resolve(N4JSGlobals.NODE_MODULES);
			result.add(globalNodeModulesFolderPath);
		}

		return result;
	}

	/**
	 * If the given N4JS project, denoted by its root folder, is a member in a yarn workspace, this method will return
	 * the "workspace root" of the containing yarn workspace. Otherwise, {@link Optional#absent() absent} is returned.
	 *
	 * NOTE: this method is accessing the file system, so it should be deemed expensive!
	 *
	 * @param n4jsProjectFolder
	 *            root folder of an N4JS project that may or may not be a member of a yarn workspace.
	 * @param workspacesCache
	 *            a cache used to avoid loading/parsing the same package.json multiple times.
	 */
	private Optional<File> getYarnWorkspaceRoot(File n4jsProjectFolder, Map<File, List<String>> workspacesCache) {
		n4jsProjectFolder = makeCanonical(n4jsProjectFolder);
		File candidate = n4jsProjectFolder.getParentFile();
		while (candidate != null) {
			// obtain value of property "workspaces" in package.json located in folder 'candidate'
			final List<String> workspaces;
			final List<String> workspacesFromCache = workspacesCache.get(candidate);
			if (workspacesFromCache != null) {
				// use the value from the cache
				workspaces = workspacesFromCache;
			} else {
				// load value from package.json
				final File packageJson = new File(candidate, N4JSGlobals.PACKAGE_JSON);
				if (packageJson.isFile()) {
					URI packageJsonURI = URI.createFileURI(packageJson.getPath());
					workspaces = projectDescriptionLoader
							.loadWorkspacesFromProjectDescriptionAtLocation(packageJsonURI);
					if (workspaces != null) {
						workspacesCache.put(candidate, workspaces);
					}
				} else {
					workspaces = null;
				}
			}
			// check if one of the values in property "workspaces" points to 'n4jsProjectFolder'
			if (workspaces != null) {
				for (String relativePath : workspaces) {
					if (isPointingTo(candidate, relativePath, n4jsProjectFolder)) {
						return Optional.of(candidate);
					}
				}
			}
			// if not, continue with parent folder as new candidate
			candidate = candidate.getParentFile();
		}
		return Optional.absent();
	}

	/** This method assumes both {@code base} and {@code target} to be {@link File#getCanonicalFile() canonical}. */
	private static boolean isPointingTo(File base, String relativePath, File target) {
		boolean wildcard = relativePath.endsWith(File.separator + "*");
		if (wildcard) {
			relativePath = relativePath.substring(0, relativePath.length() - 2);
		}
		final File destination = makeCanonical(new File(base, relativePath));
		return wildcard
				? destination.equals(target.getParentFile())
				: destination.equals(target);
	}

	private static File makeCanonical(File file) {
		try {
			return file.getCanonicalFile();
		} catch (IOException e) {
			return file;
		}
	}
}
