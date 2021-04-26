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

package com.radixdlt.api.system;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.client.ValidatorAddress;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.systeminfo.InMemorySystemInfo;
import com.radixdlt.utils.Bytes;
import org.json.JSONArray;
import org.json.JSONObject;
import com.radixdlt.api.Controller;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import org.radix.universe.system.LocalSystem;

import java.util.stream.Stream;

import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.RestUtils.respond;

public final class SystemController implements Controller {
	private final LocalSystem localSystem;
	private final VerifiedTxnsAndProof genesis;
	private final InMemorySystemInfo inMemorySystemInfo;
	private final AddressBook addressBook;
	private final PeerWithSystem localPeer;

	@Inject
	public SystemController(
		InMemorySystemInfo inMemorySystemInfo,
		LocalSystem localSystem,
		AddressBook addressBook,
		@Genesis VerifiedTxnsAndProof genesis
	) {
		this.inMemorySystemInfo = inMemorySystemInfo;
		this.addressBook = addressBook;
		this.genesis = genesis;
		this.localSystem = localSystem;
		this.localPeer = new PeerWithSystem(this.localSystem);
	}

	@Override
	public void configureRoutes(final RoutingHandler handler) {
		handler.get("/system/info", this::respondWithLocalSystem);
		handler.get("/system/checkpoints", this::respondWithGenesis);
		handler.get("/system/proof", this::respondWithCurrentProof);
		handler.get("/system/epochproof", this::respondWithEpochProof);
		handler.get("/system/peers", this::respondWithLivePeers);
	}

	void respondWithCurrentProof(final HttpServerExchange exchange) {
		var proof = inMemorySystemInfo.getCurrentProof();
		respond(exchange, proof == null ? new JSONObject() : proof.asJSON());
	}

	void respondWithEpochProof(final HttpServerExchange exchange) {
		var proof = inMemorySystemInfo.getEpochProof();
		respond(exchange, proof == null ? new JSONObject() : proof.asJSON());
	}

	@VisibleForTesting
	void respondWithLocalSystem(final HttpServerExchange exchange) {
		var json = DefaultSerialization.getInstance().toJsonObject(localSystem, DsonOutput.Output.API);
		respond(exchange, json);
	}

	@VisibleForTesting
	void respondWithGenesis(final HttpServerExchange exchange) {
		var jsonObject = new JSONObject();
		var txns = new JSONArray();
		genesis.getTxns().forEach(txn -> txns.put(Bytes.toHexString(txn.getPayload())));
		jsonObject.put("txns", txns);
		jsonObject.put("proof", genesis.getProof().asJSON());

		respond(exchange, jsonObject);
	}


	private Stream<PeerWithSystem> selfAndOthers(Stream<PeerWithSystem> others) {
		return Stream.concat(Stream.of(this.localPeer), others).distinct();
	}

	private void respondWithLivePeers(final HttpServerExchange exchange) {
		var peerArray = new JSONArray();
		selfAndOthers(this.addressBook.recentPeers())
			.map(peer -> {
				var json = jsonObject().put("address", ValidatorAddress.of(peer.getSystem().getKey()));
				peer.getSystem().supportedTransports().filter(t -> t.name().equals("TCP")).forEach(t -> {
					String port = t.metadata().get("port");
					String host = t.metadata().get("host");
					json.put("endpoint", host + ":" + port);
				});

				return json;
			})
			.forEach(peerArray::put);

		respond(exchange, peerArray);
	}
}
