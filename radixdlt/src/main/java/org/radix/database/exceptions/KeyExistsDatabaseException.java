package org.radix.database.exceptions;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.sleepycat.je.DatabaseEntry;

@SuppressWarnings("serial")
public class KeyExistsDatabaseException extends DatabaseException
{
	private final String database;
	private final DatabaseEntry key;

	public KeyExistsDatabaseException(String database, DatabaseEntry key)
	{
		super("Key "+str(key)+" EXISTS in database "+database);

		this.key = key;
		this.database = database;
	}

	public String getDatabase()
	{
		return this.database;
	}

	public DatabaseEntry getKey()
	{
		return this.key;
	}

	private static String str(DatabaseEntry de) {
		byte[] bytes = de.getData();
		return IntStream.range(0, bytes.length)
			.map(i -> bytes[i] & 0xFF)
			.mapToObj(i -> String.format("%02X", i))
			.collect(Collectors.joining(" "));
	}
}