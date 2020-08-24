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
import com.radixdlt.atommodel.message.MessageParticleConstraintScrypt;
import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atommodel.unique.UniqueParticleConstraintScrypt;
import com.radixdlt.atommodel.validators.ValidatorConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.Result;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.crypto.Hash;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.statecomputer.CommittedCommandsReader;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.LedgerAtomChecker;
import com.radixdlt.middleware2.PowFeeComputer;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomSender;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.EngineStore;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.universe.Universe;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Module which manages execution of commands
 */
public class RadixEngineModule extends AbstractModule {
	private static final Hash DEFAULT_FEE_TARGET = new Hash("0000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
	private final long viewsPerEpoch;

	public RadixEngineModule(long viewsPerEpoch) {
		this.viewsPerEpoch = viewsPerEpoch;
	}

	@Override
	protected void configure() {
		bind(StateComputer.class).to(RadixEngineStateComputer.class);
	}

	@Provides
	@Singleton
	private Function<Long, BFTValidatorSet> validatorMapping(
		VertexMetadata genesisVertexMetadata
	) {
		/*
		return (epoch, validators) -> {
				Builder<BFTValidator> validatorSetBuilder = ImmutableList.builder();
				Random random = new Random(epoch);
				List<Integer> indices = IntStream.range(0, validators.size()).boxed().collect(Collectors.toList());
				// Temporary mechanism to get some deterministic random set of validators
				for (long i = 0; i < epoch; i++) {
					random.nextInt(validators.size());
				}
				int randInt = random.nextInt(validators.size());
				int validatorSetSize = randInt + 1;

				for (int i = 0; i < validatorSetSize; i++) {
					int index = indices.remove(random.nextInt(indices.size()));
					BFTValidator validator = validators.get(index);
					validatorSetBuilder.add(validator);
				}

				ImmutableList<BFTValidator> validatorList = validatorSetBuilder.build();

				return BFTValidatorSet.from(validatorList);
		}
		*/

		return epoch -> genesisVertexMetadata.getValidatorSet()
			.orElseThrow(() -> new IllegalStateException("genesis has no validator set!"));
	}

	@Provides
	@Singleton
	private RadixEngineStateComputer radixEngineStateComputer(
		Serialization serialization,
		RadixEngine<LedgerAtom> radixEngine,
		Function<Long, BFTValidatorSet> validatorSetMapping,
		CommittedCommandsReader committedCommandsReader,
		CommittedAtomSender committedAtomSender
	) {
		return new RadixEngineStateComputer(
			serialization,
			radixEngine,
			validatorSetMapping,
			View.of(viewsPerEpoch), committedCommandsReader,
			committedAtomSender
		);
	}

	@Provides
	@Singleton
	private CMAtomOS buildCMAtomOS(Universe universe) {
		final CMAtomOS os = new CMAtomOS(addr -> {
			final int universeMagic = universe.getMagic() & 0xff;
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
		ConstraintMachine constraintMachine,
		UnaryOperator<CMStore> virtualStoreLayer,
		EngineStore<LedgerAtom> engineStore,
		Universe universe
	) {
		final PowFeeComputer powFeeComputer = new PowFeeComputer(() -> universe);
		final LedgerAtomChecker ledgerAtomChecker =
			new LedgerAtomChecker(
				() -> universe,
				powFeeComputer,
				DEFAULT_FEE_TARGET,
				false
			);

		return new RadixEngine<>(
			constraintMachine,
			virtualStoreLayer,
			engineStore,
			ledgerAtomChecker
		);
	}
}
