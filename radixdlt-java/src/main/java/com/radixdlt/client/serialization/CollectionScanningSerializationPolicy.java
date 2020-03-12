package com.radixdlt.client.serialization;

import java.util.Collection;

import com.google.common.annotations.VisibleForTesting;
import com.radixdlt.serialization.ClassScanningSerializationPolicy;
import com.radixdlt.serialization.SerializationPolicy;

/**
 * Class that maintains a map of {@link com.radixdlt.serialization.DsonOutput.Output}
 * values to a set of pairs of classes and field/method names to output for that
 * serialization type.
 * <p>
 * This implementation works by scanning a supplied {@link Collection} for
 * classes annotated with {@code SerializerConstants.SERIALIZER_ID_ANNOTATION}
 * and passing these classes to {@link ClassScanningSerializationPolicy}.
 */
public final class CollectionScanningSerializationPolicy extends ClassScanningSerializationPolicy {

	/**
	 * Create a {@link SerializationPolicy} from a supplied list of classes.
	 * The classes are scanned for appropriate annotations.
	 *
	 * @param classes The classes to scan for annotations
	 * @return A freshly created {@link CollectionScanningSerializationPolicy}
	 */
	public static SerializationPolicy create(Collection<Class<?>> classes) {
		return new CollectionScanningSerializationPolicy(classes);
	}

	@VisibleForTesting
	CollectionScanningSerializationPolicy(Collection<Class<?>> classes) {
		super(classes);
	}
}
