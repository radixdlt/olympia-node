package org.radix.common;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Criticality of events, errors and exceptions
 */
public enum Criticality
{
	/**
	 * Could not be determined
	 */
	UNKNOWN(0),

	/**
	 * Does not have a a Criticality level
	 */
	@Deprecated
	NONE(1),

	/**
	 * Events that are useful for debugging
	 */
	DEBUG(2),

	/**
	 * Events useful to know about (and log)
	 */
	INFO(3),

	/**
	 * Recoverable events in code execution, internal and external
	 * TODO this appears to be unused and equal to ERROR
	 */
	WARNING(4),

	/**
	 * Recoverable events in code execution, internal and external
	 */
	ERROR(5),

	/**
	 * Unrecoverable events in code execution, internal and external (e.g. validation)
	 */
	CRITICAL(6),

	/**
	 * Deadly, unrecoverable events (e.g. database can't be started)
	 */
	FATAL(7);

	private byte level;

	Criticality(int level)
	{
		this.level = (byte) level;
	}

	public byte getLevel() {
		return this.level;
	}

	@JsonValue
	@Override
	public String toString() {
		return this.name();
	}
}