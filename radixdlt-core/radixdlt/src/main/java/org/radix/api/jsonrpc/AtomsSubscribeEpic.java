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

import org.json.JSONArray;
import org.json.JSONObject;
import org.radix.api.AtomQuery;
import org.radix.api.observable.Disposable;
import org.radix.api.observable.ObservedAtomEvents;
import org.radix.api.services.AtomsService;

import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.radix.api.jsonrpc.JsonRpcUtil.SERVER_ERROR;
import static org.radix.api.jsonrpc.JsonRpcUtil.errorResponse;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonArray;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;
import static org.radix.api.jsonrpc.JsonRpcUtil.notification;
import static org.radix.api.jsonrpc.JsonRpcUtil.successResponse;

/**
 * Epic responsible for converting JSON RPC atom subscribe request to JSON RPC notifications
 */
public class AtomsSubscribeEpic {
	/**
	 * Observers for this epic channel
	 * TODO: should get rid of this at some point so that the epic is stateless
	 */
	private final ConcurrentHashMap<String, Disposable> observers = new ConcurrentHashMap<>();
	private final AtomsService atomsService;
	private final Consumer<JSONObject> callback;
	private final Serialization serialization;
	private final Function<JSONObject, AtomQuery> queryMapper;

	public AtomsSubscribeEpic(
		AtomsService atomsService,
		Serialization serialization,
		Function<JSONObject, AtomQuery> queryMapper,
		Consumer<JSONObject> callback
	) {
		this.atomsService = atomsService;
		this.serialization = serialization;
		this.queryMapper = queryMapper;
		this.callback = callback;
	}

	public int numObservers() {
		return observers.size();
	}

	public void dispose() {
		observers.forEachKey(100, subscriberId -> observers.remove(subscriberId).dispose());
	}

	private void onAtomUpdate(String subscriberId, JSONArray atoms, boolean isHead) {
		callback.accept(notification("Atoms.subscribeUpdate", jsonObject()
			.put("atomEvents", atoms).put("subscriberId", subscriberId).put("isHead", isHead)));
	}

	public synchronized void action(JSONObject jsonRequest) {
		var params = jsonRequest.getJSONObject("params");
		var subscriberId = params.getString("subscriberId");
		var id = jsonRequest.get("id");
		var method = jsonRequest.getString("method");

		if (method.equals("Atoms.subscribe")) {
			subscribe(params, subscriberId, id);
		} else if (method.equals("Atoms.cancel")) {
			cancelSubscription(subscriberId, id);
		}
	}

	private void subscribe(final JSONObject params, final String subscriberId, final Object id) {
		if (observers.containsKey(subscriberId)) {
			callback.accept(errorResponse(id, SERVER_ERROR, "Subscriber + " + subscriberId + " already exists."));
			return;
		}

		var query = params.getJSONObject("query");

		if (!query.has("address")) {
			callback.accept(errorResponse(id, SERVER_ERROR, "Invalid query."));
			return;
		}

		callback.accept(successResponse(id));
		observers.computeIfAbsent(subscriberId, __ -> initSubscription(subscriberId, queryMapper.apply(query)));
	}

	private Disposable initSubscription(final String subscriberId, final AtomQuery atomQuery) {
		return atomsService.getAtomEvents(atomQuery)
			.subscribe(observedAtoms -> subscriber(subscriberId, observedAtoms));
	}

	private void subscriber(final String subscriberId, final ObservedAtomEvents observedAtoms) {
		final var atomEventsJson = jsonArray();

		observedAtoms.atomEvents()
			.map(event -> serialization.toJsonObject(event, Output.WIRE))
			.forEach(atomEventsJson::put);

		onAtomUpdate(subscriberId, atomEventsJson, observedAtoms.isHead());
	}

	private void cancelSubscription(final String subscriberId, final Object id) {
		Optional.ofNullable(observers.remove(subscriberId)).ifPresent(Disposable::dispose);
		callback.accept(successResponse(id));
	}
}
