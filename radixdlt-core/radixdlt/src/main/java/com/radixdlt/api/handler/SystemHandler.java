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
import com.radixdlt.api.service.SystemConfigService;

import static com.radixdlt.api.JsonRpcUtil.response;

public class SystemHandler {
	private final SystemConfigService systemConfigService;

	@Inject
	public SystemHandler(SystemConfigService systemConfigService) {
		this.systemConfigService = systemConfigService;
	}

	public JSONObject apiGetConfiguration(JSONObject request) {
		return response(request, systemConfigService.getApiConfiguration());
	}

	public JSONObject apiGetData(JSONObject request) {
		return response(request, systemConfigService.getApiData());
	}

	public JSONObject bftGetConfiguration(JSONObject request) {
		return response(request, systemConfigService.getBftConfiguration());
	}

	public JSONObject bftGetData(JSONObject request) {
		return response(request, systemConfigService.getBftData());
	}

	public JSONObject mempoolGetConfiguration(JSONObject request) {
		return response(request, systemConfigService.getMempoolConfiguration());
	}

	public JSONObject mempoolGetData(JSONObject request) {
		return response(request, systemConfigService.getMempoolData());
	}

	public JSONObject ledgerGetLatestProof(JSONObject request) {
		return response(request, systemConfigService.getLatestProof());
	}

	public JSONObject ledgerGetLatestEpochProof(JSONObject request) {
		return response(request, systemConfigService.getLatestEpochProof());
	}

	public JSONObject radixEngineGetConfiguration(JSONObject request) {
		return response(request, systemConfigService.getRadixEngineConfiguration());
	}

	public JSONObject radixEngineGetData(JSONObject request) {
		return response(request, systemConfigService.getRadixEngineData());
	}

	public JSONObject syncGetConfiguration(JSONObject request) {
		return response(request, systemConfigService.getSyncConfig());
	}

	public JSONObject syncGetData(JSONObject request) {
		return response(request, systemConfigService.getSyncData());
	}

	public JSONObject networkingGetConfiguration(JSONObject request) {
		return response(request, systemConfigService.getNetworkingConfiguration());
	}

	public JSONObject networkingGetPeers(JSONObject request) {
		return response(request, systemConfigService.getNetworkingPeers());
	}

	public JSONObject networkingGetAddressBook(JSONObject request) {
		return response(request, systemConfigService.getNetworkingAddressBook());
	}

	public JSONObject networkingGetData(JSONObject request) {
		return response(request, systemConfigService.getNetworkingData());
	}

	public JSONObject checkpointsGetCheckpoints(JSONObject request) {
		return response(request, systemConfigService.getCheckpoints());
	}
}
