/**
 * Copyright (c) 2017 NumberFour AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   NumberFour AG - Initial API and implementation
 */
package org.eclipse.n4js.flowgraphs.analysers;

import java.util.List;

import org.eclipse.n4js.flowgraphs.analysis.BranchWalker;
import org.eclipse.n4js.flowgraphs.analysis.GraphExplorer;
import org.eclipse.n4js.flowgraphs.analysis.GraphVisitor;
import org.eclipse.n4js.flowgraphs.analysis.TraverseDirection;
import org.eclipse.n4js.n4JS.ControlFlowElement;

/**
 * Does nothing. Can be used to trigger the control flow graph to be walked through. Walks in {@literal Mode#Forward}
 * and {@literal Mode#Backward} direction through the code.
 */
public class DummyBackwardVisitor extends GraphVisitor {

	/**
	 * Constructor.
	 */
	public DummyBackwardVisitor() {
		super(TraverseDirection.Backward);
	}

	@Override
	protected void initializeContainer(ControlFlowElement curContainer) {
		super.requestActivation(new DummyExplorer());
	}

	static class DummyExplorer extends GraphExplorer {
		@Override
		protected DummyWalker firstBranchWalker() {
			return new DummyWalker();
		}

		@Override
		protected BranchWalker joinBranches(List<BranchWalker> branchWalkers) {
			return new DummyWalker();
		}
	}

	static class DummyWalker extends BranchWalker {
		@Override
		protected DummyWalker forkPath() {
			return new DummyWalker();
		}
	}

}
