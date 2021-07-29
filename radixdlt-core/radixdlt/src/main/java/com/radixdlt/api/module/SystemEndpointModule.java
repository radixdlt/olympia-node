/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
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

package com.radixdlt.api.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import com.radixdlt.api.Controller;
import com.radixdlt.api.JsonRpcHandler;
import com.radixdlt.api.controller.JsonRpcController;
import com.radixdlt.api.handler.SystemHandler;
import com.radixdlt.api.qualifier.NodeServer;
import com.radixdlt.api.qualifier.SystemEndpoint;
import com.radixdlt.api.server.JsonRpcServer;

import java.util.Map;

public class SystemEndpointModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(SystemHandler.class).in(Scopes.SINGLETON);
	}

	@SystemEndpoint
	@Provides
	public JsonRpcServer rpcServer(@SystemEndpoint Map<String, JsonRpcHandler> additionalHandlers) {
		return new JsonRpcServer(additionalHandlers);
	}

	@NodeServer
	@ProvidesIntoMap
	@StringMapKey("/system")
	public Controller systemController(@SystemEndpoint JsonRpcServer jsonRpcServer) {
		return new JsonRpcController(jsonRpcServer);
	}

	@SystemEndpoint
	@ProvidesIntoMap
	@StringMapKey("api.get_configuration")
	public JsonRpcHandler apiGetConfiguration(SystemHandler systemHandler) {
		return systemHandler::apiGetConfiguration;
	}

	@SystemEndpoint
	@ProvidesIntoMap
	@StringMapKey("api.get_data")
	public JsonRpcHandler apiGetData(SystemHandler systemHandler) {
		return systemHandler::apiGetData;
	}

	@SystemEndpoint
	@ProvidesIntoMap
	@StringMapKey("bft.get_configuration")
	public JsonRpcHandler bftGetConfiguration(SystemHandler systemHandler) {
		return systemHandler::bftGetConfiguration;
	}

	@SystemEndpoint
	@ProvidesIntoMap
	@StringMapKey("bft.get_data")
	public JsonRpcHandler bftGetData(SystemHandler systemHandler) {
		return systemHandler::bftGetData;
	}

	@SystemEndpoint
	@ProvidesIntoMap
	@StringMapKey("mempool.get_configuration")
	public JsonRpcHandler mempoolGetConfiguration(SystemHandler systemHandler) {
		return systemHandler::mempoolGetConfiguration;
	}

	@SystemEndpoint
	@ProvidesIntoMap
	@StringMapKey("mempool.get_data")
	public JsonRpcHandler mempoolGetData(SystemHandler systemHandler) {
		return systemHandler::mempoolGetData;
	}

	@SystemEndpoint
	@ProvidesIntoMap
	@StringMapKey("ledger.get_latest_proof")
	public JsonRpcHandler ledgerGetLatestProof(SystemHandler systemHandler) {
		return systemHandler::ledgerGetLatestProof;
	}

	@SystemEndpoint
	@ProvidesIntoMap
	@StringMapKey("ledger.get_latest_epoch_proof")
	public JsonRpcHandler ledgerGetLatestEpochProof(SystemHandler systemHandler) {
		return systemHandler::ledgerGetLatestEpochProof;
	}

	@SystemEndpoint
	@ProvidesIntoMap
	@StringMapKey("radix_engine.get_configuration")
	public JsonRpcHandler radixEngineGetConfiguration(SystemHandler systemHandler) {
		return systemHandler::radixEngineGetConfiguration;
	}

	@SystemEndpoint
	@ProvidesIntoMap
	@StringMapKey("radix_engine.get_data")
	public JsonRpcHandler radixEngineGetData(SystemHandler systemHandler) {
		return systemHandler::radixEngineGetData;
	}

	@SystemEndpoint
	@ProvidesIntoMap
	@StringMapKey("sync.get_configuration")
	public JsonRpcHandler syncGetConfiguration(SystemHandler systemHandler) {
		return systemHandler::syncGetConfiguration;
	}

	@SystemEndpoint
	@ProvidesIntoMap
	@StringMapKey("sync.get_data")
	public JsonRpcHandler syncGetData(SystemHandler systemHandler) {
		return systemHandler::syncGetData;
	}

	@SystemEndpoint
	@ProvidesIntoMap
	@StringMapKey("networking.get_configuration")
	public JsonRpcHandler networkingGetConfiguration(SystemHandler systemHandler) {
		return systemHandler::networkingGetConfiguration;
	}

	@SystemEndpoint
	@ProvidesIntoMap
	@StringMapKey("networking.get_peers")
	public JsonRpcHandler networkingGetPeers(SystemHandler systemHandler) {
		return systemHandler::networkingGetPeers;
	}

	@SystemEndpoint
	@ProvidesIntoMap
	@StringMapKey("networking.get_address_book")
	public JsonRpcHandler networkingGetAddressBook(SystemHandler systemHandler) {
		return systemHandler::networkingGetAddressBook;
	}

	@SystemEndpoint
	@ProvidesIntoMap
	@StringMapKey("networking.get_data")
	public JsonRpcHandler networkingGetData(SystemHandler systemHandler) {
		return systemHandler::networkingGetData;
	}

	@SystemEndpoint
	@ProvidesIntoMap
	@StringMapKey("checkpoints.get_checkpoints")
	public JsonRpcHandler checkpointsGetCheckpoints(SystemHandler systemHandler) {
		return systemHandler::checkpointsGetCheckpoints;
	}
}
