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
package org.eclipse.n4js.ui.external;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * A listener interested in changes related to the node_modules folder of a Javascript project, as sent by
 * {@link NodeModulesWatcher}.
 */
public interface NodeModulesListener {

	/**
	 * Class for representing a change inside a Javascript project related to the npm packages installed in its
	 * node_modules folder.
	 */
	public static final class NodeModulesChange {

		/**
		 * Kind of change that may occur in the node_modules folder of a Javascript project.
		 */
		public enum Kind {
			/** An npm package was created. */
			CREATE,
			/** An npm package was modified, i.e. its package.json file was created, modified, or deleted. */
			MODIFY,
			/** An npm package was deleted. */
			DELETE
		}

		/**
		 * Kind of change that happened.
		 */
		public final Kind kind;
		/**
		 * Root folder of the project in which the change occurred, i.e. a folder that was registered via
		 * {@link NodeModulesWatcher#addProjectFolder(Path) #addProjectFolder(Path)}
		 */
		public final Path projectFolder;
		/**
		 * Name of the npm package that was created, modified, or deleted (depending on {@link #kind}).
		 */
		public final String packageName;

		/**
		 * Creates an instance.
		 */
		public NodeModulesChange(Kind kind, Path projectFolder, String packageName) {
			Objects.requireNonNull(kind);
			Objects.requireNonNull(projectFolder);
			Objects.requireNonNull(packageName);
			this.kind = kind;
			this.projectFolder = projectFolder;
			this.packageName = packageName;
		}

		@Override
		public String toString() {
			return kind.toString() + ": " + packageName + " in " + projectFolder;
		}
	}

	/**
	 * For details regarding reported events see {@link NodeModulesWatcher}.
	 */
	public void onChanges(List<NodeModulesChange> changes);
}
