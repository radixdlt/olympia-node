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

package org.radix.api.jsonrpc.handler;

import org.json.JSONObject;
import org.radix.api.jsonrpc.AtomStatus;
import org.radix.api.jsonrpc.JsonRpcUtil.RpcError;
import org.radix.api.services.AtomsService;

import com.google.inject.Inject;
import com.radixdlt.identifiers.AID;

import static org.radix.api.jsonrpc.JsonRpcUtil.errorResponse;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;
import static org.radix.api.jsonrpc.JsonRpcUtil.response;
import static org.radix.api.jsonrpc.JsonRpcUtil.withParameters;
import static org.radix.api.jsonrpc.JsonRpcUtil.withRequiredParameter;

public class AtomHandler {
	private final AtomsService atomsService;

	@Inject
	public AtomHandler(AtomsService atomsService) {
		this.atomsService = atomsService;
	}

	public JSONObject handleSubmitAtom(JSONObject request) {
		return withParameters(request, jsonAtom ->
			response(request, jsonObject()
				.put("status", AtomStatus.PENDING_CM_VERIFICATION)
				.put("aid", atomsService.submitAtom(jsonAtom))
				.put("timestamp", System.currentTimeMillis())));
	}

	public JSONObject handleGetAtom(JSONObject request) {
		return withRequiredParameter(
			request,
			"aid",
			(params, aid) -> AID.fromString(aid)
				.flatMap(atomsService::getAtomByAtomId)
				.orElseGet(() -> errorResponse(request, RpcError.INVALID_PARAMS, "Atom with AID '" + aid + "' not found"))
		);
	}

}
