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

package com.radixdlt.api.handler;

import org.json.JSONObject;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.api.service.NetworkInfoService;
import com.radixdlt.qualifier.NetworkId;

import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.JsonRpcUtil.response;

@Singleton
public class ArchiveNetworkHandler {
	private final NetworkInfoService networkInfoService;

	private final int magic;

	@Inject
	public ArchiveNetworkHandler(
		NetworkInfoService networkInfoService,
		@NetworkId int networkId
	) {
		this.networkInfoService = networkInfoService;

		this.magic = networkId;
	}

	public JSONObject handleNetworkGetId(JSONObject request) {
		return response(request, jsonObject().put("networkId", magic));
	}

	public JSONObject handleNetworkGetThroughput(JSONObject request) {
		return response(request, jsonObject().put("tps", networkInfoService.throughput()));
	}

	public JSONObject handleNetworkGetDemand(JSONObject request) {
		return response(request, jsonObject().put("tps", networkInfoService.demand()));
	}
}
