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

package com.radixdlt.client.lib.api.sync;

import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.NavigationCursor;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.dto.ApiConfiguration;
import com.radixdlt.client.lib.dto.ApiData;
import com.radixdlt.client.lib.dto.BuiltTransaction;
import com.radixdlt.client.lib.dto.Checkpoint;
import com.radixdlt.client.lib.dto.ConsensusConfiguration;
import com.radixdlt.client.lib.dto.ConsensusData;
import com.radixdlt.client.lib.dto.EpochData;
import com.radixdlt.client.lib.dto.FinalizedTransaction;
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
import com.radixdlt.client.lib.dto.RadixEngineConfiguration;
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
import com.radixdlt.utils.functional.Result;

import java.util.List;
import java.util.Optional;

public interface ImperativeRadixApi {
	static ImperativeRadixApi connect(String baseUrl) {
		return toImperative(unwrap(RadixApi.connect(baseUrl)));
	}

	static ImperativeRadixApi connect(String baseUrl, int primaryPort, int secondaryPort) {
		return toImperative(unwrap(RadixApi.connect(baseUrl, primaryPort, secondaryPort)));
	}

	ImperativeRadixApi withTrace();

	interface Network {
		NetworkId id();

		NetworkStats throughput();

		NetworkStats demand();

		NetworkConfiguration configuration();

		NetworkData data();

		NetworkPeers peers();
	}

	Network network();

	interface Transaction {
		BuiltTransaction build(TransactionRequest request);

		TxBlobDTO finalize(FinalizedTransaction request);

		TxDTO submit(FinalizedTransaction request);

		TransactionDTO lookup(AID txId);

		TransactionStatusDTO status(AID txId);
	}

	Transaction transaction();

	interface Token {
		TokenInfo describeNative();

		TokenInfo describe(String rri);
	}

	Token token();

	interface Local {
		LocalAccount accountInfo();

		TxDTO submitTxSingleStep(TransactionRequest request);

		LocalValidatorInfo validatorInfo();

		EpochData currentEpoch();

		EpochData nextEpoch();
	}

	Local local();

	interface SingleAccount {
		TokenBalances balances(AccountAddress address);

		TransactionHistory history(AccountAddress address, int size, NavigationCursor cursor);

		List<StakePositions> stakes(AccountAddress address);

		List<UnstakePositions> unstakes(AccountAddress address);
	}

	SingleAccount account();

	interface Validator {
		ValidatorsResponse list(int size, NavigationCursor cursor);

		ValidatorDTO lookup(String validatorAddress);
	}

	Validator validator();

	interface Api {
		ApiConfiguration configuration();

		ApiData data();
	}

	Api api();

	interface Consensus {
		ConsensusConfiguration configuration();

		ConsensusData data();
	}

	Consensus consensus();

	interface Mempool {
		MempoolConfiguration configuration();

		MempoolData data();
	}

	Mempool mempool();

	interface RadixEngine {
		RadixEngineConfiguration configuration();

		RadixEngineData data();
	}

	RadixEngine radixEngine();

	interface Sync {
		SyncConfiguration configuration();

		SyncData data();
	}

	Sync sync();

	interface Ledger {
		Proof latest(); //ledger.get_latest_proof

		Proof epoch(); //ledger.get_latest_epoch_proof

		Checkpoint checkpoints(); //checkpoints.get_checkpoints
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
					public NetworkPeers peers() {
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
					public TxBlobDTO finalize(FinalizedTransaction request) {
						return unwrap(api.transaction().finalize(request));
					}

					@Override
					public TxDTO submit(FinalizedTransaction request) {
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

					@Override
					public EpochData nextEpoch() {
						return unwrap(api.local().nextEpoch());
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
					public TransactionHistory history(AccountAddress address, int size, NavigationCursor cursor) {
						return unwrap(api.account().history(address, size, Optional.ofNullable(cursor)));
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
					public ValidatorsResponse list(int size, NavigationCursor cursor) {
						return unwrap(api.validator().list(size, Optional.ofNullable(cursor)));
					}

					@Override
					public ValidatorDTO lookup(String validatorAddress) {
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
					public RadixEngineConfiguration configuration() {
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
