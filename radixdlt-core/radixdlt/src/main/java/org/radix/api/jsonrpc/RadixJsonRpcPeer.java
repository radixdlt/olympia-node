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

import java.util.function.BiConsumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.radix.api.AtomQuery;
import org.radix.api.services.AtomsService;

import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.Serialization;

import io.undertow.websockets.core.BufferedTextMessage;

/**
 * A Stateful JSON RPC 2.0 Server and Client for duplex communication
 */
public class RadixJsonRpcPeer {
	private static final Logger LOGGER = LogManager.getLogger();

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
		Serialization serialization,
		BiConsumer<RadixJsonRpcPeer, String> callback
	) {
		this.server = server;
		this.callback = callback;

		this.atomStatusEpic = new AtomStatusEpic(atomsService, json -> callback.accept(this, json.toString()));
		this.atomsSubscribeEpic = new AtomsSubscribeEpic(atomsService, serialization,
			queryJson -> new AtomQuery(RadixAddress.from(queryJson.getString("address")).euid()), atomJson -> callback.accept(this, atomJson.toString()));

		callback.accept(this, JsonRpcUtil.notification("Radix.welcome", new JSONObject().put("message", "Hello!")).toString());
	}

	/**
	 * Handle the text message and send a corresponding response
	 *
	 * @param message The message
	 */
	// TODO: multithreading issues - should get resolved once we use a better async framework
	public void onMessage(BufferedTextMessage message) {

		final String msg = message.getData();

		final JSONObject jsonRpcRequest;
		try {
			jsonRpcRequest = new JSONObject(msg);
		} catch (JSONException e) {
			callback.accept(this, JsonRpcUtil.errorResponse(null, -32000, e.getMessage()).toString());
			return;
		}

		if (!jsonRpcRequest.has("id")) {
			callback.accept(this, JsonRpcUtil.errorResponse(null, -32000, "JSON-RPC: No id").toString());
			return;
		}

		if (!jsonRpcRequest.has("method")) {
			callback.accept(this, JsonRpcUtil.errorResponse(null, -32000, "JSON-RPC: No method").toString());
			return;
		}

		if (!jsonRpcRequest.has("params")) {
			callback.accept(this, JsonRpcUtil.errorResponse(null, -32000, "JSON-RPC: No params").toString());
			return;
		}

		final String jsonRpcMethod = jsonRpcRequest.getString("method");

		switch (jsonRpcMethod) {
			case "Atoms.subscribe":
			case "Atoms.cancel":
				if (!jsonRpcRequest.getJSONObject("params").has("subscriberId")) {
					callback.accept(this, JsonRpcUtil.errorResponse(null, -32000, "JSON-RPC: No subscriberId").toString());
					return;
				}

				atomsSubscribeEpic.action(jsonRpcRequest);
				break;
			case "Atoms.getAtomStatusNotifications":
			case "Atoms.closeAtomStatusNotifications":
				if (!jsonRpcRequest.getJSONObject("params").has("subscriberId")) {
					callback.accept(this, JsonRpcUtil.errorResponse(null, -32000, "JSON-RPC: No subscriberId").toString());
					break;
				}
				atomStatusEpic.action(jsonRpcRequest);
				break;
			default:
				callback.accept(this, server.handleChecked(msg));
				break;
		}
	}

	// TODO: need to synchronize this with the whole peer
	public void close() {
		LOGGER.info("Closing peer");

		atomStatusEpic.dispose();
		atomsSubscribeEpic.dispose();
	}
}
