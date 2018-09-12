package com.radixdlt.client.core.serialization;

/**
 * Constants package-local to serialization.
 */
final class SerializationConstants {

	private SerializationConstants() {
		throw new IllegalStateException("Can't construct");
	}

	static final String BYT_PREFIX = ":byt:";
	static final String HSH_PREFIX = ":hsh:";
	static final String STR_PREFIX = ":str:";
	static final String UID_PREFIX = ":uid:";

}
