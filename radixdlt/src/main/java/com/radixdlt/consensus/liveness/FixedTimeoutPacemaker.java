/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.liveness;

import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.validators.ValidationState;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.utils.Longs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A pacemaker which utilizes a fixed timeout (aka requires a synchronous network).
 */
public final class FixedTimeoutPacemaker implements Pacemaker {

	/**
	 * Callback to the issuer of timeout events
	 */
	public interface TimeoutSender {

		/**
		 * Schedules a timeout event for a particular view
		 * @param view the view to schedule a timeout for
		 * @param milliseconds the milliseconds to wait before timeout occurs
		 */
		void scheduleTimeout(View view, long milliseconds);
	}

	private static final Logger log = LogManager.getLogger("PM");

	private final long timeoutMilliseconds;
	private final TimeoutSender timeoutSender;

	private final Map<View, ValidationState> pendingNewViews = new HashMap<>();
	private View currentView = View.of(0L);
	private View lastSyncView = View.of(0L);

	public FixedTimeoutPacemaker(long timeoutMilliseconds, TimeoutSender timeoutSender) {
		if (timeoutMilliseconds <= 0) {
			throw new IllegalArgumentException("timeoutMilliseconds must be > 0 but was " + timeoutMilliseconds);
		}
		this.timeoutMilliseconds = timeoutMilliseconds;
		this.timeoutSender = Objects.requireNonNull(timeoutSender);
	}

	@Override
	public View getCurrentView() {
		return currentView;
	}

	@Override
	public Optional<View> processLocalTimeout(View view) {
		if (!view.equals(this.currentView)) {
			return Optional.empty();
		}

		this.currentView = currentView.next();

		timeoutSender.scheduleTimeout(this.currentView, timeoutMilliseconds);

		return Optional.of(this.currentView);
	}

	// TODO: Move this into Event Coordinator
	@Override
	public Optional<View> processNewView(NewView newView, ValidatorSet validatorSet) {
		if (newView.getView().compareTo(this.lastSyncView) <= 0) {
			return Optional.empty();
		}

		// If QC of new-view was from previous view, then we are guaranteed to have the highest QC for this view
		// and can proceed
		final View qcView = newView.getQC().getView();
		final boolean highestQC = !qcView.isGenesis() && qcView.next().equals(this.currentView);

		if (!highestQC) {
			Hash newViewId = Hash.of(Longs.toByteArray(newView.getView().number()));
			ECDSASignature signature = newView.getSignature().orElseThrow(() -> new IllegalArgumentException("new-view is missing signature"));
			ECPublicKey newViewAuthor = newView.getAuthor();
			if (newViewAuthor.verify(newViewId, signature)) {
				// Process if signature valid
				ValidationState validationState = pendingNewViews.computeIfAbsent(newView.getView(), k -> validatorSet.newValidationState());

				// check if we have gotten enough new-views to proceed
				if (!validationState.addSignature(newViewAuthor, signature) || !validationState.complete()) {
					// if we haven't got enough new-views yet, do nothing
					return Optional.empty();
				}
			} else {
				// Signature not valid, just ignore
				return Optional.empty();
			}
		}

		if (newView.getView().equals(this.currentView)) {
			pendingNewViews.remove(newView.getView());
			this.lastSyncView = this.currentView;
			return Optional.of(this.currentView);
		} else {
			log.trace("Ignoring New View Quorum: {} Current is: {}", newView, this.currentView);
			return Optional.empty();
		}
	}

	@Override
	public Optional<View> processQC(View view) {
		// check if a new view can be started
		View newView = view.next();
		if (newView.compareTo(currentView) > 0) {
			// start new view
			this.currentView = newView;

			timeoutSender.scheduleTimeout(this.currentView, timeoutMilliseconds);

			return Optional.of(this.currentView);
		} else {
			return Optional.empty();
		}
	}
}
