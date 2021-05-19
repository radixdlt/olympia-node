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

import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;

import com.radixdlt.api.Controller;
import com.radixdlt.api.construction.TxnParser;
import com.radixdlt.api.qualifier.Construct;
import com.radixdlt.api.server.JsonRpcServer;
import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.ConstraintMachineException;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.constraintmachine.REParsedTxn;
import com.radixdlt.constraintmachine.TxnParseException;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.store.TxnIndex;
import com.radixdlt.utils.Bytes;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONObject;
import com.radixdlt.api.Controller;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;

import static com.radixdlt.api.JsonRpcUtil.jsonArray;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.RestUtils.respond;
import static com.radixdlt.api.RestUtils.withBody;

public final class ConstructionController implements Controller {
	private final TxnParser txnParser;
	private final TxnIndex txnIndex;
	private final EventDispatcher<MempoolAdd> mempoolAddEventDispatcher;
	private final JsonRpcServer jsonRpcServer;

	@Inject
	public ConstructionController(
		TxnIndex txnIndex,
		TxnParser txnParser,
		EventDispatcher<MempoolAdd> mempoolAddEventDispatcher,
		@Construct JsonRpcServer jsonRpcServer
	) {
		this.txnIndex = txnIndex;
		this.txnParser = txnParser;
		this.mempoolAddEventDispatcher = mempoolAddEventDispatcher;
		this.jsonRpcServer = jsonRpcServer;
	}

	@Override
	public void configureRoutes(RoutingHandler handler) {
		handler.post("/construct", jsonRpcServer::handleHttpRequest);
		handler.post("/construct/", jsonRpcServer::handleHttpRequest);
	}
}
