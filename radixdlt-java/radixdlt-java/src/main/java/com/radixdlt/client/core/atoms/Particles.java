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

import com.radixdlt.client.atommodel.Accountable;
import com.radixdlt.client.atommodel.Identifiable;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.identifiers.RadixAddress;

import java.util.HashSet;
import java.util.Set;

public final class Particles {
	private Particles() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	public static Set<RadixAddress> getShardables(Particle p) {
		Set<RadixAddress> addresses = new HashSet<>();

		if (p instanceof Accountable) {
			Accountable a = (Accountable) p;
			addresses.addAll(a.getAddresses());
		}

		if (p instanceof Identifiable) {
			Identifiable i = (Identifiable) p;
			addresses.add(i.getRRI().getAddress());
		}

		return new HashSet<>(addresses);
	}
}
