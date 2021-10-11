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

package com.radixdlt.client.lib.api.sync;

import com.radixdlt.api.rpc.dto.ApiConfiguration;
import com.radixdlt.api.rpc.dto.ApiData;
import com.radixdlt.api.rpc.dto.BuiltTransaction;
import com.radixdlt.api.rpc.dto.Checkpoint;
import com.radixdlt.api.rpc.dto.ConsensusConfiguration;
import com.radixdlt.api.rpc.dto.ConsensusData;
import com.radixdlt.api.rpc.dto.EpochData;
import com.radixdlt.api.rpc.dto.FinalizedTransaction;
import com.radixdlt.api.rpc.dto.ForkDetails;
import com.radixdlt.api.rpc.dto.LocalAccount;
import com.radixdlt.api.rpc.dto.LocalValidatorInfo;
import com.radixdlt.api.rpc.dto.MempoolConfiguration;
import com.radixdlt.api.rpc.dto.MempoolData;
import com.radixdlt.api.rpc.dto.NetworkConfiguration;
import com.radixdlt.api.rpc.dto.NetworkData;
import com.radixdlt.api.rpc.dto.NetworkId;
import com.radixdlt.api.rpc.dto.NetworkPeer;
import com.radixdlt.api.rpc.dto.NetworkStats;
import com.radixdlt.api.rpc.dto.Proof;
import com.radixdlt.api.rpc.dto.RadixEngineData;
import com.radixdlt.api.rpc.dto.StakePositions;
import com.radixdlt.api.rpc.dto.SyncConfiguration;
import com.radixdlt.api.rpc.dto.SyncData;
import com.radixdlt.api.rpc.dto.TokenBalances;
import com.radixdlt.api.rpc.dto.TokenInfo;
import com.radixdlt.api.rpc.dto.TransactionDTO;
import com.radixdlt.api.rpc.dto.TransactionHistory;
import com.radixdlt.api.rpc.dto.TransactionStatusDTO;
import com.radixdlt.api.rpc.dto.TxBlobDTO;
import com.radixdlt.api.rpc.dto.TxDTO;
import com.radixdlt.api.rpc.dto.UnstakePositions;
import com.radixdlt.api.rpc.dto.ValidatorDTO;
import com.radixdlt.api.rpc.dto.ValidatorsResponse;
import com.radixdlt.api.types.AccountAddress;
import com.radixdlt.api.types.NavigationCursor;
import com.radixdlt.api.types.TransactionRequest;
import com.radixdlt.api.types.ValidatorAddress;
import com.radixdlt.client.lib.api.rpc.BasicAuth;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.functional.Result;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * <h2>Imperative version of synchronous Radix JSON RPC client.</h2>
 * This version of the API converts functional style synchronous API's into more
 * traditional "throw if error"-style Java API.
 * <p>
 * The Radix Web API consists of several endpoints which are assigned to two large groups. Each group served by
 * dedicated embedded HTTP server hence full configuration of the client requires base URL and two ports.
 * <p>
 * Each endpoint can be individually enabled or disabled, so even if client is successfully connected, it does not
 * mean that all API's are available. This should be kept in mind while using client with particular hode.
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
public interface ImperativeRadixApi {
	/**
	 * Create client and connect to specified node.
	 *
	 * @param baseUrl base URL to connect. Note that it should not include path part of the URL.
	 *
	 * @return created client
	 */

	static ImperativeRadixApi connect(String baseUrl) {
		return toImperative(unwrap(RadixApi.connect(baseUrl)));
	}

	/**
	 * Create client and connect to specified node.
	 *
	 * @param baseUrl base URL to connect. Note that it should not include path part of the URL.
	 * @param authentication Login/password for basic authentication
	 *
	 * @return created client
	 */
	static ImperativeRadixApi connect(String baseUrl, BasicAuth authentication) {
		return toImperative(unwrap(RadixApi.connect(baseUrl, authentication)));
	}

