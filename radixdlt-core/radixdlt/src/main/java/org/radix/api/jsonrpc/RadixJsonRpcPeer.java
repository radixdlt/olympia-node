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
import org.radix.api.jsonrpc.JsonRpcUtil.RpcError;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static org.radix.api.jsonrpc.JsonRpcUtil.errorResponse;
import static org.radix.api.jsonrpc.JsonRpcUtil.invalidParamsError;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;
import static org.radix.api.jsonrpc.JsonRpcUtil.notification;
import static org.radix.api.jsonrpc.JsonRpcUtil.parseError;

/**
 * A Stateful JSON RPC 2.0 Server and Client for duplex communication
 */
public class RadixJsonRpcPeer {
	private static final Logger LOGGER = LogManager.getLogger();

	private final BiConsumer<RadixJsonRpcPeer, JSONObject> callback;

	/**
	 * Epic for managing atom subscriptions
	 */
	private final RadixJsonRpcServer server;

	public RadixJsonRpcPeer(
		RadixJsonRpcServer server,
		BiConsumer<RadixJsonRpcPeer, JSONObject> callback
	) {
		this.server = server;
		this.callback = callback;

		callback.accept(
			this,
			notification("Radix.welcome", jsonObject().put("message", "Radix JSON RPC Peer V1.0"))
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
			callback.accept(this, parseError(e.getMessage()));
			return;
		}

		if (!ensureRequestHas(jsonRpcRequest, "id", "method", "params")) {
			return;
		}

		CompletableFuture.supplyAsync(() -> server.handleRpc(message))
			.whenComplete((result, exception) -> {
				if (exception == null) {
					callback.accept(RadixJsonRpcPeer.this, result);
				} else {
					callback.accept(
						RadixJsonRpcPeer.this,
						errorResponse(RpcError.SERVER_ERROR, "Unable to process request: " + message)
					);
				}
			});
	}

	private boolean ensureRequestHas(final JSONObject jsonRpcRequest, final String... names) {
		for (var name : names) {
			if (!jsonRpcRequest.has(name)) {
				callback.accept(this, invalidParamsError( "JSON-RPC: No " + name));
				return false;
			}
		}
		return true;
	}

	// TODO: need to synchronize this with the whole peer
	public void close() {
		LOGGER.info("Closing peer");
	}
}
