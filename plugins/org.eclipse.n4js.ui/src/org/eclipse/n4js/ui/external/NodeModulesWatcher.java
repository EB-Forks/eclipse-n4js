package org.eclipse.n4js.ui.external;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.eclipse.n4js.N4JSGlobals;
import org.eclipse.n4js.ui.external.NodeModulesListener.NodeModulesChange;
import org.eclipse.swt.widgets.Display;

/**
 * Utility class for receiving file change notifications related to one or more <code>node_modules</code> locations on
 * disk. Since the library manager supports <code>node_modules</code> locations outside the Eclipse workspace, this
 * class does not make use of any Eclipse workspace APIs.
 * <p>
 * Once a project folder is registered with {@link #addProjectFolder(Path)}, all registered listeners will receive the
 * following events:
 * <ul>
 * <li>a new npm package appears in the project's node_modules folder,
 * <li>an npm package disappears in the project's node_modules folder,
 * <li>an npm package is modified. An npm package is deemed "modified" in this sense if and only if its package.json
 * file is created, deleted, or modified. All other modifications inside an npm package's folder tree are ignored.
 * </ul>
 * Creation or deletion of a node_modules folder itself is not reported as an event, unless it indirectly leads to one
 * of the above events. For example, if a node_modules folder is deleted that contained one or more npm packages, no
 * event will be fired for the deletion of the node_modules folder, but events will be fired for the deletion of the npm
 * packages.
 */
public class NodeModulesWatcher implements Closeable {

	/** Only used for notifying listeners with {@link Display#asyncExec(Runnable)}. */
	private final Display display;

	private final WatchService watcher;
	private final Thread thread;

	private final PathRegistry registry = new PathRegistry();
	private final List<NodeModulesListener> listeners = new CopyOnWriteArrayList<>();

	/**
	 * Creates an instance. For details see {@link NodeModulesWatcher}.
	 *
	 * @param display
	 *            only used for event notifications. Listeners added with {@link #addListener(NodeModulesListener)} will
	 *            be invoked from this display's UI thread. Intended for use within Eclipse to have listeners be invoked
	 *            on Eclipse's main thread.
	 * @throws IOException
	 *             see {@link FileSystem#newWatchService()}.
	 * @throws UnsupportedOperationException
	 *             see {@link FileSystem#newWatchService()}.
	 */
	public NodeModulesWatcher(Display display) throws IOException {
		this.display = display;
		this.watcher = FileSystems.getDefault().newWatchService();
		this.thread = new Thread(this::loop, NodeModulesWatcher.class.getSimpleName() + "'s Thread");

		thread.start();
	}

	/** Closes this watcher. May be invoked from any thread. */
	@Override
	public void close() {
		try {
			watcher.close(); // will make method #loop() return immediately and thus shut down this.thread
		} catch (IOException e) {
			// ignore
		}
	}

	/**
	 * Add a project folder to be watched by this watcher. It's safe to invoke this for paths that are already being
	 * watched. May be invoked from any thread.
	 */
	public void addProjectFolder(Path projectFolder) {
		projectFolder = projectFolder.toAbsolutePath();
		registry.registerProjectFolder(projectFolder);
	}

	/**
	 * Remove a project folder from being watched. It's safe to invoke this for paths that are not being watched
	 * currently. May be invoked from any thread.
	 */
	public void removeProjectFolder(Path projectFolder) {
		projectFolder = projectFolder.toAbsolutePath();
		registry.unregisterProjectFolder(projectFolder);
	}

	/**
	 * Watch all project folders in the given iterable AND stop watching all other project folders (if any). May be
	 * invoked from any thread.
	 */
	public void setProjectFolders(Iterable<Path> projectFolders) {
		Set<Path> projectFoldersAsSet = StreamSupport.stream(projectFolders.spliterator(), false)
				.map(path -> path.toAbsolutePath())
				.collect(Collectors.toSet());
		registry.setRegisteredProjectFolders(projectFoldersAsSet);
	}

	/**
	 * Add a listener. For details regarding reported events see {@link NodeModulesWatcher}. May be invoked from any
	 * thread.
	 */
	public void addListener(NodeModulesListener l) {
		listeners.add(l);
	}

	/** Remove a listener. May be invoked from any thread. */
	public void removeListener(NodeModulesListener l) {
		listeners.remove(l);
	}

	private void notifyListeners(List<NodeModulesChange> changes) {
		if (changes.isEmpty()) {
			return;
		}
		for (NodeModulesListener l : listeners) {
			display.asyncExec(() -> l.onChanges(changes));
		}
	}

	private void loop() {
		while (true) {

			WatchKey key;
			try {
				key = watcher.take();
			} catch (ClosedWatchServiceException e) {
				break;
			} catch (InterruptedException e) {
				close();
				break;
			}

			List<NodeModulesChange> changes = new ArrayList<>();
			processKey(key, changes);

			// instead of immediately notifying listeners and simply looping, at this point,
			// we first want to collect more pending events into the same 'changes' list:
			while ((key = watcher.poll()) != null) {
				processKey(key, changes);
			}

			// now send out this batch of changes and loop
			notifyListeners(changes);
		}
	}

