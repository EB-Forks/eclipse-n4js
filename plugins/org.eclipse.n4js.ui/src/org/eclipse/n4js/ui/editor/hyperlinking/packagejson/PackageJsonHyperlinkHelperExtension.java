/**
 * Copyright (c) 2018 NumberFour AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   NumberFour AG - Initial API and implementation
 */
package org.eclipse.n4js.ui.editor.hyperlinking.packagejson;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.n4js.N4JSGlobals;
import org.eclipse.n4js.json.JSON.JSONPackage;
import org.eclipse.n4js.json.JSON.JSONStringLiteral;
import org.eclipse.n4js.json.JSON.NameValuePair;
import org.eclipse.n4js.json.ui.editor.hyperlinking.IJSONHyperlinkHelperExtension;
import org.eclipse.n4js.packagejson.PackageJsonProperties;
import org.eclipse.n4js.projectDescription.ProjectDescription;
import org.eclipse.n4js.projectModel.IN4JSProject;
import org.eclipse.n4js.resource.XpectAwareFileExtensionCalculator;
import org.eclipse.n4js.ui.external.EclipseExternalLibraryWorkspace;
import org.eclipse.n4js.ui.internal.N4JSEclipseModel;
import org.eclipse.n4js.ui.internal.N4JSEclipseProject;
import org.eclipse.n4js.ui.projectModel.IN4JSEclipseSourceContainer;
import org.eclipse.n4js.utils.ProjectDescriptionUtils;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.EObjectAtOffsetHelper;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.ui.editor.hyperlinking.XtextHyperlink;
import org.eclipse.xtext.util.Pair;
import org.eclipse.xtext.util.Tuples;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * Creates hyper links for elements in package json files
 */
public class PackageJsonHyperlinkHelperExtension implements IJSONHyperlinkHelperExtension {

	@Inject
	private N4JSEclipseModel model;

	@Inject
	private EclipseExternalLibraryWorkspace extWS;

	@Inject
	private Provider<XtextHyperlink> hyperlinkProvider;

	@Inject
	private EObjectAtOffsetHelper eObjectAtOffsetHelper;

	@Inject
	private XpectAwareFileExtensionCalculator fileExtensionCalculator;

	@Override
	public boolean isResponsible(XtextResource resource) {
		// this extension only applies to package.json files
		return fileExtensionCalculator.getFilenameWithoutXpectExtension(resource.getURI())
				.equals(IN4JSProject.PACKAGE_JSON);
	}

	@Override
	public IHyperlink[] getHyperlinks(XtextResource resource, int offset) {
		EObject eObject = eObjectAtOffsetHelper.resolveElementAt(resource, offset);
		Pair<URI, Region> linkedProjectWithRegion = getUriRegionPair(eObject);

		if (linkedProjectWithRegion != null) {
			URI uri = linkedProjectWithRegion.getFirst();
			Region region = linkedProjectWithRegion.getSecond();
			N4JSEclipseProject uriProject = model.findProjectWith(uri);

			String lnkName = uriProject == null ? ProjectDescriptionUtils.deriveN4JSProjectNameFromURI(uri)
					: uriProject.getProjectName();

			XtextHyperlink hyperlink = hyperlinkProvider.get();
			hyperlink.setHyperlinkRegion(region);
			hyperlink.setURI(uri);
			hyperlink.setHyperlinkText(lnkName);

			return new IHyperlink[] { hyperlink };
		}

		return null;
	}

	private Pair<URI, Region> getUriRegionPair(EObject eObject) {
		PackageJsonProperties nearestKnownPJP = findNearestKnownPJP(eObject);
		if (nearestKnownPJP == null) {
			return null;
		}

		switch (nearestKnownPJP) {

		case MAIN:
			if (eObject instanceof NameValuePair) {
				eObject = ((NameValuePair) eObject).getValue();
			}
			if (eObject instanceof JSONStringLiteral) {
				return hyperlinkToMain((JSONStringLiteral) eObject);
			}
			break;

		case MAIN_MODULE:
			if (eObject instanceof NameValuePair) {
				eObject = ((NameValuePair) eObject).getValue();
			}
			if (eObject instanceof JSONStringLiteral) {
				return hyperlinkToMainModule((JSONStringLiteral) eObject);
			}
			break;

		case REQUIRED_RUNTIME_LIBRARIES:
			if (eObject instanceof NameValuePair) {
				eObject = ((NameValuePair) eObject).getValue();
			}
			if (eObject instanceof JSONStringLiteral) {
				return hyperlinkToRequiredRTLibs((JSONStringLiteral) eObject);
			}
			break;

		case DEFINES_PACKAGE:
			EObject parent1 = (eObject == null) ? null : eObject.eContainer();
			if (parent1 instanceof NameValuePair) {
				NameValuePair nvpDependency = (NameValuePair) parent1;
				return hyperlinkToDependencySection(nvpDependency);
			}
			break;

		case DEPENDENCIES:
		case DEV_DEPENDENCIES:
			if (eObject instanceof NameValuePair) {
				NameValuePair nvpDependency = (NameValuePair) eObject;
				return hyperlinkToProjectProperty(nvpDependency);
			}
			break;

		default:
			break;
		}

		return null;
	}

