package com.radixdlt.client.core.network;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;
import com.radixdlt.client.core.network.WebSocketClient.RadixClientStatus;
import com.radixdlt.client.core.serialization.RadixJson;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import java.util.List;
import com.radixdlt.client.core.atoms.Atom;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for managing the state across one web socket connection to a Radix Node.
 * This consists of mainly keeping track of JSON-RPC method calls and JSON-RPC subscription
 * calls.
 */
public class RadixJsonRpcClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixJsonRpcClient.class);

	private final Gson gson = RadixJson.getGson();
	private final JsonParser parser = new JsonParser();
	private final WebSocketClient wsClient;
	private final Observable<JsonObject> messages;

	public RadixJsonRpcClient(WebSocketClient wsClient) {

		this.wsClient = wsClient;
		this.messages = this.wsClient.getMessages()
			.map(msg -> parser.parse(msg).getAsJsonObject())
			.publish()
			.refCount();
	}


	public String getLocation() {
		return wsClient.getLocation();
	}

	public Observable<RadixClientStatus> getStatus() {
		return wsClient.getStatus();
	}

	public boolean tryClose() {
		return this.wsClient.close();
	}

	private <T> Single<T> callJsonRpcMethod(String method, TypeToken<T> returnType) {
		return this.wsClient.connect().andThen(
			Single.<T>create(emitter -> {
				final String uuid = UUID.randomUUID().toString();

				JsonObject requestObject = new JsonObject();
				requestObject.addProperty("id", uuid);
				requestObject.addProperty("method", method);
				requestObject.add("params", new JsonObject());

				messages
					.filter(msg -> msg.has("id"))
					.filter(msg -> msg.get("id").getAsString().equals(uuid))
					.firstOrError()
					.doOnSubscribe(disposable -> {
						boolean sendSuccess = wsClient.send(gson.toJson(requestObject));
						if (!sendSuccess) {
							disposable.dispose();
							emitter.onError(new RuntimeException("Could not connect."));
						}
					})
					.subscribe(msg -> {
						if (msg.getAsJsonObject().has("result")) {
							T data = gson.fromJson(msg.getAsJsonObject().get("result"), returnType.getType());
							emitter.onSuccess(data);
						} else {
							emitter.onError(new RuntimeException(msg.getAsJsonObject().get("error").toString()));
						}
					});
			})
		);
	}

	public Single<NodeRunnerData> getSelf() {
		return this.callJsonRpcMethod("Network.getSelf", new TypeToken<NodeRunnerData>() { });
	}

	public Single<List<NodeRunnerData>> getLivePeers() {
		return this.callJsonRpcMethod("Network.getLivePeers", new TypeToken<List<NodeRunnerData>>() { });
	}

	public <T extends Atom> Observable<T> getAtoms(AtomQuery<T> atomQuery) {
		return this.wsClient.connect().andThen(
			Observable.create(emitter -> {
				final String uuid = UUID.randomUUID().toString();
				JsonObject requestObject = new JsonObject();
				requestObject.addProperty("id", uuid);
				requestObject.addProperty("method", "Atoms.subscribe");
				JsonObject params = new JsonObject();
				params.addProperty("subscriberId", uuid);
				params.add("query", atomQuery.toJson());
				requestObject.add("params", params);

				Disposable subscriptionDisposable = messages
					.filter(msg -> msg.has("method"))
					.filter(msg -> msg.get("method").getAsString().equals("Atoms.subscribeUpdate"))
					.map(msg -> msg.get("params").getAsJsonObject())
					.filter(p -> p.get("subscriberId").getAsString().equals(uuid))
					.map(p -> p.get("atoms").getAsJsonArray())
					.flatMapIterable(array -> array)
					.map(JsonElement::getAsJsonObject)
					.map(jsonAtom -> gson.fromJson(jsonAtom, atomQuery.getAtomClass()))
					.map(atom -> {
						atom.putDebug("RECEIVED", System.currentTimeMillis());
						return atom;
					})
					.subscribe(
						emitter::onNext,
						emitter::onError
					);

				Disposable methodDisposable = messages
					.filter(msg -> msg.has("id"))
					.filter(msg -> msg.get("id").getAsString().equals(uuid))
					.firstOrError()
					.doOnSubscribe(disposable -> {
						boolean sendSuccess = wsClient.send(gson.toJson(requestObject));
						if (!sendSuccess) {
							disposable.dispose();
							emitter.onError(new RuntimeException("Could not connect."));
						}
					})
					.subscribe(msg -> {
						if (msg.getAsJsonObject().has("result")) {
							return;
						} else {
							// TODO: Better error message
							String err = msg.getAsJsonObject().get("error").toString();
							emitter.onError(new RuntimeException("JSON RPC Error: " + err));
						}
					});


				emitter.setCancellable(() -> {
					methodDisposable.dispose();
					subscriptionDisposable.dispose();

					final String cancelUuid = UUID.randomUUID().toString();
					JsonObject cancelObject = new JsonObject();
					cancelObject.addProperty("id", cancelUuid);
					cancelObject.addProperty("method", "Atoms.cancel");
					JsonObject cancelParams = new JsonObject();
					cancelParams.addProperty("subscriberId", uuid);
					cancelObject.add("params", cancelParams);
					wsClient.send(gson.toJson(cancelObject));
				});
			})
		);
	}

	public <T extends Atom> Observable<AtomSubmissionUpdate> submitAtom(T atom) {
		return this.wsClient.connect().andThen(
			Observable.<AtomSubmissionUpdate>create(emitter -> {
				JsonElement jsonAtom = gson.toJsonTree(atom, Atom.class);

				final String uuid = UUID.randomUUID().toString();
				JsonObject requestObject = new JsonObject();
				requestObject.addProperty("id", uuid);
				JsonObject params = new JsonObject();
				params.addProperty("subscriberId", uuid);
				params.add("atom", jsonAtom);
				requestObject.add("params", params);
				requestObject.addProperty("method", "Universe.submitAtomAndSubscribe");

				Disposable subscriptionDisposable = messages
					.filter(msg -> msg.has("method"))
					.filter(msg -> msg.get("method").getAsString().equals("AtomSubmissionState.onNext"))
					.map(msg -> msg.get("params").getAsJsonObject())
					.filter(p -> p.get("subscriberId").getAsString().equals(uuid))
					.map(p -> {
						final AtomSubmissionState state = AtomSubmissionState.valueOf(p.get("value").getAsString());
						final String message;
						if (p.has("message")) {
							message = p.get("message").getAsString();
						} else {
							message = null;
						}
						return AtomSubmissionUpdate.now(atom.getHid(), state, message);
					})
					.takeUntil(AtomSubmissionUpdate::isComplete)
					.subscribe(
						emitter::onNext,
						emitter::onError,
						emitter::onComplete
					);

				Disposable methodDisposable = messages
					.filter(msg -> msg.has("id"))
					.filter(msg -> msg.get("id").getAsString().equals(uuid))
					.firstOrError()
					.doOnSubscribe(disposable -> {
						boolean sendSuccess = wsClient.send(gson.toJson(requestObject));
						if (!sendSuccess) {
							disposable.dispose();
							emitter.onError(new RuntimeException("Could not connect."));
						} else {
							emitter.onNext(AtomSubmissionUpdate.now(atom.getHid(), AtomSubmissionState.SUBMITTING));
						}
					})
					.subscribe(msg -> {
						if (msg.getAsJsonObject().has("result")) {
							emitter.onNext(
								AtomSubmissionUpdate.now(
									atom.getHid(),
									AtomSubmissionState.SUBMITTED
								)
							);
						} else {
							JsonObject error = msg.getAsJsonObject().get("error").getAsJsonObject();
							String message = error.get("message").getAsString();
							emitter.onNext(
								AtomSubmissionUpdate.now(
									atom.getHid(),
									AtomSubmissionState.FAILED,
									message
								)
							);
							emitter.onComplete();
						}
					});

				emitter.setCancellable(() -> {
					methodDisposable.dispose();
					subscriptionDisposable.dispose();
				});
			})
		);
	}
}
