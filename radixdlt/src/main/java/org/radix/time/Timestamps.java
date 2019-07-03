package org.radix.time;

import java.util.HashMap;

// TODO: Remove inheritance, better to have hashmap within Timestamps class
public class Timestamps extends HashMap<String, Long>
{
	public static final String ACTIVE = "active";
	public static final String ATTEMPTED = "attempted";
	public static final String BANNED = "banned";
	public static final String CONNECTED = "connected";
	public static final String CREATED = "created";
	public static final String DEFAULT = "default";
	public static final String DELETED = "deleted";
	public static final String DISCONNECTED = "disconnected";
	public static final String EXPIRES = "expires";
	public static final String FROM = "from";
	public static final String INTENT = "intent";
	public static final String LATENCY = "latency";
	public static final String POSTPONED = "postponed";
	public static final String PROBED = "probed";
	public static final String RECEIVED = "received";
	public static final String STORING = "storing";
	public static final String STORED = "stored";
	public static final String TO = "to";
	public static final String UPDATING = "updating";
	public static final String UPDATED = "updated";


	public Timestamps()
	{
		super(1);
	}

	public Timestamps(Timestamps other)
	{
		super(other);
	}
}
