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

package com.radixdlt.consensus;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.mempool.Mempool;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages creation of EventCoordinators given a ValidatorSet
 */
public class EpochManager {
	private static final Logger log = LogManager.getLogger("EM");

	private final ProposalGenerator proposalGenerator;
	private final Mempool mempool;
	private final EventCoordinatorNetworkSender networkSender;
	private final SafetyRules safetyRules;
	private final Pacemaker pacemaker;
	private final VertexStore vertexStore;
	private final PendingVotes pendingVotes;
	private final ProposerElectionFactory proposerElectionFactory;
	private final ECKeyPair selfKey;
	private final SystemCounters counters;

	@Inject
	public EpochManager(
		ProposalGenerator proposalGenerator,
		Mempool mempool,
		EventCoordinatorNetworkSender networkSender,
		SafetyRules safetyRules,
		Pacemaker pacemaker,
		VertexStore vertexStore,
		PendingVotes pendingVotes,
		ProposerElectionFactory proposerElectionFactory,
		@Named("self") ECKeyPair selfKey,
		SystemCounters counters
	) {
		this.proposalGenerator = Objects.requireNonNull(proposalGenerator);
		this.mempool = Objects.requireNonNull(mempool);
		this.networkSender = Objects.requireNonNull(networkSender);
		this.safetyRules = Objects.requireNonNull(safetyRules);
		this.pacemaker = Objects.requireNonNull(pacemaker);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.pendingVotes = Objects.requireNonNull(pendingVotes);
		this.proposerElectionFactory = Objects.requireNonNull(proposerElectionFactory);
		this.selfKey = Objects.requireNonNull(selfKey);
		this.counters = Objects.requireNonNull(counters);
	}

	public EventCoordinator start() {
		return new EmptyEventCoordinator();
	}

	public EventCoordinator nextEpoch(ValidatorSet validatorSet) {

		ProposerElection proposerElection = proposerElectionFactory.create(validatorSet);
		log.info("NEXT_EPOCH: ProposerElection: {}", proposerElection);

		return new ValidatingEventCoordinator(
			this.proposalGenerator,
			this.mempool,
			this.networkSender,
			this.safetyRules,
			this.pacemaker,
			this.vertexStore,
			this.pendingVotes,
			proposerElection,
			this.selfKey,
			validatorSet,
			counters
		);
	}
}
