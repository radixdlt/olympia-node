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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.EnumSet;

/**
 * Annotation used to indicate which fields are included in coded DSON
 * for which output requirements.  As an example, the "serializer" field
 * and any signature fields, are not included in "HASH" output, as they
 * are not included in any hash computation.
 * <p>
 * As an example, to include a field only in data persisted to disk, the
 * following annotation might be used:
 * <pre>
 *         &#64;DsonOutput(Output.PERSIST)
 *         &#64;JsonProperty("diskTimestamp")
 *         private long diskTimestamp;
 * </pre>
 * To exclude data from being included in a hash, the following annotation
 * could be used:
 * <pre>
 *         &#64;DsonOutput(value = Output.HASH, include = false)
 *         &#64;JsonProperty("signature")
 *         private byte[] signature;
 * </pre>
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface DsonOutput {

	/**
	 * The serialization output modes for which this field should be
	 * included or excluded, depending on the value of {@link #include()}.
	 *
	 * @return The output modes for which this field should be
	 * 		included or excluded.
	 * @see #include()
	 */
	Output[] value();

	/**
	 * {@code true} if {@link #value()} specified output modes where this
	 * field is to be included.  Otherwise {@link #value()} specifies modes
	 * where this field is to be excluded.
	 *
	 * @return {@code true} if field to be included for specified modes,
	 * 		{@code false} otherwise.
	 */
	boolean include() default true;

	/**
	 * Output modes for serialization.
	 * <p>
	 * There are four concrete output modes, {@link #HASH}, {@link #API},
	 * {@link #WIRE} and {@link #PERSIST}.  Two additional modes are
	 * provided for ease of use {@link #ALL} and {@link #NONE}, representing
	 * the union of all the concrete modes, and the empty set respectively.
	 * <p>
	 * Note that the output mode {@link #NONE} is of limited use.
	 */
	enum Output {
		/**
		 * An output mode that never results in output.  Of limited use.
		 */
		NONE,
		/**
		 * An output mode for calculating hashes.
		 */
		HASH,
		/**
		 * An output mode for use with application interfaces.
		 */
		API,
		/**
		 * An output mode for use when communicating to other nodes.
		 */
		WIRE,
		/**
		 * An output mode for use when writing data to persistent storage.
		 */
		PERSIST,
		/**
		 * An output mode that always results in output.
		 */
		ALL;

		private static final EnumSet<Output> NONE_OF = EnumSet.noneOf(Output.class);
		private static final EnumSet<Output> ALL_OF  = EnumSet.allOf(Output.class);

		/**
		 * Convert enclosing annotation values to an {@link EnumSet}.
		 *
		 * @param value The values from the annotation
		 * @param include The include flag from the annotation
		 * @return An {@link EnumSet} identifying the {@link DsonOutput.Output}
		 * 		modes to output fields for.
		 */
		public static EnumSet<Output> toEnumSet(Output[] value, boolean include) {
			EnumSet<Output> set = EnumSet.copyOf(Arrays.asList(value));
			if (set.contains(NONE)) {
				if (value.length == 1) {
					set = NONE_OF;
				} else {
					throw new IllegalArgumentException("Can't include additional outputs with NONE: " + Arrays.toString(value));
				}
			}
			if (set.contains(ALL)) {
				if (value.length == 1) {
					set = ALL_OF;
				} else {
					throw new IllegalArgumentException("Can't include additional outputs with ALL: " + Arrays.toString(value));
				}
			}
			if (!include) {
				set = EnumSet.complementOf(set);
			}
			set.remove(ALL);
			set.remove(NONE);
			return set;
		}
	}
}
