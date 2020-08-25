/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.integration.distributed.simulation.application;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.consensus.Command;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.ClientAtom.LedgerAtomConversionException;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializationException;
import java.util.List;
import java.util.Objects;

public class RadixEngineValidatorCommandSubmitter extends PeriodicMempoolSubmitter {
	private final List<ECKeyPair> nodes;
	private int current = 0;
	public RadixEngineValidatorCommandSubmitter(List<ECKeyPair> nodes) {
		this.nodes = Objects.requireNonNull(nodes);
	}

	@Override
	Command nextCommand() {
		byte magic = 1;
		ECKeyPair node = nodes.get(current % nodes.size());
		current++;
		RadixAddress address = new RadixAddress(magic, node.getPublicKey());

		RegisteredValidatorParticle registeredValidatorParticle = new RegisteredValidatorParticle(
			address, ImmutableSet.of(), 1
		);
		UnregisteredValidatorParticle unregisteredValidatorParticle = new UnregisteredValidatorParticle(
			address, 0
		);
		ParticleGroup particleGroup = ParticleGroup.builder()
			.addParticle(unregisteredValidatorParticle, Spin.DOWN)
			.addParticle(registeredValidatorParticle, Spin.UP)
			.build();
		Atom atom = new Atom();
		atom.addParticleGroup(particleGroup);
		try {
			atom.sign(node);
			ClientAtom clientAtom = ClientAtom.convertFromApiAtom(atom);
			final byte[] payload = DefaultSerialization.getInstance().toDson(clientAtom, Output.ALL);
			return new Command(payload);
		} catch (CryptoException | LedgerAtomConversionException | SerializationException e) {
			throw new RuntimeException();
		}
	}
}
