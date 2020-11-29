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

package com.radixdlt.consensus.liveness;

import com.google.inject.Inject;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.environment.EventDispatcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

/**
 * This class is responsible for keeping track of current consensus view state.
 * It sends an internal ViewUpdate message on a transition to next view.
 */
public class PacemakerState implements PacemakerReducer {
	private static final Logger log = LogManager.getLogger();

	private final EventDispatcher<ViewUpdate> viewUpdateSender;
	private final ProposerElection proposerElection;

	private View currentView;
	private HighQC highQC;

	@Inject
	public PacemakerState(
		ViewUpdate viewUpdate,
		ProposerElection proposerElection,
		EventDispatcher<ViewUpdate> viewUpdateSender
	) {
		this.proposerElection = Objects.requireNonNull(proposerElection);
		this.viewUpdateSender = Objects.requireNonNull(viewUpdateSender);
		this.highQC = viewUpdate.getHighQC();
		this.currentView = viewUpdate.getCurrentView();
	}

	@Override
	public void processQC(HighQC highQC) {
		log.trace("QuorumCertificate: {}", highQC);

		final View view = highQC.highestQC().getView();
		if (view.gte(this.currentView)) {
			this.highQC = highQC;
			this.updateView(view.next());
		} else {
			log.trace("Ignoring QC for view {}: current view is {}", view, this.currentView);
		}
	}

	@Override
	public void updateView(View nextView) {
		if (nextView.lte(this.currentView)) {
			return;
		}

		final BFTNode leader = this.proposerElection.getProposer(nextView);
		final BFTNode nextLeader = this.proposerElection.getProposer(nextView.next());
		this.currentView = nextView;
		viewUpdateSender.dispatch(
			ViewUpdate.create(
				this.currentView,
				this.highQC,
				leader,
				nextLeader
			)
		);
	}
}
