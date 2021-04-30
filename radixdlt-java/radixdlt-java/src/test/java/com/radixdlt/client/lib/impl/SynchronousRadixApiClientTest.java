package com.radixdlt.client.lib.impl;/*
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

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.RadixApi;
import com.radixdlt.client.lib.dto.JsonRpcResponse;
import com.radixdlt.client.lib.dto.NetworkIdDTO;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.Ints;

import java.util.Optional;

import static org.junit.Assert.fail;

public class SynchronousRadixApiClientTest {
	@Test
	public void name() {
		SynchronousRadixApiClient.connect("http://localhost:8080/")
			.onSuccess(client -> {
//				client.networkId()
//					.onFailureDo(Assert::fail)
//					.onSuccess(networkIdDTO -> System.out.println("Network ID: " + networkIdDTO.getNetworkId()));
//				client.nativeToken()
//					.onFailure(failure -> fail(failure.toString()))
//					.onSuccess(tokenInfoDTO -> System.out.println("Token: " + tokenInfoDTO));
//				client.tokenInfo("xrd_rb1qya85pwq")
//					.onFailure(failure -> fail(failure.toString()))
//					.onSuccess(tokenInfoDTO -> System.out.println("Token: " + tokenInfoDTO));
//				client.tokenBalances(AccountAddress.create(pubkeyOf(1)))
//					.onFailure(failure -> fail(failure.toString()))
//					.onSuccess(tokenBalancesDTO -> System.out.println("Balances: " + tokenBalancesDTO));
				client.transactionHistory(AccountAddress.create(pubkeyOf(1)), 10, Optional.empty())
					.onFailure(failure -> fail(failure.toString()))
					.onSuccess(transactionHistoryDTO -> System.out.println("Transactions: " + transactionHistoryDTO));

			});

	}

	@Test
	public void name2() {
		var input = "{\"result\":{\"networkId\":2},\"id\":\"1\",\"jsonrpc\":\"2.0\"}";

		var type = TypeFactory.defaultInstance().constructParametricType(JsonRpcResponse.class, NetworkIdDTO.class);

		var objectMapper = new ObjectMapper();

		try {
			var res = objectMapper.readValue(input, new TypeReference<JsonRpcResponse<NetworkIdDTO>>() {});
			var result = res.rawResult();
			var rr = objectMapper.convertValue(result, NetworkIdDTO.class);

			System.out.println(res);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}

	}

	private static ECPublicKey pubkeyOf(int pk) {
		final byte[] privateKey = new byte[ECKeyPair.BYTES];
		Ints.copyTo(pk, privateKey, ECKeyPair.BYTES - Integer.BYTES);
		ECKeyPair kp;
		try {
			kp = ECKeyPair.fromPrivateKey(privateKey);
		} catch (PrivateKeyException | PublicKeyException e) {
			throw new IllegalArgumentException("Error while generating public key", e);
		}
		return kp.getPublicKey();
	}
}