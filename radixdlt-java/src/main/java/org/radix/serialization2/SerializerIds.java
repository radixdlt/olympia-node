package org.radix.serialization2;

/**
 * Interface for accessing serializer IDs given a class or vice-versa.
 */
public interface SerializerIds {

	/**
	 * Return the serializer ID, or {@code null} if no serializer known.
	 *
	 * @param cls The class to retrieve the serializer ID for.
	 * @return The serializer ID, or {@code null} if no serializer known.
	 */
	String getIdForClass(Class<?> cls);

	/**
	 * Return an object's class, given the ID.  If the serializer ID
	 * is unknown, {@code null} is returned.
	 *
	 * @param id The serializer ID to find the mapped class for.
	 * @return The class corresponding to the serializer ID, or {@code null}
	 *			if serializer ID unknown.
	 */
	Class<?> getClassForId(String id);

	/**
	 * Return true if class is serializable, or a supertype of
	 * a serializable class, excluding {@code Object}.
	 * @param cls The class to check
	 * @return {@code true} if class is serializable, or a supertype
	 */
	boolean isSerializableSuper(Class<?> cls);

}