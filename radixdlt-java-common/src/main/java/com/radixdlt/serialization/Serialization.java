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

import static com.radixdlt.serialization.mapper.DsonFieldFilter.filterProviderFor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.mapper.JacksonCborMapper;
import com.radixdlt.serialization.mapper.JacksonJsonMapper;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.json.JSONObject;

/**
 * Serialization class that handles conversion to/from DSON and JSON.
 */
public class Serialization {

	/**
	 * Create a new instance of {@link Serialization} with the specified IDs and policy.
	 *
	 * @param idLookup The {@link SerializerIds} to use for class and property lookup.
	 * @param policy The {@link SerializationPolicy} to use for determining serialization outputs.
	 * @return A new instance of {@link Serialization}.
	 */
	public static Serialization create(SerializerIds idLookup, SerializationPolicy policy) {
		return new Serialization(idLookup, policy);
	}

	private final ImmutableMap<Output, JacksonCborMapper> dsonMappers;
	private final ImmutableMap<Output, JacksonJsonMapper> jsonMappers;

	private final SerializerIds idLookup;

	private final FilterProvider allProvider;
	private final FilterProvider noneProvider;

	// Constructor set up to be dependency injection capable at some future date
	@VisibleForTesting
	Serialization(SerializerIds idLookup, SerializationPolicy policy) {
		this.idLookup = idLookup;

		EnumSet<Output> availableOutputs = EnumSet.allOf(Output.class);
		availableOutputs.remove(Output.ALL);
		availableOutputs.remove(Output.NONE);

		noneProvider = filterProviderFor(ImmutableMap.of());
		Map<Class<?>, ImmutableSet<String>> allFields = availableOutputs.stream()
				.flatMap(output -> policy.getIncludedFields(output).entrySet().stream())
				.collect(Collectors.groupingBy(
					Map.Entry::getKey,
					flatMapping(e -> e.getValue().stream(), ImmutableSet.toImmutableSet())
				));
		allProvider = filterProviderFor(ImmutableMap.copyOf(allFields));

		ImmutableMap.Builder<Output, JacksonCborMapper> dsonBuilder = ImmutableMap.builder();

		JacksonCborMapper hashDsonMapper = JacksonCborMapper.create(idLookup, filterProviderFor(policy.getIncludedFields(Output.HASH)), true);

		JacksonCborMapper apiDsonMapper = JacksonCborMapper.create(idLookup, filterProviderFor(policy.getIncludedFields(Output.API)),
				true, Optional.of(new ApiSerializationModifier(hashDsonMapper)));

		dsonBuilder.put(Output.HASH, hashDsonMapper);
		dsonBuilder.put(Output.API, apiDsonMapper);
		dsonBuilder.put(Output.NONE, JacksonCborMapper.create(idLookup, noneProvider, true));
		dsonBuilder.put(Output.ALL, JacksonCborMapper.create(idLookup, allProvider, true));
		for (Output e : Arrays.asList(Output.PERSIST, Output.WIRE)) {
			dsonBuilder.put(e, JacksonCborMapper.create(idLookup, filterProviderFor(policy.getIncludedFields(e)), true));
		}

		dsonMappers = dsonBuilder.build();

		ImmutableMap.Builder<Output, JacksonJsonMapper> jsonBuilder = ImmutableMap.builder();

		var hashJsonMapper = JacksonJsonMapper.create(
			idLookup,
			filterProviderFor(policy.getIncludedFields(Output.HASH)),
			false
		);
		var apiJsonMapper = JacksonJsonMapper.create(
			idLookup,
			filterProviderFor(policy.getIncludedFields(Output.API)),
			false,
			Optional.of(new ApiSerializationModifier(hashDsonMapper))
		);

		jsonBuilder.put(Output.HASH, hashJsonMapper);
		jsonBuilder.put(Output.API, apiJsonMapper);
		jsonBuilder.put(Output.NONE, JacksonJsonMapper.create(idLookup, noneProvider, false));
		jsonBuilder.put(Output.ALL, JacksonJsonMapper.create(idLookup, allProvider, false));
		for (Output e : Lists.newArrayList(Output.PERSIST, Output.WIRE)) {
			jsonBuilder.put(e, JacksonJsonMapper.create(idLookup, filterProviderFor(policy.getIncludedFields(e)), false));
		}

		jsonMappers = jsonBuilder.build();
	}

