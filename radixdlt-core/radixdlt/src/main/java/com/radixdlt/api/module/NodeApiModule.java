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
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.EndpointConfig;
import com.radixdlt.ModuleRunner;
import com.radixdlt.api.server.NodeHttpServer;
import com.radixdlt.api.service.NetworkInfoService;
import com.radixdlt.api.service.ScheduledStatsCollecting;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.Runners;

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
		endpoints.forEach(ep -> {
			log.info("Enabling /{} endpoint", ep.name());
			install(ep.module().get());
		});

		MapBinder.newMapBinder(binder(), String.class, ModuleRunner.class)
			.addBinding(Runners.NODE_API)
			.to(NodeHttpServer.class);

		bind(NodeHttpServer.class).in(Scopes.SINGLETON);
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> networkInfoService(NetworkInfoService networkInfoService) {
		return new EventProcessorOnRunner<>(
			Runners.APPLICATION,
			ScheduledStatsCollecting.class,
			networkInfoService.updateStats()
		);
	}
}
