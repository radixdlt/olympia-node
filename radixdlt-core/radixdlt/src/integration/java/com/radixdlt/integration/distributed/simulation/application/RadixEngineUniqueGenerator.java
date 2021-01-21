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

import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.atommodel.AtomAlreadySignedException;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.serialization.DsonOutput;

/**
 * Generates a new unique rri consumer command. Because new addresses are used
 * on every call, the command should never fail when executed on a radix engine.
 */
public class RadixEngineUniqueGenerator implements CommandGenerator {
	private final Hasher hasher = Sha256Hasher.withDefaultSerialization();

	@Override
	public Command nextCommand() {
		ECKeyPair keyPair = ECKeyPair.generateNew();
		RadixAddress address = new RadixAddress((byte) 1, keyPair.getPublicKey());

		RRI rri = RRI.of(address, "test");
		RRIParticle rriParticle = new RRIParticle(rri, 0);
		UniqueParticle uniqueParticle = new UniqueParticle("test", address, 1);
		ParticleGroup particleGroup = ParticleGroup.builder()
				.addParticle(rriParticle, Spin.DOWN)
				.addParticle(uniqueParticle, Spin.UP)
				.build();
		Atom atom = new Atom();
		atom.addParticleGroup(particleGroup);
		final Command command;
		try {
			atom.sign(keyPair, hasher);
			ClientAtom clientAtom = ClientAtom.convertFromApiAtom(atom, hasher);
			final byte[] payload = DefaultSerialization.getInstance().toDson(clientAtom, DsonOutput.Output.ALL);
			command = new Command(payload);
		} catch (AtomAlreadySignedException e) {
			throw new IllegalStateException("Should not get here.", e);
		}
		return command;
	}
}
