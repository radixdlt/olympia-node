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

package com.radixdlt.client.lib.api.async;

import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.NavigationCursor;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.api.ValidatorAddress;
import com.radixdlt.client.lib.dto.ApiConfiguration;
import com.radixdlt.client.lib.dto.ApiData;
import com.radixdlt.client.lib.dto.BuiltTransaction;
import com.radixdlt.client.lib.dto.Checkpoint;
import com.radixdlt.client.lib.dto.ConsensusConfiguration;
import com.radixdlt.client.lib.dto.ConsensusData;
import com.radixdlt.client.lib.dto.EpochData;
import com.radixdlt.client.lib.dto.FinalizedTransaction;
import com.radixdlt.client.lib.dto.ForkDetails;
import com.radixdlt.client.lib.dto.LocalAccount;
import com.radixdlt.client.lib.dto.LocalValidatorInfo;
import com.radixdlt.client.lib.dto.MempoolConfiguration;
import com.radixdlt.client.lib.dto.MempoolData;
import com.radixdlt.client.lib.dto.NetworkConfiguration;
import com.radixdlt.client.lib.dto.NetworkData;
import com.radixdlt.client.lib.dto.NetworkId;
import com.radixdlt.client.lib.dto.NetworkPeers;
import com.radixdlt.client.lib.dto.NetworkStats;
import com.radixdlt.client.lib.dto.Proof;
import com.radixdlt.client.lib.dto.RadixEngineData;
import com.radixdlt.client.lib.dto.StakePositions;
import com.radixdlt.client.lib.dto.SyncConfiguration;
import com.radixdlt.client.lib.dto.SyncData;
import com.radixdlt.client.lib.dto.TokenBalances;
import com.radixdlt.client.lib.dto.TokenInfo;
import com.radixdlt.client.lib.dto.TransactionDTO;
import com.radixdlt.client.lib.dto.TransactionHistory;
import com.radixdlt.client.lib.dto.TransactionStatusDTO;
import com.radixdlt.client.lib.dto.TxBlobDTO;
import com.radixdlt.client.lib.dto.TxDTO;
import com.radixdlt.client.lib.dto.UnstakePositions;
import com.radixdlt.client.lib.dto.ValidatorDTO;
import com.radixdlt.client.lib.dto.ValidatorsResponse;
import com.radixdlt.identifiers.AID;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * <h2>Asynchronous Radix JSON RPC client.</h2>
 * <p>
 * The Radix Web API consists of several endpoints which are assigned to two large groups. Each group served by
 * dedicated embedded HTTP server hence full configuration of the client requires base URL and two ports.
 * <p>
 * Each endpoint can be individually enabled or disabled, so even if client is successfully connected, it does not
 * mean that all API's are available. This should be kept in mind while using client with particular hode.
 * <p>
 * <h3>Client API structure</h3>
 * Due to API size, it is split into following groups:
 * <table>
 *     <tr><th>Name</th><th>Description</th></tr>
 *     <tr><td>Network</td><td>General information about network: ID, configuration, nodes, etc.</td></tr>
 *     <tr><td>Transaction</td><td>General purpose API for building and sending transactions, checking status, etc.</td></tr>
 *     <tr><td>Token</td><td>Information about tokens</td></tr>
 *     <tr><td>Local</td><td>Information about the node as well as single step transaction submission</td></tr>
 *     <tr><td>SingleAccount</td><td>Information related to single account: balances, transaction history, etc.</td></tr>
 *     <tr><td>Validator</td><td>List and lookup information about validators known to network</td></tr>
 *     <tr><td>Api</td><td>API configuration and metric counters</td></tr>
 *     <tr><td>Consensus</td><td>Consensus configuration and metric counters</td></tr>
 *     <tr><td>Mempool</td><td>Mempool configuration and metric counters</td></tr>
 *     <tr><td>RadixEngine</td><td>Radix Engine configuration and metric counters</td></tr>
 *     <tr><td>Sync</td><td>Node synchronization configuration and metric counters</td></tr>
 *     <tr><td>Ledger</td><td>Ledger proofs and checkpoints information</td></tr>
 * </table>
 */
