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

import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.ViewUpdate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

/**
 * This class is responsible for keeping track of current consensus view state.
 * It sends an internal ViewUpdate message on a transition to next view.
 */
public class PacemakerState {

    public interface ViewUpdateSender {
        void sendViewUpdate(ViewUpdate viewUpdate);
    }

    private static final Logger log = LogManager.getLogger();

    private final ViewUpdateSender viewUpdateSender;

    private View currentView = View.genesis();
    // Highest view in which a commit happened
    private View highestCommitView = View.genesis();

    public PacemakerState(ViewUpdateSender viewUpdateSender) {
        this.viewUpdateSender = Objects.requireNonNull(viewUpdateSender);
    }

    /**
     * Signifies to the pacemaker that a quorum has agreed that a view has
     * been completed.
     *
     * @param highQC the sync info for the view
     */
    public void processQC(HighQC highQC) {
        log.trace("Processing HighQC: {}", highQC);

        final View view = highQC.getHighestView();
        if (view.gte(this.currentView)) {
            this.highestCommitView = highQC.highestCommittedQC().getView();
            this.updateView(view.next());
        } else {
            log.trace("Ignoring QC for view {}: current view is {}", view, this.currentView);
        }
    }

    private void updateView(View nextView) {
        if (nextView.lte(this.currentView)) {
            return;
        }
        this.currentView = nextView;
        viewUpdateSender.sendViewUpdate(new ViewUpdate(
                this.currentView,
                this.highestCommitView
        ));
    }
}
