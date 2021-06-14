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
import com.radixdlt.api.data.ValidatorInfoDetails;
import com.radixdlt.api.service.AccountInfoService;
import com.radixdlt.api.service.ValidatorInfoService;

import static com.radixdlt.api.JsonRpcUtil.fromList;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.JsonRpcUtil.response;

@Singleton
public class ValidationHandler {
	private final AccountInfoService accountService;
	private final ValidatorInfoService validatorInfoService;

	@Inject
	public ValidationHandler(AccountInfoService accountService, ValidatorInfoService validatorInfoService) {
		this.accountService = accountService;
		this.validatorInfoService = validatorInfoService;
	}

	public JSONObject handleGetNodeInfo(JSONObject request) {
		return response(request, accountService.getValidatorInfo());
	}

	public JSONObject handleGetNextEpochData(JSONObject request) {
		return response(request, jsonObject().put(
			"validators",
			fromList(validatorInfoService.getAllValidators(), ValidatorInfoDetails::asJson)
		));
	}

	public JSONObject handleGetCurrentEpochData(JSONObject request) {
		//TODO: implement it
		return jsonObject();
	}
}
