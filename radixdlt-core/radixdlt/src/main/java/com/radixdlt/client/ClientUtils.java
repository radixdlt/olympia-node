/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.client;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.SubstateSerializer;
import com.radixdlt.atom.Txn;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.serialization.DeserializeException;

import java.util.List;

/**
 * Some useful and commonly used methods.
 */
public interface ClientUtils {
	static TokenDefinitionParticle nativeToken(List<Txn> genesisAtoms) {
		return genesisAtoms.stream()
			.map(txn -> {
				try {
					return DefaultSerialization.getInstance().fromDson(txn.getPayload(), Atom.class);
				} catch (DeserializeException e) {
					throw new IllegalStateException();
				}
			})
			.flatMap(a -> a.getInstructions().stream().map(REInstruction::create))
			.filter(i -> i.getMicroOp() == REInstruction.REOp.UP)
			.map(i -> {
				try {
					return SubstateSerializer.deserialize(i.getData());
				} catch (DeserializeException e) {
					throw new IllegalStateException("Cannot deserialize genesis", e);
				}
			})
			.filter(TokenDefinitionParticle.class::isInstance)
			.map(TokenDefinitionParticle.class::cast)
			.filter(particle -> particle.getRri().getName().equals(TokenDefinitionUtils.getNativeTokenShortCode()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Unable to retrieve native token definition"));
	}
}
