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

import org.junit.Ignore;
import org.junit.Test;

import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.AccountAddressing;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.client.lib.api.sync.RadixApi.DEFAULT_PRIMARY_PORT;
import static com.radixdlt.client.lib.api.sync.RadixApi.DEFAULT_SECONDARY_PORT;

public class SyncRadixApiLocalTest {
	public static final ECKeyPair KEY_PAIR1 = keyPairOf(1);
	public static final ECKeyPair KEY_PAIR2 = keyPairOf(2);
	private static final AccountAddressing ACCOUNTS = Addressing.ofNetwork(Network.LOCALNET).forAccounts();

	private static final AccountAddress ACCOUNT_ADDRESS1 = AccountAddress.create(KEY_PAIR1.getPublicKey());
	private static final AccountAddress ACCOUNT_ADDRESS2 = AccountAddress.create(KEY_PAIR2.getPublicKey());

	private static final String BASE_URL = "http://localhost/";

	private static final String NETWORK_ID = "{\"result\":{\"networkId\":99},\"id\":\"1\",\"jsonrpc\":\"2.0\"}";
	private static final String ACCOUNT_INFO = "{\"result\":{\"address\":\"ddx1qsprpeqt46q3qqmx56muck5rs9dhuz9a2x9l0g4"
		+ "addup7z2zfm4c3jqurkgjv\",\"balance\":{\"stakes\":[{\"delegate\":\"dv1qgcwgzawsygqxe4xklx94quptdlq302330m690"
		+ "tt0q0sjsjwawyvs6zsklj\",\"amount\":\"1000000000000000000000000000\"}],\"tokens\":[]}},\"id\":\"2\",\"jsonrp"
		+ "c\":\"2.0\"}\n";
	private static final String VALIDATOR_INFO = "{\"result\":{\"owner\":\"ddx1qsprpeqt46q3qqmx56muck5rs9dhuz9a2x9l0g4"
		+ "addup7z2zfm4c3jqurkgjv\",\"rakePercentage\":0,\"address\":\"dv1qgcwgzawsygqxe4xklx94quptdlq302330m690tt0q0s"
		+ "jsjwawyvs6zsklj\",\"stakes\":[{\"amount\":\"1000000000000000000000000000\",\"delegator\":\"ddx1qsprpeqt46q3"
		+ "qqmx56muck5rs9dhuz9a2x9l0g4addup7z2zfm4c3jqurkgjv\"}],\"allowDelegation\":true,\"name\":\"\",\"registered\""
		+ ":true,\"totalStake\":\"1000000000000000000000000000\",\"url\":\"\"},\"id\":\"2\",\"jsonrpc\":\"2.0\"}\n";
	private static final String NEXT_EPOCH = "{\"result\":{\"validators\":[{\"totalDelegatedStake\":\"1000000000000000"
		+ "000000000000\",\"rakePercentage\":0,\"address\":\"dv1qtjlayqkvk234cwh5rs72uunjwgte8gnr4gp6vvgqmmdjl9fjvr5ul"
		+ "nv4zg\",\"infoURL\":\"\",\"ownerDelegation\":\"1000000000000000000000000000\",\"name\":\"\",\"registered\":"
		+ "true,\"ownerAddress\":\"ddx1qspwtl5szeje2xhp67swretnjwfep0yazvw4q8f33qr0dktu4xfswnsjjpvcy\",\"isExternalSta"
		+ "keAccepted\":true},{\"totalDelegatedStake\":\"1000000000000000000000000000\",\"rakePercentage\":0,\"address"
		+ "\":\"dv1qgcwgzawsygqxe4xklx94quptdlq302330m690tt0q0sjsjwawyvs6zsklj\",\"infoURL\":\"\",\"ownerDelegation\":"
		+ "\"1000000000000000000000000000\",\"name\":\"\",\"registered\":true,\"ownerAddress\":\"ddx1qsprpeqt46q3qqmx5"
		+ "6muck5rs9dhuz9a2x9l0g4addup7z2zfm4c3jqurkgjv\",\"isExternalStakeAccepted\":true}]},\"id\":\"2\",\"jsonrpc\""
		+ ":\"2.0\"}\n";
	private static final String SINGLE_STEP = "";
	private static final String CURRENT_EPOCH = "";

	private final OkHttpClient client = mock(OkHttpClient.class);

	@Test
	public void testAccountInfo() throws IOException {
		var accountAddress = AccountAddress.create(ACCOUNTS.parse("ddx1qsprpeqt46q3qqmx56muck5rs9dhuz9a2x9l0g4addup7z2zfm4c3jqurkgjv"));

		prepareClient(ACCOUNT_INFO)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.local().accountInfo()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(localAccount -> assertEquals(accountAddress, localAccount.getAddress()))
				.onSuccess(localAccount -> assertEquals(0, localAccount.getBalance().getTokens().size()))
			);
	}

	@Test
	public void testValidatorInfo() throws IOException {
		prepareClient(VALIDATOR_INFO)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.local().validatorInfo()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(localValidatorInfo -> assertEquals(1, localValidatorInfo.getStakes().size()))
				.onSuccess(localValidatorInfo -> assertTrue(localValidatorInfo.isRegistered()))
			);
	}

	@Test
	public void testNextEpoch() throws IOException {
		prepareClient(NEXT_EPOCH)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.local().nextEpoch()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(epochData -> assertEquals(2, epochData.getValidators().size()))
			);
	}

	@Test
	@Ignore //FIXME: Does not work for now as accounts don't match the address of node we're talking to
	public void testSubmitTxSingleStep() throws IOException {
		var request = TransactionRequest.createBuilder(ACCOUNT_ADDRESS1)
			.transfer(
				ACCOUNT_ADDRESS1,
				ACCOUNT_ADDRESS2,
				UInt256.NINE,
				"xrd_rb1qya85pwq"
			)
			.message("Test message")
			.build();

		RadixApi.connect(BASE_URL)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.local().submitTxSingleStep(request)
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(txData -> assertNotNull(txData.getTxId()))
			);
	}

	private Result<RadixApi> prepareClient(String responseBody) throws IOException {
		var call = mock(Call.class);
		var response = mock(Response.class);
		var body = mock(ResponseBody.class);

		when(client.newCall(any())).thenReturn(call);
		when(call.execute()).thenReturn(response);
		when(response.body()).thenReturn(body);
		when(body.string()).thenReturn(NETWORK_ID, responseBody);

		return SyncRadixApi.connect(BASE_URL, DEFAULT_PRIMARY_PORT, DEFAULT_SECONDARY_PORT, client);
	}

	private static ECKeyPair keyPairOf(int pk) {
		var privateKey = new byte[ECKeyPair.BYTES];

		Ints.copyTo(pk, privateKey, ECKeyPair.BYTES - Integer.BYTES);

		try {
			return ECKeyPair.fromPrivateKey(privateKey);
		} catch (PrivateKeyException | PublicKeyException e) {
			throw new IllegalArgumentException("Error while generating public key", e);
		}
	}
}
