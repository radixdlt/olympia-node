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
import com.radixdlt.consensus.validators.ValidationResult;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.Hash;
import com.radixdlt.utils.Longs;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

import io.reactivex.rxjava3.subjects.Subject;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

/**
 * Overly simplistic pacemaker
 */
public final class PacemakerImpl implements Pacemaker, PacemakerRx {
	private static final Logger log = Logging.getLogger("PM");

	static final int TIMEOUT_MILLISECONDS = 5000;
	private final Subject<View> timeouts;
	private final Observable<View> timeoutsObservable;
	private final ScheduledExecutorService executorService;

	private final Map<View, ECDSASignatures> pendingNewViews = new HashMap<>();
	private View currentView = View.of(0L);

	public PacemakerImpl(ScheduledExecutorService executorService) {
		this.executorService = Objects.requireNonNull(executorService);
		this.timeouts = PublishSubject.<View>create().toSerialized();
		this.timeoutsObservable = this.timeouts
			.publish()
			.refCount()
			.doOnSubscribe(d -> scheduleTimeout(this.currentView));
	}

	private void scheduleTimeout(final View timeoutView) {
		log.info("Starting View: " + timeoutView);
		executorService.schedule(() -> timeouts.onNext(timeoutView), TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
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
		Hash newViewId = new Hash(Hash.hash256(Longs.toByteArray(newView.getView().number())));
		ECDSASignature signature = newView.getSignature().orElseThrow(() -> new IllegalArgumentException("new-view is missing signature"));
		ECDSASignatures signatures = pendingNewViews.getOrDefault(newView.getView(), new ECDSASignatures());
		signatures = (ECDSASignatures) signatures.concatenate(newView.getAuthor(), signature);

		// check if we have gotten enough new-views to proceed
		ValidationResult validationResult = validatorSet.validate(newViewId, signatures);
		if (!validationResult.valid()) {
			// if we haven't got enough new-views yet, do nothing
			pendingNewViews.put(newView.getView(), signatures);
			return Optional.empty();
		} else {
			// if we got enough new-views, remove pending and return formed QC
			pendingNewViews.remove(newView.getView());

			if (newView.getView().compareTo(this.currentView) > 0) {
				this.currentView = newView.getView();
				scheduleTimeout(this.currentView);
			}

			if (newView.getView().compareTo(this.currentView) >= 0) {
				return Optional.of(this.currentView);
			} else {
				log.info("Ignoring New View Quorum: " + newView.getView() + " Current is: " + this.currentView);
				return Optional.empty();
			}
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