	private void processKey(WatchKey key, List<NodeModulesChange> addChangesHere) {
		List<WatchEvent<?>> sortedEvents = moveDeletionsToFront(key.pollEvents());
		for (WatchEvent<?> event : sortedEvents) {
			processEvent(key, event, addChangesHere);
		}
		key.reset();
	}

	private void processEvent(WatchKey key, WatchEvent<?> event, List<NodeModulesChange> addChangesHere) {
		if (event.kind() == OVERFLOW) {
			return; // TODO consider unregistering and then re-registering everything
		}
		Path contextPath = (Path) event.context();
		if (contextPath == null || contextPath.getNameCount() != 1) {
			return;
		}
		WatchEvent.Kind<?> kind = event.kind();
		Path keyPath = (Path) key.watchable();
		Path changedPath = keyPath.resolve(contextPath);
		processEvent(kind, changedPath, addChangesHere);
	}

	private void processEvent(WatchEvent.Kind<?> kind, Path changedPath, List<NodeModulesChange> addChangesHere) {
		Path parentPath = changedPath.getParent();
		String changedFileName = changedPath.getFileName().toString();

		int depth = registry.getDepth(changedPath);
		if (depth == 1) {
			// change occurred inside the project folder that was added with #addLocation()
			Path projectFolder = parentPath;
			if (N4JSGlobals.NODE_MODULES.equals(changedFileName)) {
				if (kind == ENTRY_CREATE && Files.isDirectory(changedPath)) {
					onNewNodeModulesFolder(projectFolder, addChangesHere);
				} else if (kind == ENTRY_DELETE) {
					onDeleteNodeModulesFolder(projectFolder, addChangesHere);
				}
			}
		} else if (depth == 2) {
			// change occurred directly inside a node_modules folder
			Path projectFolder = parentPath.getParent();
			if (kind == ENTRY_CREATE && Files.isDirectory(changedPath)) {
				onNewPackage(projectFolder, changedFileName, addChangesHere);
			} else if (kind == ENTRY_DELETE) {
				onDeletePackage(projectFolder, changedFileName, addChangesHere);
			}
		} else {
			// change occurred inside a sub-folder of a node_modules folder
			if (N4JSGlobals.PACKAGE_JSON.equals(changedFileName)) {
				// any change of a package.json (i.e. create, modify, delete) is forwarded as a modification of the
				// containing npm package:
				Path projectFolder = parentPath.getParent().getParent();
				String projectName = parentPath.getFileName().toString();
				addChangesHere.add(new NodeModulesChange(
						NodeModulesChange.Kind.MODIFY, projectFolder, projectName));
			}
		}
		// ignore all other changes
	}

	private void onNewNodeModulesFolder(Path projectFolder, List<NodeModulesChange> addChangesHere) {
		Set<String> packageNames = registry.registerNodeModulesFolder(projectFolder);
		// forward events for packages that already existed when the node_modules folder was registered
		for (String packageName : packageNames) {
			addChangesHere.add(new NodeModulesChange(
					NodeModulesChange.Kind.CREATE, projectFolder, packageName));
		}
	}

	private void onDeleteNodeModulesFolder(Path projectFolder, List<NodeModulesChange> addChangesHere) {
		Set<String> packageNames = registry.unregisterNodeModulesFolder(projectFolder);
		// deletion of a node_modules folder triggers deletion events for all known packages
		for (String packageName : packageNames) {
			addChangesHere.add(new NodeModulesChange(
					NodeModulesChange.Kind.DELETE, projectFolder, packageName));
		}
	}

	private void onNewPackage(Path projectFolder, String packageName, List<NodeModulesChange> addChangesHere) {
		registry.registerPackage(projectFolder, packageName);
		// forward event for new package
		addChangesHere.add(new NodeModulesChange(
				NodeModulesChange.Kind.CREATE, projectFolder, packageName));
	}

	private void onDeletePackage(Path projectFolder, String packageName, List<NodeModulesChange> addChangesHere) {
		if (registry.unregisterPackage(projectFolder, packageName)) {
			// forward event for deleted package
			addChangesHere.add(new NodeModulesChange(
					NodeModulesChange.Kind.DELETE, projectFolder, packageName));
		}
	}

	private static List<WatchEvent<?>> moveDeletionsToFront(List<WatchEvent<?>> events) {
		List<WatchEvent<?>> result = new ArrayList<>(events);
		result.sort((e1, e2) -> {
			WatchEvent.Kind<?> k1 = e1.kind();
			WatchEvent.Kind<?> k2 = e2.kind();
			if (k1 == ENTRY_DELETE && k2 != ENTRY_DELETE) {
				return -1;
			} else if (k1 != ENTRY_DELETE && k2 == ENTRY_DELETE) {
				return 1;
			}
			return 0;
		});
		return result;
	}

	private final class PathRegistry {

