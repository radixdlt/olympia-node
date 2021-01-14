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

package com.radixdlt.mempool;

import com.radixdlt.consensus.Command;
import java.util.Objects;

import com.google.inject.Inject;

import io.reactivex.rxjava3.core.Observable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.reactivex.rxjava3.disposables.Disposable;

/**
 * Network glue for SubmissionControl.
 */
public final class MempoolReceiver {
	private static final Logger log = LogManager.getLogger();

	private final Observable<Command> mempoolCommands;
	private final SubmissionControl submissionControl;

	private final Object startLock = new Object();
	private Disposable disposable;

	@Inject
	public MempoolReceiver(
		Observable<Command> mempoolCommands,
		SubmissionControl submissionControl
	) {
		this.mempoolCommands = Objects.requireNonNull(mempoolCommands);
		this.submissionControl = Objects.requireNonNull(submissionControl);
	}

	public void start() {
		synchronized (this.startLock) {
			if (this.disposable == null) {
				this.disposable = this.mempoolCommands
					.subscribe(this.submissionControl::submitCommand);
			}
		}
	}

	void stop() {
		synchronized (this.startLock) {
			if (this.disposable != null) {
				// Try to do the sensible thing if disposable.dispose() throws
				Disposable d = this.disposable;
				this.disposable = null;
				d.dispose();
			}
		}
	}

	boolean running() {
		synchronized (this.startLock) {
			return this.disposable != null;
		}
	}
}
