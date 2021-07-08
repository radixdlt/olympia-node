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

import org.junit.Test;

import com.radixdlt.utils.functional.Result;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SyncRadixApiNetworkTest {
	private static final String BASE_URL = "http://localhost/";

	private static final String NETWORK_ID = "{\"result\":{\"networkId\":99},\"id\":\"2\",\"jsonrpc\":\"2.0\"}";
	private static final String DEMAND = "{\"result\":{\"tps\":5},\"id\":\"2\",\"jsonrpc\":\"2.0\"}";
	private static final String THROUGHPUT = "{\"result\":{\"tps\":283},\"id\":\"2\",\"jsonrpc\":\"2.0\"}";
	private static final String DATA = "{\"result\":{\"messages\":{\"inbound\":{\"processed\":399028,"
		+ "\"discarded\":0,\"received\":399028},\"outbound\":{\"processed\":399029,\"aborted\":0,"
		+ "\"pending\":0,\"sent\":399029}},\"networking\":{\"udp\":{\"droppedMessages\":0},"
		+ "\"tcp\":{\"outOpened\":0,\"droppedMessages\":0,\"closed\":0,\"inOpened\":0},"
		+ "\"receivedBytes\":484341853,\"sentBytes\":484444306}},\"id\":\"2\",\"jsonrpc\":\"2.0\"}\n";
	private static final String CONFIGURATION = "{\"result\":{\"defaultPort\":30000,\"maxInboundChannels\":1024,"
		+ "\"broadcastPort\":30000,\"listenAddress\":\"0.0.0.0\",\"channelBufferSize\":255,"
		+ "\"peerConnectionTimeout\":5000,\"pingTimeout\":5000,\"listenPort\":30000,\"discoveryInterval\":30000,"
		+ "\"seedNodes\":[\"radix://dn1qfwtmurydewmf64rnrektuh20g8r6svm0cpnpcuuay4ammw2cnumcfppt2p@core1\"],"
		+ "\"maxOutboundChannels\":1024,\"peerLivenessCheckInterval\":10000},\"id\":\"2\",\"jsonrpc\":\"2.0\"}\n";
	private static final String PEERS = "{\"result\":[{\"address\":\"dn1qghsre0ptn9r28d07wzrldc08shs5x7aqhj6lzy2vauyaulppg4qznsumgv\","
		+ "\"channels\":[{\"localPort\":52434,\"ip\":\"172.20.0.5\",\"type\":\"in\"}]},"
		+ "{\"address\":\"dn1qwsyxnv7gleusc34ga78kxhx4ewngsk5nvv58s4h22ngu2j8ufruw62f4eq\","
		+ "\"channels\":[{\"localPort\":30000,\"ip\":\"172.20.0.3\",\"type\":\"out\",\"uri\":"
		+ "\"radix://dn1qwsyxnv7gleusc34ga78kxhx4ewngsk5nvv58s4h22ngu2j8ufruw62f4eq@172.20.0.3:30000\"},"
		+ "{\"localPort\":42080,\"ip\":\"172.20.0.3\",\"type\":\"in\"}]},"
		+ "{\"address\":\"dn1qfwtmurydewmf64rnrektuh20g8r6svm0cpnpcuuay4ammw2cnumcfppt2p\","
		+ "\"channels\":[{\"localPort\":30000,\"ip\":\"172.20.0.4\",\"type\":\"out\",\"uri\":"
		+ "\"radix://dn1qfwtmurydewmf64rnrektuh20g8r6svm0cpnpcuuay4ammw2cnumcfppt2p@172.20.0.4:30000\"}]},"
		+ "{\"address\":\"dn1qwkdfp8z7rrlv5cf45tc4864n277p9ukjax90ec5cd03zr0uylxtuxr0wk5\","
		+ "\"channels\":[{\"localPort\":30000,\"ip\":\"172.20.0.6\",\"type\":\"out\",\"uri\":"
		+ "\"radix://dn1qwkdfp8z7rrlv5cf45tc4864n277p9ukjax90ec5cd03zr0uylxtuxr0wk5@172.20.0.6:30000\"}]}],\""
		+ "id\":\"2\",\"jsonrpc\":\"2.0\"}\n";

	private final HttpClient client = mock(HttpClient.class);

	@Test
	public void testNetworkId() throws Exception {
		prepareClient(NETWORK_ID)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.network().id()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(networkId -> assertEquals(99, networkId.getNetworkId())));
	}

	@Test
	public void testDemand() throws Exception {
		prepareClient(DEMAND)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.network().demand()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(networkStats -> assertEquals(5L, networkStats.getTps())));
	}

	@Test
	public void testThroughput() throws Exception {
		prepareClient(THROUGHPUT)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.network().throughput()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(networkStats -> assertEquals(283L, networkStats.getTps())));
	}

	@Test
	public void testData() throws Exception {
		prepareClient(DATA)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.network().data()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(networkData -> assertEquals(484341853L, networkData.getNetworking().getReceivedBytes())));
	}

	@Test
	public void testConfiguration() throws Exception {
		prepareClient(CONFIGURATION)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.network().configuration()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(networkConfiguration -> assertEquals(30000L, networkConfiguration.getDefaultPort())));
	}

	@Test
	public void testPeers() throws Exception {
		prepareClient(PEERS)
//		RadixApi.connect(BASE_URL)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.network().peers()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(peers -> System.out.println(peers)));
				//.onSuccess(peers -> assertEquals(30000L, peers..getDefaultPort())));
	}

	private Result<RadixApi> prepareClient(String responseBody) throws Exception {
		@SuppressWarnings("unchecked")
		var response = (HttpResponse<String>) mock(HttpResponse.class);

		when(response.body()).thenReturn(NETWORK_ID, responseBody);
		when(client.<String>send(any(), any())).thenReturn(response);

		return SyncRadixApi.connect(BASE_URL, RadixApi.DEFAULT_PRIMARY_PORT, RadixApi.DEFAULT_SECONDARY_PORT, client);
	}
}
