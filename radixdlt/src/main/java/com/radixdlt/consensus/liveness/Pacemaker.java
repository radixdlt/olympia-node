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

package com.radixdlt.consensus.liveness;

import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.bft.View;

import com.radixdlt.consensus.bft.BFTValidatorSet;
import java.util.Optional;

/**
 * Manages the pacemaker state machine.
 */
public interface Pacemaker extends PacemakerState {
	/**
	 * Signifies to the pacemaker that a timeout for a given view is processed
	 * @param view the view to timeout
	 */
	void processLocalTimeout(View view);

	/**
	 * Signifies to the pacemaker that a quorum has agreed that a view has been completed
	 * @param syncInfo the sync info
	 */
	void processQC(HighQC syncInfo);

	// FIXME: Temporary
	void processNextView(View view);

	/**
	 * Adds a new view message to the pacemaker state
	 * @param newView the new view message
	 * @param validatorSet validator set which forms the quorum
	 * @return optional with view, if the pacemaker gains a quorum of new views
	 */
	Optional<View> processNewView(NewView newView, BFTValidatorSet validatorSet);
}
