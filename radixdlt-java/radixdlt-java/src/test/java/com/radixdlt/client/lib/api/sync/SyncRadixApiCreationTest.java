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

import org.bouncycastle.util.encoders.Hex;
import org.junit.Ignore;
import org.junit.Test;

import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.api.ValidatorAddress;
import com.radixdlt.client.lib.dto.TxBlobDTO;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.io.IOException;
import java.util.Optional;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.client.lib.api.sync.RadixApi.DEFAULT_PRIMARY_PORT;
import static com.radixdlt.client.lib.api.sync.RadixApi.DEFAULT_SECONDARY_PORT;
import static com.radixdlt.client.lib.api.token.Amount.amount;

public class SyncRadixApiCreationTest {
	private static final String BASE_URL = "http://localhost/";
	public static final ECKeyPair KEY_PAIR1 = keyPairOf(1);
	public static final ECKeyPair KEY_PAIR2 = keyPairOf(2);
	public static final ECKeyPair KEY_PAIR3 = keyPairOf(3);
	private static final AccountAddress ACCOUNT_ADDRESS1 = AccountAddress.create(KEY_PAIR1.getPublicKey());
	private static final AccountAddress ACCOUNT_ADDRESS2 = AccountAddress.create(KEY_PAIR2.getPublicKey());
	private static final AccountAddress ACCOUNT_ADDRESS3 = AccountAddress.create(KEY_PAIR3.getPublicKey());
	private static final ValidatorAddress VALIDATOR_ADDRESS = ValidatorAddress.of(KEY_PAIR3.getPublicKey());

	private static final String NETWORK_ID = "{\"result\":{\"networkId\":2},\"id\":\"1\",\"jsonrpc\":\"2.0\"}";
	private static final String BUILT_TRANSACTION = "{\"result\":{\"fee\":\"71000000000000000\",\"transaction\":"
		+ "{\"blob\":\"047a2892ba2a60442df4b9fca7a3c626ab6fff83d5d910edfc789182985a55e19000000003092100000000000"
		+ "00000000000000000000000000000000000000000fc3e1fb30d8000010400040279be667ef9dcbbac55a06295ce870b07029b"
		+ "fcdb2dce28d959f2815b16f81798010000000000000000000000000000000000000000033b2e3c9ed4421d34f280000005000"
		+ "00000010400040279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f817980100000000000000000000"
		+ "00000000000000000000033b2e3c9ed4421d34f27ff70104000402c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca"
		+ "7abac09b95c709ee501000000000000000000000000000000000000000000000000000000000000000900060c54657374206d"
		+ "657373616765\",\"hashOfBlobToSign\":\"57fa8380ad2099c611fab66e382330d2212825d051fbcd5e4ab17072d74d443"
		+ "2\"}},\"id\":\"2\",\"jsonrpc\":\"2.0\"}";

	private static final String BLOB = "047a2892ba2a60442df4b9fca7a3c626ab6fff83d5d910edfc789182985a55e190000000"
		+ "0309210000000000000000000000000000000000000000000000000000fc3e1fb30d8000010400040279be667ef9dcbbac55a"
		+ "06295ce870b07029bfcdb2dce28d959f2815b16f81798010000000000000000000000000000000000000000033b2e3c9ed442"
		+ "1d34f28000000500000000010400040279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f8179801000"
		+ "0000000000000000000000000000000000000033b2e3c9ed4421d34f27ff70104000402c6047f9441ed7d6d3045406e95c07c"
		+ "d85c778e4b8cef3ca7abac09b95c709ee50100000000000000000000000000000000000000000000000000000000000000090"
		+ "0060c54657374206d65737361676507005a9f1588c15bcb2471e1ec9139ccc9425e7fe378d6602cfa856f7dff3f1a4af60244"
		+ "3f23fe62027a4f6f7d741f028ba4db45db7ba7343640b8ea8ccb90eb2fb5";

	private static final String TX_ID = "c87ecac097d9bca2bd097815d3184d0a47ba9d389ba6b6ad852c015b7a1fa967";

	private static final String FINALIZE_TRANSACTION = "{\"result\":{\"blob\":\"047a2892ba2a60442df4b9fca7a3c626"
		+ "ab6fff83d5d910edfc789182985a55e1900000000309210000000000000000000000000000000000000000000000000000fc3"
		+ "e1fb30d8000010400040279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f817980100000000000000"
		+ "00000000000000000000000000033b2e3c9ed4421d34f28000000500000000010400040279be667ef9dcbbac55a06295ce870"
		+ "b07029bfcdb2dce28d959f2815b16f81798010000000000000000000000000000000000000000033b2e3c9ed4421d34f27ff7"
		+ "0104000402c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5010000000000000000000000000"
		+ "00000000000000000000000000000000000000900060c54657374206d65737361676507005a9f1588c15bcb2471e1ec9139cc"
		+ "c9425e7fe378d6602cfa856f7dff3f1a4af602443f23fe62027a4f6f7d741f028ba4db45db7ba7343640b8ea8ccb90eb2fb5\""
		+ ",\"txID\":\"c87ecac097d9bca2bd097815d3184d0a47ba9d389ba6b6ad852c015b7a1fa967\"},\"id\":\"3\",\"jsonrpc\""
		+ ":\"2.0\"}";

	private final OkHttpClient client = mock(OkHttpClient.class);

