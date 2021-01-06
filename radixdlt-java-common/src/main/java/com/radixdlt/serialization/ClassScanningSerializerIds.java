/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.serialization;

import static com.radixdlt.serialization.SerializerConstants.SERIALIZER_ID_ANNOTATION;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.radixdlt.identifiers.EUID;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class that maintains a map of serializer IDs to {@code Class<?>} objects
 * and vice versa.
 * <p>
 * This {@link SerializerIds} operates by scanning a supplied list of classes.
 */
public abstract class ClassScanningSerializerIds implements SerializerIds {
	private static final Logger log = LogManager.getLogger(ClassScanningSerializerIds.class);

	// Assuming that lookups from class to ID will be more identifiers
	private final Map<Class<?>, String> classIdMap = Maps.newHashMap();
	// Inverse view of same data
	private final BiMap<String, Class<?>> idClassMap = HashBiMap.create();

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
		Map<String, List<Class<?>>> polymorphicMap = new HashMap<>();

		for (Class<?> cls : classes) {
			SerializerId2 sid = cls.getDeclaredAnnotation(SERIALIZER_ID_ANNOTATION);
			if (sid == null) {
				// For some reason, Reflections returns classes without SerializerId, but
				// that inherit from classes with the (non-inheritable) annotation.  Sad.
				log.debug("Skipping unannotated class " + cls.getName());
				continue;
			}

			if (cls.isInterface()) {
				// Interfaces should not be marked with @SerializerId
				log.warn(String.format("Skipping interface %s with unexpected %s annotation",
						cls.getName(), SERIALIZER_ID_ANNOTATION.getSimpleName()));
				continue;
			}

//			if (Modifier.isAbstract(cls.getModifiers())) {
//				// Abstract classes should not be marked with @SerializerId
//				// There may be a need to implement this to satisfy some of the Indexable stuff.
//				log.warn(String.format("Skipping abstract class %s with unexpected %s annotation",
//						cls.getName(), SERIALIZER_ID_ANNOTATION.getSimpleName()));
//				continue;
//			}

			String id = sid.value();

			if (Polymorphic.class.isAssignableFrom(cls)) {
				// Polymorphic class hierarchy checked later
				log.debug("Polymorphic class:" + cls.getName() + " with ID:" + id);
				polymorphicMap.computeIfAbsent(id, k -> new ArrayList<>()).add(cls);
			} else {
				// Check for duplicates
				Class<?> dupClass = idClassMap.put(id, cls);
				if (dupClass != null) {
					throw new SerializerIdsException(
							String.format("Aborting, duplicate ID %s discovered in classes: [%s, %s]",
									id, cls.getName(), dupClass.getName()));
				}
				log.debug("Putting Class:" + cls.getName() + " with ID:" + id);
				collectSupertypes(cls);
				collectInterfaces(cls);
			}
		}

		classIdMap.putAll(idClassMap.inverse());
		Map<EUID, String> idNumericMap = new HashMap<>();
		// Check polymorphic hierarchy consistency
		for (Map.Entry<String, List<Class<?>>> entry : polymorphicMap.entrySet()) {
			String id = entry.getKey();
			if (!idClassMap.containsKey(id)) {
				throw new SerializerIdsException(
						String.format("No concrete class with ID '%s' for polymorphic classes %s", entry.getKey(), entry.getValue()));
			}
			EUID numericId = SerializationUtils.stringToNumericID(id);
			String dupNumericId = idNumericMap.put(numericId, id);
			if (dupNumericId != null) {
				throw new SerializerIdsException(String.format("Aborting, numeric id %s of %s clashes with %s",
					numericId, id, dupNumericId));
			}
			for (Class<?> cls : entry.getValue()) {
				String dupId = classIdMap.put(cls, id);
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

	private void collectInterfaces(Class<?> cls) {
		Stream.of(cls.getInterfaces())
			.filter(this::isSerializerRoot)
			.forEachOrdered(serializableSupertypes::add);
	}

	private boolean isSerializerRoot(Class<?> clazz) {
		return clazz.isAnnotationPresent(SerializerConstants.SERIALIZER_ROOT_ANNOTATION);
	}

	@Override
	public String getIdForClass(Class<?> cls) {
		return classIdMap.get(cls);
	}

	@Override
	public Class<?> getClassForId(String id) {
		return idClassMap.get(id);
	}

	@Override
	public boolean isSerializableSuper(Class<?> cls) {
		return serializableSupertypes.contains(cls);
	}
}