	/**
	 * Create client and connect to specified node at specified primary and secondary ports.
	 *
	 * @param baseUrl base URL to connect. Note that it should not include path part of the URL.
	 * @param primaryPort primary API port
	 * @param secondaryPort secondary API port
	 *
	 * @return created client
	 */
	static ImperativeRadixApi connect(String baseUrl, int primaryPort, int secondaryPort) {
		return toImperative(unwrap(RadixApi.connect(baseUrl, primaryPort, secondaryPort)));
	}

	/**
	 * Create client and connect to specified node at specified primary and secondary ports.
	 *
	 * @param baseUrl base URL to connect. Note that it should not include path part of the URL.
	 * @param primaryPort primary API port
	 * @param secondaryPort secondary API port
	 * @param authentication Login/password for basic authentication
	 *
	 * @return created client
	 */
	static ImperativeRadixApi connect(String baseUrl, int primaryPort, int secondaryPort, BasicAuth authentication) {
		return toImperative(unwrap(RadixApi.connect(baseUrl, primaryPort, secondaryPort, authentication)));
	}

	/**
	 * Enable tracing in client.
	 */
	ImperativeRadixApi withTrace();

	/**
	 * Configure timeout for network operations.
	 *
	 * @param timeout - operation timeout
	 */
	ImperativeRadixApi withTimeout(Duration timeout);

	/**
	 * Network API's
	 */
	interface Network {
		/**
		 * Get network ID.
		 */
		NetworkId id();

		/**
		 * Get current network throughput in transactions per second.
		 */
		NetworkStats throughput();

		/**
		 * Get current network demand in transactions per second.
		 */
		NetworkStats demand();

		/**
		 * Get current network configuration.
		 */
		NetworkConfiguration configuration();

		/**
		 * Get network metrics.
		 */
		NetworkData data();

		/**
		 * Get network peers.
		 */
		List<NetworkPeer> peers();
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
		BuiltTransaction build(TransactionRequest request);

		/**
		 * Finalize transaction.
		 *
		 * @param request transaction request (can be built from {@link BuiltTransaction} by invoking {@link BuiltTransaction#toFinalized(ECKeyPair)}
		 * 	method)
		 * @param immediateSubmit if set to {@code true} then transaction will be immediately submitted to mempool
		 */
		TxBlobDTO finalize(FinalizedTransaction request, boolean immediateSubmit);

		/**
		 * Submit transaction.
		 *
		 * @param request transaction request
		 */
		TxDTO submit(TxBlobDTO request);

		/**
		 * Lookup transaction.
		 *
		 * @param txId the ID of the transaction to look up
		 */
		TransactionDTO lookup(AID txId);

		/**
		 * Get transaction status.
		 *
		 * @param txId the ID of the transaction to get status for
		 */
		TransactionStatusDTO status(AID txId);
	}

	Transaction transaction();

	/**
	 * Token-related API's
	 */
	interface Token {
		/**
		 * Get description of the native token.
		 */
		TokenInfo describeNative();

		/**
		 * Get description of the token with a given RRI.
		 */
		TokenInfo describe(String rri);
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
		LocalAccount accountInfo();

		/**
		 * Submit transaction is single step, using local node private key to sign the transaction.
		 *
		 * @param request high level action description
		 */
		TxDTO submitTxSingleStep(TransactionRequest request);

		/**
		 * Get information about local node as a validator.
		 */
		LocalValidatorInfo validatorInfo();

		/**
		 * Get information about current epoch validator set.
		 */
		EpochData currentEpoch();
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
		TokenBalances balances(AccountAddress address);

		/**
		 * Get transaction history.
		 *
		 * @param address account address for which information is requested
		 * @param size batch size
		 * @param offset offset to start retrieval at
		 * @param verbose provide extended information about transactions
		 */
		TransactionHistory history(AccountAddress address, int size, OptionalLong offset, boolean verbose);

		/**
		 * Get stakes made from given account.
		 *
		 * @param address account address for which information is requested
		 */
		List<StakePositions> stakes(AccountAddress address);

