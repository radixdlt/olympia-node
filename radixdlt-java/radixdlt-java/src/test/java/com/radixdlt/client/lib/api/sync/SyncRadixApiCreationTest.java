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
import com.radixdlt.client.lib.dto.FinalizedTransaction;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.client.lib.api.sync.RadixApi.DEFAULT_PRIMARY_PORT;
import static com.radixdlt.client.lib.api.sync.RadixApi.DEFAULT_SECONDARY_PORT;

public class SyncRadixApiCreationTest {
	private static final String BASE_URL = "http://localhost/";
	public static final ECKeyPair KEY_PAIR1 = keyPairOf(1);
	public static final ECKeyPair KEY_PAIR2 = keyPairOf(2);
	public static final ECKeyPair KEY_PAIR3 = keyPairOf(3);
	private static final AccountAddress ACCOUNT_ADDRESS1 = AccountAddress.create(KEY_PAIR1.getPublicKey());
	private static final AccountAddress ACCOUNT_ADDRESS2 = AccountAddress.create(KEY_PAIR2.getPublicKey());
	private static final AccountAddress ACCOUNT_ADDRESS3 = AccountAddress.create(KEY_PAIR3.getPublicKey());
	private static final ValidatorAddress VALIDATOR_ADDRESS = ValidatorAddress.of(KEY_PAIR3.getPublicKey());

	private static final String BUILT_TRANSACTION = "{\"result\":{\"fee\":\"100000000000000000\",\"transaction\":"
		+ "{\"blob\":\"048ea194df8759b0780ca7a0526540f8067c97c67040c656aadde295e745f6485200000006010301040279be66"
		+ "7ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798000000000000000000000000002cd76fe086b93ce2f7"
		+ "68a009bf5a87a2760000000103010402c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5000000"
		+ "00000000000000000000000000000000000000000000000000000000090500000001010301040279be667ef9dcbbac55a06295"
		+ "ce870b07029bfcdb2dce28d959f2815b16f81798000000000000000000000000002cd76fe086b93ce2f768a009bf5a87a275ff"
		+ "f700060c54657374206d657373616765\",\"hashOfBlobToSign\":\"503072d2fa763dd8e4f3796165674389785401686b4a"
		+ "6b2ca8b518203930bf0e\"}},\"id\":\"1\",\"jsonrpc\":\"2.0\"}\n";

	private static final String FINALIZE_TRANSACTION = "{\"result\":{\"txID\":"
		+ "\"a41e12e424431e8f5f8b86eddc36fb84c6a1811d9005607258f799675a3bc338\"},\"id\":\"2\",\"jsonrpc\":\"2.0\"}\n";

	private final OkHttpClient client = mock(OkHttpClient.class);

	@Test
	public void testBuildTransaction() throws IOException {
		var hash = Hex.decode("503072d2fa763dd8e4f3796165674389785401686b4a6b2ca8b518203930bf0e");

		var request = TransactionRequest.createBuilder(ACCOUNT_ADDRESS1)
			.transfer(
				ACCOUNT_ADDRESS1,
				ACCOUNT_ADDRESS2,
				UInt256.NINE,
				"xrd_rb1qya85pwq"
			)
			.message("Test message")
			.build();

		prepareClient(BUILT_TRANSACTION)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.transaction().build(request)
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(dto -> assertEquals(UInt256.from(100000000000000000L), dto.getFee()))
				.onSuccess(dto -> assertArrayEquals(hash, dto.getTransaction().getHashToSign()))
			);
	}

	@Test
	public void testFinalizeTransaction() throws Exception {
		var request = buildFinalizedTransaction();
		var txId = AID.from("a41e12e424431e8f5f8b86eddc36fb84c6a1811d9005607258f799675a3bc338");

		prepareClient(FINALIZE_TRANSACTION)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.transaction().finalize(request)
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(dto -> assertEquals(txId, dto.getTxId()))
			);
	}

	@Test
	public void testSubmitTransaction() throws Exception {
		var txId = AID.from("a41e12e424431e8f5f8b86eddc36fb84c6a1811d9005607258f799675a3bc338");
		var request = buildFinalizedTransaction().withTxId(txId);

		prepareClient(FINALIZE_TRANSACTION)
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
					.map(txDTO -> finalizedTransaction.withTxId(txDTO.getTxId()))
					.onSuccess(submittableTransaction -> client.transaction().submit(submittableTransaction)
						.onFailure(failure -> fail(failure.toString()))
						.onSuccess(txDTO -> submittableTransaction.rawTxId()
							.ifPresentOrElse(aid -> assertEquals(aid, txDTO.getTxId()), () -> fail("Should not happen")))))
			);
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
					.map(txDTO -> finalizedTransaction.withTxId(txDTO.getTxId()))
					.onSuccess(submittableTransaction -> client.transaction().submit(submittableTransaction)
						.onFailure(failure -> fail(failure.toString()))
						.onSuccess(txDTO -> submittableTransaction.rawTxId()
							.ifPresentOrElse(aid -> assertEquals(aid, txDTO.getTxId()), () -> fail("Should not happen")))))
			);
	}

	private FinalizedTransaction buildFinalizedTransaction() throws PublicKeyException {
		var blob = Hex.decode("0103010402c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee"
								  + "5000000000000000000000000000000000000000000000000000000000000000904fcdcdd43e66c"
								  + "ff732ba9a0cbd484cdd9fa9579388b67e3878fd981280a48372e00000003010301040279be667ef"
								  + "9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798000000000000000000000000"
								  + "002cd76fe086b93ce2f768a00b229ffffffffff7000500000002010301040279be667ef9dcbbac5"
								  + "5a06295ce870b07029bfcdb2dce28d959f2815b16f81798000000000000000000000000002cd76f"
								  + "e086b93ce2f768a009bf5a87a275fff700");
		var sig = ECDSASignature.decodeFromHexDer("30440220768a67a36549e11f19ddb6e2c172c3"
													  + "f2f2996600413f1d2f246667ab2de81ddf0220"
													  + "70f3bb613bcba2704728b99fad91668e2d6759"
													  + "3f73b7c3567eae61596242f64c");

		var pubkey = ECPublicKey.fromHex("0479be667ef9dcbbac55a06295ce870b07029bfcdb2dce"
											 + "28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8"
											 + "fd17b448a68554199c47d08ffb10d4b8");
		return FinalizedTransaction.create(blob, sig, pubkey, null);
	}

	private Result<RadixApi> prepareClient(String responseBody) throws IOException {
		var call = mock(Call.class);
		var response = mock(Response.class);
		var body = mock(ResponseBody.class);

		when(client.newCall(any())).thenReturn(call);
		when(call.execute()).thenReturn(response);
		when(response.body()).thenReturn(body);
		when(body.string()).thenReturn(responseBody);

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
