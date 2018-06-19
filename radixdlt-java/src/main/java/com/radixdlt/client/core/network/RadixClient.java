package com.radixdlt.client.core.network;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;
import com.radixdlt.client.core.serialization.RadixJson;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Single;
import io.reactivex.subjects.BehaviorSubject;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import com.radixdlt.client.core.atoms.Atom;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RadixClient extends WebSocketListener {
	private static final Logger logger = LoggerFactory.getLogger(RadixClient.class);

	private static class RadixObserver {
		private final Consumer<JsonObject> onNext;
		private final Consumer<Throwable> onError;

		public RadixObserver(Consumer<JsonObject> onNext, Consumer<Throwable> onError) {
			this.onNext = onNext;
			this.onError = onError;
		}
	}

	private WebSocket webSocket;
	private final Gson gson = RadixJson.getGson();
	private final JsonParser parser = new JsonParser();
	private final ConcurrentHashMap<String, RadixObserver> observers = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Consumer<JsonObject>> jsonRpcMethodCalls = new ConcurrentHashMap<>();
	private final AtomicBoolean closed = new AtomicBoolean(false);

	public enum RadixClientStatus {
		CONNECTING, OPEN, CLOSED, FAILURE
	}

	private final BehaviorSubject<RadixClientStatus> status = BehaviorSubject.createDefault(RadixClientStatus.CLOSED);

	private final String location;
	private final OkHttpClient okHttpClient;

	public RadixClient(OkHttpClient okHttpClient, String location) {
		this.okHttpClient = okHttpClient;
		this.location = location;

		this.status
			.filter(status -> status.equals(RadixClientStatus.FAILURE))
			.debounce(1, TimeUnit.MINUTES)
			.subscribe(i -> this.status.onNext(RadixClientStatus.CLOSED));
	}


	public String getLocation() {
		return location;
	}

	public Observable<RadixClientStatus> getStatus() {
		return status;
	}

	public boolean tryClose() {
		// TODO: must make this logic from check to close atomic, otherwise race issue occurs

		if (!this.jsonRpcMethodCalls.isEmpty()) {
			logger.info("Attempt to close " + location + " but methods still being completed.");
			return false;
		}

		if (!this.observers.isEmpty()) {
			logger.info("Attempt to close " + location + " but observers still subscribed.");
			return false;
		}

		this.closed.set(true);

		if (this.webSocket != null) {
			this.webSocket.close(1000, null);
		}

		return true;
	}

	@Override
	public void onOpen(WebSocket webSocket, Response response) {
		this.status.onNext(RadixClientStatus.OPEN);
	}

	@Override
	public void onMessage(WebSocket webSocket, String message) {
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
					logger.info(location + " says " + json.get("params"));
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

		// same as above
	}

	@Override
	public void onClosing(WebSocket webSocket, int code, String reason) {
		webSocket.close(1000, null);
	}

	@Override
	public void onClosed(WebSocket webSocket, int code, String reason) {
		if (!observers.isEmpty()) {
			logger.warn("Websocket closed but observers still exist.");
		}

		if (!jsonRpcMethodCalls.isEmpty()) {
			logger.warn("Websocket closed but methods still exist.");
		}

		this.status.onNext(RadixClientStatus.CLOSED);
	}

	@Override
	public void onFailure(WebSocket websocket, Throwable t, Response response) {
		if (closed.get()) {
			return;
		}

		logger.error(t.toString());
		this.status.onNext(RadixClientStatus.FAILURE);

		// Again, race conditions here
		this.observers.forEachValue(100, radixObserver -> radixObserver.onError.accept(t));
		this.observers.clear();
	}

	public void tryConnect() {
		// TODO: Race condition here but not fatal, fix later on
		if (this.status.getValue() == RadixClientStatus.CONNECTING) {
			return;
		}

		this.status.onNext(RadixClientStatus.CONNECTING);

		final Request request = new Request.Builder().url(location).build();

		// HACKISH: fix
		this.webSocket = this.okHttpClient.newWebSocket(request, this);
	}

	/**
	 * Attempts to connect to this Radix node on subscribe if not already connected
	 *
	 * @return completable which signifies when connection has been made
	 */
	public Completable connect() {
		return this.getStatus()
			.doOnNext(status -> {
				// TODO: cancel tryConnect on dispose
				if (status.equals(RadixClientStatus.CLOSED)) {
					this.tryConnect();
				} else if (status.equals(RadixClientStatus.FAILURE)) {
					throw new IOException();
				}
			})
			.filter(status -> status.equals(RadixClientStatus.OPEN))
			.firstOrError()
			.ignoreElement()
			;
	}

	public Single<NodeRunnerData> getSelf() {
		return this.connect().andThen(
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

			boolean sendSuccess = webSocket.send(gson.toJson(requestObject));
			if (!sendSuccess) {
				jsonRpcMethodCalls.remove(uuid);
				emitter.onError(new RuntimeException("Unable to get self"));
			}
		}));
	}

	public io.reactivex.Single<List<NodeRunnerData>> getLivePeers() {
		return this.connect().andThen(
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

			boolean sendSuccess = webSocket.send(gson.toJson(requestObject));
			if (!sendSuccess) {
				jsonRpcMethodCalls.remove(uuid);
				emitter.onError(new RuntimeException("Unable to tryConnect"));
			}
		}));
	}

	public <T extends Atom> io.reactivex.Observable<T> getAtoms(AtomQuery<T> atomQuery) {
		return this.connect().andThen(
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
					webSocket.send(gson.toJson(cancelObject));
				});

				// TODO: add unsubscribe!
				if (!webSocket.send(gson.toJson(requestObject))) {
					emitter.onError(new RuntimeException("Socket closed"));
				}
			})
		);
	}

	public <T extends Atom> io.reactivex.Observable<AtomSubmissionUpdate> submitAtom(T atom) {
		return this.connect().andThen(
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

					boolean sendSuccess = webSocket.send(gson.toJson(requestObject));
					if (!sendSuccess) {
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
