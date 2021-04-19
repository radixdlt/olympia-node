/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.client.core.atoms;

import com.radixdlt.atommodel.tokens.TokensParticle;
import com.radixdlt.atommodel.validators.ValidatorParticle;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.RadixAddress;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Temporary class to support some old interfaces
 * TODO: remove
 */
public final class Addresses {
	private Addresses() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	public static Stream<RadixAddress> ofAtom(Atom atom) {
		return Stream.of();
	}

	public static Set<RadixAddress> getShardables(Particle p) {
		Set<RadixAddress> addresses = new HashSet<>();

		if (p instanceof TokensParticle) {
			var a = (TokensParticle) p;
			addresses.add(a.getAddress());
		} else if (p instanceof ValidatorParticle) {
			var a = (ValidatorParticle) p;
			addresses.add(a.getAddress());
		}

		return new HashSet<>(addresses);
	}
}
