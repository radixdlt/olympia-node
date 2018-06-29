package com.radixdlt.client.core.network;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;
import com.radixdlt.client.core.network.WebSocketClient.RadixClientStatus;
import com.radixdlt.client.core.serialization.RadixJson;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.WebSocketListener;
import com.radixdlt.client.core.atoms.Atom;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for managing the state across one web socket connection to a Radix Node.
 * This consists of mainly keeping track of JSON-RPC method calls and JSON-RPC subscription
 * calls.
 */
public class RadixJsonRpcClient {
	private static final Logger logger = LoggerFactory.getLogger(RadixJsonRpcClient.class);

	private static class RadixObserver {
		private final Consumer<JsonObject> onNext;
		private final Consumer<Throwable> onError;

		public RadixObserver(Consumer<JsonObject> onNext, Consumer<Throwable> onError) {
			this.onNext = onNext;
			this.onError = onError;
		}
	}

	private final Gson gson = RadixJson.getGson();
	private final JsonParser parser = new JsonParser();
	private final ConcurrentHashMap<String, RadixObserver> observers = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Consumer<JsonObject>> jsonRpcMethodCalls = new ConcurrentHashMap<>();

	private final WebSocketClient wsClient;

	public RadixJsonRpcClient(WebSocketClient wsClient) {

		this.wsClient = wsClient;
		this.wsClient.getMessages().subscribe(this::onMessage);
		this.wsClient.getStatus()
			.filter(status -> status == RadixClientStatus.CLOSED)
			.subscribe(status -> {
				if (status == RadixClientStatus.CLOSED) {
					if (!observers.isEmpty()) {
						logger.warn("Websocket closed but observers still exist.");
					}

					if (!jsonRpcMethodCalls.isEmpty()) {
						logger.warn("Websocket closed but methods still exist.");
					}
				} else if (status == RadixClientStatus.FAILURE) {
					// Again, race conditions here
					this.observers.forEachValue(100, radixObserver -> radixObserver.onError.accept(new RuntimeException("Network failure")));
					this.observers.clear();
				}
			});
	}


	public String getLocation() {
		return wsClient.getLocation();
	}

	public Observable<RadixClientStatus> getStatus() {
		return wsClient.getStatus();
	}

	public boolean tryClose() {
		// TODO: must make this logic from check to close atomic, otherwise race issue occurs

		if (!this.jsonRpcMethodCalls.isEmpty()) {
			logger.info("Attempt to close " + wsClient.getLocation() + " but methods still being completed.");
			return false;
		}

		if (!this.observers.isEmpty()) {
			logger.info("Attempt to close " + wsClient.getLocation() + " but observers still subscribed.");
			return false;
		}

		this.wsClient.close();

		return true;
	}

	private void onMessage(String message) {
		JsonObject json;
		try {
			json = parser.parse(message).getAsJsonObject();
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException();
		}

		// JSON RPC responses
		if (json.has("id")) {
			final String id = json.get("id").getAsString();

			if (json.has("result")) {
				if (jsonRpcMethodCalls.containsKey(id)) {
					jsonRpcMethodCalls.remove(id).accept(json);
				}
			} else if (json.has("error")) {
				logger.error(json.toString());
				if (jsonRpcMethodCalls.containsKey(id)) {
					jsonRpcMethodCalls.remove(id).accept(json);
				}
			} else {
				throw new RuntimeException("Bad JSON RPC message: " + message);
			}

			return;
		}

		// JSON RPC notifications
		if (json.get("method") != null) {
			final String methodName = json.get("method").getAsString();
			switch (methodName) {
				case "Radix.welcome":
					logger.info(wsClient.getLocation() + " says " + json.get("params"));
					break;
				case "Atoms.subscribeUpdate":
				case "AtomSubmissionState.onNext":
					final JsonObject params = json.get("params").getAsJsonObject();
					final String subscriberId = params.get("subscriberId").getAsString();
					RadixObserver observer = observers.get(subscriberId);
					if (observer == null) {
						logger.warn("Received {} for subscriberId {} which doesn't exist/has been cancelled.", methodName, subscriberId);
					} else {
						observers.get(subscriberId).onNext.accept(params);
					}
					break;
				default:
					throw new IllegalStateException("Unknown method received: " + methodName);
			}
		}
	}

