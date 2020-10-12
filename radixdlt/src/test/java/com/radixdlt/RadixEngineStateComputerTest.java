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

package com.radixdlt;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.atommodel.AtomAlreadySignedException;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.ClientAtom.LedgerAtomConversionException;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.RadixEngineStateComputer;

import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.UInt256;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;

public class RadixEngineStateComputerTest {
	@Inject
	private RadixEngineStateComputer sut;

	private Serialization serialization = DefaultSerialization.getInstance();
	private BFTValidatorSet validatorSet;
	private EngineStore<LedgerAtom> engineStore;

	private static final Hasher hasher = Sha256Hasher.withDefaultSerialization();

	private Module getExternalModule() {
		return new AbstractModule() {
			public void configure() {
				bind(Serialization.class).toInstance(serialization);
				bind(BFTValidatorSet.class).toInstance(validatorSet);
				bind(Hasher.class).toInstance(Sha256Hasher.withDefaultSerialization());
				bind(new TypeLiteral<EngineStore<LedgerAtom>>() { }).toInstance(engineStore);
			}

			@Provides
			@Named("magic")
			int magic() {
				return 0;
			}
		};
	}

	@Before
	public void setup() {
		this.validatorSet = BFTValidatorSet.from(Stream.of(
			BFTValidator.from(BFTNode.random(), UInt256.ONE),
			BFTValidator.from(BFTNode.random(), UInt256.ONE)
		));
		this.engineStore = new InMemoryEngineStore<>();
		Guice.createInjector(
			new RadixEngineModule(View.of(10)),
			new NoFeeModule(),
			getExternalModule()
		).injectMembers(this);
	}

	private static Command registerCommand(ECKeyPair keyPair) {
		RadixAddress address = new RadixAddress((byte) 0, keyPair.getPublicKey());
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
			Atom.sign(atom, keyPair, hasher);
			ClientAtom clientAtom = ClientAtom.convertFromApiAtom(atom, hasher);
			final byte[] payload = DefaultSerialization.getInstance().toDson(clientAtom, Output.ALL);
			return new Command(payload);
		} catch (AtomAlreadySignedException | LedgerAtomConversionException e) {
			throw new RuntimeException();
		}
	}

	@Test
	public void executing_non_epoch_high_view_should_return_no_validator_set() {
		// Action
		StateComputerResult result = sut.prepare(ImmutableList.of(), null, View.of(9));

		// Assert
		assertThat(result.getSuccessfulCommands()).isEmpty();
		assertThat(result.getFailedCommands()).isEmpty();
		assertThat(result.getNextValidatorSet()).isEmpty();
	}

	@Test
	public void executing_epoch_high_view_should_return_next_validator_set() {
		// Action
		StateComputerResult result = sut.prepare(ImmutableList.of(), null, View.of(10));

		// Assert
		assertThat(result.getSuccessfulCommands()).isEmpty();
		assertThat(result.getFailedCommands()).isEmpty();
		assertThat(result.getNextValidatorSet()).contains(this.validatorSet);
	}

	@Test
	public void executing_epoch_high_view_with_register_should_return_new_next_validator_set() {
		// Assemble
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command cmd = registerCommand(keyPair);
		BFTNode node = BFTNode.create(keyPair.getPublicKey());

		// Action
		StateComputerResult result = sut.prepare(ImmutableList.of(), cmd, View.of(10));

		// Assert
		assertThat(result.getSuccessfulCommands()).contains(cmd);
		assertThat(result.getFailedCommands()).isEmpty();
		assertThat(result.getNextValidatorSet()).hasValueSatisfying(s -> {
			assertThat(s.getValidators()).hasSize(3);
			assertThat(s.getValidators()).extracting(BFTValidator::getNode).contains(node);
		});
	}

	@Test
	public void executing_epoch_high_view_with_previous_registered_should_return_new_next_validator_set() {
		// Assemble
		ECKeyPair keyPair = ECKeyPair.generateNew();
		Command cmd = registerCommand(keyPair);
		BFTNode node = BFTNode.create(keyPair.getPublicKey());

		// Action
		StateComputerResult result = sut.prepare(ImmutableList.of(cmd), null, View.of(10));

		// Assert
		assertThat(result.getSuccessfulCommands()).isEmpty();
		assertThat(result.getFailedCommands()).isEmpty();
		assertThat(result.getNextValidatorSet()).hasValueSatisfying(s -> {
			assertThat(s.getValidators()).hasSize(3);
			assertThat(s.getValidators()).extracting(BFTValidator::getNode).contains(node);
		});
	}
}