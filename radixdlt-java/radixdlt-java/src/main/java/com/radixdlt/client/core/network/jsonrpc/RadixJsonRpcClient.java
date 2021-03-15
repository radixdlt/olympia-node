/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core.network.jsonrpc;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.radixdlt.atom.ClientAtom;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.AtomStatusEvent;
import com.radixdlt.client.core.ledger.AtomEvent;
import com.radixdlt.client.serialization.GsonJson;
import com.radixdlt.client.serialization.Serialize;
import com.radixdlt.serialization.DeserializeException;
import io.reactivex.functions.Cancellable;
import java.util.List;
import java.util.UUID;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.JsonJavaType;
import com.radixdlt.serialization.Serialization;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.atom.AtomBuilder;
import com.radixdlt.client.core.ledger.AtomObservation;

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;

/**
 * Responsible for managing the state across one web socket connection to a Radix Node.
 * This consists of mainly keeping track of JSON-RPC method calls and JSON-RPC subscription
 * calls.
 */
public class RadixJsonRpcClient {
	public enum NotificationType {
		START, EVENT
	}

	public static final class Notification<T> {
		private final T event;
		private final NotificationType type;

		private Notification(NotificationType type, T event) {
			this.type = type;
			this.event = event;
		}

		static <T> Notification<T> ofEvent(T event) {
			return new Notification<>(NotificationType.EVENT, event);
		}

		public NotificationType getType() {
			return type;
		}

		public T getEvent() {
			return event;
		}
	}

	public static class JsonRpcResponse {
		private final boolean isSuccess;
		private final JsonElement jsonResponse;

		public JsonRpcResponse(boolean isSuccess, JsonElement jsonResponse) {
			this.isSuccess = isSuccess;
			this.jsonResponse = jsonResponse;
		}

		public boolean isSuccess() {
			return isSuccess;
		}

		public JsonElement getJsonResponse() {
			return jsonResponse;
		}

		public JsonElement getResult() {
			return jsonResponse.getAsJsonObject().get("result");
		}

		public JsonElement getError() {
			return jsonResponse.getAsJsonObject().get("error");
		}
	}

	private static final Logger LOGGER = LogManager.getLogger(RadixJsonRpcClient.class);

	/**
	 * API version of Client, must match with Server
	 */
	public static final Integer API_VERSION = 1;

	/**
	 * The channel this JSON RPC client utilizes for messaging
	 */
	private final PersistentChannel channel;

	/**
	 * Cached API version of Node
	 */
	private final Single<Integer> serverApiVersion;

	/**
	 * Cached Universe of Node
	 */
	private final Single<RadixUniverseConfig> universeConfig;

	private final int defaultTimeoutSecs = 30;

	private final JsonParser parser = new JsonParser();

	public RadixJsonRpcClient(PersistentChannel channel) {
		this.channel = channel;

		Serialization serialization = Serialize.getInstance();
		this.serverApiVersion = jsonRpcCall("Api.getVersion")
			.map(JsonRpcResponse::getResult)
			.map(JsonElement::getAsJsonObject)
			.map(result -> result.get("version").getAsInt())
			.onErrorReturn(e -> {
				LOGGER.error(String.format("Error while requesting Api.getVersion: %s", e));
				return API_VERSION; // TODO assume api version matches for now until fixed in core
			})
			.cache();
		this.universeConfig = jsonRpcCall("Universe.getUniverse")
			.map(JsonRpcResponse::getResult)
			.map(element -> GsonJson.getInstance().stringFromGson(element))
			.map(result -> serialization.fromJson(result, RadixUniverseConfig.class))
			.onErrorReturn(e -> {
				LOGGER.error(String.format("Error while requesting Universe.getUniverse: %s", e));
				return null; // TODO until we have a better option
			})
			.cache();
	}

	/**
	 * Generic helper method for calling a JSON-RPC method. Deserializes the received json.
	 *
	 * @param method name of JSON-RPC method
	 * @param params request parameters
	 * @return response from rpc method
	 */
	public Single<JsonRpcResponse> jsonRpcCall(String method, JsonElement params) {
		return Single.<JsonRpcResponse>create(emitter -> {
			final String uuid = UUID.randomUUID().toString();

			final JsonObject requestObject = new JsonObject();
			requestObject.addProperty("id", uuid);
			requestObject.addProperty("method", method);
			requestObject.add("params", params);

			Cancellable c = channel.addListener(msg -> {
				JsonObject json = parser.parse(msg).getAsJsonObject();
				if (!json.has("id")) {
					return;
				}

				if (!json.get("id").isJsonNull() && !json.get("id").getAsString().equals(uuid)) {
					return;
				}

				final JsonRpcResponse response = new JsonRpcResponse(!json.has("error"), json);
				emitter.onSuccess(response);
			});

			emitter.setCancellable(c);

			boolean sendSuccess = channel.sendMessage(GsonJson.getInstance().stringFromGson(requestObject));
			if (!sendSuccess) {
				emitter.onError(new RuntimeException("Could not send message: " + method + " " + params));
			}
		}).timeout(defaultTimeoutSecs, TimeUnit.SECONDS);
	}

