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
package com.radixdlt.client.lib.api.async;

import org.bouncycastle.util.encoders.Hex;
import org.junit.Ignore;
import org.junit.Test;

import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.api.ValidatorAddress;
import com.radixdlt.client.lib.dto.FinalizedTransaction;
import com.radixdlt.client.lib.dto.TxBlobDTO;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.UInt256;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.client.lib.api.token.Amount.amount;

//TODO: test remaining actions!!!
public class AsyncRadixApiCreationTest {
	private static final String BASE_URL = "http://localhost/";
	public static final ECKeyPair KEY_PAIR1 = keyPairOf(1);
	public static final ECKeyPair KEY_PAIR2 = keyPairOf(2);
	public static final ECKeyPair KEY_PAIR3 = keyPairOf(3);
	private static final AccountAddress ACCOUNT_ADDRESS1 = AccountAddress.create(KEY_PAIR1.getPublicKey());
	private static final AccountAddress ACCOUNT_ADDRESS2 = AccountAddress.create(KEY_PAIR2.getPublicKey());
	private static final AccountAddress ACCOUNT_ADDRESS3 = AccountAddress.create(KEY_PAIR3.getPublicKey());
	private static final ValidatorAddress VALIDATOR_ADDRESS = ValidatorAddress.of(KEY_PAIR3.getPublicKey());

	private static final String NETWORK_ID = "{\"result\":{\"networkId\":2},\"id\":\"1\",\"jsonrpc\":\"2.0\"}";
	private static final String BUILT_TRANSACTION = "{\"result\":{\"fee\":\"73800000000000000\",\"transaction\":{\"blob\":"
		+ "\"060a104c95b2f14ca1c6500b519ad696bee17b7a982810c5e4fe43d39b979bfbc300000001012100000000000000000000000000000"
		+ "000000000000000000000010630b5806c8000020500040279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f817"
		+ "98010000000000000000000000000000000000000000033b2e3730f52422c1c17ff6000700000000020500040279be667ef9dcbbac55a"
		+ "06295ce870b07029bfcdb2dce28d959f2815b16f81798010000000000000000000000000000000000000000033b2e3730f52422c1c17f"
		+ "ec0205000402c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5010000000000000000000000000000000"
		+ "00000000000000000000000000000000a000b0c54657374206d657373616765\",\"hashOfBlobToSign\":\"76448a9f09e5bb9fbce1"
		+ "1844731e8a7e28601733100787462401f47916bbc4ac\"}},\"id\":\"2\",\"jsonrpc\":\"2.0\"}";

	private static final String BLOB = "060a104c95b2f14ca1c6500b519ad696bee17b7a982810c5e4fe43d39b979bfbc300000001012100"
		+ "000000000000000000000000000000000000000000000000010630b5806c8000020500040279be667ef9dcbbac55a06295ce870b07029"
		+ "bfcdb2dce28d959f2815b16f81798010000000000000000000000000000000000000000033b2e3730f52422c1c17ff600070000000002"
		+ "0500040279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f817980100000000000000000000000000000000000"
		+ "00000033b2e3730f52422c1c17fec0205000402c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5010000"
		+ "00000000000000000000000000000000000000000000000000000000000a000b0c54657374206d657373616765";

	private static final String TX_ID = "a84843d8c51f92a872a926cd29a2074f1c85bf47392a2fd0e41a4272e38f1aa5";
	private static final String SIG = "3045022100f179714d7577a105d0a37891bc149ed6ba519435dcc53340f0467611e0d31bb40220388"
		+ "dfd1e25b25a80366fc70415334d1e4af259aee5685e93a8cd03004e1a8157";
	private static final String PUB_KEY = "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";

	private static final String FINALIZE_TRANSACTION = "{\"result\":{\"blob\":\"060a104c95b2f14ca1c6500b519ad696bee17b7a"
		+ "982810c5e4fe43d39b979bfbc300000001012100000000000000000000000000000000000000000000000000010630b5806c800002050"
		+ "0040279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f817980100000000000000000000000000000000000000"
		+ "00033b2e3730f52422c1c17ff6000700000000020500040279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81"
		+ "798010000000000000000000000000000000000000000033b2e3730f52422c1c17fec0205000402c6047f9441ed7d6d3045406e95c07c"
		+ "d85c778e4b8cef3ca7abac09b95c709ee501000000000000000000000000000000000000000000000000000000000000000a000b0c546"
		+ "57374206d6573736167650a01f179714d7577a105d0a37891bc149ed6ba519435dcc53340f0467611e0d31bb4388dfd1e25b25a80366f"
		+ "c70415334d1e4af259aee5685e93a8cd03004e1a8157\",\"txID\":\"a84843d8c51f92a872a926cd29a2074f1c85bf47392a2fd0e41"
		+ "a4272e38f1aa5\"},\"id\":\"3\",\"jsonrpc\":\"2.0\"}";
	private static final String SUBMIT_TRANSACTION = "{\"result\":{\"txID\":\"a84843d8c51f92a872a926cd29a2074f1c85bf4739"
		+ "2a2fd0e41a4272e38f1aa5\"},\"id\":\"4\",\"jsonrpc\":\"2.0\"}";

	private final HttpClient client = mock(HttpClient.class);

