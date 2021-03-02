/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package org.radix.api.jsonrpc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.radix.api.AtomQuery;
import org.radix.api.services.AtomsService;

import com.radixdlt.identifiers.RadixAddress;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static org.radix.api.jsonrpc.JsonRpcUtil.INVALID_PARAMS;
import static org.radix.api.jsonrpc.JsonRpcUtil.PARSE_ERROR;
import static org.radix.api.jsonrpc.JsonRpcUtil.SERVER_ERROR;
import static org.radix.api.jsonrpc.JsonRpcUtil.errorResponse;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;
import static org.radix.api.jsonrpc.JsonRpcUtil.notification;

/**
 * A Stateful JSON RPC 2.0 Server and Client for duplex communication
 */
public class RadixJsonRpcPeer {
	private static final Logger LOGGER = LogManager.getLogger();

	private static final Set<String> SUBSCRIPTION_METHODS = Set.of("Atoms.subscribe", "Atoms.cancel");
	private static final Set<String> STATUS_METHODS =
		Set.of("Atoms.getAtomStatusNotifications", "Atoms.closeAtomStatusNotifications");

	private final BiConsumer<RadixJsonRpcPeer, String> callback;

	/**
	 * Epic for managing atom subscriptions
	 */
	private final AtomsSubscribeEpic atomsSubscribeEpic;
	private final AtomStatusEpic atomStatusEpic;
	private final RadixJsonRpcServer server;

	public RadixJsonRpcPeer(
		RadixJsonRpcServer server,
		AtomsService atomsService,
		BiConsumer<RadixJsonRpcPeer, String> callback
	) {
		this.server = server;
		this.callback = callback;

		this.atomStatusEpic = new AtomStatusEpic(atomsService, json -> callback.accept(this, json.toString()));
		this.atomsSubscribeEpic = new AtomsSubscribeEpic(
			atomsService,
			queryJson -> new AtomQuery(RadixAddress.from(queryJson.getString("address")).euid()),
			atomJson -> callback.accept(this, atomJson.toString())
		);

		callback.accept(
			this,
			notification("Radix.welcome", jsonObject().put("message", "Radix JSON RPC Peer V1.0")).toString()
		);
	}

	/**
	 * Handle the text message and send a corresponding response
	 *
	 * @param message The message
	 */
	// TODO: multithreading issues ???
	public void onMessage(String message) {

		final JSONObject jsonRpcRequest;
		try {
			jsonRpcRequest = new JSONObject(message);
		} catch (JSONException e) {
			callback.accept(this, errorResponse(PARSE_ERROR, e.getMessage()).toString());
			return;
		}

		if (!ensureRequestHas(jsonRpcRequest, "id", "method", "params")) {
			return;
		}

		final var jsonRpcMethod = jsonRpcRequest.getString("method");

		if (STATUS_METHODS.contains(jsonRpcMethod) || SUBSCRIPTION_METHODS.contains(jsonRpcMethod)) {
			if (!jsonRpcRequest.getJSONObject("params").has("subscriberId")) {
				callback.accept(this, errorResponse(INVALID_PARAMS, "JSON-RPC: No subscriberId").toString());
				return;
			}

			if (SUBSCRIPTION_METHODS.contains(jsonRpcMethod)) {
				atomsSubscribeEpic.action(jsonRpcRequest);
			} else {
				atomStatusEpic.action(jsonRpcRequest);
			}
		} else {
			CompletableFuture.supplyAsync(() -> server.handleRpc(message))
				.whenComplete((result, exception) -> {
					if (exception == null) {
						callback.accept(RadixJsonRpcPeer.this, result);
					} else {
						callback.accept(
							RadixJsonRpcPeer.this,
							errorResponse(SERVER_ERROR, "unable to process request: " + message).toString()
						);
					}
				});

		}
	}

	private boolean ensureRequestHas(final JSONObject jsonRpcRequest, final String... names) {
		for (var name : names) {
			if (!jsonRpcRequest.has(name)) {
				callback.accept(this, errorResponse(INVALID_PARAMS, "JSON-RPC: No " + name).toString());
				return false;
			}
		}
		return true;
	}

	// TODO: need to synchronize this with the whole peer
	public void close() {
		LOGGER.info("Closing peer");

		atomStatusEpic.dispose();
		atomsSubscribeEpic.dispose();
	}
}
