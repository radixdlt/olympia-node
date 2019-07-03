package com.radixdlt.serialization;

import com.radixdlt.serialization.DsonOutput.Output;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Serialization policy that returns a set of classes and field names for
 * a specified {@link DsonOutput.Output} mode.
 */
public interface SerializationPolicy {

	/**
	 * Retrieve the fields to output for the given output mode.
	 *
	 * @param output The output mode
	 * @return The set of pairs of {@code Class<?>} and field names to output
	 */
	ImmutableMap<Class<?>, ImmutableSet<String>> getIncludedFields(Output output);

}