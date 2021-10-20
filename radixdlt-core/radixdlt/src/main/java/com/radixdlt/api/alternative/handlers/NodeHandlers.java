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

package com.radixdlt.api.alternative.handlers;

import com.radixdlt.api.dto.request.AccountGetInfoRequest;
import com.radixdlt.api.dto.request.AccountSubmitTransactionSingleStepRequest;
import com.radixdlt.api.dto.request.ApiGetConfigurationRequest;
import com.radixdlt.api.dto.request.ApiGetDataRequest;
import com.radixdlt.api.dto.request.BftGetConfigurationRequest;
import com.radixdlt.api.dto.request.BftGetDataRequest;
import com.radixdlt.api.dto.request.CheckpointsGetCheckpointsRequest;
import com.radixdlt.api.dto.request.GetTransactionsRequest;
import com.radixdlt.api.dto.request.LedgerGetLatestEpochProofRequest;
import com.radixdlt.api.dto.request.LedgerGetLatestProofRequest;
import com.radixdlt.api.dto.request.MempoolGetConfigurationRequest;
import com.radixdlt.api.dto.request.MempoolGetDataRequest;
import com.radixdlt.api.dto.request.NetworkingGetAddressBookRequest;
import com.radixdlt.api.dto.request.NetworkingGetConfigurationRequest;
import com.radixdlt.api.dto.request.NetworkingGetDataRequest;
import com.radixdlt.api.dto.request.NetworkingGetPeersRequest;
import com.radixdlt.api.dto.request.RadixEngineGetConfigurationRequest;
import com.radixdlt.api.dto.request.RadixEngineGetDataRequest;
import com.radixdlt.api.dto.request.SyncGetConfigurationRequest;
import com.radixdlt.api.dto.request.SyncGetDataRequest;
import com.radixdlt.api.dto.request.ValidationGetCurrentEpochDataRequest;
import com.radixdlt.api.dto.request.ValidationGetNodeInfoRequest;
import com.radixdlt.api.dto.response.AddressBookEntry;
import com.radixdlt.api.dto.response.ApiConfiguration;
import com.radixdlt.api.dto.response.ApiData;
import com.radixdlt.api.dto.response.Checkpoint;
import com.radixdlt.api.dto.response.ConsensusConfiguration;
import com.radixdlt.api.dto.response.ConsensusData;
import com.radixdlt.api.dto.response.EpochData;
import com.radixdlt.api.dto.response.ForkDetails;
import com.radixdlt.api.dto.response.LocalAccount;
import com.radixdlt.api.dto.response.LocalValidatorInfo;
import com.radixdlt.api.dto.response.MempoolConfiguration;
import com.radixdlt.api.dto.response.MempoolData;
import com.radixdlt.api.dto.response.NetworkConfiguration;
import com.radixdlt.api.dto.response.NetworkData;
import com.radixdlt.api.dto.response.NetworkPeer;
import com.radixdlt.api.dto.response.Proof;
import com.radixdlt.api.dto.response.RadixEngineData;
import com.radixdlt.api.dto.response.SyncConfiguration;
import com.radixdlt.api.dto.response.SyncData;
import com.radixdlt.api.dto.response.TransactionsDTO;
import com.radixdlt.api.dto.response.TxDTO;
import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;

import java.util.List;

import static com.radixdlt.utils.functional.Result.fail;

public class NodeHandlers {
	private static final Failure ERR_NOT_IMPLEMENTED = Failure.failure(-1, "Function is not implemented");

	public Result<ApiConfiguration> apiGetConfiguration(ApiGetConfigurationRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
	public Result<ApiData> apiGetData(ApiGetDataRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
	public Result<Checkpoint> checkpointsGetCheckpoints(CheckpointsGetCheckpointsRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
	public Result<ConsensusConfiguration> bftGetConfiguration(BftGetConfigurationRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
	public Result<ConsensusData> bftGetData(BftGetDataRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
	public Result<EpochData> validationGetCurrentEpochData(ValidationGetCurrentEpochDataRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
	public Result<List<AddressBookEntry>> networkingGetAddressBook(NetworkingGetAddressBookRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
	public Result<List<ForkDetails>> radixEngineGetConfiguration(RadixEngineGetConfigurationRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
	public Result<List<NetworkPeer>> networkingGetPeers(NetworkingGetPeersRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
	public Result<LocalAccount> accountGetInfo(AccountGetInfoRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
	public Result<LocalValidatorInfo> validationGetNodeInfo(ValidationGetNodeInfoRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
	public Result<MempoolConfiguration> mempoolGetConfiguration(MempoolGetConfigurationRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
	public Result<MempoolData> mempoolGetData(MempoolGetDataRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
	public Result<NetworkConfiguration> networkingGetConfiguration(NetworkingGetConfigurationRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
	public Result<NetworkData> networkingGetData(NetworkingGetDataRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
	public Result<Proof> ledgerGetLatestEpochProof(LedgerGetLatestEpochProofRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
	public Result<Proof> ledgerGetLatestProof(LedgerGetLatestProofRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
	public Result<RadixEngineData> radixEngineGetData(RadixEngineGetDataRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
	public Result<SyncConfiguration> syncGetConfiguration(SyncGetConfigurationRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
	public Result<SyncData> syncGetData(SyncGetDataRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
	public Result<TransactionsDTO> getTransactions(GetTransactionsRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
	public Result<TxDTO> accountSubmitTransactionSingleStep(AccountSubmitTransactionSingleStepRequest request) { return fail(ERR_NOT_IMPLEMENTED);}
}
