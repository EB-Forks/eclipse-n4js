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
package org.eclipse.n4js.json.utils;

import static org.eclipse.n4js.json.JSONGlobals.JSON_FILE_EXTENSION;

import java.io.IOException;
import java.io.StringWriter;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.n4js.json.JSON.JSONDocument;
import org.eclipse.n4js.json.JSON.JSONValue;
import org.eclipse.n4js.json.model.utils.JSONModelUtils;
import org.eclipse.n4js.utils.languages.N4LanguageUtils;
import org.eclipse.n4js.utils.languages.N4LanguageUtils.ParseResult;
import org.eclipse.xtext.resource.SaveOptions;
import org.eclipse.xtext.serializer.ISerializer;

/**
 * Utility methods for dealing with JSON, e.g. serialization and deserialization.
 */
public class JSONUtils {

	/**
	 * Parses the given string as a JSON document. Returns <code>null</code> in case of syntax errors. If more
	 * fine-grained error handling is needed, use {@link N4LanguageUtils#parseXtextLanguage(String, Class, String)}
	 * instead.
	 */
	public static JSONDocument parseJSON(String jsonString) {
		ParseResult<JSONDocument> result = N4LanguageUtils.parseXtextLanguage(JSON_FILE_EXTENSION, JSONDocument.class,
				jsonString);
		return result.errors.isEmpty() ? result.ast : null;
	}

	/**
	 * Like {@link #serializeJSON(JSONDocument)}, but accepts any kind of {@link JSONValue}.
	 */
	public static String serializeJSON(JSONValue value) {
		return serializeJSON(JSONModelUtils.createDocument(value));
	}

	/**
	 * Serializes the given {@link JSONDocument} using the Xtext serialization facilities provided by the JSON language.
	 */
	public static String serializeJSON(JSONDocument document) {
		ISerializer jsonSerializer = N4LanguageUtils.getServiceForContext(JSON_FILE_EXTENSION, ISerializer.class).get();
		ResourceSet resourceSet = N4LanguageUtils.getServiceForContext(JSON_FILE_EXTENSION, ResourceSet.class).get();

		// Use temporary Resource as AbstractFormatter2 implementations can only format
		// semantic elements that are contained in a Resource.
		Resource temporaryResource = resourceSet
				.createResource(URI.createFileURI("__synthetic." + JSON_FILE_EXTENSION));
		temporaryResource.getContents().add(document);

		// create string writer as serialization output
		StringWriter writer = new StringWriter();

		// enable formatting as serialization option
		SaveOptions serializerOptions = SaveOptions.newBuilder().format().getOptions();
		try {
			jsonSerializer.serialize(document, writer, serializerOptions);
			return writer.toString();
		} catch (IOException e) {
			throw new RuntimeException("Failed to serialize JSONDocument " + document, e);
		}
	}
}