public interface RadixApi {
	int DEFAULT_PRIMARY_PORT = 8080;
	int DEFAULT_SECONDARY_PORT = 3333;

	/**
	 * Create client and connect to specified node.
	 *
	 * @param baseUrl base URL to connect. Note that it should not include path part of the URL.
	 *
	 * @return {@link Promise} which will be resolved with built client or with error info.
	 */
	static Promise<RadixApi> connect(String baseUrl) {
		return connect(baseUrl, DEFAULT_PRIMARY_PORT, DEFAULT_SECONDARY_PORT);
	}

	/**
	 * Create client and connect to specified node at specified primary and secondary ports.
	 *
	 * @param baseUrl base URL to connect. Note that it should not include path part of the URL.
	 *
	 * @return {@link Promise} which will be resolved with built client or with error info.
	 */
	static Promise<RadixApi> connect(String baseUrl, int primaryPort, int secondaryPort) {
		return AsyncRadixApi.connect(baseUrl, primaryPort, secondaryPort);
	}

	/**
	 * Enable tracing in client.
	 */
	RadixApi withTrace();

	/**
	 * Configure timeout for asynchronous operations.
	 *
	 * @param timeout - operation timeout
	 */
	AsyncRadixApi withTimeout(Duration timeout);

	interface Network {
		/**
		 * Retrieve network ID.
		 */
		Promise<NetworkId> id();

		/**
		 * Retrieve current network throughput in transactions per second.
		 */
		Promise<NetworkStats> throughput();

		/**
		 * Retrieve current network demand in transactions per second.
		 */
		Promise<NetworkStats> demand();

		Promise<NetworkConfiguration> configuration();

		Promise<NetworkData> data();

		Promise<NetworkPeers> peers();
	}

	Network network();

	interface Transaction {
		Promise<BuiltTransaction> build(TransactionRequest request);

		Promise<TxBlobDTO> finalize(FinalizedTransaction request, boolean immediateSubmit);

		Promise<TxDTO> submit(TxBlobDTO request);

		Promise<TransactionDTO> lookup(AID txId);

		Promise<TransactionStatusDTO> status(AID txId);
	}

	Transaction transaction();

	interface Token {
		Promise<TokenInfo> describeNative();

		Promise<TokenInfo> describe(String rri);
	}

	Token token();

	interface Local {
		Promise<LocalAccount> accountInfo();

		Promise<TxDTO> submitTxSingleStep(TransactionRequest request);

		Promise<LocalValidatorInfo> validatorInfo();

		Promise<EpochData> currentEpoch();

		Promise<EpochData> nextEpoch();
	}

	Local local();

	interface SingleAccount {
		Promise<TokenBalances> balances(AccountAddress address);

		Promise<TransactionHistory> history(AccountAddress address, int size, Optional<NavigationCursor> cursor);

		Promise<List<StakePositions>> stakes(AccountAddress address);

		Promise<List<UnstakePositions>> unstakes(AccountAddress address);
	}

	SingleAccount account();

	interface Validator {
		Promise<ValidatorsResponse> list(int size, Optional<NavigationCursor> cursor);

		Promise<ValidatorDTO> lookup(ValidatorAddress validatorAddress);
	}

	Validator validator();

	interface Api {
		Promise<ApiConfiguration> configuration();

		Promise<ApiData> data();
	}

	Api api();

	interface Consensus {
		Promise<ConsensusConfiguration> configuration();

		Promise<ConsensusData> data();
	}

	Consensus consensus();

	interface Mempool {
		Promise<MempoolConfiguration> configuration();

		Promise<MempoolData> data();
	}

	Mempool mempool();

	interface RadixEngine {
		Promise<List<ForkDetails>> configuration();

		Promise<RadixEngineData> data();
	}

	RadixEngine radixEngine();

	interface Sync {
		Promise<SyncConfiguration> configuration();

		Promise<SyncData> data();
	}

	Sync sync();

	interface Ledger {
		Promise<Proof> latest(); //ledger.get_latest_proof

		Promise<Proof> epoch(); //ledger.get_latest_epoch_proof

		Promise<Checkpoint> checkpoints(); //checkpoints.get_checkpoints
	}

	Ledger ledger();
}














