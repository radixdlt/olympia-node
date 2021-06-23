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

package com.radixdlt.api.controller;

import com.radixdlt.api.service.ForkVoteStatusService;
import static com.radixdlt.api.service.ForkVoteStatusService.ForkVoteStatus.NO_ACTION_NEEDED;
import static com.radixdlt.api.service.ForkVoteStatusService.ForkVoteStatus.VOTE_REQUIRED;

import com.radixdlt.api.service.PeersForksHashesInfoService;
import org.json.JSONObject;
import org.junit.Test;

import com.radixdlt.api.service.NetworkInfoService;

import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.HeaderMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.radixdlt.api.data.NodeStatus.BOOTING;
import static com.radixdlt.api.data.NodeStatus.STALLED;
import static com.radixdlt.api.data.NodeStatus.SYNCING;
import static com.radixdlt.api.data.NodeStatus.UP;

public class HealthControllerTest {
	private final NetworkInfoService networkInfoService = mock(NetworkInfoService.class);
	private final ForkVoteStatusService forkVoteStatusService = mock(ForkVoteStatusService.class);
	private final PeersForksHashesInfoService peersForksHashesInfoService = mock(PeersForksHashesInfoService.class);
	private final HealthController controller = new HealthController(networkInfoService, forkVoteStatusService, peersForksHashesInfoService);

	@Test
	public void routesAreConfigured() {
		var handler = mock(RoutingHandler.class);

		controller.configureRoutes("/health", handler);

		verify(handler).get(eq("/health"), any());
	}

	@Test
	public void healthStatusIsReturned() {
		var exchange = mock(HttpServerExchange.class);
		var sender = mock(Sender.class);

		when(exchange.getResponseHeaders()).thenReturn(new HeaderMap());
		when(exchange.getResponseSender()).thenReturn(sender);
		when(networkInfoService.nodeStatus()).thenReturn(BOOTING, SYNCING, UP, STALLED);
		when(forkVoteStatusService.forkVoteStatus()).thenReturn(VOTE_REQUIRED, NO_ACTION_NEEDED, VOTE_REQUIRED, NO_ACTION_NEEDED);
		when(peersForksHashesInfoService.getUnknownReportedForksHashes()).thenReturn(new JSONObject());

		controller.handleHealthRequest(exchange);
		verify(sender).send("{\"unknown_reported_forks_hashes\":{},\"fork_vote_status\":\"VOTE_REQUIRED\",\"network_status\":\"BOOTING\"}");

		controller.handleHealthRequest(exchange);
		verify(sender).send("{\"unknown_reported_forks_hashes\":{},\"fork_vote_status\":\"NO_ACTION_NEEDED\",\"network_status\":\"SYNCING\"}");

		controller.handleHealthRequest(exchange);
		verify(sender).send("{\"unknown_reported_forks_hashes\":{},\"fork_vote_status\":\"VOTE_REQUIRED\",\"network_status\":\"UP\"}");

		controller.handleHealthRequest(exchange);
		verify(sender).send("{\"unknown_reported_forks_hashes\":{},\"fork_vote_status\":\"NO_ACTION_NEEDED\",\"network_status\":\"STALLED\"}");
	}
}
