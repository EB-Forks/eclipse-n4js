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
package org.eclipse.n4js.tester.extension;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.log4j.Logger;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.n4js.runner.extension.RuntimeEnvironment;
import org.eclipse.n4js.tester.ITester;

import com.google.inject.Inject;
import com.google.inject.Injector;

/**
 * Implementation of {@link ITesterDescriptor} that represents a tester registered via the Eclipse extension point
 * {@link TesterRegistry#TESTERS_EXTENSION_POINT_ID TESTERS_EXTENSION_POINT_ID}. In headless mode, use class
 * {@link TesterDescriptorImpl} instead.
 */
public class EclipseTesterDescriptor implements ITesterDescriptor { // TODO move to UI bundle??

	private final static Logger log = Logger.getLogger(EclipseTesterDescriptor.class);

	// attributes of the extension point (has to be kept in sync with the schema definition!)
	private static final String ATTR_ID = "id";
	private static final String ATTR_NAME = "name";
	private static final String ATTR_ENVIRONMENT = "environment";
	private static final String ATTR_CLASS = "class";

	private final IConfigurationElement configElem;
	private final String id;
	private final String name;
	private final RuntimeEnvironment environment;

	private ITester tester;

	@Inject
	private Injector injector;

	/**
	 * Create a new tester descriptor from the given configuration element. Will perform extensive validity checks and
	 * throw {@link IllegalArgumentException}s in case of errors.
	 */
	public EclipseTesterDescriptor(IConfigurationElement configElem) {
		this.configElem = configElem;
		if (this.configElem == null) {
			throw new IllegalArgumentException("configElem may not be null");
		}

		this.id = configElem.getAttribute(ATTR_ID);
		if (this.id == null || this.id.trim().isEmpty()) {
			throw new IllegalArgumentException(
					"extension attribute '" + ATTR_ID + "' may not be null or empty");
		}

		this.name = configElem.getAttribute(ATTR_NAME);
		if (this.name == null || this.name.trim().isEmpty()) {
			throw new IllegalArgumentException(
					"extension attribute '" + ATTR_NAME + "' may not be null or empty");
		}

		final String environmentRaw = configElem.getAttribute(ATTR_ENVIRONMENT);
		if (environmentRaw == null || environmentRaw.trim().isEmpty()) {
			throw new IllegalArgumentException(
					"extension attribute '" + ATTR_ENVIRONMENT + "' may not be null or empty");
		}
		this.environment = RuntimeEnvironment.fromProjectName(environmentRaw);
		if (this.environment == null) {
			throw new IllegalArgumentException("unknown runtime environment: "
					+ environmentRaw
					+ " (valid values are: "
					+ Stream.of(RuntimeEnvironment.values()).map(re -> re.getProjectName())
							.collect(Collectors.joining(", "))
					+ ")");
		}
	}

	/** Returns the configuration element from which this tester descriptor was created. */
	public IConfigurationElement getConfigurationElement() {
		return configElem;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public RuntimeEnvironment getEnvironment() {
		return environment;
	}

	@Override
	public ITester getTester() {
		if (tester == null) {
			try {
				tester = (ITester) getConfigurationElement().createExecutableExtension(ATTR_CLASS);
				injector.injectMembers(tester);
			} catch (CoreException ex) {
				log.error("error while creating executable extension from attribute '" + ATTR_CLASS + "'", ex);
			}
		}
		return tester;
	}
}
