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

package com.radixdlt.api.rpc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.radixdlt.api.rpc.parameter.AccountGetBalances;
import com.radixdlt.api.rpc.parameter.AccountGetInfo;
import com.radixdlt.api.rpc.parameter.AccountGetStakePositions;
import com.radixdlt.api.rpc.parameter.AccountGetTransactionHistory;
import com.radixdlt.api.rpc.parameter.AccountGetUnstakePositions;
import com.radixdlt.api.rpc.parameter.AccountSubmitTransactionSingleStep;
import com.radixdlt.api.rpc.parameter.ApiGetConfiguration;
import com.radixdlt.api.rpc.parameter.ApiGetData;
import com.radixdlt.api.rpc.parameter.BftGetConfiguration;
import com.radixdlt.api.rpc.parameter.BftGetData;
import com.radixdlt.api.rpc.parameter.CheckpointsGetCheckpoints;
import com.radixdlt.api.rpc.parameter.ConstructionBuildTransaction;
import com.radixdlt.api.rpc.parameter.ConstructionFinalizeTransaction;
import com.radixdlt.api.rpc.parameter.ConstructionSubmitTransaction;
import com.radixdlt.api.rpc.parameter.GetTransactions;
import com.radixdlt.api.rpc.parameter.LedgerGetLatestEpochProof;
import com.radixdlt.api.rpc.parameter.LedgerGetLatestProof;
import com.radixdlt.api.rpc.parameter.MempoolGetConfiguration;
import com.radixdlt.api.rpc.parameter.MempoolGetData;
import com.radixdlt.api.rpc.parameter.MethodParameters;
import com.radixdlt.api.rpc.parameter.NetworkGetDemand;
import com.radixdlt.api.rpc.parameter.NetworkGetId;
import com.radixdlt.api.rpc.parameter.NetworkGetThroughput;
import com.radixdlt.api.rpc.parameter.NetworkingGetAddressBook;
import com.radixdlt.api.rpc.parameter.NetworkingGetConfiguration;
import com.radixdlt.api.rpc.parameter.NetworkingGetData;
import com.radixdlt.api.rpc.parameter.NetworkingGetPeers;
import com.radixdlt.api.rpc.parameter.RadixEngineGetConfiguration;
import com.radixdlt.api.rpc.parameter.RadixEngineGetData;
import com.radixdlt.api.rpc.parameter.SyncGetConfiguration;
import com.radixdlt.api.rpc.parameter.SyncGetData;
import com.radixdlt.api.rpc.parameter.TokensGetInfo;
import com.radixdlt.api.rpc.parameter.TokensGetNativeToken;
import com.radixdlt.api.rpc.parameter.TransactionsGetTransactionStatus;
import com.radixdlt.api.rpc.parameter.TransactionsLookupTransaction;
import com.radixdlt.api.rpc.parameter.ValidationGetCurrentEpochData;
import com.radixdlt.api.rpc.parameter.ValidationGetNodeInfo;
import com.radixdlt.api.rpc.parameter.ValidatorsGetNextEpochSet;
import com.radixdlt.api.rpc.parameter.ValidatorsLookupValidator;

import java.util.Optional;

public class JsonRpcRequest<T extends MethodParameters> {
	private static final String VERSION = "2.0";

	private final String version;
	private final String id;
	private final String method;
	private final T parameters;

	private JsonRpcRequest(String version, String id, String method, T parameters) {
		this.version = version;
		this.id = id;
		this.method = method;
		this.parameters = parameters;
	}

