package org.radix.serialization2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;

import static org.radix.serialization2.SerializerConstants.SERIALIZER_ID_ANNOTATION;

/**
 * Class that maintains a map of serializer IDs to {@code Class<?>} objects
 * and vice versa.
 * <p>
 * This {@link SerializerIds} operates by scanning a supplied list of classes.
 */
public abstract class ClassScanningSerializerIds implements SerializerIds {

	// Assuming that lookups from class to ID will be more common
	private final Map<Class<?>, Long> classIdMap = Maps.newHashMap();
	// Inverse view of same data
	private final BiMap<Long, Class<?>> idClassMap = HashBiMap.create();

	private final HashSet<Class<?>> serializableSupertypes = new HashSet<>();

	/**
	 * Scan for all classes with an {@code SerializerId} annotation
	 * in the specified set of classes.
	 *
	 * @param classes The list of classes to scan for serialization annotations
	 * @throws SerializerIdsException If two or more classes are
	 *			found with the same {@code SerializerId}
	 */
	protected ClassScanningSerializerIds(Collection<Class<?>> classes) {
		Map<Long, List<Class<?>>> polymorphicMap = new HashMap<>();

		for (Class<?> cls : classes) {
			SerializerId2 sid = cls.getDeclaredAnnotation(SERIALIZER_ID_ANNOTATION);
			if (sid == null) {
				// For some reason, Reflections returns classes without SerializerId, but
				// that inherit from classes with the (non-inheritable) annotation.  Sad.
//				if (log.hasLevel(Logging.DEBUG)) {
//					log.debug("Skipping unannotated class " + cls.getName());
//				}
				continue;
			}

			if (cls.isInterface()) {
				// Interfaces should not be marked with @SerializerId
//				log.warn(String.format("Skipping interface %s with unexpected %s annotation",
//						cls.getName(), SERIALIZER_ID_ANNOTATION.getSimpleName()));
				continue;
			}

//			if (Modifier.isAbstract(cls.getModifiers())) {
//				// Abstract classes should not be marked with @SerializerId
//				// There may be a need to implement this to satisfy some of the Indexable stuff.
//				log.warn(String.format("Skipping abstract class %s with unexpected %s annotation",
//						cls.getName(), SERIALIZER_ID_ANNOTATION.getSimpleName()));
//				continue;
//			}

			// Currently Loggables are required to have a SerializerID so that
			// they can be correctly deserialized in a polymorphic way.
//			if (Loggable.class.isAssignableFrom(cls)) {
//				// Loggable objects are serialized for persistence only.
//				// No need for @SerializerId
//				log.warn("Skipping 'Loggable' sub-class " + cls.getName());
//				continue;
//			}

			long id = sid.value().hashCode();

			if (Polymorphic.class.isAssignableFrom(cls)) {
				// Polymorphic class hierarchy checked later
//				if (log.hasLevel(Logging.DEBUG)) {
//					log.debug("Polymorphic class:" + cls.getName() + " with ID:" + id);
//				}
				polymorphicMap.computeIfAbsent(id, k -> new ArrayList<>()).add(cls);
			} else {
				// Check for duplicates
				Class<?> dupClass = idClassMap.put(id, cls);
				if (dupClass != null) {
					throw new SerializerIdsException(
							String.format("Aborting, duplicate ID %s discovered in classes: [%s, %s]",
									id, cls.getName(), dupClass.getName()));
				}
//				if (log.hasLevel(Logging.DEBUG)) {
//					log.debug("Putting Class:" + cls.getName() + " with ID:" + id);
//				}
				collectSupertypes(cls);
			}
		}

		classIdMap.putAll(idClassMap.inverse());
		// Check polymorphic hierarchy consistency
		for (Map.Entry<Long, List<Class<?>>> entry : polymorphicMap.entrySet()) {
			Long id = entry.getKey();
			if (!idClassMap.containsKey(id)) {
				throw new SerializerIdsException(
						String.format("No concrete class with ID '%s' for polymorphic classes %s",
								entry.getKey(), entry.getValue()));
			}
			for (Class<?> cls : entry.getValue()) {
				Long dupId = classIdMap.put(cls, id);
				if (dupId != null) {
					throw new SerializerIdsException(
							String.format("Aborting, class %s has duplicate IDs %s and %s",
									cls.getName(), id, dupId));
				}
			}
		}
	}

	private void collectSupertypes(Class<?> cls) {
		while (!Object.class.equals(cls)) {
			serializableSupertypes.add(cls);
			cls = cls.getSuperclass();
		}
	}

	@Override
	public Long getIdForClass(Class<?> cls) {
		if (!this.classIdMap.containsKey(cls)) {
			throw new IllegalArgumentException("Class " + cls + " could not be found");
		}

		return classIdMap.get(cls);
	}

	@Override
	public Class<?> getClassForId(long id) {
		if (!this.idClassMap.containsKey(id)) {
			throw new IllegalArgumentException("Class id " + id + " could not be found");
		}

		return idClassMap.get(id);
	}

	@Override
	public boolean isSerializableSuper(Class<?> cls) {
		return serializableSupertypes.contains(cls);
	}
}
