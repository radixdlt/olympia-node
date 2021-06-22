/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.properties;

import java.util.Arrays;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.json.JSONObject;

/**
 * Runtime properties are temporary property sets that are discarded upon client termination.
 *
 * They combine a set of persisted properties, with a set of command line properties.
 */
public final class RuntimeProperties extends PersistedProperties {
	private final CommandLine commandLine;

	public RuntimeProperties(JSONObject commandLineConfig, String[] commandLineArguments) throws ParseException {
		CommandLineParser parser = new DefaultParser();
		Options gnuOptions = new Options();

		for (String clKey : commandLineConfig.keySet()) {
			JSONObject clOption = commandLineConfig.getJSONObject(clKey);
			gnuOptions.addOption(clOption.getString("short"), clKey, clOption.getBoolean("has_arg"), clOption.optString("desc", ""));
		}

		commandLine = parser.parse(gnuOptions, commandLineArguments);

		load(commandLine.getOptionValue("config", "default.config"));
	}

	@Override
	public String get(String key) {
		for (Option commandLineOption : commandLine.getOptions()) {
			if (commandLineOption.getOpt().equals(key)) {
				if (commandLineOption.hasArg()) {
					return commandLineOption.getValue();
				} else {
					return "1";
				}
			}
		}

		return super.get(key);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Arrays.hashCode(commandLine.getArgs());
		result = prime * result + Arrays.hashCode(commandLine.getOptions());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (super.equals(obj) && obj instanceof RuntimeProperties) {
			RuntimeProperties other = (RuntimeProperties) obj;
			return Arrays.equals(commandLine.getArgs(), other.commandLine.getArgs())
				&& Arrays.equals(commandLine.getOptions(), other.commandLine.getOptions());
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[%s, args=%s, options=%s]",
			getClass().getSimpleName(),
			super.toString(),
			Arrays.toString(commandLine.getArgs()),
			Arrays.toString(commandLine.getOptions())
		);
	}
}
