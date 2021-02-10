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

package com.radixdlt.statecomputer;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.radixdlt.atommodel.system.SystemConstraintScrypt;
import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atommodel.unique.UniqueParticleConstraintScrypt;
import com.radixdlt.atommodel.validators.ValidatorConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.Result;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.engine.AtomChecker;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.StateReducer;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.middleware2.store.RadixEngineAtomicCommitManager;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.EngineStore;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;

import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Module which manages execution of commands
 */
public class RadixEngineModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(StateComputer.class).to(RadixEngineStateComputer.class);
		Multibinder.newSetBinder(binder(), new TypeLiteral<StateReducer<?, ?>>() { });
	}

	@Provides
	@Singleton
	private RadixEngineStateComputer radixEngineStateComputer(
		Serialization serialization,
		RadixEngine<LedgerAtom> radixEngine,
		Mempool mempool,
		RadixEngineAtomicCommitManager atomicCommitManager,
		@EpochCeilingView View epochCeilingView,
		ValidatorSetBuilder validatorSetBuilder,
		Hasher hasher,
		EventDispatcher<MempoolAddFailure> mempoolAddFailureEventDispatcher
	) {
		return RadixEngineStateComputer.create(
			serialization,
			radixEngine,
			mempool,
			atomicCommitManager,
			epochCeilingView,
			validatorSetBuilder,
			hasher,
			mempoolAddFailureEventDispatcher
		);
	}

	@Provides
	private ValidatorSetBuilder validatorSetBuilder(
		@MinValidators int minValidators,
		@MaxValidators int maxValidators
	) {
		return ValidatorSetBuilder.create(minValidators, maxValidators);
	}

	@Provides
	@Singleton
	private CMAtomOS buildCMAtomOS(
		@Named("magic") int magic
	) {
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
		os.load(new SystemConstraintScrypt());
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
		ConstraintMachine constraintMachine,
		UnaryOperator<CMStore> virtualStoreLayer,
		EngineStore<LedgerAtom> engineStore,
		AtomChecker<LedgerAtom> ledgerAtomChecker,
		RegisteredValidators initialRegisteredValidators,
		Stakes initialStakes,
		Set<StateReducer<?, ?>> stateReducers,
		@NativeToken RRI stakeToken // FIXME: ability to use a different token for fees and staking
	) {
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

		radixEngine.addStateReducer(new ValidatorsReducer(() -> initialRegisteredValidators));
		radixEngine.addStateReducer(new StakesReducer(stakeToken, () -> initialStakes));

		// TODO: should use different mechanism for constructing system atoms but this is good enough for now
		radixEngine.addStateReducer(new LastSystemParticleReducer());

		stateReducers.forEach(radixEngine::addStateReducer);

		return radixEngine;
	}
}
