/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.mempool;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import com.radixdlt.ModuleRunner;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.environment.rx.ModuleRunnerImpl;
import com.radixdlt.environment.rx.RemoteEvent;
import io.reactivex.rxjava3.core.BackpressureOverflowStrategy;
import io.reactivex.rxjava3.core.Flowable;

import java.util.concurrent.TimeUnit;

/**
 * Module for receiving mempool commands
 */
public final class MempoolReceiverModule extends AbstractModule {
	@ProvidesIntoMap
	@StringMapKey("mempool")
	private ModuleRunner mempoolReceiver(
		Flowable<RemoteEvent<MempoolAddSuccess>> mempoolCommands,
		RemoteEventProcessor<MempoolAddSuccess> remoteEventProcessor
	) {
		Flowable<RemoteEvent<MempoolAddSuccess>> backpressured = mempoolCommands
			.onBackpressureBuffer(5, null, BackpressureOverflowStrategy.DROP_LATEST)
			.throttleLatest(50, TimeUnit.MILLISECONDS);

		return ModuleRunnerImpl.builder()
			.add(backpressured, remoteEventProcessor)
			.build("MempoolReceiver");
	}
}