	/**
	 * Generic helper method for calling a JSON-RPC method with no parameters. Deserializes the received json.
	 *
	 * @param method name of JSON-RPC method
	 * @return response from rpc method
	 */
	public Single<JsonRpcResponse> jsonRpcCall(String method) {
		return this.jsonRpcCall(method, new JsonObject());
	}

	public Single<Integer> apiVersion() {
		return this.serverApiVersion;
	}

	/**
	 * Retrieve the universe the node is supporting. The result is cached for future calls.
	 *
	 * @return universe config which the node is supporting
	 */
	public Single<RadixUniverseConfig> universe() {
		return this.universeConfig;
	}

	/**
	 * Retrieve the node data for node we are connected to
	 *
	 * @return node data for node we are connected to
	 */
	public Single<NodeRunnerData> getInfo() {
		return this.jsonRpcCall("Network.getInfo")
			.map(JsonRpcResponse::getResult)
			.map(result -> Serialize.getInstance().fromJson(result.toString(), RadixSystem.class))
			.map(NodeRunnerData::new);
	}

	/**
	 * Retrieve list of nodes this node knows about
	 *
	 * @return list of nodes this node knows about
	 */
	public Single<List<NodeRunnerData>> getLivePeers() {
		JsonJavaType listOfNodeRunnerData = Serialize.getInstance().jsonCollectionType(List.class, NodeRunnerData.class);
		return this.jsonRpcCall("Network.getLivePeers")
				.map(JsonRpcResponse::getResult)
				.map(result -> Serialize.getInstance().fromJson(result.toString(), listOfNodeRunnerData));
	}

	/**
	 * Submits an atom to the node.
	 * @param atom the atom to submit
	 * @return a completable which completes when the atom is queued
	 */
	public Completable pushAtom(ClientAtom atom) {
		JSONObject jsonAtomTemp = Serialize.getInstance().toJsonObject(atom, Output.API);
		JsonElement jsonAtom = GsonJson.getInstance().toGson(jsonAtomTemp);

		return this.jsonRpcCall("Atoms.submitAtom", jsonAtom).map(r -> {
			if (!r.isSuccess || r.getError() != null) {
				throw new SubmitAtomException(r.getError().getAsJsonObject());
			} else {
				return r;
			}
		}).ignoreElement();
	}

	/**
	 * Sends a request to receive streaming updates on an atom's status.
	 * @param subscriberId the subscriberId for the streaming updates
	 * @param aid the AID of the atom
	 * @return a completable which completes when subscription is registered
	 */
	public Completable sendGetAtomStatusNotifications(String subscriberId, AID aid) {
		final JsonObject params = new JsonObject();
		params.addProperty("aid", aid.toString());
		params.addProperty("subscriberId", subscriberId);

		return this.jsonRpcCall("Atoms.getAtomStatusNotifications", params).map(r -> {
			if (!r.isSuccess) {
				throw new RuntimeException();
			} else {
				return r;
			}
		}).ignoreElement();
	}

	/**
	 * Closes a streaming status subscription
	 * @param subscriberId the subscriberId for the streaming updates
	 * @return a completable which completes when subscription is closed
	 */
	public Completable closeAtomStatusNotifications(String subscriberId) {
		final JsonObject cancelParams = new JsonObject();
		cancelParams.addProperty("subscriberId", subscriberId);

		return this.jsonRpcCall("Atoms.closeAtomStatusNotifications", cancelParams).map(r -> {
			if (!r.isSuccess) {
				throw new RuntimeException();
			} else {
				return r;
			}
		}).ignoreElement();
	}