	public Single<NodeRunnerData> getSelf() {
		return this.wsClient.connect().andThen(
			Single.create(emitter -> {
			final String uuid = UUID.randomUUID().toString();

			JsonObject requestObject = new JsonObject();
			requestObject.addProperty("id", uuid);
			requestObject.addProperty("method", "Network.getSelf");
			requestObject.add("params", new JsonObject());

			jsonRpcMethodCalls.put(uuid, response -> {
				NodeRunnerData data = gson.fromJson(response.getAsJsonObject().get("result"), NodeRunnerData.class);
				emitter.onSuccess(data);
			});

			boolean sendSuccess = wsClient.send(gson.toJson(requestObject));
			if (!sendSuccess) {
				jsonRpcMethodCalls.remove(uuid);
				emitter.onError(new RuntimeException("Unable to get self"));
			}
		}));
	}

	public io.reactivex.Single<List<NodeRunnerData>> getLivePeers() {
		return this.wsClient.connect().andThen(
			Single.create(emitter -> {
			final String uuid = UUID.randomUUID().toString();

			JsonObject requestObject = new JsonObject();
			requestObject.addProperty("id", uuid);
			requestObject.addProperty("method", "Network.getLivePeers");
			requestObject.add("params", new JsonObject());

			jsonRpcMethodCalls.put(uuid, response -> {
				List<NodeRunnerData> peers = gson.fromJson(response.getAsJsonObject().get("result"), new TypeToken<List<NodeRunnerData>>(){}.getType());
				emitter.onSuccess(peers);
			});

			boolean sendSuccess = wsClient.send(gson.toJson(requestObject));
			if (!sendSuccess) {
				jsonRpcMethodCalls.remove(uuid);
				emitter.onError(new RuntimeException("Unable to tryConnect"));
			}
		}));
	}

	public <T extends Atom> io.reactivex.Observable<T> getAtoms(AtomQuery<T> atomQuery) {
		return this.wsClient.connect().andThen(
			io.reactivex.Observable.create(emitter -> {
				final String uuid = UUID.randomUUID().toString();

				JsonObject requestObject = new JsonObject();
				requestObject.addProperty("id", uuid);
				requestObject.addProperty("method", "Atoms.subscribe");
				JsonObject params = new JsonObject();
				params.addProperty("subscriberId", uuid);
				params.add("query", atomQuery.toJson());
				requestObject.add("params", params);

				observers.put(uuid, new RadixObserver(
					(result) -> {
						try {
							JsonArray atoms = result.get("atoms").getAsJsonArray();

							atoms.iterator().forEachRemaining(rawAtom -> {
								JsonObject jsonAtom = rawAtom.getAsJsonObject();
								if (atomQuery.getAtomType().isPresent() && jsonAtom.getAsJsonObject().get("serializer").getAsLong() != atomQuery.getAtomType()
									.get().getSerializer()) {
									emitter.onError(new IllegalStateException("Received wrong type of atom!"));
									return;
								}

								try {
									T atom = gson.fromJson(jsonAtom, atomQuery.getAtomClass());
									atom.putDebug("RECEIVED", System.currentTimeMillis());
									emitter.onNext(atom);
								} catch (Exception e) {
									emitter.onError(e);
								}
							});
						} catch (Exception e) {
							emitter.onError(e);
						}
					},
					emitter::onError
				));

				// TODO: fix concurrency
				jsonRpcMethodCalls.put(uuid, json -> {
					if (json.getAsJsonObject().has("result")) {
						return;
					}

					if (json.getAsJsonObject().has("error")) {
						// TODO: use better exception
						emitter.onError(new RuntimeException("JSON RPC 2.0 Error: " + json.toString()));
						return;
					}

					emitter.onError(new RuntimeException("JSON RPC 2.0 Unknown Response: " + json.toString()));
				});

				emitter.setCancellable(() -> {
					observers.remove(uuid);
					jsonRpcMethodCalls.remove(uuid);
					final String cancelUuid = UUID.randomUUID().toString();
					JsonObject cancelObject = new JsonObject();
					cancelObject.addProperty("id", cancelUuid);
					cancelObject.addProperty("method", "Atoms.cancel");
					JsonObject cancelParams = new JsonObject();
					cancelParams.addProperty("subscriberId", uuid);
					cancelObject.add("params", params);
					wsClient.send(gson.toJson(cancelObject));
				});

				// TODO: add unsubscribe!
				if (!wsClient.send(gson.toJson(requestObject))) {
					emitter.onError(new RuntimeException("Socket closed"));
				}
			})
		);
	}

