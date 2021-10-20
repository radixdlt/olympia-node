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

package com.radixdlt.client.lib.api.async;

import com.radixdlt.api.addressing.AccountAddress;
import com.radixdlt.api.addressing.ValidatorAddress;
import com.radixdlt.api.dto.common.NavigationCursor;
import com.radixdlt.api.dto.response.AddressBookEntry;
import com.radixdlt.api.dto.response.ApiConfiguration;
import com.radixdlt.api.dto.response.ApiData;
import com.radixdlt.api.dto.response.BuiltTransaction;
import com.radixdlt.api.dto.response.Checkpoint;
import com.radixdlt.api.dto.response.ConsensusConfiguration;
import com.radixdlt.api.dto.response.ConsensusData;
import com.radixdlt.api.dto.response.EpochData;
import com.radixdlt.api.dto.response.FinalizedTransaction;
import com.radixdlt.api.dto.response.ForkDetails;
import com.radixdlt.api.dto.response.LocalAccount;
import com.radixdlt.api.dto.response.LocalValidatorInfo;
import com.radixdlt.api.dto.response.MempoolConfiguration;
import com.radixdlt.api.dto.response.MempoolData;
import com.radixdlt.api.dto.response.NetworkConfiguration;
import com.radixdlt.api.dto.response.NetworkData;
import com.radixdlt.api.dto.response.NetworkId;
import com.radixdlt.api.dto.response.NetworkPeer;
import com.radixdlt.api.dto.response.NetworkStats;
import com.radixdlt.api.dto.response.Proof;
import com.radixdlt.api.dto.response.RadixEngineData;
import com.radixdlt.api.dto.response.StakePositions;
import com.radixdlt.api.dto.response.SyncConfiguration;
import com.radixdlt.api.dto.response.SyncData;
import com.radixdlt.api.dto.response.TokenBalances;
import com.radixdlt.api.dto.response.TokenInfo;
import com.radixdlt.api.dto.response.TransactionDTO;
import com.radixdlt.api.dto.response.TransactionHistory;
import com.radixdlt.api.dto.response.TransactionStatusDTO;
import com.radixdlt.api.dto.response.TransactionsDTO;
import com.radixdlt.api.dto.response.TxBlobDTO;
import com.radixdlt.api.dto.response.TxDTO;
import com.radixdlt.api.dto.response.UnstakePositions;
import com.radixdlt.api.dto.response.ValidatorDTO;
import com.radixdlt.api.dto.response.ValidatorsResponse;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.api.rpc.BasicAuth;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.AID;
import com.radixdlt.networks.Addressing;
import com.radixdlt.utils.functional.Promise;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * <h2>Asynchronous Radix JSON RPC client.</h2>
 * <p>
 * The Radix Web API consists of several endpoints which are assigned to two large groups. Each group served by
 * dedicated embedded HTTP server hence full configuration of the client requires base URL and two ports.
 * <p>
 * Each endpoint can be individually enabled or disabled, so even if client is successfully connected, it does not
 * mean that all API's are available. This should be kept in mind while using client with particular node.
 * <p>
 * <h3>Client API structure</h3>
 * API is split into following groups:
 * <p>
 * <table>
 * <caption style="display:none">apiGroups</caption>
 * <tbody>
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
 * </tbody>
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
	 * Create client and connect to specified node.
	 *
	 * @param baseUrl base URL to connect. Note that it should not include path part of the URL.
	 * @param authentication Login/password for basic authentication
	 *
	 * @return {@link Promise} which will be resolved with built client or with error info.
	 */
	static Promise<RadixApi> connect(String baseUrl, BasicAuth authentication) {
		return connect(baseUrl, DEFAULT_PRIMARY_PORT, DEFAULT_SECONDARY_PORT, authentication);
	}

	/**
	 * Create client and connect to specified node at specified primary and secondary ports.
	 *
	 * @param baseUrl base URL to connect. Note that it should not include path part of the URL.
	 * @param primaryPort primary API port
	 * @param secondaryPort secondary API port
	 *
	 * @return {@link Promise} which will be resolved with built client or with error info.
	 */
	static Promise<RadixApi> connect(String baseUrl, int primaryPort, int secondaryPort) {
		return AsyncRadixApi.connect(baseUrl, primaryPort, secondaryPort, Optional.empty());
	}

	/**
	 * Create client and connect to specified node at specified primary and secondary ports.
	 *
	 * @param baseUrl base URL to connect. Note that it should not include path part of the URL.
	 * @param primaryPort primary API port
	 * @param secondaryPort secondary API port
	 * @param authentication Login/password for basic authentication
	 *
	 * @return {@link Promise} which will be resolved with built client or with error info.
	 */
	static Promise<RadixApi> connect(String baseUrl, int primaryPort, int secondaryPort, BasicAuth authentication) {
		return AsyncRadixApi.connect(baseUrl, primaryPort, secondaryPort, Optional.of(authentication));
	}

	/**
	 * Enable tracing in client.
	 */
	RadixApi withTrace();

	/**
	 * Get {@link Addressing} instance corresponding to connected network
	 */
	Addressing addressing();

	/**
	 * Configure timeout for asynchronous operations.
	 *
	 * @param timeout - operation timeout
	 */
	AsyncRadixApi withTimeout(Duration timeout);

	/**
	 * Network API's
	 */
	interface Network {
		/**
		 * Get network ID.
		 */
		Promise<NetworkId> id();

		/**
		 * Get current network throughput in transactions per second.
		 */
		Promise<NetworkStats> throughput();

		/**
		 * Get current network demand in transactions per second.
		 */
		Promise<NetworkStats> demand();

		/**
		 * Get current network configuration.
		 */
		Promise<NetworkConfiguration> configuration();

		/**
		 * Get network metrics.
		 */
		Promise<NetworkData> data();

		/**
		 * Get network peers.
		 */
		Promise<List<NetworkPeer>> peers();

		/**
		 * Get current address book.
		 */
		Promise<List<AddressBookEntry>> addressBook();
	}

	Network network();

	/**
	 * Transaction API's.
	 * <p>
	 * Radix API uses three step transaction submission:
	 * <ol>
	 *     <li>Build - transaction blob is assembled from the high level action description</li>
	 *     <li>Finalize - transaction is prepared, validated, transaction ID is calculated and returned</li>
	 *     <li>Submit - transaction is actually submitted to mempool</li>
	 * </ol>
	 * This process is designed for the case of very unreliable communication, to prevent double submission and
	 * other potential issues. If this is less of an issue in particular use case, it is possible to omit last
	 * step and submit transaction during finalization step. To achieve this, set {@code immediateSubmit} flag
	 * in {@link #finalize(FinalizedTransaction, boolean)} to {@code true}.
	 */
	interface Transaction {
		/**
		 * Build transaction for a given transaction request.
		 *
		 * @param request transaction request
		 */
		Promise<BuiltTransaction> build(TransactionRequest request);

		/**
		 * Finalize transaction.
		 *
		 * @param request transaction request (can be built from {@link BuiltTransaction} by invoking {@link BuiltTransaction#toFinalized(ECKeyPair)}
		 * 	method)
		 * @param immediateSubmit if set to {@code true} then transaction will be immediately submitted to mempool
		 */
		Promise<TxBlobDTO> finalize(FinalizedTransaction request, boolean immediateSubmit);

		/**
		 * Submit transaction.
		 *
		 * @param request transaction request
		 */
		Promise<TxDTO> submit(TxBlobDTO request);

		/**
		 * Lookup transaction.
		 *
		 * @param txId the ID of the transaction to look up
		 */
		Promise<TransactionDTO> lookup(AID txId);

		/**
		 * Get transaction status.
		 *
		 * @param txId the ID of the transaction to get status for
		 */
		Promise<TransactionStatusDTO> status(AID txId);

		/**
		 * Get paginated list of indexed transactions.
		 *
		 * @param limit number of transactions to return
		 * @param offset starting offset
		 */
		Promise<TransactionsDTO> list(long limit, OptionalLong offset);
	}

	Transaction transaction();

	/**
	 * Token-related API's
	 */
	interface Token {
		/**
		 * Get description of the native token.
		 */
		Promise<TokenInfo> describeNative();

		/**
		 * Get description of the token with a given RRI.
		 */
		Promise<TokenInfo> describe(String rri);
	}

	Token token();

	/**
	 * API's which deal with information local to node to which client is connected.
	 * <p>
	 * <b>WARNING:</b> These API's may expose or use security-sensitive information. Use with care.
	 */
	interface Local {
		/**
		 * Get local node account information.
		 */
		Promise<LocalAccount> accountInfo();

		/**
		 * Submit transaction is single step, using local node private key to sign the transaction.
		 *
		 * @param request high level action description
		 */
		Promise<TxDTO> submitTxSingleStep(TransactionRequest request);

		/**
		 * Get information about local node as a validator.
		 */
		Promise<LocalValidatorInfo> validatorInfo();

		/**
		 * Get information about current epoch validator set.
		 */
		Promise<EpochData> currentEpoch();
	}

	Local local();

	/**
	 * Single account address API's
	 */
	interface SingleAccount {
		/**
		 * Get account balances.
		 *
		 * @param address account address for which information is requested
		 */
		Promise<TokenBalances> balances(AccountAddress address);

		/**
		 * Get transaction history.
		 *
		 * @param address account address for which information is requested
		 * @param size batch size
		 * @param offset offset to start retrieval at
		 */
		Promise<TransactionHistory> history(AccountAddress address, int size, OptionalLong offset);

		/**
		 * Get stakes made from given account.
		 *
		 * @param address account address for which information is requested
		 */
		Promise<List<StakePositions>> stakes(AccountAddress address);

		/**
		 * Get pending (not yet transferred back) unstakes.
		 *
		 * @param address account address for which information is requested
		 */
		Promise<List<UnstakePositions>> unstakes(AccountAddress address);
	}

	SingleAccount account();

	/**
	 * General validator information API's
	 */
	interface Validator {
		/**
		 * Get paginated list of all validators known to the network.
		 * <p>
		 * To get full list, pass empty cursor for first request and then just pass cursor received in the response
		 * back to API until you get empty cursor again.
		 *
		 * @param size batch size
		 * @param cursor pagination cursor
		 */
		Promise<ValidatorsResponse> list(int size, Optional<NavigationCursor> cursor);

		/**
		 * Lookup validator by address.
		 *
		 * @param validatorAddress validator address
		 */
		Promise<ValidatorDTO> lookup(ValidatorAddress validatorAddress);
	}

	Validator validator();

	/**
	 * Node API configuration and metrics.
	 */
	interface Api {
		/**
		 * Get API configuration.
		 */
		Promise<ApiConfiguration> configuration();

		/**
		 * Get API metrics.
		 */
		Promise<ApiData> data();
	}

	Api api();

	/**
	 * Consensus configuration and metrics.
	 */
	interface Consensus {
		/**
		 * Get consensus configuration.
		 */
		Promise<ConsensusConfiguration> configuration();

		/**
		 * Get consensus metrics.
		 */
		Promise<ConsensusData> data();
	}

	Consensus consensus();

	/**
	 * Mempool configuration and metrics.
	 */
	interface Mempool {
		/**
		 * Get mempool configuration.
		 */
		Promise<MempoolConfiguration> configuration();

		/**
		 * Get mempool metrics.
		 */
		Promise<MempoolData> data();
	}

	Mempool mempool();

	/**
	 * RadixEngine configuration and metrics.
	 */
	interface RadixEngine {
		/**
		 * Get Radix Engine configuration.
		 */
		Promise<List<ForkDetails>> configuration();

		/**
		 * Get Radix Engine metrics.
		 */
		Promise<RadixEngineData> data();
	}

	RadixEngine radixEngine();

	/**
	 * Inter-node synchronization configuration and metrics.
	 */
	interface Sync {
		/**
		 * Get synchronization configuration.
		 */
		Promise<SyncConfiguration> configuration();

		/**
		 * Get synchronization metrics.
		 */
		Promise<SyncData> data();
	}

	Sync sync();

	/**
	 * Ledger API's.
	 */
	interface Ledger {
		/**
		 * Get latest proof.
		 */
		Promise<Proof> latest();

		/**
		 * Get latest epoch proof.
		 */
		Promise<Proof> epoch();

		/**
		 * Get checkpoint configuration.
		 */
		Promise<Checkpoint> checkpoints();
	}

	Ledger ledger();
}
