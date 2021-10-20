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
package com.radixdlt.client.lib.api.async;

import org.bouncycastle.util.encoders.Hex;
import org.junit.Ignore;
import org.junit.Test;

import com.radixdlt.api.addressing.AccountAddress;
import com.radixdlt.api.addressing.ValidatorAddress;
import com.radixdlt.api.dto.response.FinalizedTransaction;
import com.radixdlt.api.dto.response.TxBlobDTO;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Promise;

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
	@Ignore("Online test")
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
	@Ignore("Online test")
	public void testCreateFixedSupplyToken() {
		var request = TransactionRequest.createBuilder(ACCOUNT_ADDRESS1)
			.createFixed(ACCOUNT_ADDRESS1, KEY_PAIR1.getPublicKey(),
						 "fix", "fix", "fix",
						 "https://some.host.com/", "https://some.other.host.com",
						 amount(1000).tokens())
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
	@Ignore("Online test")
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
		return AsyncRadixApi.connect(BASE_URL, RadixApi.DEFAULT_PRIMARY_PORT, RadixApi.DEFAULT_SECONDARY_PORT, client, Optional.empty());
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
