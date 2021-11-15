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

public class AsyncRadixApiNetworkTest {
	private static final String BASE_URL = "http://localhost/";

	private static final String NETWORK_ID = "{\"result\":{\"networkId\":99},\"id\":\"2\",\"jsonrpc\":\"2.0\"}";
	private static final String DEMAND = "{\"result\":{\"tps\":5},\"id\":\"2\",\"jsonrpc\":\"2.0\"}";
	private static final String THROUGHPUT = "{\"result\":{\"tps\":283},\"id\":\"2\",\"jsonrpc\":\"2.0\"}";
	private static final String DATA = "{\"result\":{\"messages\":{\"inbound\":{\"processed\":399028,"
		+ "\"discarded\":0,\"received\":399028},\"outbound\":{\"processed\":399029,\"aborted\":0,"
		+ "\"pending\":0,\"sent\":399029}},\"networking\":{\"udp\":{\"droppedMessages\":0},"
		+ "\"tcp\":{\"outOpened\":0,\"droppedMessages\":0,\"closed\":0,\"inOpened\":0},"
		+ "\"receivedBytes\":484341853,\"sentBytes\":484444306}},\"id\":\"2\",\"jsonrpc\":\"2.0\"}\n";
	private static final String CONFIGURATION = "{\"result\":{\"discoveryInterval\":30000,\"seedNodes\":["
		+ "\"radix://dn1qfwtmurydewmf64rnrektuh20g8r6svm0cpnpcuuay4ammw2cnumcfppt2p@core1\","
		+ "\"radix://dn1qghsre0ptn9r28d07wzrldc08shs5x7aqhj6lzy2vauyaulppg4qznsumgv@core2\","
		+ "\"radix://dn1qwkdfp8z7rrlv5cf45tc4864n277p9ukjax90ec5cd03zr0uylxtuxr0wk5@core3\","
		+ "\"radix://dn1qwsyxnv7gleusc34ga78kxhx4ewngsk5nvv58s4h22ngu2j8ufruw62f4eq@core4\"],"
		+ "\"nodeAddress\":\"dn1q0llj774w40wafpqg5apgd2jxhfc9aj897zk3gvt9uzh59rq9964v2swj9m\","
		+ "\"peerLivenessCheckInterval\":10000,\"defaultPort\":30000,\"maxInboundChannels\":1024,"
		+ "\"broadcastPort\":30000,\"listenAddress\":\"0.0.0.0\",\"channelBufferSize\":255,"
		+ "\"peerConnectionTimeout\":5000,\"pingTimeout\":5000,\"listenPort\":30000,"
		+ "\"maxOutboundChannels\":1024},\"id\":\"2\",\"jsonrpc\":\"2.0\"}\n";
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
	public static final String ADDRESS_BOOK = "{\"result\":[{\"address\":\"dn1qfwtmurydewmf64rnrektuh20g8r6svm0cpnpcuua"
		+ "y4ammw2cnumcfppt2p\",\"knownAddresses\":[{\"blacklisted\":false,\"latestConnectionStatus\":"
		+ "\"UNKNOWN\",\"uri\":\"radix://dn1qfwtmurydewmf64rnrektuh20g8r6svm0cpnpcuuay4ammw2cnumcfp"
		+ "pt2p@172.18.0.3:30000\"}],\"banned\":false}],\"id\":\"2\",\"jsonrpc\":\"2.0\"}\n";

	private final HttpClient client = mock(HttpClient.class);

	@Test
	public void testNetworkId() throws IOException {
		prepareClient(NETWORK_ID)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.network().id().join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(networkIdDTO -> assertEquals(99, networkIdDTO.getNetworkId())));
	}

	@Test
	public void testDemand() throws IOException {
		prepareClient(DEMAND)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.network().demand().join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(networkStatsDTO -> assertEquals(5L, networkStatsDTO.getTps())));
	}

	@Test
	public void testThroughput() throws IOException {
		prepareClient(THROUGHPUT)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.network().throughput().join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(networkStatsDTO -> assertEquals(283L, networkStatsDTO.getTps())));
	}

	@Test
	public void testData() throws Exception {
		prepareClient(DATA)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.network().data().join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(networkData -> assertEquals(484341853L, networkData.getNetworking().getReceivedBytes())));
	}

	@Test
	public void testConfiguration() throws Exception {
		prepareClient(CONFIGURATION)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.network().configuration().join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(networkConfiguration -> assertEquals(30000L, networkConfiguration.getDefaultPort())));
	}

	@Test
	public void testPeers() throws Exception {
		prepareClient(PEERS)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.network().peers().join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(peers -> assertEquals(4, peers.size())));
	}

	@Test
	public void testAddressBook() throws Exception {
		prepareClient(ADDRESS_BOOK)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.network().addressBook().join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(addressBookEntries -> assertEquals(1, addressBookEntries.size())));
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
