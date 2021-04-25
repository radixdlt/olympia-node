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

package com.radixdlt.api;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.api.construction.ConstructionController;
import com.radixdlt.client.service.ScheduledCacheCleanup;
import com.radixdlt.client.store.berkeley.ScheduledQueueFlush;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.statecomputer.AtomsCommittedToLedger;
import com.radixdlt.statecomputer.AtomsRemovedFromMempool;
import org.radix.api.http.ChaosController;
import org.radix.api.http.Controller;
import org.radix.api.http.NodeController;
import org.radix.api.http.RadixHttpServer;
import org.radix.api.http.RpcController;
import org.radix.api.http.SystemController;
import org.radix.api.jsonrpc.JsonRpcHandler;

/**
 * Configures the api including http server setup
 */
public final class ApiModule extends AbstractModule {
	@Override
	public void configure() {
		bind(RadixHttpServer.class).in(Scopes.SINGLETON);
		var controllers = Multibinder.newSetBinder(binder(), Controller.class);
		controllers.addBinding().to(ConstructionController.class).in(Scopes.SINGLETON);
		controllers.addBinding().to(ChaosController.class).in(Scopes.SINGLETON);
		controllers.addBinding().to(NodeController.class).in(Scopes.SINGLETON);
		controllers.addBinding().to(RpcController.class).in(Scopes.SINGLETON);
		controllers.addBinding().to(SystemController.class).in(Scopes.SINGLETON);

		var eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
				.permitDuplicates();
		eventBinder.addBinding().toInstance(AtomsCommittedToLedger.class);
		eventBinder.addBinding().toInstance(MempoolAddFailure.class);
		eventBinder.addBinding().toInstance(AtomsRemovedFromMempool.class);
		eventBinder.addBinding().toInstance(ScheduledQueueFlush.class);
		eventBinder.addBinding().toInstance(ScheduledCacheCleanup.class);

		// For additional handlers
		MapBinder.newMapBinder(binder(), String.class, JsonRpcHandler.class);
	}
}
