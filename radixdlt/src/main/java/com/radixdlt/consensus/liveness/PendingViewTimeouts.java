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

import com.radixdlt.consensus.ViewTimeout;
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
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.ValidationState;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECDSASignature;

/**
 * Manages pending {@link ViewTimeout} items.
 * <p>
 * This class is NOT thread-safe.
 * <p>
 * This class is security critical (validator set membership checks).
 */
@NotThreadSafe
@SecurityCritical({ SecurityKind.GENERAL })
public final class PendingViewTimeouts {
	private static final Logger log = LogManager.getLogger();

	private final Map<View, ValidationState> viewTimeoutState = Maps.newHashMap();
	private final Map<BFTNode, View> previousViewTimeout = Maps.newHashMap();

	public PendingViewTimeouts() {
		// Nothing
	}

	/**
	 * Inserts a {@link ViewTimeout}, attempting to form a quorum.
	 * <p>
	 * A quorum will only be formed if permitted by the {@link BFTValidatorSet}.
	 *
	 * @param viewTimeout The {@link ViewTimeout} to be inserted
	 * @param validatorSet The validator set to form a quorum with
	 * @return View to proceed to, if any
	 */
	public Optional<View> insertViewTimeout(ViewTimeout viewTimeout, BFTValidatorSet validatorSet) {
		final BFTNode node = viewTimeout.getAuthor();
		if (!validatorSet.containsNode(node)) {
			// Not a valid validator
			log.info("Ignoring view timeout from invalid author {}", node);
			return Optional.empty();
		}

		final ECDSASignature signature = viewTimeout.signature();
		final View thisView = viewTimeout.getView();
		if (!replacePreviousViewTimeout(node, thisView)) {
			return Optional.empty();
		}

		ValidationState validationState = this.viewTimeoutState.computeIfAbsent(thisView, k -> validatorSet.newValidationState());

		// check if we have gotten enough view timeouts to proceed
		// Timestamps here are not required, so we use 0L below
		if (!(validationState.addSignature(node, 0L, signature) && validationState.complete())) {
			return Optional.empty();
		}

		// if we have enough view timeouts, return view
		return Optional.of(thisView);
	}

	private boolean replacePreviousViewTimeout(BFTNode author, View thisView) {
		View previousView = this.previousViewTimeout.put(author, thisView);
		if (previousView == null) {
			// No previous item for this author, all good here
			return true;
		}

		if (thisView.equals(previousView)) {
			// Just going to ignore this duplicate item for now.
			// However, we can't count duplicates multiple times.
			return false;
		}

		// Prune last pending item from pending.
		// This limits the number of pending view timeouts that are in the pipeline.
		ValidationState validationState = this.viewTimeoutState.get(previousView);
		if (validationState != null) {
			validationState.removeSignature(author);
			if (validationState.isEmpty()) {
				this.viewTimeoutState.remove(previousView);
			}
		}
		return true;
	}

	@VisibleForTesting
	// Greybox stuff for testing
	int viewTimeoutStateSize() {
		return this.viewTimeoutState.size();
	}

	@VisibleForTesting
	// Greybox stuff for testing
	int previousViewTimeoutSize() {
		return this.previousViewTimeout.size();
	}
}