	@JsonCreator
	public static <T extends MethodParameters> JsonRpcRequest<T> deserialize(
		@JsonProperty(value = "method", required = true) String method,
		@JsonProperty(value = "id", required = true) String id,
		@JsonProperty(value = "version", required = true) String version,
		@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "method")
		@JsonSubTypes({
						  @JsonSubTypes.Type(value = TokensGetNativeToken.class, name = TokensGetNativeToken.METHOD_NAME),
						  @JsonSubTypes.Type(value = TokensGetInfo.class, name = TokensGetInfo.METHOD_NAME),
						  @JsonSubTypes.Type(value = AccountGetBalances.class, name = AccountGetBalances.METHOD_NAME),
						  @JsonSubTypes.Type(value = AccountGetTransactionHistory.class, name = AccountGetTransactionHistory.METHOD_NAME),
						  @JsonSubTypes.Type(value = AccountGetStakePositions.class, name = AccountGetStakePositions.METHOD_NAME),
						  @JsonSubTypes.Type(value = AccountGetUnstakePositions.class, name = AccountGetUnstakePositions.METHOD_NAME),
						  @JsonSubTypes.Type(value = TransactionsLookupTransaction.class, name = TransactionsLookupTransaction.METHOD_NAME),
						  @JsonSubTypes.Type(value = TransactionsGetTransactionStatus.class, name = TransactionsGetTransactionStatus.METHOD_NAME),
						  @JsonSubTypes.Type(value = NetworkGetId.class, name = NetworkGetId.METHOD_NAME),
						  @JsonSubTypes.Type(value = NetworkGetThroughput.class, name = NetworkGetThroughput.METHOD_NAME),
						  @JsonSubTypes.Type(value = NetworkGetDemand.class, name = NetworkGetDemand.METHOD_NAME),
						  @JsonSubTypes.Type(value = NetworkingGetConfiguration.class, name = NetworkingGetConfiguration.METHOD_NAME),
						  @JsonSubTypes.Type(value = NetworkingGetPeers.class, name = NetworkingGetPeers.METHOD_NAME),
						  @JsonSubTypes.Type(value = NetworkingGetData.class, name = NetworkingGetData.METHOD_NAME),
						  @JsonSubTypes.Type(value = NetworkingGetAddressBook.class, name = NetworkingGetAddressBook.METHOD_NAME),
						  @JsonSubTypes.Type(value = ValidatorsGetNextEpochSet.class, name = ValidatorsGetNextEpochSet.METHOD_NAME),
						  @JsonSubTypes.Type(value = ValidatorsLookupValidator.class, name = ValidatorsLookupValidator.METHOD_NAME),
						  @JsonSubTypes.Type(value = ConstructionBuildTransaction.class, name = ConstructionBuildTransaction.METHOD_NAME),
						  @JsonSubTypes.Type(value = ConstructionFinalizeTransaction.class, name = ConstructionFinalizeTransaction.METHOD_NAME),
						  @JsonSubTypes.Type(value = ConstructionSubmitTransaction.class, name = ConstructionSubmitTransaction.METHOD_NAME),
						  @JsonSubTypes.Type(value = GetTransactions.class, name = GetTransactions.METHOD_NAME),
						  @JsonSubTypes.Type(value = ApiGetConfiguration.class, name = ApiGetConfiguration.METHOD_NAME),
						  @JsonSubTypes.Type(value = ApiGetData.class, name = ApiGetData.METHOD_NAME),
						  @JsonSubTypes.Type(value = BftGetConfiguration.class, name = BftGetConfiguration.METHOD_NAME),
						  @JsonSubTypes.Type(value = BftGetData.class, name = BftGetData.METHOD_NAME),
						  @JsonSubTypes.Type(value = MempoolGetConfiguration.class, name = MempoolGetConfiguration.METHOD_NAME),
						  @JsonSubTypes.Type(value = MempoolGetData.class, name = MempoolGetData.METHOD_NAME),
						  @JsonSubTypes.Type(value = LedgerGetLatestProof.class, name = LedgerGetLatestProof.METHOD_NAME),
						  @JsonSubTypes.Type(value = LedgerGetLatestEpochProof.class, name = LedgerGetLatestEpochProof.METHOD_NAME),
						  @JsonSubTypes.Type(value = CheckpointsGetCheckpoints.class, name = CheckpointsGetCheckpoints.METHOD_NAME),
						  @JsonSubTypes.Type(value = RadixEngineGetConfiguration.class, name = RadixEngineGetConfiguration.METHOD_NAME),
						  @JsonSubTypes.Type(value = RadixEngineGetData.class, name = RadixEngineGetData.METHOD_NAME),
						  @JsonSubTypes.Type(value = SyncGetConfiguration.class, name = SyncGetConfiguration.METHOD_NAME),
						  @JsonSubTypes.Type(value = SyncGetData.class, name = SyncGetData.METHOD_NAME),
						  @JsonSubTypes.Type(value = ValidationGetNodeInfo.class, name = ValidationGetNodeInfo.METHOD_NAME),
						  @JsonSubTypes.Type(value = ValidationGetCurrentEpochData.class, name = ValidationGetCurrentEpochData.METHOD_NAME),
						  @JsonSubTypes.Type(value = AccountGetInfo.class, name = AccountGetInfo.METHOD_NAME),
						  @JsonSubTypes.Type(value = AccountSubmitTransactionSingleStep.class, name = AccountSubmitTransactionSingleStep.METHOD_NAME),
					  })
		@JsonProperty(value = "params", required = true) T parameters
	) {
		return new JsonRpcRequest<>(version, id, method, parameters);
	}

	public static <T extends MethodParameters> JsonRpcRequest<T> create(RpcMethod method, long id, T parameters) {
		return new JsonRpcRequest<>(VERSION, Long.toString(id), method.method(), parameters);
	}

	@JsonProperty("jsonrpc")
	public String getVersion() {
		return version;
	}

	@JsonProperty("id")
	public String getId() {
		return id;
	}

	@JsonProperty("params")
	public T getParameters() {
		return parameters;
	}

	@JsonProperty("method")
	public String getMethod() {
		return method;
	}

	public Optional<RpcMethod> rpcDetails() {
		return RpcMethod.fromString(method);
	}
}
