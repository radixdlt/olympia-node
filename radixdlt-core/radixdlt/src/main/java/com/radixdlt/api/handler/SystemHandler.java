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
import com.radixdlt.api.service.SystemConfigService;

import static com.radixdlt.api.JsonRpcUtil.ARRAY;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;

@Singleton
public class SystemHandler {
	private final SystemConfigService systemConfigService;

	@Inject
	public SystemHandler(SystemConfigService systemConfigService) {
		this.systemConfigService = systemConfigService;
	}

	public JSONObject apiGetConfiguration(JSONObject request) {
		return systemConfigService.getApiConfiguration();
	}

	public JSONObject apiGetData(JSONObject request) {
		//TODO: implement it
		throw new UnsupportedOperationException("Not implemented");
	}

	public JSONObject bftGetConfiguration(JSONObject request) {
		//TODO: implement it
		throw new UnsupportedOperationException("Not implemented");
	}

	public JSONObject bftGetData(JSONObject request) {
		//TODO: implement it
		throw new UnsupportedOperationException("Not implemented");
	}

	public JSONObject mempoolGetConfiguration(JSONObject request) {
		return systemConfigService.getMempoolConfiguration();
	}

	public JSONObject mempoolGetData(JSONObject request) {
		//TODO: implement it
		throw new UnsupportedOperationException("Not implemented");
	}

	public JSONObject ledgerGetLatestProof(JSONObject request) {
		//TODO: implement it
		throw new UnsupportedOperationException("Not implemented");
	}

	public JSONObject ledgerGetLatestEpochProof(JSONObject request) {
		//TODO: implement it
		throw new UnsupportedOperationException("Not implemented");
	}

	public JSONObject radixEngineGetConfiguration(JSONObject request) {
		return systemConfigService.getRadixEngineConfiguration();
	}

	public JSONObject radixEngineGetData(JSONObject request) {
		//TODO: implement it
		throw new UnsupportedOperationException("Not implemented");
	}

	public JSONObject syncGetConfiguration(JSONObject request) {
		//TODO: implement it
		throw new UnsupportedOperationException("Not implemented");
	}

	public JSONObject syncGetData(JSONObject request) {
		//TODO: implement it
		throw new UnsupportedOperationException("Not implemented");
	}

	public JSONObject networkingGetConfiguration(JSONObject request) {
		//TODO: implement it
		throw new UnsupportedOperationException("Not implemented");
	}

	public JSONObject networkingGetPeers(JSONObject request) {
		return jsonObject().put(ARRAY, systemConfigService.getLivePeers());
	}

	public JSONObject networkingGetData(JSONObject request) {
		//TODO: implement it
		throw new UnsupportedOperationException("Not implemented");
	}

	public JSONObject checkpointsGetCheckpoints(JSONObject request) {
		//TODO: implement it
		throw new UnsupportedOperationException("Not implemented");
	}
}
