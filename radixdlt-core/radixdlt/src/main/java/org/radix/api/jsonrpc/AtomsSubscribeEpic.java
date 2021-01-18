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

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import org.json.JSONArray;
import org.json.JSONObject;
import org.radix.api.AtomQuery;
import org.radix.api.observable.Disposable;
import org.radix.api.services.AtomsService;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;

/**
 * Epic responsible for converting JSON RPC atom subscribe request to JSON RPC notifications
 *
 * TODO: replace with a framework like akka
 */
public class AtomsSubscribeEpic {

	/**
	 * Observers for this epic channel
	 * TODO: should get rid of this at some point so that the epic is stateless
	 */
	private final ConcurrentHashMap<String, Disposable> observers = new ConcurrentHashMap<>();

	/**
	 * Interface for atom submission and return of results
	 */
	private final AtomsService atomsService;

	/**
	 * Stream of JSON RPC objects to be sent back in the same channel
	 */
	private final Consumer<JSONObject> callback;

	/**
	 * DSON serializer/deserializer
	 */
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
		JSONObject notification = new JSONObject();
		notification.put("jsonrpc", "2.0");
		notification.put("method", "Atoms.subscribeUpdate");
		JSONObject params = new JSONObject();
		params.put("atomEvents", atoms);
		params.put("subscriberId", subscriberId);
		params.put("isHead", isHead);
		notification.put("params", params);
		callback.accept(notification);
	}

	public synchronized void action(JSONObject jsonRequest) {
		JSONObject params = jsonRequest.getJSONObject("params");
		String subscriberId = jsonRequest.getJSONObject("params").getString("subscriberId");
		Object id = jsonRequest.get("id");

		if (jsonRequest.getString("method").equals("Atoms.subscribe")) {
			JSONObject query = params.getJSONObject("query");

			final AtomQuery atomQuery;
			if (query.has("address")) {
				atomQuery = queryMapper.apply(query);
			} else {
				callback.accept(JsonRpcUtil.errorResponse(id, -32000, "Invalid query.", new JSONObject()));
				return;
			}

			if (observers.containsKey(subscriberId)) {
				callback.accept(JsonRpcUtil.errorResponse(
					id, -32000, "Subscriber + " + subscriberId + " already exists.", new JSONObject()
				));
				return;
			} else {
				callback.accept(JsonRpcUtil.simpleResponse(id, "success", true));
			}

			observers.computeIfAbsent(subscriberId, (i) -> atomsService.getAtomEvents(atomQuery)
				.subscribe(observedAtoms -> {
					final JSONArray atomEventsJson = new JSONArray();
					observedAtoms.atomEvents()
						.map(event -> serialization.toJsonObject(event, Output.WIRE))
						.forEach(atomEventsJson::put);

					onAtomUpdate(subscriberId, atomEventsJson, observedAtoms.isHead());
				}));
		} else if (jsonRequest.getString("method").equals("Atoms.cancel")) {
			Disposable disposable = observers.remove(subscriberId);
			if (disposable != null) {
				disposable.dispose();
			}
			callback.accept(JsonRpcUtil.simpleResponse(id, "success", true));
			return;
		}
	}

}
