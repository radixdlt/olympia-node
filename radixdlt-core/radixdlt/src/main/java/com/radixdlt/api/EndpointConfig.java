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

import com.radixdlt.networks.Network;
import com.radixdlt.api.module.DeveloperEndpointModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.Module;
import com.radixdlt.api.module.AccountEndpointModule;
import com.radixdlt.api.module.ArchiveEndpointModule;
import com.radixdlt.api.module.ChaosEndpointModule;
import com.radixdlt.api.module.ConstructEndpointModule;
import com.radixdlt.api.module.FaucetEndpointModule;
import com.radixdlt.api.module.HealthEndpointModule;
import com.radixdlt.api.module.MetricsEndpointModule;
import com.radixdlt.api.module.SystemEndpointModule;
import com.radixdlt.api.module.UniverseEndpointModule;
import com.radixdlt.api.module.ValidationEndpointModule;
import com.radixdlt.api.module.VersionEndpointModule;
import com.radixdlt.properties.RuntimeProperties;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.radixdlt.api.EndpointConfig.Environment.ALL;
import static com.radixdlt.api.EndpointConfig.Environment.DEV_ONLY;
import static com.radixdlt.api.EndpointConfig.Type.ARCHIVE;
import static com.radixdlt.api.EndpointConfig.Type.NODE;

public final class EndpointConfig {
	private static final Logger log = LogManager.getLogger();

	public enum Type {
		ARCHIVE,
		NODE;
	}

	public enum Environment {
		ALL,
		DEV_ONLY;
	}

	private static final String API_PREFIX = "api.";
	private static final String API_SUFFIX_ENABLE = ".enable";
	private static final String API_ARCHIVE = "archive";
	private static final String API_CONSTRUCTION = "construction";
	private static final String API_SYSTEM = "system";
	private static final String API_ACCOUNT = "account";
	private static final String API_VALIDATION = "validation";
	private static final String API_UNIVERSE = "universe";
	private static final String API_FAUCET = "faucet";
	private static final String API_CHAOS = "chaos";
	private static final String API_HEALTH = "health";
	private static final String API_VERSION = "version";
	private static final String API_METRICS = "metrics";
	private static final String API_DEVELOPER = "developer";
	private static final List<EndpointConfig> ENDPOINTS = List.of(
		new EndpointConfig(API_ARCHIVE, false, ARCHIVE, ALL, ArchiveEndpointModule::new),
		new EndpointConfig(API_CONSTRUCTION, false, ARCHIVE, ALL, ConstructEndpointModule::new),
		new EndpointConfig(API_METRICS, false, NODE, ALL, MetricsEndpointModule::new),
		new EndpointConfig(API_SYSTEM, false, NODE, ALL, SystemEndpointModule::new),
		new EndpointConfig(API_ACCOUNT, false, NODE, ALL, AccountEndpointModule::new),
		new EndpointConfig(API_VALIDATION, false, NODE, ALL, ValidationEndpointModule::new),
		new EndpointConfig(API_UNIVERSE, false, NODE, ALL, UniverseEndpointModule::new),
		new EndpointConfig(API_FAUCET, false, NODE, DEV_ONLY, FaucetEndpointModule::new),
		new EndpointConfig(API_CHAOS, false, NODE, DEV_ONLY, ChaosEndpointModule::new),
		new EndpointConfig(API_HEALTH, true, NODE, ALL, HealthEndpointModule::new),
		new EndpointConfig(API_VERSION, true, NODE, ALL, VersionEndpointModule::new),
		new EndpointConfig(API_DEVELOPER, true, NODE, ALL, DeveloperEndpointModule::new)
	);

	private static final List<EndpointConfig> ARCHIVE_ENDPOINTS = ENDPOINTS.stream()
		.filter(e -> e.type == ARCHIVE)
		.collect(Collectors.toList());

	private static final List<EndpointConfig> NODE_ENDPOINTS = ENDPOINTS.stream()
		.filter(e -> e.type == NODE)
		.collect(Collectors.toList());

	private final String name;
	private final boolean defaultValue;
	private final Type type;
	private final Environment environment;
	private final Supplier<Module> moduleSupplier;

	private EndpointConfig(
		String name,
		boolean defaultValue,
		Type type,
		Environment environment,
		Supplier<Module> moduleSupplier
	) {
		this.name = name;
		this.defaultValue = defaultValue;
		this.type = type;
		this.environment = environment;
		this.moduleSupplier = moduleSupplier;
	}

	public static List<EndpointConfig> enabledArchiveEndpoints(RuntimeProperties properties, int networkId) {
		return ARCHIVE_ENDPOINTS.stream()
			.filter(e -> e.isEnabled(properties))
			.filter(e -> isEnabledInEnvironment(e, networkId))
			.collect(Collectors.toList());
	}

	public static List<EndpointConfig> enabledNodeEndpoints(RuntimeProperties properties, int networkId) {
		return NODE_ENDPOINTS.stream()
			.filter(e -> e.isEnabled(properties))
			.filter(e -> isEnabledInEnvironment(e, networkId))
			.collect(Collectors.toList());
	}

	public Supplier<Module> module() {
		return moduleSupplier;
	}

	public String name() {
		return name;
	}

	public boolean isEnabled(RuntimeProperties properties) {
		var value = properties.get(API_PREFIX + name + API_SUFFIX_ENABLE, defaultValue);
		log.debug("Endpoint config {}, default = {}, properties = {}", name(), defaultValue, value);

		return value;
	}

	public static List<EndpointStatus> endpointStatuses(RuntimeProperties properties, int networkId) {
		return NODE_ENDPOINTS.stream()
			.map(e -> EndpointStatus.create(e.name, isEnabled(e, properties, networkId)))
			.collect(Collectors.toList());
	}

	private static boolean isEnabled(EndpointConfig e, RuntimeProperties properties, int networkId) {
		return e.isEnabled(properties) && isEnabledInEnvironment(e, networkId);
	}

	private static boolean isEnabledInEnvironment(EndpointConfig e, int networkId) {
		return networkId != Network.MAINNET.getId() || e.environment == ALL;
	}
}
