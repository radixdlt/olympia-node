package org.radix.time;

// TODO: Remove inheritance, better to have hashmap within Timestamps class
public final class Timestamps {
	public static final String ACTIVE = "active";
	public static final String BANNED = "banned";
	public static final String CREATED = "created";
	public static final String DEFAULT = "default";
	public static final String FROM = "from";
	public static final String LATENCY = "latency";
	public static final String PROBED = "probed";
	public static final String RECEIVED = "received";
	public static final String TO = "to";


	private Timestamps() {
		throw new IllegalStateException("Cannot instantiate");
	}
}
