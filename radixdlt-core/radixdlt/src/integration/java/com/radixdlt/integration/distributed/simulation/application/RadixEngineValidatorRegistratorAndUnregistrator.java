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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.serialization.DsonOutput.Output;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Randomly registers and unregisters nodes as validators
 */
public class RadixEngineValidatorRegistratorAndUnregistrator implements CommandGenerator {

	private final ImmutableMap<ECKeyPair, AtomicLong> nodeNonces;
	private final PublishSubject<BFTNode> validatorRegistrationSubmissions;
	private final Hasher hasher;

	private final Random random = new Random();

	public RadixEngineValidatorRegistratorAndUnregistrator(List<ECKeyPair> nodes, Hasher hasher) {
		this.nodeNonces = nodes.stream().collect(ImmutableMap.toImmutableMap(n -> n, n -> new AtomicLong(0L)));
		this.validatorRegistrationSubmissions = PublishSubject.create();
		this.hasher = hasher;
	}

	@Override
	public Command nextCommand() {
		byte magic = 1;
		ImmutableList<ECKeyPair> nodes = nodeNonces.keySet().asList();
		ECKeyPair keyPair = nodes.get(random.nextInt(nodes.size()));
		RadixAddress address = new RadixAddress(magic, keyPair.getPublicKey());

		AtomicLong nonce = nodeNonces.get(keyPair);
		long curNonce = nonce.getAndIncrement();
		boolean isRegistered = curNonce % 2 == 1;
		RegisteredValidatorParticle registeredValidatorParticle = new RegisteredValidatorParticle(
			address, ImmutableSet.of(), isRegistered ? curNonce : curNonce + 1
		);
		UnregisteredValidatorParticle unregisteredValidatorParticle = new UnregisteredValidatorParticle(
			address, isRegistered ? curNonce + 1 : curNonce
		);

		ParticleGroup particleGroup = ParticleGroup.builder()
			.addParticle(unregisteredValidatorParticle, isRegistered ? Spin.UP : Spin.DOWN)
			.addParticle(registeredValidatorParticle, isRegistered ? Spin.DOWN : Spin.UP)
			.build();
		Atom atom = new Atom();
		atom.addParticleGroup(particleGroup);
		atom.sign(keyPair, hasher);
		ClientAtom clientAtom = ClientAtom.convertFromApiAtom(atom, hasher);
		final byte[] payload = DefaultSerialization.getInstance().toDson(clientAtom, Output.ALL);
		Command command = new Command(payload);

		BFTNode node = BFTNode.create(keyPair.getPublicKey());
		validatorRegistrationSubmissions.onNext(node);
		return command;
	}

	public Observable<BFTNode> validatorRegistrationSubmissions() {
		return validatorRegistrationSubmissions;
	}
}
