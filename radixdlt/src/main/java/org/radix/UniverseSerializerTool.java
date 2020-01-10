package org.radix;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;

/**
 * Very simple tool to parse and serialize a Universe from JSON.
 * <br><br>
 * Useful when updating live networks to ensure that Universes match on old/new nodes.
 * <br><br>
 * Usage:  	Call /api/universe on a node, copy the JSON output to a file, run this tool with the filename as an argument.<br>
 * 			Outputs a reserialized BASE64 encoded Universe to standard output for use in default.config.
 * <br><br>
 * @author Dan
 *
 */
public class UniverseSerializerTool
{
	public static void main(String[] args) throws IOException
	{
		if (args == null || args.length == 0)
			throw new IllegalArgumentException("No arguments");

		File universeFile = new File(args[0]);
		if (!universeFile.exists())
			throw new FileNotFoundException("Universe JSON file not found");

		String universeFileContent = new String(Files.readAllBytes(universeFile.toPath()));

		Serialization serialization = Serialization.getDefault();
		Universe universe = serialization.fromJson(universeFileContent, Universe.class);
		String serializedUniverseBase64String = Base64.getEncoder().encodeToString(serialization.toDson(universe, Output.ALL));
		System.out.println(serializedUniverseBase64String);
	}
}
