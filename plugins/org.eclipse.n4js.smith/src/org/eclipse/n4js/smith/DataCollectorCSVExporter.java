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
package org.eclipse.n4js.smith;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Exports a given {@link DataCollector} and all its children to the CSV (comma-separated values) format.
 */
public class DataCollectorCSVExporter {
	/**
	 * Returns a CSV representation of all the data captured under the given {@code key}.
	 */
	public static String toCSV(String key) {
		final DataSeries dataSeries = CollectedDataAccess.getDataSeries(key);
		final Set<DataSeries> allSeries = collectAllSeries(dataSeries, new LinkedHashSet<>());

		final List<String> header = new ArrayList<>();
		final List<String> data = new ArrayList<>();

		for (DataSeries s : allSeries) {
			header.add(s.name);
			data.add(Long.toString(s.sum));
		}

		final String headerRow = header.stream().collect(Collectors.joining(";"));
		final String dataRow = data.stream().collect(Collectors.joining(";"));

		return headerRow + "\n" + dataRow;
	}

	/** Recursively collects all child data series of the given {@code series}. */
	private static Set<DataSeries> collectAllSeries(DataSeries series, Set<DataSeries> allSeries) {
		allSeries.add(series);
		series.getChildren().forEach(c -> {
			collectAllSeries(c, allSeries);
		});
		return allSeries;
	}
}
