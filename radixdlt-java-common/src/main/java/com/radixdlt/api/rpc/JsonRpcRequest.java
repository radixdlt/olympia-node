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
import com.radixdlt.api.rpc.RpcMethodDescriptor.*;
import com.radixdlt.api.rpc.parameter.AccountGetBalancesRequest;
import com.radixdlt.api.rpc.parameter.AccountGetInfoRequest;
import com.radixdlt.api.rpc.parameter.AccountGetStakePositionsRequest;
import com.radixdlt.api.rpc.parameter.AccountGetTransactionHistoryRequest;
import com.radixdlt.api.rpc.parameter.AccountGetUnstakePositionsRequest;
import com.radixdlt.api.rpc.parameter.AccountSubmitTransactionSingleStepRequest;
import com.radixdlt.api.rpc.parameter.ApiGetConfigurationRequest;
import com.radixdlt.api.rpc.parameter.ApiGetDataRequest;
import com.radixdlt.api.rpc.parameter.BftGetConfigurationRequest;
import com.radixdlt.api.rpc.parameter.BftGetDataRequest;
import com.radixdlt.api.rpc.parameter.CheckpointsGetCheckpointsRequest;
import com.radixdlt.api.rpc.parameter.ConstructionBuildTransactionRequest;
import com.radixdlt.api.rpc.parameter.ConstructionFinalizeTransactionRequest;
import com.radixdlt.api.rpc.parameter.ConstructionSubmitTransactionRequest;
import com.radixdlt.api.rpc.parameter.GetTransactionsRequest;
import com.radixdlt.api.rpc.parameter.LedgerGetLatestEpochProofRequest;
import com.radixdlt.api.rpc.parameter.LedgerGetLatestProofRequest;
import com.radixdlt.api.rpc.parameter.MempoolGetConfigurationRequest;
import com.radixdlt.api.rpc.parameter.MempoolGetDataRequest;
import com.radixdlt.api.rpc.parameter.MethodParameters;
import com.radixdlt.api.rpc.parameter.NetworkGetDemandRequest;
import com.radixdlt.api.rpc.parameter.NetworkGetIdRequest;
import com.radixdlt.api.rpc.parameter.NetworkGetThroughputRequest;
import com.radixdlt.api.rpc.parameter.NetworkingGetAddressBookRequest;
import com.radixdlt.api.rpc.parameter.NetworkingGetConfigurationRequest;
import com.radixdlt.api.rpc.parameter.NetworkingGetDataRequest;
import com.radixdlt.api.rpc.parameter.NetworkingGetPeersRequest;
import com.radixdlt.api.rpc.parameter.RadixEngineGetConfigurationRequest;
import com.radixdlt.api.rpc.parameter.RadixEngineGetDataRequest;
import com.radixdlt.api.rpc.parameter.SyncGetConfigurationRequest;
import com.radixdlt.api.rpc.parameter.SyncGetDataRequest;
import com.radixdlt.api.rpc.parameter.TokensGetInfoRequest;
import com.radixdlt.api.rpc.parameter.TokensGetNativeTokenRequest;
import com.radixdlt.api.rpc.parameter.TransactionsGetTransactionStatusRequest;
import com.radixdlt.api.rpc.parameter.TransactionsLookupTransactionRequest;
import com.radixdlt.api.rpc.parameter.ValidationGetCurrentEpochDataRequest;
import com.radixdlt.api.rpc.parameter.ValidationGetNodeInfoRequest;
import com.radixdlt.api.rpc.parameter.ValidatorsGetNextEpochSetRequest;
import com.radixdlt.api.rpc.parameter.ValidatorsLookupValidatorRequest;

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
		@JsonSubTypes(
			{
				@JsonSubTypes.Type(value = TokensGetNativeTokenRequest.class, name = TokensGetNativeTokenMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = TokensGetInfoRequest.class, name = TokensGetInfoMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = AccountGetBalancesRequest.class, name = AccountGetBalancesMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = AccountGetTransactionHistoryRequest.class, name = AccountGetTransactionHistoryMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = AccountGetStakePositionsRequest.class, name = AccountGetStakePositionsMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = AccountGetUnstakePositionsRequest.class, name = AccountGetUnstakePositionsMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = TransactionsLookupTransactionRequest.class, name = TransactionsLookupTransactionMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = TransactionsGetTransactionStatusRequest.class, name = TransactionsGetTransactionStatusMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = NetworkGetIdRequest.class, name = NetworkGetIdMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = NetworkGetThroughputRequest.class, name = NetworkGetThroughputMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = NetworkGetDemandRequest.class, name = NetworkGetDemandMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = NetworkingGetConfigurationRequest.class, name = NetworkingGetConfigurationMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = NetworkingGetPeersRequest.class, name = NetworkingGetPeersMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = NetworkingGetDataRequest.class, name = NetworkingGetDataMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = NetworkingGetAddressBookRequest.class, name = NetworkingGetAddressBookMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = ValidatorsGetNextEpochSetRequest.class, name = ValidatorsGetNextEpochSetMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = ValidatorsLookupValidatorRequest.class, name = ValidatorsLookupValidatorMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = ConstructionBuildTransactionRequest.class, name = ConstructionBuildTransactionMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = ConstructionFinalizeTransactionRequest.class, name = ConstructionFinalizeTransactionMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = ConstructionSubmitTransactionRequest.class, name = ConstructionSubmitTransactionMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = GetTransactionsRequest.class, name = GetTransactionsMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = ApiGetConfigurationRequest.class, name = ApiGetConfigurationMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = ApiGetDataRequest.class, name = ApiGetDataMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = BftGetConfigurationRequest.class, name = BftGetConfigurationMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = BftGetDataRequest.class, name = BftGetDataMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = MempoolGetConfigurationRequest.class, name = MempoolGetConfigurationMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = MempoolGetDataRequest.class, name = MempoolGetDataMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = LedgerGetLatestProofRequest.class, name = LedgerGetLatestProofMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = LedgerGetLatestEpochProofRequest.class, name = LedgerGetLatestEpochProofMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = CheckpointsGetCheckpointsRequest.class, name = CheckpointsGetCheckpointsMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = RadixEngineGetConfigurationRequest.class, name = RadixEngineGetConfigurationMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = RadixEngineGetDataRequest.class, name = RadixEngineGetDataMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = SyncGetConfigurationRequest.class, name = SyncGetConfigurationMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = SyncGetDataRequest.class, name = SyncGetDataMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = ValidationGetNodeInfoRequest.class, name = ValidationGetNodeInfoMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = ValidationGetCurrentEpochDataRequest.class, name = ValidationGetCurrentEpochDataMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = AccountGetInfoRequest.class, name = AccountGetInfoMethod.METHOD_NAME),
				@JsonSubTypes.Type(value = AccountSubmitTransactionSingleStepRequest.class,
								   name = AccountSubmitTransactionSingleStepMethod.METHOD_NAME),
			}
		)
		@JsonProperty(value = "params", required = true) T parameters
	) {
		return new JsonRpcRequest<>(version, id, method, parameters);
	}

	public static <T extends MethodParameters> JsonRpcRequest<T> create(String methodName, long id, T parameters) {
		return new JsonRpcRequest<>(VERSION, Long.toString(id), methodName, parameters);
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
}
