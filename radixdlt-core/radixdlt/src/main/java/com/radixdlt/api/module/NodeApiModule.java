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

package com.radixdlt.api.module;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.EndpointConfig;
import com.radixdlt.ModuleRunner;
import com.radixdlt.api.Controller;
import com.radixdlt.api.server.NodeHttpServer;
import com.radixdlt.api.service.ScheduledCacheCleanup;
import com.radixdlt.api.store.ClientApiStore;
import com.radixdlt.api.data.ScheduledQueueFlush;
import com.radixdlt.api.controller.ConfigController;
import com.radixdlt.api.controller.ValidatorController;
import com.radixdlt.api.store.berkeley.BerkeleyClientApiStore;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.Runners;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.statecomputer.AtomsRemovedFromMempool;

import java.util.List;

/**
 * Configures the api including http server setup
 */
public final class NodeApiModule extends AbstractModule {
	private static final Logger log = LogManager.getLogger();

	private final List<EndpointConfig> endpoints;

	public NodeApiModule(List<EndpointConfig> endpoints) {
		this.endpoints = endpoints;
	}

	@Override
	public void configure() {
		if (endpoints.isEmpty()) {
			return;
		}

		MapBinder.newMapBinder(binder(), String.class, ModuleRunner.class)
			.addBinding(Runners.NODE_API)
			.to(NodeHttpServer.class);
		bind(NodeHttpServer.class).in(Scopes.SINGLETON);

		var controllers = Multibinder.newSetBinder(binder(), Controller.class);
		//TODO: move each into appropriate modules with @ProvidesIntoSet annotation
		controllers.addBinding().to(ValidatorController.class).in(Scopes.SINGLETON);
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

		//TODO: finish configuring endpoints
		endpoints.forEach(ep -> {
			log.info("Enabling /{} endpoint", ep.name());
			install(ep.module().get());
		});
//
//		if (properties.get(API_ARCHIVE, false)) {
//			log.info("Enabling /archive API");
//			install(new ArchiveApiModule());
//		}
//
//		if (properties.get(API_CONSTRUCT, false)) {
//			log.info("Enabling /construct API");
//			install(new ConstructApiModule());
//		}
//
//		if (properties.get(API_SYSTEM, false)) {
//			//TODO: finish it
//			log.info("Enabling /system API");
//			install(new SystemApiModule());
//		}
//
//		if (properties.get(API_ACCOUNT, false)) {
//			//TODO: finish it
//			log.info("Enabling /account API");
//			//install(new AccountApiModule());
//		}
//
//		if (properties.get(API_VALIDATOR, false)) {
//			log.info("Enabling /validator API");
//			install(new ValidatorApiModule());
//		}
//
//		if (properties.get(API_UNIVERSE, false)) {
//			log.info("Enabling /universe API");
//			var controllers = Multibinder.newSetBinder(binder(), Controller.class);
//			controllers.addBinding().to(UniverseController.class).in(Scopes.SINGLETON);
//		}
//
//		if (properties.get(API_FAUCET, false)) {
//			//TODO: disable on mainnet
//			log.info("Enabling /faucet API");
//			install(new FaucetModule());
//		}
//
//		if (properties.get(API_CHAOS, false)) {
//			//TODO: disable on mainnet
//			log.info("Enabling /chaos API");
//			install(new ChaosModule());
//		}
//
//		if (properties.get(API_HEALTH, true)) {
//			//TODO: disable on mainnet
//			log.info("Enabling /chaos API");
//			//install(new HealthModule());
//		}
//
//		if (properties.get(API_VERSION, true)) {
//			//TODO: disable on mainnet
//			log.info("Enabling /version API");
//			//install(new VersionModule());
//		}
	}
}
