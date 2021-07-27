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

package com.radixdlt.api.controller;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.api.service.ForkVoteStatusService;
import static com.radixdlt.api.service.ForkVoteStatusService.ForkVoteStatus.NO_ACTION_NEEDED;
import static com.radixdlt.api.service.ForkVoteStatusService.ForkVoteStatus.VOTE_REQUIRED;

import com.radixdlt.api.service.PeersForksHashesInfoService;
import com.radixdlt.statecomputer.forks.ForksEpochStore;
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
	private final ForksEpochStore forksEpochStore = mock(ForksEpochStore.class);
	private final HealthController controller = new HealthController(
		networkInfoService, forkVoteStatusService, peersForksHashesInfoService, forksEpochStore);

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
		when(forkVoteStatusService.currentFork()).thenReturn(
			new JSONObject().put("name", "fork1"),
			new JSONObject().put("name", "fork2"),
			new JSONObject().put("name", "fork3"),
			new JSONObject().put("name", "fork4")
		);
		when(peersForksHashesInfoService.getUnknownReportedForksHashes()).thenReturn(new JSONObject());
		when(forksEpochStore.getEpochsForkHashes()).thenReturn(ImmutableMap.of());

		controller.handleHealthRequest(exchange);
		verify(sender).send("{\"unknown_reported_forks_hashes\":{},\"fork_vote_status\":\"VOTE_REQUIRED\","
			+ "\"executed_forks\":[],\"network_status\":\"BOOTING\","
			+ "\"current_fork\":{\"name\":\"fork1\"}}");

		controller.handleHealthRequest(exchange);
		verify(sender).send("{\"unknown_reported_forks_hashes\":{},\"fork_vote_status\":\"NO_ACTION_NEEDED\","
			+ "\"executed_forks\":[],\"network_status\":\"SYNCING\","
			+ "\"current_fork\":{\"name\":\"fork2\"}}");

		controller.handleHealthRequest(exchange);
		verify(sender).send("{\"unknown_reported_forks_hashes\":{},\"fork_vote_status\":\"VOTE_REQUIRED\","
			+ "\"executed_forks\":[],\"network_status\":\"UP\","
			+ "\"current_fork\":{\"name\":\"fork3\"}}");

		controller.handleHealthRequest(exchange);
		verify(sender).send("{\"unknown_reported_forks_hashes\":{},\"fork_vote_status\":\"NO_ACTION_NEEDED\","
			+ "\"executed_forks\":[],\"network_status\":\"STALLED\","
			+ "\"current_fork\":{\"name\":\"fork4\"}}");
	}
}
