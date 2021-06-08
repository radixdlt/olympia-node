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

package com.radixdlt.client.lib.api;

import com.radixdlt.client.lib.dto.BuiltTransactionDTO;
import com.radixdlt.client.lib.dto.FinalizedTransaction;
import com.radixdlt.client.lib.dto.NetworkIdDTO;
import com.radixdlt.client.lib.dto.NetworkStatsDTO;
import com.radixdlt.client.lib.dto.StakePositionsDTO;
import com.radixdlt.client.lib.dto.TokenBalancesDTO;
import com.radixdlt.client.lib.dto.TokenInfoDTO;
import com.radixdlt.client.lib.dto.TransactionDTO;
import com.radixdlt.client.lib.dto.TransactionHistoryDTO;
import com.radixdlt.client.lib.dto.TransactionStatusDTO;
import com.radixdlt.client.lib.dto.TxDTO;
import com.radixdlt.client.lib.dto.UnstakePositionsDTO;
import com.radixdlt.client.lib.dto.ValidatorDTO;
import com.radixdlt.client.lib.dto.ValidatorsResponseDTO;
import com.radixdlt.client.lib.dto.extra.ApiConfigurationDTO;
import com.radixdlt.client.lib.dto.extra.ApiDataDTO;
import com.radixdlt.client.lib.dto.extra.CheckpointDTO;
import com.radixdlt.client.lib.dto.extra.ConsensusConfigurationDTO;
import com.radixdlt.client.lib.dto.extra.ConsensusDataDTO;
import com.radixdlt.client.lib.dto.extra.EpochDataDTO;
import com.radixdlt.client.lib.dto.extra.LocalAccountDTO;
import com.radixdlt.client.lib.dto.extra.LocalValidatorInfoDTO;
import com.radixdlt.client.lib.dto.extra.MempoolConfigurationDTO;
import com.radixdlt.client.lib.dto.extra.MempoolDataDTO;
import com.radixdlt.client.lib.dto.extra.NetworkConfigurationDTO;
import com.radixdlt.client.lib.dto.extra.NetworkDataDTO;
import com.radixdlt.client.lib.dto.extra.NetworkPeersDTO;
import com.radixdlt.client.lib.dto.extra.ProofDTO;
import com.radixdlt.client.lib.dto.extra.RadixEngineConfigurationDTO;
import com.radixdlt.client.lib.dto.extra.RadixEngineDataDTO;
import com.radixdlt.client.lib.dto.extra.SyncConfigurationDTO;
import com.radixdlt.client.lib.dto.extra.SyncDataDTO;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.functional.Result;

import java.util.List;
import java.util.Optional;

public interface RadixApi {
	static Result<SynchronousRadixApiClient> connect(String baseUrl) {
		return SynchronousRadixApiClient.connect(baseUrl);
	}

	RadixApi withTrace();

	interface Network {
		Result<NetworkIdDTO> id();

		Result<NetworkStatsDTO> throughput();

		Result<NetworkStatsDTO> demand();

		Result<NetworkConfigurationDTO> configuration();

		Result<NetworkDataDTO> data();

		Result<NetworkPeersDTO> peers();
	}

	Network network();

	interface Transaction {
		Result<BuiltTransactionDTO> build(TransactionRequest request);

		Result<TxDTO> finalize(FinalizedTransaction request);

		Result<TxDTO> submit(FinalizedTransaction request);

		Result<TransactionDTO> lookup(AID txId);

		Result<TransactionStatusDTO> status(AID txId);
	}

	Transaction transaction();

	interface Token {
		Result<TokenInfoDTO> describeNative();

		Result<TokenInfoDTO> describe(String rri);
	}

	Token token();

	interface Local {
		Result<LocalAccountDTO> accountInfo();

		Result<TxDTO> submitTxSingleStep();

		Result<LocalValidatorInfoDTO> validatorInfo(); //validation.get_node_info

		Result<EpochDataDTO> currentEpoch(); //validation.get_current_epoch_data

		Result<EpochDataDTO> nextEpoch(); //validation.get_next_epoch_data
	}

	Local local();

	interface SingleAccount {
		Result<TokenBalancesDTO> balances(AccountAddress address);

		Result<TransactionHistoryDTO> history(AccountAddress address, int size, Optional<NavigationCursor> cursor);

		Result<List<StakePositionsDTO>> stakes(AccountAddress address);

		Result<List<UnstakePositionsDTO>> unstakes(AccountAddress address);
	}

	SingleAccount account();

	interface Validator {
		Result<ValidatorsResponseDTO> list(int size, Optional<NavigationCursor> cursor);

		Result<ValidatorDTO> lookup(String validatorAddress);
	}

	Validator validator();

	interface Api {
		Result<ApiConfigurationDTO> configuration();

		Result<ApiDataDTO> data();
	}

	Api api();

	interface Consensus {
		Result<ConsensusConfigurationDTO> configuration();

		Result<ConsensusDataDTO> data();
	}

	Consensus consensus();

	interface Mempool {
		Result<MempoolConfigurationDTO> configuration();

		Result<MempoolDataDTO> data();
	}

	Mempool mempool();

	interface RadixEngine {
		Result<RadixEngineConfigurationDTO> configuration();

		Result<RadixEngineDataDTO> data();
	}

	RadixEngine radixEngine();

	interface Sync {
		Result<SyncConfigurationDTO> configuration();

		Result<SyncDataDTO> data();
	}

	Sync sync();

	interface Ledger {
		Result<ProofDTO> latest(); //ledger.get_latest_proof

		Result<ProofDTO> epoch(); //ledger.get_latest_epoch_proof

		Result<CheckpointDTO> checkpoints(); //checkpoints.get_checkpoints
	}

	Ledger ledger();

	interface Faucet {
		Result<TxDTO> request(AccountAddress address); //faucet.request_tokens
	}

	Faucet faucet();
}
