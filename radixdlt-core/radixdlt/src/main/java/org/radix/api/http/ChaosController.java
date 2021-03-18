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

package org.radix.api.http;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerUpdate;
import com.radixdlt.chaos.messageflooder.MessageFlooderUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.environment.EventDispatcher;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;

import static org.radix.api.http.RestUtils.withBodyAsyncAndDefaultResponse;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

import static com.radixdlt.crypto.ECPublicKey.fromBytes;
import static com.radixdlt.utils.Base58.fromBase58;

public final class ChaosController implements Controller {
	private final EventDispatcher<MempoolFillerUpdate> mempoolDispatcher;
	private final EventDispatcher<MessageFlooderUpdate> messageDispatcher;

	@Inject
	public ChaosController(
		final EventDispatcher<MempoolFillerUpdate> mempoolDispatcher,
		final EventDispatcher<MessageFlooderUpdate> messageDispatcher
	) {
		this.mempoolDispatcher = mempoolDispatcher;
		this.messageDispatcher = messageDispatcher;
	}

	@Override
	public void configureRoutes(final RoutingHandler handler) {
		handler.put("/api/chaos/message-flooder", this::handleMessageFlood);
		handler.put("/api/chaos/mempool-filler", this::handleMempoolFill);
	}

	@VisibleForTesting
	void handleMessageFlood(HttpServerExchange exchange) {
		withBodyAsyncAndDefaultResponse(exchange, values -> {
			var update = MessageFlooderUpdate.create();

			if (values.getBoolean("enabled")) {
				var data = values.getJSONObject("data");

				if (data.has("nodeKey")) {
					update = update.bftNode(createNodeByKey(data.getString("nodeKey")));
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
		withBodyAsyncAndDefaultResponse(exchange, values -> {
			var fillerUpdate = values.getBoolean("enabled")
							   ? MempoolFillerUpdate.enable(100, true)
							   : MempoolFillerUpdate.disable();
			mempoolDispatcher.dispatch(fillerUpdate);
		});
	}

	private static BFTNode createNodeByKey(final String nodeKeyBase58) throws PublicKeyException {
		return BFTNode.create(fromBytes(fromBase58(nodeKeyBase58)));
	}
}