	/**
	 * Listens to atom status notifications
	 * @param subscriberId the subscription to listen for
	 * @return observable of status notifications
	 */
	public Observable<Notification<AtomStatusEvent>> observeAtomStatusNotifications(String subscriberId) {
		return this.observeNotifications(
			"Atoms.nextStatusEvent",
			subscriberId,
			json -> {
				AtomStatus atomStatus = AtomStatus.valueOf(json.get("status").getAsString());
				JsonObject data = json.get("data").getAsJsonObject();
				AtomStatusEvent atomStatusEvent = new AtomStatusEvent(atomStatus, data);
				return Stream.of(atomStatusEvent);
			}
		);
	}

	/**
	 * Get the current status of an atom for this node
	 * @param aid the aid of the atom
	 * @return the status of the atom
	 */
	public Single<AtomStatus> getAtomStatus(AID aid) {
		JsonObject params = new JsonObject();
		params.addProperty("aid", aid.toString());
		return this.jsonRpcCall("Atoms.getAtomStatus", params)
			.map(JsonRpcResponse::getResult)
			.map(JsonElement::getAsJsonObject)
			.map(json -> AtomStatus.valueOf(json.get("status").getAsString()));
	}

	/**
	 * Queries for an atom by HID.
	 * If the node does not carry the atom (e.g. if it does not reside on the same shard) then
	 * this method will return an empty Maybe.
	 *
	 * @param hid the hash id of the atom being queried
	 * @return the atom if found, if not, return an empty Maybe
	 */
	public Maybe<AtomBuilder> getAtom(EUID hid) {
		JsonObject params = new JsonObject();
		params.addProperty("hid", hid.toString());

		JsonJavaType listOfAtom = Serialize.getInstance().jsonCollectionType(List.class, AtomBuilder.class);
		return this.jsonRpcCall("Ledger.getAtoms", params)
			.map(JsonRpcResponse::getResult)
			.<List<AtomBuilder>>map(result -> Serialize.getInstance().fromJson(result.toString(), listOfAtom))
			.flatMapMaybe(list -> list.isEmpty() ? Maybe.empty() : Maybe.just(list.get(0)));
	}

	<T> Observable<Notification<T>> observeNotifications(String notificationMethod, String subscriberId, Function<JsonObject, Stream<T>> mapper) {
		return Observable.create(emitter -> {
			Cancellable c = channel.addListener(msg -> {
				JsonObject json = parser.parse(msg).getAsJsonObject();
				if (!json.has("method")) {
					return;
				}

				if (!json.get("method").getAsString().equals(notificationMethod)) {
					return;
				}

				JsonObject params = json.get("params").getAsJsonObject();

				if (!params.get("subscriberId").getAsString().equals(subscriberId)) {
					return;
				}

				mapper.apply(params).map(Notification::ofEvent).forEach(emitter::onNext);
			});

			emitter.onNext(new Notification<>(NotificationType.START, null));

			emitter.setCancellable(c);
		});
	}

	public Observable<Notification<AtomObservation>> observeAtoms(String subscriberId) {
		return this.observeNotifications(
			"Atoms.subscribeUpdate",
			subscriberId,
			json -> {
				LOGGER.debug("Received Atoms.subscribeUpdate: for {}: {}", subscriberId, json);
				JsonArray atomEvents = json.getAsJsonArray("atomEvents");
				boolean isHead = json.has("isHead") && json.get("isHead").getAsBoolean();
				Stream<AtomObservation> observations = StreamSupport.stream(atomEvents.spliterator(), false)
					.map(jsonAtom -> {
						try {
							return Serialize.getInstance().fromJson(jsonAtom.toString(), AtomEvent.class);
						} catch (DeserializeException e) {
							throw new IllegalStateException("Failed to deserialize", e);
						}
					})
					.map(AtomObservation::ofEvent);

				if (isHead) {
					return Stream.concat(observations, Stream.of(AtomObservation.head()));
				} else {
					return observations;
				}
			}
		);
	}

	public Completable cancelAtomsSubscribe(String subscriberId) {
		final JsonObject cancelParams = new JsonObject();
		cancelParams.addProperty("subscriberId", subscriberId);

		return this.jsonRpcCall("Atoms.cancel", cancelParams).map(r -> {
			if (!r.isSuccess) {
				throw new RuntimeException();
			} else {
				return r;
			}
		}).ignoreElement();
	}

	public Completable sendAtomsSubscribe(String subscriberId, AtomQuery atomQuery) {
		final JsonObject params = new JsonObject();
		params.add("query", atomQuery.toJson());
		params.addProperty("subscriberId", subscriberId);

		return this.jsonRpcCall("Atoms.subscribe", params).map(r -> {
			if (!r.isSuccess) {
				throw new RuntimeException();
			} else {
				return r;
			}
		}).ignoreElement();
	}
}