	@Test
	public void testBuildTransaction() throws IOException {
		var hash = Hex.decode("76448a9f09e5bb9fbce11844731e8a7e28601733100787462401f47916bbc4ac");

		var request = TransactionRequest.createBuilder(ACCOUNT_ADDRESS1)
			.transfer(ACCOUNT_ADDRESS1, ACCOUNT_ADDRESS2, amount(10).subunits(), "xrd_dr1qyrs8qwl")
			.message("Test message")
			.build();

		prepareClient(BUILT_TRANSACTION)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.transaction().build(request).join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(dto -> assertEquals(amount(73800).micros(), dto.getFee()))
				.onSuccess(dto -> assertArrayEquals(hash, dto.getTransaction().getHashToSign())));
	}

	@Test
	public void testFinalizeTransaction() throws Exception {
		var request = buildFinalizedTransaction();
		var txId = AID.from(TX_ID);

		prepareClient(FINALIZE_TRANSACTION)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.transaction().finalize(request, false).join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(dto -> assertEquals(txId, dto.getTxId())));
	}

	@Test
	public void testSubmitTransaction() throws Exception {
		var txId = AID.from(TX_ID);
		var request = buildBlobDto();

		prepareClient(SUBMIT_TRANSACTION)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.transaction().submit(request).join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(dto -> assertEquals(txId, dto.getTxId())));
	}

	@Test
	@Ignore
	public void testBuildAndSubmitTransactionWithMessage() {
		var request = TransactionRequest.createBuilder(ACCOUNT_ADDRESS1)
			.transfer(ACCOUNT_ADDRESS1, ACCOUNT_ADDRESS2, UInt256.NINE, "xrd_dr1qyrs8qwl")
			.message("Test message")
			.build();

		RadixApi.connect(BASE_URL)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.transaction().build(request).join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(builtTransaction -> assertEquals(amount(73800).micros(), builtTransaction.getFee()))
				.map(builtTransaction -> builtTransaction.toFinalized(KEY_PAIR1))
				.onSuccess(finalizedTransaction -> client.transaction().finalize(finalizedTransaction, true).join()
					.onFailure(failure -> fail(failure.toString()))));
	}

	@Test
	@Ignore
	public void testCreateFixedSupplyToken() {
		var request = TransactionRequest.createBuilder(ACCOUNT_ADDRESS1)
			.createFixed(ACCOUNT_ADDRESS1, KEY_PAIR1.getPublicKey(), "fix", "fix", "fix", "https://some.host.com/", "https://some.other.host.com", amount(1000).tokens())
			.build();

		RadixApi.connect(BASE_URL)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.transaction().build(request)
				.join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(builtTransaction -> assertEquals(amount(1000109).millis(), builtTransaction.getFee()))
				.map(builtTransaction -> builtTransaction.toFinalized(KEY_PAIR1))
				.onSuccess(finalizedTransaction -> client.transaction().finalize(finalizedTransaction, true)
					.join()
					.onFailure(failure -> fail(failure.toString()))));
	}

	@Test
	@Ignore
	//TODO: for some reason operation succeeds only if transaction contains only one action
	public void testRegisterValidator() {
		var request = TransactionRequest.createBuilder(ACCOUNT_ADDRESS3)
			.registerValidator(VALIDATOR_ADDRESS, Optional.of("MyValidator"), Optional.of("http://my.validator.url.com/"))
			.updateValidatorFee(VALIDATOR_ADDRESS, 3.1)
			.updateValidatorOwner(VALIDATOR_ADDRESS, ACCOUNT_ADDRESS3)
			.updateValidatorAllowDelegationFlag(VALIDATOR_ADDRESS, true)
			.build();

		RadixApi.connect(BASE_URL)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.transaction().build(request)
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(builtTransaction -> assertEquals(amount(102600).micros(), builtTransaction.getFee()))
				.map(builtTransaction -> builtTransaction.toFinalized(KEY_PAIR3))
				.onSuccess(finalizedTransaction -> client.transaction().finalize(finalizedTransaction, true).join()
					.onFailure(failure -> fail(failure.toString()))));
	}

	private TxBlobDTO buildBlobDto() {
		return TxBlobDTO.create(AID.from(TX_ID), BLOB);
	}

	private FinalizedTransaction buildFinalizedTransaction() throws PublicKeyException {
		var sig = ECDSASignature.decodeFromHexDer(SIG);
		var publicKey = ECPublicKey.fromHex(PUB_KEY);
		var blob = buildBlobDto();
		return FinalizedTransaction.create(blob.getBlob(), sig, publicKey, blob.getTxId());
	}

	private Promise<RadixApi> prepareClient(String responseBody) throws IOException {
		@SuppressWarnings("unchecked")
		var response = (HttpResponse<String>) mock(HttpResponse.class);
		var completableFuture = new CompletableFuture<HttpResponse<String>>();

		when(response.body()).thenReturn(NETWORK_ID, responseBody);
		when(client.<String>sendAsync(any(), any())).thenReturn(completableFuture);

		completableFuture.completeAsync(() -> response);
		return AsyncRadixApi.connect(BASE_URL, RadixApi.DEFAULT_PRIMARY_PORT, RadixApi.DEFAULT_SECONDARY_PORT, client);
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
