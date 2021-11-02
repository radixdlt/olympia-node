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

import org.junit.Test;

import com.radixdlt.utils.functional.Promise;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AsyncRadixApiLedgerTest {
	private static final String BASE_URL = "http://localhost/";

	private static final String NETWORK_ID = "{\"result\":{\"networkId\":99},\"id\":\"1\",\"jsonrpc\":\"2.0\"}";
	private static final String LATEST = "{\"result\":{\"opaque\":\"9d0d7d4db4822318f817e8c877d5041052f208e9e5"
		+ "277c1e0b316506ace50174\",\"sigs\":[{\"signature\":\"00ac3318d3aa4fef7521ab3479da43f8f91905cf0b0a886"
		+ "7154e911853e641c94633e78ca4b4d7a6bacced172844c4fc694cd9c0e3032e0f28130f200c1d5e5c0e\",\"key\":\"047"
		+ "8a55e2203ac74fcd1aed34546575b0dff550163d8e60660a69d72d1f248d4139cb81aac9e4ea8046308d635c8894efb8388"
		+ "e3ebbe7971d5dbb5326659137a76\",\"timestamp\":1625056180672},{\"signature\":\"004b1caf44bd69c18ee93b"
		+ "9987293c2f9283dc8beddd12a16cdb0ae9d5a78695445a320bce8bce206dffddaffc09f4f3fe911ba7c96cca7a368c8cd3f"
		+ "a41b8ef98\",\"key\":\"04686816105b2b3b5b70ac5ddce0023c6156da9ad6a03e4ec6a5f5801403c10298eb018887684"
		+ "161d26e998f801947aee9c22f2d02f636879fcf94e13f8b70ffa9\",\"timestamp\":1625056180670}],\"header\":{\""
		+ "view\":212000,\"epoch\":1,\"accumulator\":\"722f7a84e8b19c7ea1ca170f13197455900dd87b3287cbd6966cf77d"
		+ "24d4ae7b\",\"version\":212000,\"timestamp\":1625056180643}},\"id\":\"2\",\"jsonrpc\":\"2.0\"}\n";

	private static final String EPOCH = "{\"result\":{\"opaque\":\"00000000000000000000000000000000000000000000"
		+ "00000000000000000000\",\"sigs\":[],\"header\":{\"view\":0,\"nextValidators\":[{\"stake\":\"100000000"
		+ "0000000000000000000\",\"address\":\"dv1qfu22h3zqwk8flx34mf523jhtvxl74gpv0vwvpnq56wh950jfr2pxlsh8j4\""
		+ "},{\"stake\":\"1000000000000000000000000000\",\"address\":\"dv1qd5xs9sstv4nkkms43waecqz83s4dk5666sru"
		+ "nkx5h6cq9qrcypfsehda8j\"}],\"epoch\":0,\"accumulator\":\"93386db99f9a2c897dbe48a2f8ed227bd1f06a8f6b9"
		+ "8e102c850f9d6a43c42a8\",\"version\":1,\"timestamp\":0}},\"id\":\"2\",\"jsonrpc\":\"2.0\"}\n";

	private static final String CHECKPOINTS = "{\"result\":{\"txn\":[\"020000000102000000000000000000010100000"
		+ "0000000000000000000005e0be1000003000001037872640103000104526164730c526164697820546f6b656e731c687474"
		+ "70733a2f2f746f6b656e732e7261646978646c742e636f6d2f3468747470733a2f2f6173736574732e7261646978646c742"
		+ "e636f6d2f69636f6e732f69636f6e2d7872642d33327833322e706e6700010400040279be667ef9dcbbac55a06295ce870b"
		+ "07029bfcdb2dce28d959f2815b16f81798010000000000000000000000000000000000000000033b2e3c9fd0803ce800000"
		+ "0000104000402c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee50100000000000000000000"
		+ "00000000000000000000033b2e3c9fd0803ce8000000000104000402f9308a019258c31049344f85f89d5229b531c845836"
		+ "f99b08601f113bce036f9010000000000000000000000000000000000000000033b2e3c9fd0803ce8000000000104000402"
		+ "e493dbf1c10d80f3581e4904930b1404cc6c13900ee0758474fa94abe8c4cd1301000000000000000000000000000000000"
		+ "0000000033b2e3c9fd0803ce80000000001040004022f8bde4d1a07209355b4a7250a5c5128e88b84bddc619ab7cba8d569"
		+ "b240efe4010000000000000000000000000000000000000000033b2e3c9fd0803ce800000000010400040230e40bae81100"
		+ "366a6b7cc5a83815b7e08bd518bf7a2bd6b781f09424eeb88c8010000000000000000000000000000000000000000033b2e"
		+ "3c9fd0803ce8000000000104000402e5fe901665951ae1d7a0e1e573939390bc9d131d501d318806f6d97ca993074e01000"
		+ "0000000000000000000000000000000000000033b2e3c9fd0803ce800000000020d000230e40bae81100366a6b7cc5a8381"
		+ "5b7e08bd518bf7a2bd6b781f09424eeb88c800010e000230e40bae81100366a6b7cc5a83815b7e08bd518bf7a2bd6b781f0"
		+ "9424eeb88c80100020c000230e40bae81100366a6b7cc5a83815b7e08bd518bf7a2bd6b781f09424eeb88c800010c000230"
		+ "e40bae81100366a6b7cc5a83815b7e08bd518bf7a2bd6b781f09424eeb88c80100020d0002e5fe901665951ae1d7a0e1e57"
		+ "3939390bc9d131d501d318806f6d97ca993074e00010e0002e5fe901665951ae1d7a0e1e573939390bc9d131d501d318806"
		+ "f6d97ca993074e0100020c0002e5fe901665951ae1d7a0e1e573939390bc9d131d501d318806f6d97ca993074e00010c000"
		+ "2e5fe901665951ae1d7a0e1e573939390bc9d131d501d318806f6d97ca993074e010005000000080c0000000b0105000230"
		+ "e40bae81100366a6b7cc5a83815b7e08bd518bf7a2bd6b781f09424eeb88c8040230e40bae81100366a6b7cc5a83815b7e0"
		+ "8bd518bf7a2bd6b781f09424eeb88c80000000000000000000000000000000000000000033b2e3c9fd0803ce80000000005"
		+ "000000090c0000000d01050002e5fe901665951ae1d7a0e1e573939390bc9d131d501d318806f6d97ca993074e0402e5fe9"
		+ "01665951ae1d7a0e1e573939390bc9d131d501d318806f6d97ca993074e0000000000000000000000000000000000000000"
		+ "033b2e3c9fd0803ce800000000050000000105000000000b0a08000000000000000001080b08070805020a0000000000000"
		+ "000000000000000000000000000000000000000000000000000000002e5fe901665951ae1d7a0e1e573939390bc9d131d50"
		+ "1d318806f6d97ca993074e0000000000000000000000000000000000000000000000000000000000000000000027100402e"
		+ "5fe901665951ae1d7a0e1e573939390bc9d131d501d318806f6d97ca993074e01060002e5fe901665951ae1d7a0e1e57393"
		+ "9390bc9d131d501d318806f6d97ca993074e0402e5fe901665951ae1d7a0e1e573939390bc9d131d501d318806f6d97ca99"
		+ "3074e0000000000000000000000000000000000000000033b2e3c9fd0803ce8000000020a00000000000000000000000000"
		+ "0000000000000000000000000000000000000000000230e40bae81100366a6b7cc5a83815b7e08bd518bf7a2bd6b781f094"
		+ "24eeb88c8000000000000000000000000000000000000000000000000000000000000000000002710040230e40bae811003"
		+ "66a6b7cc5a83815b7e08bd518bf7a2bd6b781f09424eeb88c80106000230e40bae81100366a6b7cc5a83815b7e08bd518bf"
		+ "7a2bd6b781f09424eeb88c8040230e40bae81100366a6b7cc5a83815b7e08bd518bf7a2bd6b781f09424eeb88c800000000"
		+ "00000000000000000000000000000000033b2e3c9fd0803ce80000000b0a100000000000000000010812080e010d0002e5f"
		+ "e901665951ae1d7a0e1e573939390bc9d131d501d318806f6d97ca993074e01010d000230e40bae81100366a6b7cc5a8381"
		+ "5b7e08bd518bf7a2bd6b781f09424eeb88c801010a00010000000000000000000000000000000000000000033b2e3c9fd08"
		+ "03ce800000002e5fe901665951ae1d7a0e1e573939390bc9d131d501d318806f6d97ca993074e0000000000000000000000"
		+ "000000000000000000033b2e3c9fd0803ce8000000000027100402e5fe901665951ae1d7a0e1e573939390bc9d131d501d3"
		+ "18806f6d97ca993074e010a00010000000000000000000000000000000000000000033b2e3c9fd0803ce80000000230e40b"
		+ "ae81100366a6b7cc5a83815b7e08bd518bf7a2bd6b781f09424eeb88c800000000000000000000000000000000000000000"
		+ "33b2e3c9fd0803ce800000000002710040230e40bae81100366a6b7cc5a83815b7e08bd518bf7a2bd6b781f09424eeb88c8"
		+ "010b0002e5fe901665951ae1d7a0e1e573939390bc9d131d501d318806f6d97ca993074e000000000000000000000000000"
		+ "00000010b000230e40bae81100366a6b7cc5a83815b7e08bd518bf7a2bd6b781f09424eeb88c80000000000000000000000"
		+ "000000000001020000000000000000010101000000000000000000000000005e0be10000\"],\"proof\":{\"opaque\":"
		+ "\"0000000000000000000000000000000000000000000000000000000000000000\",\"sigs\":[],\"header\":{\"view"
		+ "\":0,\"nextValidators\":[{\"stake\":\"1000000000000000000000000000\",\"address\":\"dv1qtjlayqkvk234"
		+ "cwh5rs72uunjwgte8gnr4gp6vvgqmmdjl9fjvr5ulnv4zg\"},{\"stake\":\"1000000000000000000000000000\",\"add"
		+ "ress\":\"dv1qgcwgzawsygqxe4xklx94quptdlq302330m690tt0q0sjsjwawyvs6zsklj\"}],\"epoch\":0,\"accumulat"
		+ "or\":\"7d773d192544808ba16a047781ae06ce3cb140d65bb757439947de81aea8422b\",\"version\":1,\"timestamp"
		+ "\":0}}},\"id\":\"2\",\"jsonrpc\":\"2.0\"}\n";

	private final HttpClient client = mock(HttpClient.class);

	@Test
	public void testLatest() throws IOException {
		prepareClient(LATEST)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.ledger().latest().join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(proof -> assertEquals(2, proof.getSigs().size()))
				.onSuccess(proof -> assertEquals(1L, proof.getHeader().getEpoch()))
				.onSuccess(proof -> assertEquals(212000L, proof.getHeader().getVersion()))
				.onSuccess(proof -> assertEquals(212000L, proof.getHeader().getView())));
	}

	@Test
	public void testEpoch() throws IOException {
		prepareClient(EPOCH)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.ledger().epoch().join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(proof -> assertEquals(0, proof.getSigs().size()))
				.onSuccess(proof -> assertEquals(0L, proof.getHeader().getEpoch()))
				.onSuccess(proof -> assertEquals(1L, proof.getHeader().getVersion()))
				.onSuccess(proof -> assertEquals(0L, proof.getHeader().getView()))
				.onSuccess(proof -> assertEquals(2, proof.getHeader().getNextValidators().size())));
	}

	@Test
	public void testCheckpoints() throws IOException {
		prepareClient(CHECKPOINTS)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.ledger().checkpoints().join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(checkpoint -> assertEquals(1, checkpoint.getTxn().size()))
				.onSuccess(checkpoint -> assertEquals(2, checkpoint.getProof().getHeader().getNextValidators().size())));
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
}
