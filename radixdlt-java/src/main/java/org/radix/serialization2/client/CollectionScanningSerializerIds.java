package org.radix.serialization2.client;

import java.util.Collection;

import org.radix.serialization2.ClassScanningSerializerIds;
import org.radix.serialization2.SerializerIds;

import com.google.common.annotations.VisibleForTesting;

/**
 * Class that maintains a map of serializer IDs to {@code Class<?>} objects,
 * and vice versa, for all serializable classes in the core system.
 * <p>
 * This {@link SerializerIds} operates by scanning a supplied
 * {@link Collection} of classes.
 */
public final class CollectionScanningSerializerIds extends ClassScanningSerializerIds {
	/**
	 * Create a freshly initialized instance of
	 * {@link CollectionScanningSerializerIds}.
	 *
	 * @param classes The classes to scan for annotations.
	 * @return A freshly created and initialized instance
	 */
	public static SerializerIds create(Collection<Class<?>> classes) {
		return new CollectionScanningSerializerIds(classes);
	}

	@VisibleForTesting
	CollectionScanningSerializerIds(Collection<Class<?>> classes) {
		super(classes);
	}
}
