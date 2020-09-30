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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.RateLimiter;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Objects;
import java.util.Optional;

/**
 * A pacemaker which utilizes a fixed timeout (aka requires a synchronous network).
 */
public final class ExponentialTimeoutPacemaker implements Pacemaker {


	/**
	 * Sender of information regarding the BFT
	 */
	public interface PacemakerInfoSender {

		/**
		 * Signify that the bft node is on a new view
		 * @param view the view the bft node has changed to
		 */
		void sendCurrentView(View view);

		/**
		 * Signify that a timeout was processed by this bft node
		 * @param view the view of the timeout
		 */
		void sendTimeoutProcessed(View view);
	}

	private static final Logger log = LogManager.getLogger();

	private final long timeoutMilliseconds;
	private final double rate;
	private final int maxExponent;

	private final PacemakerTimeoutSender timeoutSender;
	private final PacemakerInfoSender pacemakerInfoSender;
	private final PendingNewViews pendingNewViews;

	private final RateLimiter newViewLogLimiter = RateLimiter.create(1.0);

	private View currentView = View.genesis();
	private View lastSyncView = View.genesis();
	// Highest view in which a commit happened
	private View highestCommitView = View.genesis();

	public ExponentialTimeoutPacemaker(
		long timeoutMilliseconds,
		double rate,
		int maxExponent,
		PacemakerTimeoutSender timeoutSender,
		PacemakerInfoSender pacemakerInfoSender
	) {
		if (timeoutMilliseconds <= 0) {
			throw new IllegalArgumentException("timeoutMilliseconds must be > 0 but was " + timeoutMilliseconds);
		}
		if (rate <= 1.0) {
			throw new IllegalArgumentException("rate must be > 1.0, but was " + rate);
		}
		if (maxExponent < 0) {
			throw new IllegalArgumentException("maxExponent must be >= 0, but was " + maxExponent);
		}
		double maxTimeout = timeoutMilliseconds * Math.pow(rate, maxExponent);
		if (maxTimeout > Long.MAX_VALUE) {
			throw new IllegalArgumentException("Maximum timeout value of " + maxTimeout + " is too large");
		}
		this.timeoutMilliseconds = timeoutMilliseconds;
		this.rate = rate;
		this.maxExponent = maxExponent;
		this.timeoutSender = Objects.requireNonNull(timeoutSender);
		this.pacemakerInfoSender = Objects.requireNonNull(pacemakerInfoSender);
		this.pendingNewViews = new PendingNewViews();
		log.debug("{} with max timeout {}*{}^{}ms",
			getClass().getSimpleName(), this.timeoutMilliseconds, this.rate, this.maxExponent);

	}

	@Override
	public View getCurrentView() {
		return currentView;
	}

	private void updateView(View nextView) {
		Level logLevel = this.newViewLogLimiter.tryAcquire() ? Level.INFO : Level.TRACE;
		long timeout = timeout(uncommittedViews(nextView));
		log.log(logLevel, "Starting View: {} with timeout {}ms", nextView, timeout);
		this.currentView = nextView;
		this.timeoutSender.scheduleTimeout(this.currentView, timeout);
		this.pacemakerInfoSender.sendCurrentView(this.currentView);
	}

	@Override
	public Optional<View> processLocalTimeout(View view) {
		if (!view.equals(this.currentView)) {
			return Optional.empty();
		}

		this.pacemakerInfoSender.sendTimeoutProcessed(view);
		this.updateView(currentView.next());

		return Optional.of(this.currentView);
	}

	// TODO: Move this into Event Coordinator
	@Override
	public Optional<View> processNewView(NewView newView, BFTValidatorSet validatorSet) {
		View newViewView = newView.getView();
		if (newViewView.compareTo(this.lastSyncView) <= 0) {
			log.debug("Ignoring NewView message {}: last sync view is {}", newView, this.lastSyncView);
			return Optional.empty();
		}

		// If QC of new-view was from previous view, then we are guaranteed to have the highest QC for this view
		// and can proceed
		final View qcView = newView.getQC().getView();
		final boolean highestQC = !qcView.isGenesis() && qcView.next().equals(this.currentView);

		if (!this.pendingNewViews.insertNewView(newView, validatorSet).isPresent() && !highestQC) {
			log.debug("NewView quorum not yet formed (qc {}+1 -> {})", qcView, this.currentView);
			return Optional.empty();
		}

		if (newViewView.equals(this.currentView)) {
			this.lastSyncView = this.currentView;
			return Optional.of(this.currentView);
		}
		log.trace("Ignoring NewView quorum for view {}, current is: {}", newViewView, this.currentView);
		return Optional.empty();
	}

	@Override
	public Optional<View> processQC(QuorumCertificate qc, QuorumCertificate highestCommittedQC) {
		this.highestCommitView = highestCommittedQC.getView();
		return processNextView(qc.getView());
	}

	@Override
	public Optional<View> processNextView(View view) {
		// check if a new view can be started
		View newView = view.next();
		if (newView.compareTo(currentView) > 0) {
			// start new view
			this.updateView(newView);
			return Optional.of(this.currentView);
		} else {
			return Optional.empty();
		}
	}

	@VisibleForTesting
	View highestCommitView() {
		return this.highestCommitView;
	}

	private long uncommittedViews(View v) {
		return Math.max(0L, v.number() - this.highestCommitView.number() - 1);
	}

	private long timeout(long uncommittedViews) {
		double exponential = Math.pow(this.rate, Math.min(this.maxExponent, uncommittedViews));
		return Math.round(this.timeoutMilliseconds * exponential);
	}

	private static <T extends Comparable<T>> T max(T a, T b) {
	    return a.compareTo(b) >= 0 ? a : b;
	}

}
