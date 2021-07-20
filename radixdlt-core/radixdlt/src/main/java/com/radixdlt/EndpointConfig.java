/* Copyright 2021 Radix DLT Ltd incorporated in England.
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt;

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

import static com.radixdlt.EndpointConfig.Environment.ALL;
import static com.radixdlt.EndpointConfig.Environment.DEV_ONLY;
import static com.radixdlt.EndpointConfig.Type.ARCHIVE;
import static com.radixdlt.EndpointConfig.Type.NODE;

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
