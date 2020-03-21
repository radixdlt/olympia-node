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
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.VertexStore;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.liveness.SingleLeader;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.PacemakerImpl;
import com.radixdlt.consensus.liveness.PacemakerRx;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.QuorumRequirements;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SingleNodeQuorumRequirements;
import com.radixdlt.consensus.tempo.Scheduler;
import com.radixdlt.consensus.tempo.SingleThreadedScheduler;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.universe.Universe;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.util.concurrent.Executors;

public class CerberusModule extends AbstractModule {
	private static final Logger log = Logging.getLogger("Startup");

	@Override
	protected void configure() {
		// dependencies
		bind(QuorumRequirements.class).to(SingleNodeQuorumRequirements.class);
		bind(Scheduler.class).toProvider(SingleThreadedScheduler::new);
		bind(PacemakerRx.class).to(PacemakerImpl.class);
		bind(Pacemaker.class).to(PacemakerImpl.class);
		bind(SafetyRules.class).in(Scopes.SINGLETON);
	}

	@Provides
	@Singleton
	private ProposerElection proposerElection(
		@Named("self") ECKeyPair selfKey
	) {
		return new SingleLeader(selfKey.getUID());
	}

	@Provides
	@Singleton
	private PacemakerImpl pacemaker(QuorumRequirements quorumRequirements) {
		return new PacemakerImpl(quorumRequirements, Executors.newSingleThreadScheduledExecutor());
	}

	@Provides
	@Singleton
	private VertexStore getVertexStore(
		Universe universe,
		RadixEngine radixEngine
	) throws RadixEngineException {
		if (universe.getGenesis().size() != 1) {
			throw new IllegalStateException("Can only support one genesis atom.");
		}

		final Vertex genesisVertex = Vertex.createGenesis(universe.getGenesis().get(0));
		final VertexMetadata genesisMetadata = new VertexMetadata(View.genesis(), genesisVertex.getId(), View.genesis(), genesisVertex.getId());
		final QuorumCertificate rootQC = new QuorumCertificate(genesisMetadata, new ECDSASignatures());

		log.info("Genesis Vertex Id: " + genesisVertex.getId());
		return new VertexStore(genesisVertex, rootQC, radixEngine);
	}
}
