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
import com.radixdlt.atommodel.system.SystemConstraintScrypt;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atommodel.unique.UniqueParticleConstraintScrypt;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.ValidatorConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.Result;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.engine.AtomChecker;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.middleware2.store.RadixEngineAtomicCommitManager;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.MaxValidators;
import com.radixdlt.statecomputer.MinValidators;
import com.radixdlt.statecomputer.RadixEngineStakeComputer;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.statecomputer.RadixEngineValidatorsComputer;
import com.radixdlt.statecomputer.ValidatorSetBuilder;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.EngineStore;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import java.util.function.UnaryOperator;

/**
 * Module which manages execution of commands
 */
public class RadixEngineModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(StateComputer.class).to(RadixEngineStateComputer.class);
	}

	@Provides
	@Singleton
	private RadixEngineStateComputer radixEngineStateComputer(
		Serialization serialization,
		RadixEngine<LedgerAtom> radixEngine,
		RadixEngineAtomicCommitManager atomicCommitManager,
		@EpochCeilingView View epochCeilingView,
		ValidatorSetBuilder validatorSetBuilder,
		Hasher hasher
	) {
		return RadixEngineStateComputer.create(
			serialization,
			radixEngine,
			atomicCommitManager,
			epochCeilingView,
			validatorSetBuilder,
			hasher
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
		os.load(new MessageParticleConstraintScrypt());
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
		RadixEngineValidatorsComputer validatorsComputer,
		RadixEngineStakeComputer stakeComputer
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

		radixEngine.addStateComputer(
			RegisteredValidatorParticle.class,
			RadixEngineValidatorsComputer.class,
			validatorsComputer,
			(computer, p) -> computer.addValidator(p.getAddress().getPublicKey()),
			(computer, p) -> computer.removeValidator(p.getAddress().getPublicKey())
		);
		radixEngine.addStateComputer(
			StakedTokensParticle.class,
			RadixEngineStakeComputer.class,
			stakeComputer,
			(computer, p) -> computer.addStake(p.getDelegateAddress().getPublicKey(), p.getTokDefRef(), p.getAmount()),
			(computer, p) -> computer.removeStake(p.getDelegateAddress().getPublicKey(), p.getTokDefRef(), p.getAmount())
		);

		// TODO: should use different mechanism for constructing system atoms but this is good enough for now
		radixEngine.addStateComputer(
			SystemParticle.class,
			SystemParticle.class,
			new SystemParticle(0, 0, 0),
			(prev, p) -> p,
			(prev, p) -> prev
		);

		return radixEngine;
	}
}
