/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.client;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.multibindings.StringMapKey;
import com.radixdlt.client.store.ClientApiStore;
import com.radixdlt.client.store.berkeley.BerkeleyClientApiStore;
import com.radixdlt.client.store.berkeley.ScheduledParticleFlush;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.LocalEvents;
import org.radix.api.jsonrpc.JsonRpcHandler;
import org.radix.api.jsonrpc.handler.HighLevelApiHandler;

public class ClientApiModule extends AbstractModule {
	@Override
	public void configure() {
		var eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
			.permitDuplicates();
		bind(ClientApiStore.class).to(BerkeleyClientApiStore.class).in(Scopes.SINGLETON);
		eventBinder.addBinding().toInstance(ScheduledParticleFlush.class);
	}

	@ProvidesIntoMap
	@StringMapKey("radix.universeMagic")
	public JsonRpcHandler universeMagicHandler(HighLevelApiHandler highLevelApiHandler) {
		return highLevelApiHandler::handleUniverseMagic;
	}

	@ProvidesIntoMap
	@StringMapKey("radix.nativeToken")
	public JsonRpcHandler nativeTokenHandler(HighLevelApiHandler highLevelApiHandler) {
		return highLevelApiHandler::handleNativeToken;
	}

	@ProvidesIntoMap
	@StringMapKey("radix.tokenBalances")
	public JsonRpcHandler tokenBalancesHandler(HighLevelApiHandler highLevelApiHandler) {
		return highLevelApiHandler::handleTokenBalances;
	}

	@ProvidesIntoMap
	@StringMapKey("radix.executedTransactions")
	public JsonRpcHandler executedTransactions(HighLevelApiHandler highLevelApiHandler) {
		return highLevelApiHandler::handleExecutedTransactions;
	}

	@ProvidesIntoMap
	@StringMapKey("radix.transactionStatus")
	public JsonRpcHandler transactionStatus(HighLevelApiHandler highLevelApiHandler) {
		return highLevelApiHandler::handleTransactionStatus;
	}

	@ProvidesIntoMap
	@StringMapKey("radix.tokenInfo")
	public JsonRpcHandler tokenInfo(HighLevelApiHandler highLevelApiHandler) {
		return highLevelApiHandler::handleTokenInfo;
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> clientApiStore(ClientApiStore clientApiStore) {
		return new EventProcessorOnRunner<>("application",
			ScheduledParticleFlush.class,
			clientApiStore.particleFlushProcessor()
		);
	}
}
