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
 *
 */

package com.radixdlt.mempool;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessOnDispatch;

/**
 * Module responsible for sending/receiving mempool messages to/from other nodes.
 */
public final class MempoolRelayerModule extends AbstractModule {

	@Override
	public void configure() {
		bind(MempoolRelayer.class).in(Scopes.SINGLETON);
	}

	@ProvidesIntoSet
	@ProcessOnDispatch
	private EventProcessor<MempoolAddSuccess> mempoolAddedCommandEventProcessor(MempoolRelayer mempoolRelayer) {
		return mempoolRelayer.mempoolAddedCommandEventProcessor();
	}
}
