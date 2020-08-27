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
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.ClientAtom.LedgerAtomConversionException;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializationException;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.List;
import java.util.Objects;

/**
 * Goes through a list of nodes and registers all of them over some
 * amount of time.
 */
public final class RadixEngineValidatorRegistrator extends LocalMempoolPeriodicSubmittor {
	private final List<ECKeyPair> nodes;
	private final PublishSubject<BFTNode> validatorRegistrationSubmissions;
	private int current = 0;

	public RadixEngineValidatorRegistrator(List<ECKeyPair> nodes) {
		this.nodes = Objects.requireNonNull(nodes);
		this.validatorRegistrationSubmissions = PublishSubject.create();
	}

	@Override
	Command nextCommand() {
		byte magic = 1;
		ECKeyPair keyPair = nodes.get(current % nodes.size());
		current++;
		RadixAddress address = new RadixAddress(magic, keyPair.getPublicKey());
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
		final Command command;
		try {
			atom.sign(keyPair);
			ClientAtom clientAtom = ClientAtom.convertFromApiAtom(atom);
			final byte[] payload = DefaultSerialization.getInstance().toDson(clientAtom, Output.ALL);
			command = new Command(payload);
		} catch (CryptoException | LedgerAtomConversionException | SerializationException e) {
			throw new RuntimeException();
		}

		BFTNode node = BFTNode.create(keyPair.getPublicKey());
		System.out.println("Registering node " + node);
		validatorRegistrationSubmissions.onNext(node);
		return command;
	}

	public Observable<BFTNode> validatorRegistrationSubmissions() {
		return validatorRegistrationSubmissions;
	}
}
