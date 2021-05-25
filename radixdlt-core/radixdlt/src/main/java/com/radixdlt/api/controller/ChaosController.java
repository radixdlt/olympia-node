/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.api.controller;

import com.google.common.annotations.VisibleForTesting;
import com.radixdlt.api.Controller;
import com.radixdlt.api.chaos.mempoolfiller.MempoolFillerUpdate;
import com.radixdlt.api.chaos.messageflooder.MessageFlooderUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.serialization.DeserializeException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;

import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.RestUtils.respond;
import static com.radixdlt.api.RestUtils.withBody;
import static com.radixdlt.api.RestUtils.withBodyAsyncAndDefaultResponse;

public final class ChaosController implements Controller {
	private final EventDispatcher<MempoolFillerUpdate> mempoolDispatcher;
	private final EventDispatcher<MessageFlooderUpdate> messageDispatcher;

	public ChaosController(
		EventDispatcher<MempoolFillerUpdate> mempoolDispatcher,
		EventDispatcher<MessageFlooderUpdate> messageDispatcher
	) {
		this.mempoolDispatcher = mempoolDispatcher;
		this.messageDispatcher = messageDispatcher;
	}

	@Override
	public String root() {
		return "/chaos";
	}

	@Override
	public void configureRoutes(final RoutingHandler handler) {
		handler.put("/chaos/message-flooder", this::handleMessageFlood);
		handler.put("/chaos/mempool-filler", this::handleMempoolFill);
	}

	@VisibleForTesting
	void handleMessageFlood(HttpServerExchange exchange) {
		withBody(exchange, values -> {
			var update = MessageFlooderUpdate.create();

			if (values.getBoolean("enabled")) {
				var data = values.getJSONObject("data");

				if (data.has("nodeAddress")) {
					update = update.bftNode(createNodeByKey(data.getString("nodeAddress")));
				}

				if (data.has("messagesPerSec")) {
					update = update.messagesPerSec(data.getInt("messagesPerSec"));
				}

				if (data.has("commandSize")) {
					update = update.commandSize(data.getInt("commandSize"));
				}
			}

			this.messageDispatcher.dispatch(update);
		});
	}

	@VisibleForTesting
	void handleMempoolFill(HttpServerExchange exchange) {
		withBody(exchange, values -> {
			MempoolFillerUpdate update;
			var completableFuture = new CompletableFuture<Void>();
			var enable = values.getBoolean("enabled");
			if (enable) {
				update = MempoolFillerUpdate.enable(100, true, completableFuture);
			} else {
				update = MempoolFillerUpdate.disable(completableFuture);
			}
			mempoolDispatcher.dispatch(update);

			try {
				completableFuture.get();
				respond(exchange, jsonObject().put("result", enable ? "enabled" : "disabled"));
			} catch (ExecutionException e) {
				respond(exchange, jsonObject().put("error", jsonObject().put("message", e.getCause().getMessage())));
			}
		});
	}

	private static BFTNode createNodeByKey(final String nodeAddress) {
		try {
			return BFTNode.create(ValidatorAddress.parse(nodeAddress));
		} catch (DeserializeException e) {
			throw new IllegalArgumentException();
		}
	}
}
