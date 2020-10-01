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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.radixdlt.atommodel.message.MessageParticleConstraintScrypt;
import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atommodel.unique.UniqueParticleConstraintScrypt;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.ValidatorConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.Result;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.AtomChecker;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.statecomputer.CommittedCommandsReader;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomSender;
import com.radixdlt.statecomputer.RadixEngineValidatorSetBuilder;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.EngineStore;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.sync.CommittedReader;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Module which manages execution of commands
 */
public class RadixEngineModule extends AbstractModule {
	private final View epochHighView;

	public RadixEngineModule(View epochHighView) {
		this.epochHighView = epochHighView;
	}

	@Override
	protected void configure() {
		bind(StateComputer.class).to(RadixEngineStateComputer.class);
		bind(CommittedReader.class).to(RadixEngineStateComputer.class);
	}

	@Provides
	@Singleton
	private RadixEngineStateComputer radixEngineStateComputer(
		Serialization serialization,
		RadixEngine<LedgerAtom> radixEngine,
		CommittedCommandsReader committedCommandsReader,
		CommittedAtomSender committedAtomSender
	) {
		return new RadixEngineStateComputer(
			serialization,
			radixEngine,
			epochHighView,
			committedCommandsReader,
			committedAtomSender
		);
	}

	@Provides
	@Singleton
	private CMAtomOS buildCMAtomOS(@Named("magic") int magic) {
		final CMAtomOS os = new CMAtomOS(addr -> {
			final int universeMagic = magic & 0xff;
			if (addr.getMagic() != universeMagic) {
				return Result.error("Address magic " + addr.getMagic() + " does not match universe " + universeMagic);
			}
			return Result.success();
		});
		os.load(new ValidatorConstraintScrypt()); // load before TokensConstraintScrypt due to dependency
		os.load(new TokensConstraintScrypt());
		os.load(new UniqueParticleConstraintScrypt());
		os.load(new MessageParticleConstraintScrypt());
		return os;
	}

	@Provides
	@Singleton
	private ConstraintMachine buildConstraintMachine(CMAtomOS os) {
		return new ConstraintMachine.Builder()
			.setParticleTransitionProcedures(os.buildTransitionProcedures())
			.setParticleStaticCheck(os.buildParticleStaticCheck())
			.build();
	}

	@Provides
	private UnaryOperator<CMStore> buildVirtualLayer(CMAtomOS atomOS) {
		return atomOS.buildVirtualLayer();
	}

	@Provides
	@Singleton
	private RadixEngine<LedgerAtom> getRadixEngine(
		BFTConfiguration initialConfig,
		ConstraintMachine constraintMachine,
		UnaryOperator<CMStore> virtualStoreLayer,
		EngineStore<LedgerAtom> engineStore,
		AtomChecker<LedgerAtom> ledgerAtomChecker
	) {
		final int minValidators = 2; // TODO: Fix pacemaker so can Default 1 so can debug in IDE, possibly from properties at some point

		RadixEngine<LedgerAtom> radixEngine = new RadixEngine<>(
			constraintMachine,
			virtualStoreLayer,
			engineStore,
			ledgerAtomChecker
		);

		// TODO: Convert to something more like the following:
		// RadixEngine
		//   .newStateComputer()
		//   .ofType(RegisteredValidatorParticle.class)
		//   .toWindowedSet(initialValidatorSet, RegisteredValidatorParticle.class, p -> p.getAddress(), 2)
		//   .build();
		Set<ECPublicKey> initialValidatorKeys = initialConfig.getValidatorSet().getValidators().stream()
			.map(v -> v.getNode().getKey())
			.collect(Collectors.toCollection(HashSet::new));
		radixEngine.addStateComputer(
			RegisteredValidatorParticle.class,
			new RadixEngineValidatorSetBuilder(initialValidatorKeys, new AtLeastNValidators(minValidators)),
			(builder, p) -> builder.addValidator(p.getAddress()),
			(builder, p) -> builder.removeValidator(p.getAddress())
		);

		return radixEngine;
	}

	private static final class AtLeastNValidators implements Predicate<Set<ECPublicKey>> {
		private final int n;

		private AtLeastNValidators(int n) {
			if (n < 1) {
				throw new IllegalArgumentException("Minimum number of validators must be at least 1: " + n);
			}
			this.n = n;
		}

		@Override
		public boolean test(Set<ECPublicKey> vset) {
			return vset.size() >= this.n;
		}

		@Override
		public String toString() {
			return String.format("At least %s validators", this.n);
		}
	}
}
