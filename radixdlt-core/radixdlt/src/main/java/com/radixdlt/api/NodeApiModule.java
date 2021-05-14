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
import com.radixdlt.ModuleRunner;
import com.radixdlt.api.archive.service.ScheduledCacheCleanup;
import com.radixdlt.api.archive.store.ClientApiStore;
import com.radixdlt.api.archive.store.berkeley.BerkeleyClientApiStore;
import com.radixdlt.api.archive.store.berkeley.ScheduledQueueFlush;
import com.radixdlt.api.config.ConfigController;
import com.radixdlt.api.node.NodeController;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.Runners;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.statecomputer.AtomsRemovedFromMempool;

/**
 * Configures the api including http server setup
 */
public final class NodeApiModule extends AbstractModule {
	@Override
	public void configure() {
		MapBinder.newMapBinder(binder(), String.class, ModuleRunner.class)
			.addBinding(Runners.NODE_API)
			.to(NodeHttpServer.class);
		bind(NodeHttpServer.class).in(Scopes.SINGLETON);

		var controllers = Multibinder.newSetBinder(binder(), Controller.class);
		//TODO: move each into appropriate modules with @ProvidesIntoSet annotation
		controllers.addBinding().to(NodeController.class).in(Scopes.SINGLETON);
		controllers.addBinding().to(ConfigController.class).in(Scopes.SINGLETON);
		//controllers.addBinding().to(SystemController.class).in(Scopes.SINGLETON);
		//controllers.addBinding().to(ConstructController.class).in(Scopes.SINGLETON);

		var eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
			.permitDuplicates();
		eventBinder.addBinding().toInstance(MempoolAddFailure.class);
		eventBinder.addBinding().toInstance(AtomsRemovedFromMempool.class);
		eventBinder.addBinding().toInstance(ScheduledQueueFlush.class);
		eventBinder.addBinding().toInstance(ScheduledCacheCleanup.class);

		//TODO: find appropriate place
		bind(ClientApiStore.class).to(BerkeleyClientApiStore.class).in(Scopes.SINGLETON);
	}
}
