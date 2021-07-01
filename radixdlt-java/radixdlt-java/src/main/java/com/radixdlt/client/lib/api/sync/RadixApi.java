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
import com.radixdlt.client.lib.api.ValidatorAddress;
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

public interface RadixApi {
	int DEFAULT_PRIMARY_PORT = 8080;
	int DEFAULT_SECONDARY_PORT = 3333;

	static Result<RadixApi> connect(String baseUrl) {
		return connect(baseUrl, DEFAULT_PRIMARY_PORT, DEFAULT_SECONDARY_PORT);
	}

	static Result<RadixApi> connect(String baseUrl, int primaryPort, int secondaryPort) {
		return SyncRadixApi.connect(baseUrl, primaryPort, secondaryPort);
	}

	RadixApi withTrace();

	interface Network {
		Result<NetworkId> id();

		Result<NetworkStats> throughput();

		Result<NetworkStats> demand();

		Result<NetworkConfiguration> configuration();

		Result<NetworkData> data();

		Result<NetworkPeers> peers();
	}

	Network network();

	interface Transaction {
		Result<BuiltTransaction> build(TransactionRequest request);

		Result<TxBlobDTO> finalize(FinalizedTransaction request, boolean immediateSubmit);

		Result<TxDTO> submit(TxBlobDTO request);

		Result<TransactionDTO> lookup(AID txId);

		Result<TransactionStatusDTO> status(AID txId);
	}

	Transaction transaction();

	interface Token {
		Result<TokenInfo> describeNative();

		Result<TokenInfo> describe(String rri);
	}

	Token token();

	interface Local {
		Result<LocalAccount> accountInfo();

		Result<TxDTO> submitTxSingleStep(TransactionRequest request);

		Result<LocalValidatorInfo> validatorInfo();

		Result<EpochData> currentEpoch();

		Result<EpochData> nextEpoch();
	}

	Local local();

	interface SingleAccount {
		Result<TokenBalances> balances(AccountAddress address);

		Result<TransactionHistory> history(AccountAddress address, int size, Optional<NavigationCursor> cursor);

		Result<List<StakePositions>> stakes(AccountAddress address);

		Result<List<UnstakePositions>> unstakes(AccountAddress address);
	}

	SingleAccount account();

	interface Validator {
		Result<ValidatorsResponse> list(int size, Optional<NavigationCursor> cursor);

		Result<ValidatorDTO> lookup(ValidatorAddress validatorAddress);
	}

	Validator validator();

	interface Api {
		Result<ApiConfiguration> configuration();

		Result<ApiData> data();
	}

	Api api();

	interface Consensus {
		Result<ConsensusConfiguration> configuration();

		Result<ConsensusData> data();
	}

	Consensus consensus();

	interface Mempool {
		Result<MempoolConfiguration> configuration();

		Result<MempoolData> data();
	}

	Mempool mempool();

	interface RadixEngine {
		Result<RadixEngineConfiguration> configuration();

		Result<RadixEngineData> data();
	}

	RadixEngine radixEngine();

	interface Sync {
		Result<SyncConfiguration> configuration();

		Result<SyncData> data();
	}

	Sync sync();

	interface Ledger {
		Result<Proof> latest(); //ledger.get_latest_proof

		Result<Proof> epoch(); //ledger.get_latest_epoch_proof

		Result<Checkpoint> checkpoints(); //checkpoints.get_checkpoints
	}

	Ledger ledger();
}
