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

import com.radixdlt.consensus.bft.BFTNode;
import java.util.Map;
import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.ValidationState;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECDSASignature;

/**
 * Manages pending {@link NewView} items.
 * <p>
 * This class is NOT thread-safe.
 * <p>
 * This class is security critical (signature checks, validator set membership checks).
 */
@NotThreadSafe
@SecurityCritical({ SecurityKind.SIG_VERIFY, SecurityKind.GENERAL })
public final class PendingNewViews {
	private static final Logger log = LogManager.getLogger();

	private final Map<View, ValidationState> newViewState = Maps.newHashMap();
	private final Map<BFTNode, View> previousNewView = Maps.newHashMap();

	/**
	 * Inserts a {@link NewView}, attempting to form a quorum certificate.
	 * <p>
	 * A QC will only be formed if permitted by the {@link BFTValidatorSet}.
	 *
	 * @param newView The {@link NewView} to be inserted
	 * @param validatorSet The validator set to form a quorum with
	 * @return The generated QC, if any
	 */
	public Optional<View> insertNewView(NewView newView, BFTValidatorSet validatorSet) {
		final BFTNode node = newView.getAuthor();
		if (!validatorSet.containsNode(node)) {
			// Not a valid validator
			log.info("Ignoring new view from invalid author {}", node::getSimpleName);
			return Optional.empty();
		}

		final ECDSASignature signature = newView.getSignature().orElseThrow(() -> new IllegalArgumentException("new-view is missing signature"));
		final View thisView = newView.getView();
		if (!replacePreviousNewView(node, thisView)) {
			return Optional.empty();
		}

		ValidationState validationState = this.newViewState.computeIfAbsent(thisView, k -> validatorSet.newValidationState());

		// check if we have gotten enough new-views to proceed
		// NewView timestamps here are not required, so we use 0L below
		if (!(validationState.addSignature(node, 0L, signature) && validationState.complete())) {
			return Optional.empty();
		}

		// if we have enough new-views, return view
		return Optional.of(thisView);
	}

	private boolean replacePreviousNewView(BFTNode author, View thisView) {
		View previousView = this.previousNewView.put(author, thisView);
		if (previousView == null) {
			// No previous NewView for this author, all good here
			return true;
		}

		if (thisView.equals(previousView)) {
			// Just going to ignore this duplicate NewView for now.
			// However, we can't count duplicates multiple times.
			return false;
		}

		// Prune last pending NewView from pending.
		// This limits the number of pending new views that are in the pipeline.
		ValidationState validationState = this.newViewState.get(previousView);
		if (validationState != null) {
			validationState.removeSignature(author);
			if (validationState.isEmpty()) {
				this.newViewState.remove(previousView);
			}
		}
		return true;
	}

	@VisibleForTesting
	// Greybox stuff for testing
	int newViewStateSize() {
		return this.newViewState.size();
	}

	@VisibleForTesting
	// Greybox stuff for testing
	int previousNewViewsSize() {
		return this.previousNewView.size();
	}
}
