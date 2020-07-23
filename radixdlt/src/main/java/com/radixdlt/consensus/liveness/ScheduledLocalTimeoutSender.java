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

import com.radixdlt.consensus.epoch.LocalTimeout;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Schedules timeouts and exposes the events as an rx stream
 */
public final class ScheduledLocalTimeoutSender implements PacemakerRx, LocalTimeoutSender {
	private final ScheduledExecutorService executorService;
	private final Subject<LocalTimeout> timeouts;
	private final Observable<LocalTimeout> timeoutsObservable;


	public ScheduledLocalTimeoutSender(ScheduledExecutorService executorService) {
		this.executorService = Objects.requireNonNull(executorService);
		// BehaviorSubject so that nextLocalTimeout will complete if timeout already occurred
		this.timeouts = BehaviorSubject.<LocalTimeout>create().toSerialized();
		this.timeoutsObservable = this.timeouts
			.publish()
			.refCount();
	}

	@Override
	public void scheduleTimeout(LocalTimeout localTimeout, long timeoutMilliseconds) {
		executorService.schedule(() -> timeouts.onNext(localTimeout), timeoutMilliseconds, TimeUnit.MILLISECONDS);
	}

	@Override
	public Observable<LocalTimeout> localTimeouts() {
		return this.timeoutsObservable;
	}
}
