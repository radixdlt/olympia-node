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
public class PacemakerState implements PacemakerUpdater {
    private static final Logger log = LogManager.getLogger();

    private final EventDispatcher<ViewUpdate> viewUpdateSender;

    private View currentView = View.genesis();
    // Highest view in which a commit happened
    private View highestCommitView = View.genesis();
    // Last view that we had any kind of quorum for
    private View lastQuorumView = View.genesis();

    @Inject
    public PacemakerState(EventDispatcher<ViewUpdate> viewUpdateSender) {
        this.viewUpdateSender = Objects.requireNonNull(viewUpdateSender);
    }

    /**
     * Signifies to the pacemaker that a quorum has agreed that a view has
     * been completed.
     *
     * @param highQC the sync info for the view
     * @return {@code true} if proceeded to a new view
     */
    @Override
    public boolean processQC(HighQC highQC) {
        log.trace("QuorumCertificate: {}", highQC);

        final View view = highQC.highestQC().getView();
        if (view.gte(this.currentView)) {
            this.lastQuorumView = view;
            this.highestCommitView = highQC.highestCommittedQC().getView();
            this.updateView(view.next());
            return true;
        }
        log.trace("Ignoring QC for view {}: current view is {}", view, this.currentView);
        return false;
    }

    @Override
    public void updateView(View nextView) {
        if (nextView.lte(this.currentView)) {
            return;
        }
        this.currentView = nextView;
        viewUpdateSender.dispatch(new ViewUpdate(
                this.currentView,
                this.lastQuorumView,
                this.highestCommitView
        ));
    }
}
