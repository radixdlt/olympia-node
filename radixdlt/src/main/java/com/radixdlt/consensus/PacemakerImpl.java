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

package com.radixdlt.consensus;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Overly simplistic pacemaker
 */
public final class PacemakerImpl implements Pacemaker, PacemakerRx {
	static final int TIMEOUT_MILLISECONDS = 500;
	private final PublishSubject<Round> timeouts;
	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

	private ScheduledFuture<?> currentTimeout;
	private Round currentRound = Round.create(0L);;
	private Round highestQCRound = Round.create(0L);

	public PacemakerImpl() {
		this.timeouts = PublishSubject.create();
	}

	private void scheduleTimeout() {
		if (this.currentTimeout != null) {
			this.currentTimeout.cancel(false);
		}

		final Round round = this.currentRound;
		this.currentTimeout = executorService.schedule(() -> {
			timeouts.onNext(round);
		}, TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
	}

	@Override
	public Round getCurrentRound() {
		return currentRound;
	}

	@Override
	public boolean processLocalTimeout(Round round) {
		if (!round.equals(this.currentRound)) {
			return false;
		}

		this.currentRound = currentRound.next();

		scheduleTimeout();
		return true;
	}

	@Override
	public Optional<Round> processRemoteNewRound(NewRound newRound) {
		// gather new rounds to form new round QC
		// TODO assumes single node network for now
		return Optional.of(newRound.getRound());
	}

	private void updateHighestQCRound(Round round) {
		if (round.compareTo(highestQCRound) > 0) {
			highestQCRound = round;
		}
	}

	@Override
	public Optional<Round> processQC(Round round) {
		// update
		updateHighestQCRound(round);

		// check if a new round can be started
		Round newRound = highestQCRound.next();
		if (newRound.compareTo(currentRound) <= 0) {
			return Optional.empty();
		}

		// start new round
		currentRound = newRound;

		scheduleTimeout();

		return Optional.of(currentRound);
	}

	@Override
	public void start() {
		scheduleTimeout();
	}

	@Override
	public Observable<Round> localTimeouts() {
		return timeouts;
	}
}
