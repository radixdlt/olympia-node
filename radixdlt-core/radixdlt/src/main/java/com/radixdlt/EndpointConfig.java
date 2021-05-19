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

package com.radixdlt;

import com.google.inject.Module;
import com.radixdlt.api.module.AccountEndpointModule;
import com.radixdlt.api.module.ArchiveEndpointModule;
import com.radixdlt.api.module.ChaosEndpointModule;
import com.radixdlt.api.module.ConstructEndpointModule;
import com.radixdlt.api.module.FaucetEndpointModule;
import com.radixdlt.api.module.HealthEndpointModule;
import com.radixdlt.api.module.SystemEndpointModule;
import com.radixdlt.api.module.UniverseEndpointModule;
import com.radixdlt.api.module.ValidationEndpointModule;
import com.radixdlt.api.module.VersionEndpointModule;
import com.radixdlt.properties.RuntimeProperties;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class EndpointConfig {
	public enum Type {
		ARCHIVE,
		NODE;
	}

	private static final String API_PREFIX = "api.";
	private static final String API_SUFFIX_ENABLE = ".enable";

	private static final String API_ARCHIVE = "archive";
	private static final String API_CONSTRUCT = "construct";
	private static final String API_SYSTEM = "system";
	private static final String API_ACCOUNT = "account";
	private static final String API_VALIDATOR = "validator";
	private static final String API_UNIVERSE = "universe";
	private static final String API_FAUCET = "faucet";
	private static final String API_CHAOS = "chaos";
	private static final String API_HEALTH = "health";
	private static final String API_VERSION = "version";
	private static final List<EndpointConfig> ENDPOINTS = List.of(
		new EndpointConfig(API_ARCHIVE, false, Type.ARCHIVE, ArchiveEndpointModule::new),
		new EndpointConfig(API_CONSTRUCT, false, Type.ARCHIVE, ConstructEndpointModule::new),
		new EndpointConfig(API_SYSTEM, false, Type.NODE, SystemEndpointModule::new),
		new EndpointConfig(API_ACCOUNT, false, Type.NODE, AccountEndpointModule::new),
		new EndpointConfig(API_VALIDATOR, false, Type.NODE, ValidationEndpointModule::new),
		new EndpointConfig(API_UNIVERSE, false, Type.NODE, UniverseEndpointModule::new),
		new EndpointConfig(API_FAUCET, false, Type.NODE, FaucetEndpointModule::new),
		new EndpointConfig(API_CHAOS, false, Type.NODE, ChaosEndpointModule::new),
		new EndpointConfig(API_HEALTH, true, Type.NODE, HealthEndpointModule::new),
		new EndpointConfig(API_VERSION, true, Type.NODE, VersionEndpointModule::new)
	);

	private static final List<EndpointConfig> ARCHIVE_ENDPOINTS = ENDPOINTS.stream()
		.filter(e -> e.type == Type.ARCHIVE)
		.collect(Collectors.toList());

	private static final List<EndpointConfig> NODE_ENDPOINTS = ENDPOINTS.stream()
		.filter(e -> e.type == Type.NODE)
		.collect(Collectors.toList());

	private final String name;

	private final boolean defaultValue;
	private final Type type;
	private final Supplier<Module> moduleSupplier;
	private EndpointConfig(String name, boolean defaultValue, Type type, Supplier<Module> moduleSupplier) {
		this.name = name;
		this.defaultValue = defaultValue;
		this.type = type;
		this.moduleSupplier = moduleSupplier;
	}

	public static List<EndpointConfig> enabledArchiveEndpoints(RuntimeProperties properties) {
		return ARCHIVE_ENDPOINTS.stream()
			.filter(e -> e.isEnabled(properties))
			.collect(Collectors.toList());
	}

	public static List<EndpointConfig> enabledNodeEndpoints(RuntimeProperties properties) {
		return NODE_ENDPOINTS.stream()
			.filter(e -> e.isEnabled(properties))
			.collect(Collectors.toList());
	}

	public Supplier<Module> module() {
		return moduleSupplier;
	}

	public String name() {
		return name;
	}

	public boolean isEnabled(RuntimeProperties properties) {
		return properties.get(API_PREFIX + name + API_SUFFIX_ENABLE, defaultValue);
	}
}
