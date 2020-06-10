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


import com.radixdlt.consensus.View;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Schedules timeouts and exposes the events as an rx stream
 */
public final class ScheduledTimeoutSender implements FixedTimeoutPacemaker.TimeoutSender, PacemakerRx {
	private static final Logger log = LogManager.getLogger();
	private static final long LOGGING_INTERVAL = TimeUnit.SECONDS.toMillis(1);
	private final ScheduledExecutorService executorService;
	private final Subject<View> timeouts;
	private final Observable<View> timeoutsObservable;
	private volatile long nextLogging = 0;

	public ScheduledTimeoutSender(ScheduledExecutorService executorService) {
		this.executorService = Objects.requireNonNull(executorService);
		// BehaviorSubject so that nextLocalTimeout will complete if timeout already occurred
		this.timeouts = BehaviorSubject.<View>create().toSerialized();
		this.timeoutsObservable = this.timeouts
			.publish()
			.refCount();
	}

	@Override
	public void scheduleTimeout(final View view, long timeoutMilliseconds) {
	    long crtTime = System.currentTimeMillis();
	    if (crtTime >= nextLogging) {
	        log.info("Starting View: {}", view);
	        nextLogging = crtTime + LOGGING_INTERVAL;
	    } else {
	        log.trace("Starting View: {}", view);
	    }
		executorService.schedule(() -> timeouts.onNext(view), timeoutMilliseconds, TimeUnit.MILLISECONDS);
	}

	@Override
	public Completable timeout(View view) {
		return Completable.fromSingle(
			timeouts
				.filter(v -> v.compareTo(view) >= 0)
				.firstOrError()
		);
	}

	@Override
	public Observable<View> localTimeouts() {
		return this.timeoutsObservable;
	}

}