		/**
		 * Get pending (not yet transferred back) unstakes.
		 *
		 * @param address account address for which information is requested
		 */
		List<UnstakePositions> unstakes(AccountAddress address);
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
		ValidatorsResponse list(int size, Optional<NavigationCursor> cursor);

		/**
		 * Lookup validator by address.
		 *
		 * @param validatorAddress validator address
		 */
		ValidatorDTO lookup(ValidatorAddress validatorAddress);
	}

	Validator validator();

	/**
	 * Node API configuration and metrics.
	 */
	interface Api {
		/**
		 * Get API configuration.
		 */
		ApiConfiguration configuration();

		/**
		 * Get API metrics.
		 */
		ApiData data();
	}

	Api api();

	/**
	 * Consensus configuration and metrics.
	 */
	interface Consensus {
		/**
		 * Get consensus configuration.
		 */
		ConsensusConfiguration configuration();

		/**
		 * Get consensus metrics.
		 */
		ConsensusData data();
	}

	Consensus consensus();

	/**
	 * Mempool configuration and metrics.
	 */
	interface Mempool {
		/**
		 * Get mempool configuration.
		 */
		MempoolConfiguration configuration();

		/**
		 * Get mempool metrics.
		 */
		MempoolData data();
	}

	Mempool mempool();

	/**
	 * RadixEngine configuration and metrics.
	 */
	interface RadixEngine {
		/**
		 * Get Radix Engine configuration.
		 */
		List<ForkDetails> configuration();

		/**
		 * Get Radix Engine metrics.
		 */
		RadixEngineData data();
	}

	RadixEngine radixEngine();

	/**
	 * Inter-node synchronization configuration and metrics.
	 */
	interface Sync {
		/**
		 * Get synchronization configuration.
		 */
		SyncConfiguration configuration();

		/**
		 * Get synchronization metrics.
		 */
		SyncData data();
	}

	Sync sync();

	/**
	 * Ledger API's.
	 */
	interface Ledger {
		/**
		 * Get latest proof.
		 */
		Proof latest();

		/**
		 * Get latest epoch proof.
		 */
		Proof epoch();

		/**
		 * Get checkpoint configuration.
		 */
		Checkpoint checkpoints();
	}

	Ledger ledger();

	static <T> T unwrap(Result<T> value) {
		return value.fold(failure -> {
			throw new RadixApiException(failure);
		}, content -> content);
	}

