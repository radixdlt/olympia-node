/* Copyright 2021 Radix DLT Ltd incorporated in England.
 * 
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 * 
 * radixfoundation.org/licenses/LICENSE-v1
 * 
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 * 
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 * 
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 * 
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 * 
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system 
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 * 
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 * 
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 * 
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 * 
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 * 
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 * 
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 * 
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radix.acceptance;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.ImmutableMap;

/**
 * Class for maintaining a map of string names to string values,
 * where allowable names and default values can be specified.
 */
public class SpecificProperties {
	private final ImmutableMap<String, String> defaultValues;
	private final Map<String, String> propertyValues = new HashMap<>();

	SpecificProperties(String... propertyNamesAndValues) {
		ImmutableMap.Builder<String, String> defaults = ImmutableMap.builder();
		for (int i = 0; i < propertyNamesAndValues.length; i += 2) {
			defaults.put(propertyNamesAndValues[i], propertyNamesAndValues[i + 1]);
		}
		this.defaultValues = defaults.build();
		this.propertyValues.putAll(this.defaultValues);
	}

	/**
	 * Retrieves a value for a given property name.
	 * @param name The property name to retrieve the value for.
	 * @return The property value
	 * @throws IllegalArgumentException if the given property does not have a value
	 */
	public String get(String name) {
		Objects.requireNonNull(name);
		if (!this.propertyValues.containsKey(name)) {
			throw new IllegalArgumentException("No such property: " + name);
		}
		return this.propertyValues.get(name);
	}

	/**
	 * Associates a value with a given property name.
	 * @param name The property name
	 * @param value The property value
	 * @throws IllegalArgumentException if the specified property name is not known
	 */
	public void put(String name, String value) {
		Objects.requireNonNull(name);
		Objects.requireNonNull(value);
		if (!this.defaultValues.containsKey(name)) {
			throw new IllegalArgumentException("Invalid property name: " + name);
		}
		this.propertyValues.put(name, value);
	}

	/**
	 * Resets this property map to default values.
	 */
	public void clear() {
		this.propertyValues.clear();
		this.propertyValues.putAll(this.defaultValues);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.defaultValues, this.propertyValues);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof SpecificProperties) {
			SpecificProperties other = (SpecificProperties) obj;
			return Objects.equals(this.defaultValues, other.defaultValues)
				&& Objects.equals(this.propertyValues, other.propertyValues);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[defaults=%s, values=%s]",
				getClass().getSimpleName(), defaultValues, propertyValues);
	}

	/**
	 * Constructs a properties map of names to default values.
	 * @param propertyNamesAndValues A sequence of alternating property names and
	 * 		default values.  A default value of {@code null} may be used to indicate
	 * 		no default value.  There must therefore be an even number of elements
	 * 		in this sequence.
	 * @return The corresponding properties map.
	 */
	public static SpecificProperties of(String... propertyNamesAndValues) {
		if ((propertyNamesAndValues.length % 2) != 0) {
			throw new IllegalArgumentException("Must specify names and values");
		}
		return new SpecificProperties(propertyNamesAndValues);
	}
}
