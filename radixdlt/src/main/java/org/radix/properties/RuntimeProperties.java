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

package org.radix.properties;

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
 *
 * @author Dan Hughes
 *
 */
public class RuntimeProperties extends PersistedProperties
{
	private CommandLine	commandLine = null;

	public RuntimeProperties(JSONObject commandLineConfig, String[] commandLineArguments) throws ParseException
	{
		super();

		CommandLineParser parser = new DefaultParser ();
		Options gnuOptions = new Options ();

		for (String clKey : commandLineConfig.keySet())
		{
			JSONObject clOption = commandLineConfig.getJSONObject(clKey);

			gnuOptions.addOption (clOption.getString("short"), 	clKey, clOption.getBoolean("has_arg"),  clOption.optString("desc", ""));
		}

		commandLine = parser.parse (gnuOptions, commandLineArguments);

		load(commandLine.getOptionValue("config", "default.config"));
	}

	public CommandLine getCommandLine() { return commandLine; }

	@Override
	public String get(String key)
	{
		for (Option commandLineOption : commandLine.getOptions())
		{
			if (commandLineOption.getOpt().equals(key) || commandLineOption.getLongOpt().equals(key))
			{
				if (commandLineOption.hasArg())
					return commandLineOption.getValue();
				else
					return "1";
			}
		}

		return super.get(key);
	}
}