	@Test
	public void testBuildTransaction() throws IOException {
		var hash = Hex.decode("57fa8380ad2099c611fab66e382330d2212825d051fbcd5e4ab17072d74d4432");

		var request = TransactionRequest.createBuilder(ACCOUNT_ADDRESS1)
			.transfer(
				ACCOUNT_ADDRESS1,
				ACCOUNT_ADDRESS2,
				amount(10).subunits(),
				"xrd_dr1qyrs8qwl"
			)
			.message("Test message")
			.build();

		//prepareClient(BUILT_TRANSACTION)
		RadixApi.connect(BASE_URL)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.transaction().build(request)
				.onFailure(failure -> fail(failure.toString()))
//				.onSuccess(dto -> assertEquals(UInt256.from(71000000000000000L), dto.getFee()))
//				.onSuccess(dto -> assertArrayEquals(hash, dto.getTransaction().getHashToSign()))
				.onSuccess(dto -> client.transaction().finalize(dto.toFinalized(KEY_PAIR1))
					.onSuccess(finalized -> client.transaction().submit(finalized)))
			);
	}

	@Test
	public void testFinalizeTransaction() throws Exception {
//		var request = buildFinalizedTransaction();
//		var txId = AID.from("4f9e31d3986ed6add26c48792389c1270ec5f3033525a50a2ffd34c88cc5e7cc");
//
//		prepareClient(FINALIZE_TRANSACTION)
//			.map(RadixApi::withTrace)
//			.onFailure(failure -> fail(failure.toString()))
//			.onSuccess(client -> client.transaction().finalize(request)
//				.onFailure(failure -> fail(failure.toString()))
//				.onSuccess(dto -> assertEquals(txId, dto.getTxId()))
//			);
	}

	@Test
	public void testSubmitTransaction() throws Exception {
		var txId = AID.from("4f9e31d3986ed6add26c48792389c1270ec5f3033525a50a2ffd34c88cc5e7cc");
		var request = buildFinalizedTransaction(); //.withTxId(txId);

//		prepareClient(FINALIZE_TRANSACTION)
		RadixApi.connect(BASE_URL)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.transaction().submit(request)
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(dto -> assertEquals(txId, dto.getTxId()))
			);
	}

	@Test
	@Ignore
	public void testBuildAndSubmitTransactionWithMessage() {
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
			.onSuccess(client -> client.transaction().build(request)
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(builtTransactionDTO -> assertEquals(UInt256.from(100000000000000000L), builtTransactionDTO.getFee()))
				.map(builtTransactionDTO -> builtTransactionDTO.toFinalized(KEY_PAIR1))
				.onSuccess(finalizedTransaction -> client.transaction().finalize(finalizedTransaction)
					.onSuccess(txDTO -> assertNotNull(txDTO.getTxId()))
					.onSuccess(submittableTransaction -> client.transaction().submit(submittableTransaction)
						.onFailure(failure -> fail(failure.toString()))
						.onSuccess(txDTO -> assertEquals(submittableTransaction.getTxId(), txDTO.getTxId())))));
	}

	@Test
	@Ignore
	public void testRegisterValidator() {
		var request = TransactionRequest.createBuilder(ACCOUNT_ADDRESS3)
			.registerValidator(VALIDATOR_ADDRESS, Optional.of("MyValidator"), Optional.of("http://my.validator.url.com/"))
			.build();

		RadixApi.connect(BASE_URL)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.transaction().build(request)
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(builtTransactionDTO -> assertEquals(UInt256.from(100000000000000000L), builtTransactionDTO.getFee()))
				.map(builtTransactionDTO -> builtTransactionDTO.toFinalized(KEY_PAIR3))
				.onSuccess(finalizedTransaction -> client.transaction().finalize(finalizedTransaction)
					.onSuccess(txDTO -> assertNotNull(txDTO.getTxId()))
					.onSuccess(submittableTransaction -> client.transaction().submit(submittableTransaction)
						.onFailure(failure -> fail(failure.toString()))
						.onSuccess(txDTO -> assertEquals(submittableTransaction.getTxId(), txDTO.getTxId())))));
	}

	private TxBlobDTO buildFinalizedTransaction() throws PublicKeyException {
		return TxBlobDTO.create(null, null);
//		var blob = Hex.decode("047a2892ba2a60442df4b9fca7a3c626ab6fff83d5d910edfc789182985a55e1900000000309"
//								  + "210000000000000000000000000000000000000000000000000000fc3e1fb30d8000010400040279be"
//								  + "667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f817980100000000000000000000"
//								  + "00000000000000000000033b2e3c9ed4421d34f28000000500000000010400040279be667ef9dcbbac"
//								  + "55a06295ce870b07029bfcdb2dce28d959f2815b16f817980100000000000000000000000000000000"
//								  + "00000000033b2e3c9ed4421d34f27ff70104000402c6047f9441ed7d6d3045406e95c07cd85c778e4b"
//								  + "8cef3ca7abac09b95c709ee50100000000000000000000000000000000000000000000000000000000"
//								  + "0000000900060c54657374206d657373616765");
//		var sig = ECDSASignature.decodeFromHexDer("30440220768a67a36549e11f19ddb6e2c172c3"
//													  + "f2f2996600413f1d2f246667ab2de81ddf0220"
//													  + "70f3bb613bcba2704728b99fad91668e2d6759"
//													  + "3f73b7c3567eae61596242f64c");
//
//		var pubkey = ECPublicKey.fromHex("0479be667ef9dcbbac55a06295ce870b07029bfcdb2dce"
//											 + "28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8"
//											 + "fd17b448a68554199c47d08ffb10d4b8");
//		return FinalizedTransaction.create(blob, sig, pubkey, null);
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