	/**
	 * Convert the specified object to DSON encoded bytes for the specified
	 * output mode.
	 *
	 * @param o The object to serialize
	 * @param output The output mode to serialize for
	 * @return The serialized object as a DSON byte array
	 */
	public byte[] toDson(Object o, DsonOutput.Output output) {
		try {
			return dsonMapper(output).writeValueAsBytes(o);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException(assembleMessage(o, "DSON"), ex);
		}
	}

	/**
	 * Convert the specified object to a JSON encoded string for the specified
	 * output mode.
	 *
	 * @param o The object to serialize
	 * @param output The output mode to serialize for
	 * @return The serialized object as a JSON string
	 */
	public String toJson(Object o, DsonOutput.Output output) {
		try {
			return jsonMapper(output).writeValueAsString(o);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException(assembleMessage(o, "JSON"), ex);
		}
	}

	/**
	 * Convert the specified object to a {@link JSONObject} encoded string for the specified
	 * output mode.
	 *
	 * @param o The object to serialize
	 * @param output The output mode to serialize for
	 * @return The serialized object as a JSON string
	 */
	public JSONObject toJsonObject(Object o, DsonOutput.Output output) {
		return jsonMapper(output).convertValue(o, JSONObject.class);
	}

	/**
	 * Convert the specified DSON encoded byte array to an instance of the
	 * specified class.
	 *
	 * @param bytes The DSON encoded object to deserialize
	 * @param valueType The class of the object to deserialize
	 * @return The deserialized object
	 * @throws DeserializeException if something goes wrong with serialization
	 */
	public <T> T fromDson(byte[] bytes, int offset, int len, Class<T> valueType) throws DeserializeException {
		try {
			return dsonMapper(Output.ALL).readValue(bytes, offset, len, valueType);
		} catch (IOException ex) {
			throw new DeserializeException("Error converting from DSON", ex);
		}
	}

	/**
	 * Convert the specified DSON encoded byte array to an instance of the
	 * specified class.
	 *
	 * @param bytes The DSON encoded object to deserialize
	 * @param valueType The class of the object to deserialize
	 * @return The deserialized object
	 * @throws DeserializeException if something goes wrong with serialization
	 */
	public <T> T fromDson(byte[] bytes, Class<T> valueType) throws DeserializeException {
		try {
			return dsonMapper(Output.ALL).readValue(bytes, valueType);
		} catch (IOException ex) {
			throw new DeserializeException("Error converting from DSON", ex);
		}
	}

	/**
	 * Convert the specified DSON encoded byte array to an instance of the
	 * specified class.
	 *
	 * @param bytes The DSON encoded object to deserialize
	 * @param valueType The class of the object to deserialize
	 * @return The deserialized object
	 * @throws DeserializeException if something goes wrong with serialization
	 */
	public <T> T fromDson(byte[] bytes, DsonJavaType valueType) throws DeserializeException {
		try {
			return dsonMapper(Output.ALL).readValue(bytes, valueType.javaType());
		} catch (IOException ex) {
			throw new DeserializeException("Error converting from DSON", ex);
		}
	}

	/**
	 * Convert the specified JSON encoded string to an instance of the
	 * specified class.
	 *
	 * @param json The JSON encoded object to deserialize
	 * @param valueType The class of the object to deserialize
	 * @return The deserialized object
	 * @throws DeserializeException if something goes wrong with serialization
	 */
	public <T> T fromJson(String json, Class<T> valueType) throws DeserializeException {
		try {
			return jsonMapper(Output.ALL).readValue(json, valueType);
		} catch (IOException ex) {
			throw new DeserializeException("Error converting from JSON", ex);
		}
	}

