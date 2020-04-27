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
import com.radixdlt.crypto.Hash;
import com.radixdlt.utils.Longs;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Overly simplistic pacemaker
 */
public final class PacemakerImpl implements Pacemaker, PacemakerRx {
	private static final Logger log = LogManager.getLogger("PM");

	private final int timeoutMilliseconds;
	private final Subject<View> timeouts;
	private final Observable<View> timeoutsObservable;
	private final ScheduledExecutorService executorService;

	private final Map<View, ValidationState> pendingNewViews = new HashMap<>();
	private View currentView = View.of(0L);
	private View lastSyncView = View.of(0L);

	public PacemakerImpl(int timeoutMilliseconds, ScheduledExecutorService executorService) {
		if (timeoutMilliseconds <= 0) {
			throw new IllegalArgumentException("timeoutMilliseconds must be > 0 but was " + timeoutMilliseconds);
		}
		this.timeoutMilliseconds = timeoutMilliseconds;
		this.executorService = Objects.requireNonNull(executorService);
		// BehaviorSubject so that nextLocalTimeout will complete if timeout already occurred
		this.timeouts = BehaviorSubject.<View>create().toSerialized();
		this.timeoutsObservable = this.timeouts
			.publish()
			.refCount()
			.doOnSubscribe(d -> scheduleTimeout(this.currentView));
	}

	private void scheduleTimeout(final View timeoutView) {
		log.info("Starting View: {}", timeoutView);
		executorService.schedule(() -> timeouts.onNext(timeoutView), timeoutMilliseconds, TimeUnit.MILLISECONDS);
	}

	@Override
	public Completable nextLocalTimeout() {
		return Completable.fromSingle(
			timeouts
				.filter(this.currentView::equals)
				.firstOrError()
		);
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

		scheduleTimeout(this.currentView);
		return Optional.of(this.currentView);
	}

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
			ValidationState validationState = pendingNewViews.computeIfAbsent(newView.getView(), k -> validatorSet.newValidationState(newViewId));

			// check if we have gotten enough new-views to proceed
			if (!validationState.addSignature(newView.getAuthor(), signature)) {
				// if we haven't got enough new-views yet, do nothing
				return Optional.empty();
			}
		}

		if (newView.getView().equals(this.currentView)) {
			pendingNewViews.remove(newView.getView());
			this.lastSyncView = this.currentView;
			return Optional.of(this.currentView);
		} else {
			log.info("Ignoring New View Quorum: {} Current is: {}", newView.getView(), this.currentView);
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

			scheduleTimeout(this.currentView);

			return Optional.of(this.currentView);
		} else {
			return Optional.empty();
		}
	}

	@Override
	public Observable<View> localTimeouts() {
		return this.timeoutsObservable;
	}
}
