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

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.radixdlt.consensus.BasicEpochRx;
import com.radixdlt.consensus.DefaultHasher;
import com.radixdlt.consensus.EpochRx;
import com.radixdlt.consensus.ProposerElectionFactory;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.VertexStore;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.PacemakerImpl;
import com.radixdlt.consensus.liveness.PacemakerRx;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.tempo.Scheduler;
import com.radixdlt.consensus.tempo.SingleThreadedScheduler;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.universe.Universe;

import com.radixdlt.utils.UInt256;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CerberusModule extends AbstractModule {
	private static final Logger log = LogManager.getLogger("Startup");

	private final RuntimeProperties runtimeProperties;

	public CerberusModule(RuntimeProperties runtimeProperties) {
		this.runtimeProperties = Objects.requireNonNull(runtimeProperties);
	}

	@Override
	protected void configure() {
		// dependencies
		bind(Scheduler.class).toProvider(SingleThreadedScheduler::new);
		bind(PacemakerRx.class).to(PacemakerImpl.class);
		bind(Pacemaker.class).to(PacemakerImpl.class);
		bind(SafetyRules.class).in(Scopes.SINGLETON);
		bind(Hasher.class).to(DefaultHasher.class);
	}

	@Provides
	@Singleton
	private ProposerElectionFactory proposerElectionFactory() {
		final int cacheSize = runtimeProperties.get("consensus.weighted_rotating_leaders.cache_size", 10);
		return validatorSet -> new WeightedRotatingLeaders(validatorSet, Comparator.comparing(v -> v.nodeKey().euid()), cacheSize);
	}

	@Provides
	@Singleton
	private EpochRx epochRx(
		@Named("self") ECKeyPair selfKey,
		AddressBook addressBook
	) {
		final int fixedNodeCount = runtimeProperties.get("consensus.fixed_node_count", 1);
		return new BasicEpochRx(selfKey.getPublicKey(), addressBook, fixedNodeCount);
	}

	@Provides
	@Singleton
	private ValidatorSet validatorSet(
		@Named("self") ECKeyPair selfKey
	) {
		return ValidatorSet.from(Collections.singleton(Validator.from(selfKey.getPublicKey(), UInt256.ONE)));
	}

	@Provides
	@Singleton
	private PacemakerImpl pacemaker() {
		final int pacemakerTimeout = runtimeProperties.get("consensus.pacemaker_timeout_millis", 5000);
		return new PacemakerImpl(pacemakerTimeout, Executors.newSingleThreadScheduledExecutor());
	}

	@Provides
	@Singleton
	private VertexStore getVertexStore(
		Universe universe,
		RadixEngine radixEngine,
		SystemCounters counters
	) {
		if (universe.getGenesis().size() != 1) {
			throw new IllegalStateException("Can only support one genesis atom.");
		}

		final Vertex genesisVertex = Vertex.createGenesis(universe.getGenesis().get(0));
		final VertexMetadata genesisMetadata = new VertexMetadata(View.genesis(), genesisVertex.getId());
		final VoteData voteData = new VoteData(genesisMetadata, null);
		final QuorumCertificate rootQC = new QuorumCertificate(voteData, new ECDSASignatures());

		log.info("Genesis Vertex Id: {}", genesisVertex.getId());
		return new VertexStore(genesisVertex, rootQC, radixEngine, counters);
	}
}
