package org.radix.serialization2;

/**
 * Package-local constants.
 */
public final class SerializerConstants {
	private SerializerConstants() {
		throw new IllegalStateException("Can't construct");
	}

	// At least this will cause compilation fail when updated
	public static final Class<SerializerId2> SERIALIZER_ID_ANNOTATION = SerializerId2.class;
}
