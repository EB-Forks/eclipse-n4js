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
package org.eclipse.n4js.tests.resource

import com.google.inject.Inject
import com.google.inject.Provider
import org.eclipse.emf.common.util.URI
import org.eclipse.jface.text.DocumentEvent
import org.eclipse.n4js.N4JSInjectorProvider
import org.eclipse.n4js.resource.N4JSResource
import org.eclipse.xtext.resource.DerivedStateAwareResource
import org.eclipse.xtext.resource.IDerivedStateComputer
import org.eclipse.xtext.resource.OutdatedStateManager
import org.eclipse.xtext.resource.XtextResourceSet
import org.eclipse.xtext.service.OperationCanceledManager
import org.eclipse.xtext.testing.InjectWith
import org.eclipse.xtext.testing.XtextRunner
import org.eclipse.xtext.ui.editor.model.DocumentTokenSource
import org.eclipse.xtext.ui.editor.model.XtextDocument
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(XtextRunner)
@InjectWith(N4JSInjectorProvider)
class N4JSResourcePluginUITest {

	@Inject Provider<XtextResourceSet> resourceSetProvider

	@Inject OutdatedStateManager outdatedStateManager

	@Inject OperationCanceledManager cancelManager

	// moved here from N4JSResourceTest because it must be run as a plugin test as of Eclipse 2018-12:
	@Test
	def void testDisposeDocumentDoesntTriggerResolution() {
		val someResource = resourceSetProvider.get.getResource(URI.createURI("src/org/eclipse/n4js/tests/resource/Supplier.n4js"), true) as N4JSResource
		someResource.derivedStateComputer = new IDerivedStateComputer() {

			override discardDerivedState(DerivedStateAwareResource resource) {
				Assert.fail('Unexpected access to the derived state computer')
			}

			override installDerivedState(DerivedStateAwareResource resource, boolean preLinkingPhase) {
				Assert.fail('Unexpected access to the derived state computer')
			}

		}
		val doc = new XtextDocument(new DocumentTokenSource() {
			override protected computeDamageRegion(DocumentEvent e) {
				return null
			}
		}, null, outdatedStateManager, cancelManager)
		doc.input = someResource
		Assert.assertFalse(someResource.script.eIsProxy)
		doc.disposeInput // no exception
	}
}