	// CHECKSTYLE:OFF checkstyle:MethodLength
	static ImperativeRadixApi toImperative(RadixApi api) {
		return new ImperativeRadixApi() {
			@Override
			public ImperativeRadixApi withTrace() {
				api.withTrace();
				return this;
			}

			@Override
			public ImperativeRadixApi withTimeout(Duration timeout) {
				api.withTimeout(timeout);
				return this;
			}

			@Override
			public Network network() {
				return new Network() {
					@Override
					public NetworkId id() {
						return unwrap(api.network().id());
					}

					@Override
					public NetworkStats throughput() {
						return unwrap(api.network().throughput());
					}

					@Override
					public NetworkStats demand() {
						return unwrap(api.network().demand());
					}

					@Override
					public NetworkConfiguration configuration() {
						return unwrap(api.network().configuration());
					}

					@Override
					public NetworkData data() {
						return unwrap(api.network().data());
					}

					@Override
					public List<NetworkPeer> peers() {
						return unwrap(api.network().peers());
					}
				};
			}

			@Override
			public Transaction transaction() {
				return new Transaction() {
					@Override
					public BuiltTransaction build(TransactionRequest request) {
						return unwrap(api.transaction().build(request));
					}

					@Override
					public TxBlobDTO finalize(FinalizedTransaction request, boolean immediateSubmit) {
						return unwrap(api.transaction().finalize(request, immediateSubmit));
					}

					@Override
					public TxDTO submit(TxBlobDTO request) {
						return unwrap(api.transaction().submit(request));
					}

					@Override
					public TransactionDTO lookup(AID txId) {
						return unwrap(api.transaction().lookup(txId));
					}

					@Override
					public TransactionStatusDTO status(AID txId) {
						return unwrap(api.transaction().status(txId));
					}
				};
			}

			@Override
			public Token token() {
				return new Token() {
					@Override
					public TokenInfo describeNative() {
						return unwrap(api.token().describeNative());
					}

					@Override
					public TokenInfo describe(String rri) {
						return unwrap(api.token().describe(rri));
					}
				};
			}

			@Override
			public Local local() {
				return new Local() {
					@Override
					public LocalAccount accountInfo() {
						return unwrap(api.local().accountInfo());
					}

					@Override
					public TxDTO submitTxSingleStep(TransactionRequest request) {
						return unwrap(api.local().submitTxSingleStep(request));
					}

					@Override
					public LocalValidatorInfo validatorInfo() {
						return unwrap(api.local().validatorInfo());
					}

					@Override
					public EpochData currentEpoch() {
						return unwrap(api.local().currentEpoch());
					}
				};
			}

			@Override
			public SingleAccount account() {
				return new SingleAccount() {
					@Override
					public TokenBalances balances(AccountAddress address) {
						return unwrap(api.account().balances(address));
					}

					@Override
					public TransactionHistory history(AccountAddress address, int size, OptionalLong offset, boolean verbose) {
						return unwrap(api.account().history(address, size, offset, verbose));
					}

					@Override
					public List<StakePositions> stakes(AccountAddress address) {
						return unwrap(api.account().stakes(address));
					}

					@Override
					public List<UnstakePositions> unstakes(AccountAddress address) {
						return unwrap(api.account().unstakes(address));
					}
				};
			}

			@Override
			public Validator validator() {
				return new Validator() {
					@Override
					public ValidatorsResponse list(int size, Optional<NavigationCursor> cursor) {
						return unwrap(api.validator().list(size, cursor));
					}

					@Override
					public ValidatorDTO lookup(ValidatorAddress validatorAddress) {
						return unwrap(api.validator().lookup(validatorAddress));
					}
				};
			}

			@Override
			public Api api() {
				return new Api() {
					@Override
					public ApiConfiguration configuration() {
						return unwrap(api.api().configuration());
					}

					@Override
					public ApiData data() {
						return unwrap(api.api().data());
					}
				};
			}

			@Override
			public Consensus consensus() {
				return new Consensus() {
					@Override
					public ConsensusConfiguration configuration() {
						return unwrap(api.consensus().configuration());
					}

					@Override
					public ConsensusData data() {
						return unwrap(api.consensus().data());
					}
				};
			}

			@Override
			public Mempool mempool() {
				return new Mempool() {
					@Override
					public MempoolConfiguration configuration() {
						return unwrap(api.mempool().configuration());
					}

					@Override
					public MempoolData data() {
						return unwrap(api.mempool().data());
					}
				};
			}

			@Override
			public RadixEngine radixEngine() {
				return new RadixEngine() {
					@Override
					public List<ForkDetails> configuration() {
						return unwrap(api.radixEngine().configuration());
					}

					@Override
					public RadixEngineData data() {
						return unwrap(api.radixEngine().data());
					}
				};
			}

			@Override
			public Sync sync() {
				return new Sync() {
					@Override
					public SyncConfiguration configuration() {
						return unwrap(api.sync().configuration());
					}

					@Override
					public SyncData data() {
						return unwrap(api.sync().data());
					}
				};
			}

			@Override
			public Ledger ledger() {
				return new Ledger() {
					@Override
					public Proof latest() {
						return unwrap(api.ledger().latest());
					}

					@Override
					public Proof epoch() {
						return unwrap(api.ledger().epoch());
					}

					@Override
					public Checkpoint checkpoints() {
						return unwrap(api.ledger().checkpoints());
					}
				};
			}
		};
	}
	// CHECKSTYLE:ON
}
