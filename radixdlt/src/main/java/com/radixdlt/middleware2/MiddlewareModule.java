/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.middleware2;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.message.MessageParticleConstraintScrypt;
import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atommodel.unique.UniqueParticleConstraintScrypt;
import com.radixdlt.atommodel.validators.ValidatorConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.Result;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.sync.StateSyncNetwork;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.Hash;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.middleware2.ClientAtom.LedgerAtomConversionException;
import com.radixdlt.middleware2.converters.AtomToBinaryConverter;
import com.radixdlt.middleware2.network.MessageCentralLedgerSync;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.middleware2.store.CommittedAtomsStore.AtomIndexer;
import com.radixdlt.middleware2.store.EngineAtomIndices;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.EngineStore;
import com.radixdlt.universe.Universe;
import java.util.Set;
import java.util.function.Consumer;
import org.radix.time.Time;

import java.util.function.UnaryOperator;

public class MiddlewareModule extends AbstractModule {
	private static final Hash DEFAULT_FEE_TARGET = new Hash("0000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");

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
		os.load(new TokensConstraintScrypt());
		os.load(new UniqueParticleConstraintScrypt());
		os.load(new MessageParticleConstraintScrypt());
		os.load(new ValidatorConstraintScrypt());
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
	private StateSyncNetwork stateSyncNetwork(
		Universe universe,
		MessageCentral messageCentral
	) {
		return new MessageCentralLedgerSync(
			universe,
			messageCentral
		);
	}

	@Provides
	@Singleton
	private CommittedAtom genesisAtom(Universe universe) throws LedgerAtomConversionException {
		final ClientAtom genesisAtom = ClientAtom.convertFromApiAtom(universe.getGenesis().get(0));
		final VertexMetadata vertexMetadata = VertexMetadata.ofGenesisAncestor();
		return new CommittedAtom(genesisAtom, vertexMetadata);
	}


	@Provides
	@Singleton
	private EngineStore<LedgerAtom> engineStore(CommittedAtomsStore committedAtomsStore) {
		return new EngineStore<LedgerAtom>() {
			@Override
			public void getAtomContaining(Particle particle, boolean b, Consumer<LedgerAtom> consumer) {
				committedAtomsStore.getAtomContaining(particle, b, consumer::accept);
			}

			@Override
			public void storeAtom(LedgerAtom ledgerAtom) {
				if (!(ledgerAtom instanceof CommittedAtom)) {
					throw new IllegalStateException("Should not be storing atoms which aren't committed");
				}

				CommittedAtom committedAtom = (CommittedAtom) ledgerAtom;
				committedAtomsStore.storeAtom(committedAtom);
			}

			@Override
			public void deleteAtom(AID aid) {
				committedAtomsStore.deleteAtom(aid);
			}

			@Override
			public boolean supports(Set<EUID> set) {
				return committedAtomsStore.supports(set);
			}

			@Override
			public Spin getSpin(Particle particle) {
				return committedAtomsStore.getSpin(particle);
			}
		};
	}

	@Provides
	@Singleton
	private AtomIndexer buildAtomIndexer(Serialization serialization) {
		return atom -> EngineAtomIndices.from(atom, serialization);
	}

	@Provides
	@Singleton
	private RadixEngine<LedgerAtom> getRadixEngine(
		ConstraintMachine constraintMachine,
		UnaryOperator<CMStore> virtualStoreLayer,
		EngineStore<LedgerAtom> engineStore,
		RuntimeProperties properties,
		Universe universe
	) {
		final boolean skipAtomFeeCheck = properties.get("debug.nopow", false);
		final PowFeeComputer powFeeComputer = new PowFeeComputer(() -> universe);
		final LedgerAtomChecker ledgerAtomChecker =
			new LedgerAtomChecker(
				() -> universe,
				Time::currentTimestamp,
				powFeeComputer,
				DEFAULT_FEE_TARGET,
				skipAtomFeeCheck,
				Time.MAXIMUM_DRIFT
			);

		return new RadixEngine<>(
			constraintMachine,
			virtualStoreLayer,
			engineStore,
			ledgerAtomChecker
		);
	}

	@Override
	protected void configure() {
		bind(new TypeLiteral<EngineStore<CommittedAtom>>() { }).to(CommittedAtomsStore.class).in(Scopes.SINGLETON);
		bind(AtomToBinaryConverter.class).toInstance(new AtomToBinaryConverter(DefaultSerialization.getInstance()));
	}
}
