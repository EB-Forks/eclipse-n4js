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

	public static final class NodeModulesChange {

		public enum Kind {
			CREATE, MODIFY, DELETE
		}

		public final Kind kind;
		public final Path nodeModulesFolder;
		public final String packageName;

		public NodeModulesChange(Kind kind, Path nodeModulesFolder, String packageName) {
			Objects.requireNonNull(kind);
			Objects.requireNonNull(nodeModulesFolder);
			Objects.requireNonNull(packageName);
			this.kind = kind;
			this.nodeModulesFolder = nodeModulesFolder;
			this.packageName = packageName;
		}

		@Override
		public String toString() {
			return kind.toString() + ": " + packageName + " in " + nodeModulesFolder;
		}
	}

	/**
	 * For details regarding reported events see {@link NodeModulesWatcher}.
	 */
	public void onChanges(List<NodeModulesChange> changes);
}
