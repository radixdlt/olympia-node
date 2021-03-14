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

import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.client.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.client.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.client.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.atommodel.unique.UniqueParticle;
import com.radixdlt.client.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.client.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.RadixAddress;

import java.util.HashSet;
import java.util.Set;

public final class Particles {
	private Particles() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	public static Set<RadixAddress> getShardables(Particle p) {
		Set<RadixAddress> addresses = new HashSet<>();

		if (p instanceof RRIParticle) {
			var a = (RRIParticle) p;
			addresses.add(a.getRri().getAddress());
		} else if (p instanceof StakedTokensParticle) {
			var a = (StakedTokensParticle) p;
			addresses.addAll(a.getAddresses());
		} else if (p instanceof TransferrableTokensParticle) {
			var a = (TransferrableTokensParticle) p;
			addresses.addAll(a.getAddresses());
		} else if (p instanceof UnallocatedTokensParticle) {
			var a = (UnallocatedTokensParticle) p;
			addresses.addAll(a.getAddresses());
		} else if (p instanceof RegisteredValidatorParticle) {
			var a = (RegisteredValidatorParticle) p;
			addresses.addAll(a.getAddresses());
		} else if (p instanceof UnregisteredValidatorParticle) {
			var a = (UnregisteredValidatorParticle) p;
			addresses.addAll(a.getAddresses());
		} else if (p instanceof FixedSupplyTokenDefinitionParticle) {
			var i = (FixedSupplyTokenDefinitionParticle) p;
			addresses.add(i.getRRI().getAddress());
		} else if (p instanceof MutableSupplyTokenDefinitionParticle) {
			var i = (MutableSupplyTokenDefinitionParticle) p;
			addresses.add(i.getRRI().getAddress());
		} else if (p instanceof UniqueParticle) {
			var i = (UniqueParticle) p;
			addresses.add(i.getRRI().getAddress());
		}

		return new HashSet<>(addresses);
	}
}
