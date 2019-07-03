package com.radixdlt.serialization.core;

import java.util.Set;

import org.reflections.Reflections;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import com.google.common.annotations.VisibleForTesting;
import com.radixdlt.serialization.ClassScanningSerializerIds;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerIds;

/**
 * Class that maintains a map of serializer IDs to {@code Class<?>} objects,
 * and vice versa, for all serializable classes in the core system.
 * <p>
 * This {@link SerializerIds} operates by scanning the class path.
 */
public final class ClasspathScanningSerializerIds extends ClassScanningSerializerIds {
	/**
	 * Create a freshly initialized instance of
	 * {@link ClasspathScanningSerializerIds}.
	 * <p>
	 * Note that is is quite expensive to create an instance of this
	 * class, perhaps in the order of seconds.  Once created, the class
	 * is immutable, and therefore thread-safe.
	 *
	 * @return A freshly created and initialized instance
	 * @throws SerializerIdsException If two or more classes are
	 *			found with the same {@code SerializerId}
	 */
	public static SerializerIds create() {
		return new ClasspathScanningSerializerIds();
	}

	@VisibleForTesting
	ClasspathScanningSerializerIds() {
		super(scanForSerializable());
	}

	private static Set<Class<?>> scanForSerializable() {
		ConfigurationBuilder config = new ConfigurationBuilder()
			.setUrls(ClasspathHelper.forJavaClassPath())
			.filterInputsBy(new FilterBuilder().includePackage("org.radix", "com.radixdlt"));
		Reflections reflections = new Reflections(config);
		return reflections.getTypesAnnotatedWith(SerializerConstants.SERIALIZER_ID_ANNOTATION);
    }
}
