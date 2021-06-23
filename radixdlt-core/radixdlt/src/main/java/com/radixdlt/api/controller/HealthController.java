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

import com.google.common.annotations.VisibleForTesting;
import com.radixdlt.api.Controller;
import com.radixdlt.api.service.ForkVoteStatusService;
import com.radixdlt.api.service.NetworkInfoService;

import com.radixdlt.api.service.PeersForksHashesInfoService;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;

import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.RestUtils.respond;
import static com.radixdlt.api.RestUtils.sanitizeBaseUrl;

public class HealthController implements Controller {
	private final NetworkInfoService networkInfoService;
	private final ForkVoteStatusService forkVoteStatusService;
	private final PeersForksHashesInfoService peersForksHashesInfoService;

	public HealthController(
		NetworkInfoService networkInfoService,
		ForkVoteStatusService forkVoteStatusService,
		PeersForksHashesInfoService peersForksHashesInfoService
	) {
		this.networkInfoService = networkInfoService;
		this.forkVoteStatusService = forkVoteStatusService;
		this.peersForksHashesInfoService = peersForksHashesInfoService;
	}

	@Override
	public void configureRoutes(String root, RoutingHandler handler) {
		handler.get(sanitizeBaseUrl(root), this::handleHealthRequest);
	}

	@VisibleForTesting
	void handleHealthRequest(HttpServerExchange exchange) {
		respond(exchange, jsonObject()
			.put("network_status", networkInfoService.nodeStatus())
			.put("fork_vote_status", forkVoteStatusService.forkVoteStatus())
			.put("unknown_reported_forks_hashes", peersForksHashesInfoService.getUnknownReportedForksHashes())
		);
	}
}