	private Pair<URI, Region> hyperlinkToMain(JSONStringLiteral mainModuleJsonLiteral) {
		String mainPath = mainModuleJsonLiteral.getValue();
		if (!Strings.isNullOrEmpty(mainPath)) {
			URI packageJsonLoc = mainModuleJsonLiteral.eResource().getURI();
			N4JSEclipseProject project = model.findProjectWith(packageJsonLoc);
			INode node = NodeModelUtils.getNode(mainModuleJsonLiteral);

			if (project != null && node != null) {
				Region region = new Region(node.getOffset() + 1, node.getLength() - 2);
				Path mainResolvedPath = project.getLocationPath().resolve(mainPath);
				if (mainResolvedPath.toFile().exists()) {
					URI mainResolvedUri = URI.createFileURI(mainResolvedPath.toString());
					return Tuples.pair(mainResolvedUri, region);
				}
			}
		}

		return null;
	}

	private Pair<URI, Region> hyperlinkToMainModule(JSONStringLiteral mainModuleJsonLiteral) {
		String mainModule = mainModuleJsonLiteral.getValue();
		if (!Strings.isNullOrEmpty(mainModule)) {
			URI packageJsonLoc = mainModuleJsonLiteral.eResource().getURI();
			N4JSEclipseProject project = model.findProjectWith(packageJsonLoc);
			INode node = NodeModelUtils.getNode(mainModuleJsonLiteral);

			if (project != null && node != null) {
				Region region = new Region(node.getOffset() + 1, node.getLength() - 2);

				for (IN4JSEclipseSourceContainer sc : project.getSourceContainers()) {
					QualifiedName qualifiedName = QualifiedName.create(mainModule);
					URI mainModuleURI = sc.findArtifact(qualifiedName, Optional.of(N4JSGlobals.N4JS_FILE_EXTENSION));
					if (mainModuleURI == null) {
						mainModuleURI = sc.findArtifact(qualifiedName, Optional.of(N4JSGlobals.N4JSX_FILE_EXTENSION));
					}
					if (mainModuleURI != null) {
						return Tuples.pair(mainModuleURI, region);
					}
				}
			}
		}

		return null;
	}

	private Pair<URI, Region> hyperlinkToRequiredRTLibs(JSONStringLiteral mainModuleJsonLiteral) {
		String projectName = mainModuleJsonLiteral.getValue();
		if (!Strings.isNullOrEmpty(projectName)) {
			URI pdu = getProjectDescriptionLocationForName(projectName);
			INode node = NodeModelUtils.getNode(mainModuleJsonLiteral);

			if (pdu != null && node != null) {
				Region region = new Region(node.getOffset() + 1, node.getLength() - 2);

				return Tuples.pair(pdu, region);
			}
		}

		return null;
	}

	private Pair<URI, Region> hyperlinkToProjectProperty(NameValuePair nvpDependency) {
		String projectName = nvpDependency.getName();
		URI pdu = getProjectDescriptionLocationForName(projectName);
		if (pdu != null) {
			List<INode> node = NodeModelUtils.findNodesForFeature(nvpDependency,
					JSONPackage.Literals.NAME_VALUE_PAIR__NAME);

			if (!node.isEmpty()) {
				INode nameNode = node.get(0);
				Region region = new Region(nameNode.getOffset() + 1, nameNode.getLength() - 2);

				return Tuples.pair(pdu, region);
			}
		}

		return null;
	}

	private Pair<URI, Region> hyperlinkToDependencySection(NameValuePair projectNameInValue) {
		JSONStringLiteral jsonValue = (JSONStringLiteral) projectNameInValue.getValue();
		String projectName = jsonValue.getValue();
		URI pdu = getProjectDescriptionLocationForName(projectName);

		if (pdu != null) {
			INode valueNode = NodeModelUtils.getNode(jsonValue);
			Region region = new Region(valueNode.getOffset() + 1, valueNode.getLength() - 2);

			return Tuples.pair(pdu, region);
		}
		return null;
	}

	private URI getProjectDescriptionLocationForName(String projectName) {
		IN4JSProject project = model.findAllProjectMappings().get(projectName);
		URI rootLocation = null;
		if (project == null) {
			for (Pair<URI, ProjectDescription> pair : extWS.getProjectsIncludingUnnecessary()) {
				String name = pair.getSecond().getProjectName();
				if (Objects.equal(projectName, name)) {
					rootLocation = pair.getFirst();
				}
			}
		} else {
			rootLocation = project.getLocation();
		}

		if (rootLocation != null) {
			URI pckjsonUri = rootLocation.appendSegment(IN4JSProject.PACKAGE_JSON);
			return pckjsonUri;
		}
		return null;
	}

	private PackageJsonProperties findNearestKnownPJP(EObject eObject) {
		EObject tmpObj = eObject;
		while (tmpObj != null) {
			if (tmpObj instanceof NameValuePair) {
				String name = ((NameValuePair) tmpObj).getName();
				PackageJsonProperties pjp = PackageJsonProperties.valueOfNameOrNull(name);
				if (pjp != null) {
					return pjp;
				}
			}

			tmpObj = tmpObj.eContainer();
		}
		return null;
	}

}
