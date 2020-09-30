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

package com.radixdlt.consensus.bft;

import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.bft.BFTEventReducer.ProceedToViewSender;
import com.radixdlt.consensus.liveness.ProposerElection;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class SignedNewViewToLeaderSender implements ProceedToViewSender {
	private static final Logger log = LogManager.getLogger();

	public interface BFTNewViewSender {
		/**
		 * Send a new-view message to a given validator
		 * @param newView the new-view message
		 * @param nextLeader the validator the message gets sent to
		 */
		void sendNewView(NewView newView, BFTNode nextLeader);
	}

	private final NewViewSigner newViewSigner;
	private final ProposerElection proposerElection;
	private final VertexStore vertexStore;
	private final BFTNewViewSender sender;

	public SignedNewViewToLeaderSender(
		NewViewSigner newViewSigner,
		ProposerElection proposerElection,
		VertexStore vertexStore,
		BFTNewViewSender sender
	) {
		this.newViewSigner = Objects.requireNonNull(newViewSigner);
		this.proposerElection = Objects.requireNonNull(proposerElection);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.sender = Objects.requireNonNull(sender);
	}

	@Override
	public void sendProceedToNextView(View nextView) {
		NewView newView = newViewSigner.signNewView(nextView, this.vertexStore.getHighestQC(), this.vertexStore.getHighestCommittedQC());
		BFTNode nextLeader = this.proposerElection.getProposer(nextView);
		log.trace("Sending NEW_VIEW to {}: {}", () -> nextLeader, () ->  newView);
		this.sender.sendNewView(newView, nextLeader);
	}
}