	public <T extends Atom> io.reactivex.Observable<AtomSubmissionUpdate> submitAtom(T atom) {
		return this.wsClient.connect().andThen(
			io.reactivex.Observable.<AtomSubmissionUpdate>create(emitter -> {
				try {
					JsonElement jsonAtom = gson.toJsonTree(atom, Atom.class);

					final String uuid = UUID.randomUUID().toString();
					JsonObject requestObject = new JsonObject();
					requestObject.addProperty("id", uuid);
					JsonObject params = new JsonObject();
					params.addProperty("subscriberId", uuid);
					params.add("atom", jsonAtom);
					requestObject.add("params", params);
					requestObject.addProperty("method", "Universe.submitAtomAndSubscribe");
					observers.put(uuid,
						new RadixObserver(
							(json) -> {
								AtomSubmissionState state;
								String message = null;

								try {
									if (json.getAsJsonObject().get("message") != null) {
										message = json.getAsJsonObject().get("message").getAsString();
									}
									state = AtomSubmissionState.valueOf(json.getAsJsonObject().get("value").getAsString());
								} catch (IllegalArgumentException e) {
									state = AtomSubmissionState.UNKNOWN_FAILURE;
								}

								emitter.onNext(AtomSubmissionUpdate.now(atom.getHid(), state, message));

								if (state.isComplete()) {
									emitter.onComplete();
									observers.remove(uuid);
								}
							},
							emitter::onError
						)
					);

					// TODO: add unsubscribe!
					// emitter.setDisposable()

					jsonRpcMethodCalls.put(uuid, json -> {
						try {
							JsonObject jsonObject = json.getAsJsonObject();
							if (jsonObject.has("result")) {
								emitter.onNext(AtomSubmissionUpdate.now(atom.getHid(), AtomSubmissionState.SUBMITTED));
							} else if (jsonObject.has("error")) {
								String message = jsonObject.get("error").getAsJsonObject().get("message").getAsString();
								emitter.onNext(AtomSubmissionUpdate.now(atom.getHid(), AtomSubmissionState.FAILED, message));
							} else {
								emitter.onNext(AtomSubmissionUpdate.now(atom.getHid(), AtomSubmissionState.FAILED, "Unrecognizable json rpc response " + jsonObject.toString()));
							}
						} catch (Exception e) {
							emitter.onError(e);
						}
					});


					emitter.onNext(AtomSubmissionUpdate.now(atom.getHid(), AtomSubmissionState.SUBMITTING));

					if (!wsClient.send(gson.toJson(requestObject))) {
						jsonRpcMethodCalls.remove(uuid);
						emitter.onNext(AtomSubmissionUpdate.now(atom.getHid(), AtomSubmissionState.FAILED, "Websocket Send Fail"));
					}
				} catch (Exception e) {
					e.printStackTrace();
					emitter.onError(e);
				}
			})
		);
	}
}
