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

import org.junit.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AsyncRadixApiLedgerTest {
	private static final String BASE_URL = "http://localhost/";

	private static final String LATEST = "{\"result\":{\"opaque\":\"885a33bf8c2f4aef73580b0cd7da796a44dbf6036d37fcaab7b525ba27fff843\","
		+ "\"sigs\":[{\"address\":\"vb1q27acjcz0vs0dg9mwv7nwyxfxu28rcvu35zwcnn9ulul25ss3kfgkue7d6p\","
		+ "\"signature\":\"7c4a53130db2d893e14798c61b9c3e0c2e0ba40bad204f76baeaa70b0cf12c3c77fb07e33a4"
		+ "1a70a7e41cf2098bd8158984d7deb64e81771789f2a4fb619aaff\",\"timestamp\":1623323904385},{\"address\":"
		+ "\"vb1q0tczj5k4n5nw7lf4prxrawja84pjtxwh68gl65hd9almsg77r87zmhdqpf\",\"signature\":\"53380384a947d12"
		+ "df668265b4b273620950d12e5f75eeb4a5d68a2e266ecaabe1f7cec661e482efddfe158ad3a111c16823076289d04e786c"
		+ "9e22d21c0d230e7\",\"timestamp\":1623323904384}],\"header\":{\"view\":4469,\"epoch\":99,\"accumulator\":"
		+ "\"7a640a1600596d9f9c77c39fb759e0a0a5c92962ecb217f9280618be30f614dc\",\"version\":903468,"
		+ "\"timestamp\":1623323904373}},\"id\":\"1\",\"jsonrpc\":\"2.0\"}\n";

	private static final String EPOCH = "{\"result\":{\"opaque\":\"8ffa3f6ecd4a22a2260e19e2973a0fb1a714afb69e56e"
		+ "24c73e6dcd10c02cc02\",\"sigs\":[{\"address\":\"vb1q27acjcz0vs0dg9mwv7nwyxfxu28rcvu35zwcnn9ulul25ss3kf"
		+ "gkue7d6p\",\"signature\":\"3b69dd571c17b69b4695e5bfa6fa68c2d64153217f6cca605fe12dcd3e5e43ca7cfbbd43d2"
		+ "27ed6a2b557d4f111308a30379a81e0b011ff95f0644ce0111e427\",\"timestamp\":1623324682937},{\"address\":"
		+ "\"vb1q0tczj5k4n5nw7lf4prxrawja84pjtxwh68gl65hd9almsg77r87zmhdqpf\",\"signature\":"
		+ "\"8c52f3f6d7008df4d39cc5b5b178f0d0b94b7d2457242ce88d880b0d1fb9f66424857fc6a96bce52"
		+ "8a05510df3d6b7d46e295176c061360798ffc1414a9e9cee\",\"timestamp\":1623324682936}],\"header\":"
		+ "{\"view\":1000,\"epoch\":6,\"accumulator\":\"770d2a33c73ca44bda865a0f67e11a3d4752a4419b4c4110"
		+ "571c48405105e8a6\",\"nextValidators\":[{\"stake\":\"1000000000000000000000000\",\"address\":"
		+ "\"vb1q27acjcz0vs0dg9mwv7nwyxfxu28rcvu35zwcnn9ulul25ss3kfgkue7d6p\"},{\"stake\":\"100000000000"
		+ "0000000000000\",\"address\":\"vb1q0tczj5k4n5nw7lf4prxrawja84pjtxwh68gl65hd9almsg77r87zmhdqpf\"}],"
		+ "\"version\":5999,\"timestamp\":1623324682927}},\"id\":\"1\",\"jsonrpc\":\"2.0\"}\n";

	private static final String CHECKPOINTS = "{\"result\":{\"txn\":[\"020000010600000300010378726401020100045"
		+ "261647314526164697820426574616e657420546f6b656e731c68747470733a2f2f746f6b656e732e7261646978646c742e"
		+ "636f6d2f3468747470733a2f2f6173736574732e7261646978646c742e636f6d2f69636f6e732f69636f6e2d7872642d333"
		+ "27833322e706e6700010301040279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f8179800000000"
		+ "0000000000000000002cd76fe086b93ce2f768a00b22a00000000000000103010402c6047f9441ed7d6d3045406e95c07cd"
		+ "85c778e4b8cef3ca7abac09b95c709ee5000000000000000000000000002cd76fe086b93ce2f768a00b22a0000000000000"
		+ "0103010402f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f90000000000000000000000000"
		+ "02cd76fe086b93ce2f768a00b22a00000000000000103010402e493dbf1c10d80f3581e4904930b1404cc6c13900ee07584"
		+ "74fa94abe8c4cd13000000000000000000000000002cd76fe086b93ce2f768a00b22a000000000000001030104022f8bde4"
		+ "d1a07209355b4a7250a5c5128e88b84bddc619ab7cba8d569b240efe4000000000000000000000000002cd76fe086b93ce2"
		+ "f768a00b22a00000000000000103010403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a14602975560"
		+ "00000000000000000000000002cd76fe086b93ce2f768a00b22a00000000000000103010402bddc4b027b20f6a0bb733d37"
		+ "10c9371471e19c8d04ec4e65e7f9f552108d928b000000000000000000000000002cd76fe086b93ce2f768a00b22a000000"
		+ "00000000103010403d7814a96ace9377be9a84661f5d2e9ea192ccebe8e8fea97697bfdc11ef0cfe1000000000000000000"
		+ "000000002cd76fe086b93ce2f768a00b22a0000000000000020502bddc4b027b20f6a0bb733d3710c9371471e19c8d04ec4"
		+ "e65e7f9f552108d928b000000010502bddc4b027b20f6a0bb733d3710c9371471e19c8d04ec4e65e7f9f552108d928b0100"
		+ "0000020503d7814a96ace9377be9a84661f5d2e9ea192ccebe8e8fea97697bfdc11ef0cfe1000000010503d7814a96ace93"
		+ "77be9a84661f5d2e9ea192ccebe8e8fea97697bfdc11ef0cfe10100000001040403fff97bd5755eeea420453a14355235d3"
		+ "82f6472f8568a18b2f057a146029755602bddc4b027b20f6a0bb733d3710c9371471e19c8d04ec4e65e7f9f552108d928b0"
		+ "0000000000000000000000000000000000000000000d3c21bcecceda100000005000000100103010403fff97bd5755eeea4"
		+ "20453a14355235d382f6472f8568a18b2f057a1460297556000000000000000000000000002cd76fe086b93ce2f694ddef5"
		+ "3d3125f0000000001040403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a146029755603d7814a96ac"
		+ "e9377be9a84661f5d2e9ea192ccebe8e8fea97697bfdc11ef0cfe100000000000000000000000000000000000000000000d"
		+ "3c21bcecceda1000000050000001e0103010403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a146029"
		+ "7556000000000000000000000000002cd76fe086b93ce2f5c11bd3850624be00000000030003263979958c0fea4f80f7d8a"
		+ "bfd17d91ac1478d9b236742047c160367756d010203263979958c0fea4f80f7d8abfd17d91ac1478d9b236742047c160200"
		+ "0000000000000000000000002cd76fe086b93ce2f768a00b22a000000000000847756d62616c6c730000000103032639799"
		+ "58c0fea4f80f7d8abfd17d91ac1478d9b236742047c160403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f"
		+ "057a1460297556000000000000000000000000002cd76fe086b93ce2f768a00b22a00000000000000300037b9118adeeef7"
		+ "2cd91b06a8fd3b43e2f429201eff4819f1a89b704636572620102037b9118adeeef72cd91b06a8fd3b43e2f429201eff481"
		+ "9f1a89b702000000000000000000000000002cd76fe086b93ce2f768a00b22a000000000001543657262797320537065636"
		+ "9616c20546f6b656e730000000103037b9118adeeef72cd91b06a8fd3b43e2f429201eff4819f1a89b70403fff97bd5755e"
		+ "eea420453a14355235d382f6472f8568a18b2f057a1460297556000000000000000000000000002cd76fe086b93ce2f768a"
		+ "00b22a0000000000000030003fbb9b21d79466488f134c87a9704444ac883855f4d81f681f5cc06656d756e6965010203fb"
		+ "b9b21d79466488f134c87a9704444ac883855f4d81f681f5cc02000000000000000000000000002cd76fe086b93ce2f768a"
		+ "00b22a000000000000d654d756e696520546f6b656e73000000010303fbb9b21d79466488f134c87a9704444ac883855f4d"
		+ "81f681f5cc0403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556000000000000000000000"
		+ "000002cd76fe086b93ce2f768a00b22a0000000000000010303263979958c0fea4f80f7d8abfd17d91ac1478d9b23674204"
		+ "7c160402bddc4b027b20f6a0bb733d3710c9371471e19c8d04ec4e65e7f9f552108d928b000000000000000000000000000"
		+ "00000000000000000d3c21bcecceda10000000500000026010303263979958c0fea4f80f7d8abfd17d91ac1478d9b236742"
		+ "047c160403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a14602975560000000000000000000000000"
		+ "02cd76fe086b93ce2f694ddef53d3125f000000000103037b9118adeeef72cd91b06a8fd3b43e2f429201eff4819f1a89b7"
		+ "0402bddc4b027b20f6a0bb733d3710c9371471e19c8d04ec4e65e7f9f552108d928b0000000000000000000000000000000"
		+ "0000000000000d3c21bcecceda1000000050000002a0103037b9118adeeef72cd91b06a8fd3b43e2f429201eff4819f1a89"
		+ "b70403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556000000000000000000000000002cd"
		+ "76fe086b93ce2f694ddef53d3125f00000000010303fbb9b21d79466488f134c87a9704444ac883855f4d81f681f5cc0402"
		+ "bddc4b027b20f6a0bb733d3710c9371471e19c8d04ec4e65e7f9f552108d928b00000000000000000000000000000000000"
		+ "000000000d3c21bcecceda1000000050000002e010303fbb9b21d79466488f134c87a9704444ac883855f4d81f681f5cc04"
		+ "03fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556000000000000000000000000002cd76fe"
		+ "086b93ce2f694ddef53d3125f00000000010303263979958c0fea4f80f7d8abfd17d91ac1478d9b236742047c160403d781"
		+ "4a96ace9377be9a84661f5d2e9ea192ccebe8e8fea97697bfdc11ef0cfe1000000000000000000000000000000000000000"
		+ "00000d3c21bcecceda10000000500000032010303263979958c0fea4f80f7d8abfd17d91ac1478d9b236742047c160403ff"
		+ "f97bd5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556000000000000000000000000002cd76fe086b"
		+ "93ce2f5c11bd3850624be000000000103037b9118adeeef72cd91b06a8fd3b43e2f429201eff4819f1a89b70403d7814a96"
		+ "ace9377be9a84661f5d2e9ea192ccebe8e8fea97697bfdc11ef0cfe10000000000000000000000000000000000000000000"
		+ "0d3c21bcecceda100000005000000360103037b9118adeeef72cd91b06a8fd3b43e2f429201eff4819f1a89b70403fff97b"
		+ "d5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556000000000000000000000000002cd76fe086b93ce"
		+ "2f5c11bd3850624be00000000010303fbb9b21d79466488f134c87a9704444ac883855f4d81f681f5cc0403d7814a96ace9"
		+ "377be9a84661f5d2e9ea192ccebe8e8fea97697bfdc11ef0cfe100000000000000000000000000000000000000000000d3c"
		+ "21bcecceda1000000050000003a010303fbb9b21d79466488f134c87a9704444ac883855f4d81f681f5cc0403fff97bd575"
		+ "5eeea420453a14355235d382f6472f8568a18b2f057a1460297556000000000000000000000000002cd76fe086b93ce2f5c"
		+ "11bd3850624be00000000020100000000000000000000000000000000000000000000000001010000000000000001000000"
		+ "00000000000000016f5e66e80000\"],\"proof\":{\"opaque\":\"0000000000000000000000000000000000000000000"
		+ "000000000000000000000\",\"sigs\":[],\"header\":{\"view\":0,\"epoch\":0,\"accumulator\":\"8ea194df87"
		+ "59b0780ca7a0526540f8067c97c67040c656aadde295e745f64852\",\"nextValidators\":[{\"stake\":\"10000000"
		+ "00000000000000000\",\"address\":\"vb1q27acjcz0vs0dg9mwv7nwyxfxu28rcvu35zwcnn9ulul25ss3kfgkue7d6p\"},"
		+ "{\"stake\":\"1000000000000000000000000\",\"address\":\"vb1q0tczj5k4n5nw7lf4prxrawja84pjtxwh68gl65hd9"
		+ "almsg77r87zmhdqpf\"}],\"version\":0,\"timestamp\":1577836800000}}},\"id\":\"1\",\"jsonrpc\":\"2.0\"}\n";

	private final HttpClient client = mock(HttpClient.class);

	@Test
	public void testLatest() throws IOException {
		prepareClient(LATEST)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.ledger().latest()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(proof -> assertEquals(2, proof.getSigs().size()))
				.onSuccess(proof -> assertEquals(99L, proof.getHeader().getEpoch()))
				.onSuccess(proof -> assertEquals(903468L, proof.getHeader().getVersion()))
				.onSuccess(proof -> assertEquals(4469L, proof.getHeader().getView()))
				.join())
			.join();
	}

	@Test
	public void testEpoch() throws IOException {
		prepareClient(EPOCH)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.ledger().epoch()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(proof -> assertEquals(2, proof.getSigs().size()))
				.onSuccess(proof -> assertEquals(6L, proof.getHeader().getEpoch()))
				.onSuccess(proof -> assertEquals(5999L, proof.getHeader().getVersion()))
				.onSuccess(proof -> assertEquals(1000L, proof.getHeader().getView()))
				.onSuccess(proof -> assertEquals(2, proof.getHeader().getNextValidators().size()))
				.join())
			.join();
	}

	@Test
	public void testCheckpoints() throws IOException {
		prepareClient(CHECKPOINTS)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.ledger().checkpoints()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(checkpoint -> assertEquals(1, checkpoint.getTxn().size()))
				.onSuccess(checkpoint -> assertEquals(2, checkpoint.getProof().getHeader().getNextValidators().size()))
				.join())
			.join();
	}

	private Promise<RadixApi> prepareClient(String responseBody) throws IOException {
		@SuppressWarnings("unchecked")
		var response = (HttpResponse<String>) mock(HttpResponse.class);
		var completableFuture = new CompletableFuture<HttpResponse<String>>();

		when(response.body()).thenReturn(responseBody);
		when(client.<String>sendAsync(any(), any())).thenReturn(completableFuture);

		try {
			return AsyncRadixApi.connect(BASE_URL, RadixApi.DEFAULT_PRIMARY_PORT, RadixApi.DEFAULT_SECONDARY_PORT, client);
		} finally {
			completableFuture.completeAsync(() -> response);
		}
	}
}
