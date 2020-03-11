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
import com.radixdlt.consensus.safety.QuorumRequirements;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Overly simplistic pacemaker
 */
public final class PacemakerImpl implements Pacemaker, PacemakerRx {
	static final int TIMEOUT_MILLISECONDS = 500;
	private final PublishSubject<View> timeouts;
	private final ScheduledExecutorService executorService;

	private View currentView = View.of(0L);
	private View highestQCView = View.of(0L);

	public PacemakerImpl(ScheduledExecutorService executorService) {
		this.timeouts = PublishSubject.create();
		this.executorService = executorService;
	}

	private void scheduleTimeout(final View timeoutView) {
		executorService.schedule(() -> {
			timeouts.onNext(timeoutView);
		}, TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
	}

	@Override
	public View getCurrentView() {
		return currentView;
	}

	@Override
	public boolean processLocalTimeout(View view) {
		if (!view.equals(this.currentView)) {
			return false;
		}

		this.currentView = currentView.next();

		scheduleTimeout(this.currentView);
		return true;
	}

	@Override
	public Optional<View> processRemoteNewView(NewView newView, QuorumRequirements quorumRequirements) {
		// gather new views to form new views QC
		// TODO assumes single node network for now
		return Optional.of(newView.getView());
	}

	private void updateHighestQCView(View view) {
		if (view.compareTo(highestQCView) > 0) {
			highestQCView = view;
		}
	}

	@Override
	public Optional<View> processQC(View view) {
		// update
		updateHighestQCView(view);

		// check if a new view can be started
		View newView = highestQCView.next();
		if (newView.compareTo(currentView) <= 0) {
			return Optional.empty();
		}

		// start new view
		this.currentView = newView;

		scheduleTimeout(this.currentView);

		return Optional.of(this.currentView);
	}

	@Override
	public void start() {
		scheduleTimeout(this.currentView);
	}

	@Override
	public Observable<View> localTimeouts() {
		return timeouts;
	}
}