		private final Map<Path, Set<String>> packagesPerProject = new HashMap<>();
		private final Map<Path, WatchKey> pathToWatchKey = new HashMap<>();

		/**
		 * Returns
		 * <ul>
		 * <li>0 if the given path points to a project folder registered with {@link #registerProjectFolder(Path)},
		 * <li>1 if it points to a sub-folder of such a project folder,
		 * <li>2 if it points to a sub-sub-folder,
		 * <li>-1 in all other cases.
		 * </ul>
		 * It is assumed that the given path is absolute.
		 */
		public synchronized int getDepth(Path path) {
			int depth = 0;
			while (path != null && depth <= 2) {
				if (packagesPerProject.containsKey(path)) {
					return depth;
				}
				path = path.getParent();
				depth++;
			}
			return -1;
		}

		public synchronized void setRegisteredProjectFolders(Set<Path> projectFolders) {
			Set<Path> toBeRegistered = new HashSet<>(projectFolders);
			toBeRegistered.removeAll(packagesPerProject.keySet());
			Set<Path> toBeUnregistered = new HashSet<>(packagesPerProject.keySet());
			toBeUnregistered.removeAll(projectFolders);
			for (Path projectFolder : toBeUnregistered) {
				unregisterProjectFolder(projectFolder);
			}
			for (Path projectFolder : toBeRegistered) {
				registerProjectFolder(projectFolder);
			}
		}

		/**
		 * Returns names of packages already present in the given project's node_modules folder at the time of
		 * registering the project.
		 */
		public synchronized Set<String> registerProjectFolder(Path projectFolder) {
			if (packagesPerProject.putIfAbsent(projectFolder, new HashSet<>()) == null) {
				registerPath(projectFolder);
				// register node_modules folder and its content (if present)
				Path nodeModulesFolder = projectFolder.resolve(N4JSGlobals.NODE_MODULES);
				if (Files.isDirectory(nodeModulesFolder)) {
					return registerNodeModulesFolder(projectFolder);
				}
			}
			return Collections.emptySet();
		}

		/**
		 * Returns names of packages that were present in the given project's node_modules folder at time of
		 * unregistering the project.
		 */
		public synchronized Set<String> unregisterProjectFolder(Path projectFolder) {
			unregisterPath(projectFolder);
			return unregisterNodeModulesFolder(projectFolder);
		}

		/**
		 * Returns names of packages already present in the given node_modules folder at the time of registering it.
		 */
		public synchronized Set<String> registerNodeModulesFolder(Path projectFolder) {
			Path nodeModulesFolder = projectFolder.resolve(N4JSGlobals.NODE_MODULES);
			Set<String> packageNames;
			try {
				packageNames = Files.list(nodeModulesFolder)
						.filter(Files::isDirectory)
						.map(path -> path.getFileName().toString())
						.collect(Collectors.toSet());
			} catch (IOException e) {
				packageNames = Collections.emptySet();
			}
			registerPath(nodeModulesFolder);
			for (String packageName : packageNames) {
				registerPackage(projectFolder, packageName);
			}
			return Collections.unmodifiableSet(packageNames);
		}

		/**
		 * Returns names of packages that were present in the given node_modules folder at time of unregistering it.
		 */
		public synchronized Set<String> unregisterNodeModulesFolder(Path projectFolder) {
			unregisterPath(projectFolder.resolve(N4JSGlobals.NODE_MODULES));
			Set<String> packageNames = packagesPerProject.get(projectFolder);
			if (packageNames != null) {
				Set<String> packageNamesCpy = new HashSet<>(packageNames);
				for (String packageName : packageNamesCpy) {
					unregisterPackage(projectFolder, packageName);
				}
				return Collections.unmodifiableSet(packageNamesCpy);
			}
			return Collections.emptySet();
		}

		public synchronized boolean registerPackage(Path projectFolder, String packageName) {
			Set<String> packageNames = packagesPerProject.get(projectFolder);
			if (packageNames != null) {
				boolean wasUnknownPackage = packageNames.add(packageName);
				Path packageFolder = projectFolder.resolve(N4JSGlobals.NODE_MODULES).resolve(packageName);
				registerPath(packageFolder);
				return wasUnknownPackage;
			}
			return false;
		}

		public synchronized boolean unregisterPackage(Path projectFolder, String packageName) {
			Set<String> packageNames = packagesPerProject.get(projectFolder);
			if (packageNames != null) {
				boolean wasKnownPackage = packageNames.remove(packageName);
				Path packageFolder = projectFolder.resolve(N4JSGlobals.NODE_MODULES).resolve(packageName);
				unregisterPath(packageFolder);
				return wasKnownPackage;
			}
			return false;
		}

		private synchronized void registerPath(Path path) {
			try {
				WatchKey key = path.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
				pathToWatchKey.put(path, key);
			} catch (IOException e) {
				// ignore
			}
		}

		private synchronized void unregisterPath(Path path) {
			WatchKey key = pathToWatchKey.remove(path);
			if (key != null) {
				key.cancel();
			}
		}
	}
}