	/**
	 * Convert the specified JSON encoded string to an instance of the
	 * specified class.
	 *
	 * @param json The JSON encoded object to deserialize
	 * @param valueType The class of the object to deserialize
	 * @return The deserialized object
	 * @throws DeserializeException if something goes wrong with serialization
	 */
	public <T> T fromJson(String json, JsonJavaType valueType) throws DeserializeException {
		try {
			return jsonMapper(Output.ALL).readValue(json, valueType.javaType());
		} catch (IOException ex) {
			throw new DeserializeException("Error converting from JSON", ex);
		}
	}

	/**
	 * Convert the specified JSONObject to an instance of the
	 * specified class.
	 *
	 * @param json The {@link JSONObject} object to convert
	 * @param valueType The class of the object to convert to
	 * @return The converted object
	 */
	public <T> T fromJsonObject(JSONObject json, Class<T> valueType) {
		return jsonMapper(Output.ALL).convertValue(json, valueType);
	}

	/**
	 * Return a collection type for use with the {@link #fromDson(byte[], DsonJavaType)} method.
	 *
	 * @param collectionClass The collection class to deserialize.
	 * @param elementClass The collection element type.
	 * @return The deserialized collection.
	 */
	@SuppressWarnings("rawtypes")
	public DsonJavaType dsonCollectionType(Class<? extends Collection> collectionClass, Class<?> elementClass) {
		JavaType type = dsonMappers.get(Output.ALL).getTypeFactory().constructCollectionType(collectionClass, elementClass);
		return new DsonJavaType(type);
	}

	/**
	 * Return a collection type for use with the {@link #fromJson(String, JsonJavaType)} method.
	 *
	 * @param collectionClass The collection class to deserialize.
	 * @param elementClass The collection element type.
	 * @return The deserialized collection.
	 */
	@SuppressWarnings("rawtypes")
	public JsonJavaType jsonCollectionType(Class<? extends Collection> collectionClass, Class<?> elementClass) {
		JavaType type = jsonMappers.get(Output.ALL).getTypeFactory().constructCollectionType(collectionClass, elementClass);
		return new JsonJavaType(type);
	}

	/**
	 * Retrieve serializer ID from class.
	 *
	 * @param cls The class to look up the ID for
	 * @return The serializer ID, or {@code null} if no serializer for the specified class.
	 */
	public String getIdForClass(Class<?> cls) {
		return idLookup.getIdForClass(cls);
	}

	/**
	 * Retrieve class given a serializer ID.
	 *
	 * @param id The ID to look up the class for
	 * @return The class, or {@code null} if serializer ID unknown.
	 */
	public Class<?> getClassForId(String id) {
		return idLookup.getClassForId(id);
	}

	private JacksonCborMapper dsonMapper(Output output) {
		return dsonMappers.get(output);
	}

	private JacksonJsonMapper jsonMapper(Output output) {
		return jsonMappers.get(output);
	}

	private static <T, U, A, R> Collector<T, ?, R> flatMapping(Function<? super T, ? extends Stream<? extends U>> mapper,
			Collector<? super U, A, R> downstream) {
		BiConsumer<A, ? super U> acc = downstream.accumulator();
		return Collector.of(downstream.supplier(), (a, t) -> {
			try (Stream<? extends U> s = mapper.apply(t)) {
				if (s != null) {
					s.forEachOrdered(u -> acc.accept(a, u));
				}
			}
		}, downstream.combiner(), downstream.finisher(), downstream.characteristics().toArray(new Collector.Characteristics[0]));
	}

	private static String assembleMessage(Object o, String type) {
		String className = o == null ? "(null)" : o.getClass().getName();
		return "Error converting to " + type + ". Check registration for " + className;
	}
}
