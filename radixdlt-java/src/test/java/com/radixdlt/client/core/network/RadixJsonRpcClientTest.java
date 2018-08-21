package com.radixdlt.client.core.network;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.PayloadAtom;
import com.radixdlt.client.core.atoms.Shards;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;
import com.radixdlt.client.core.network.WebSocketClient.RadixClientStatus;
import com.radixdlt.client.core.serialization.RadixJson;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import io.reactivex.subjects.ReplaySubject;
import org.junit.Test;

public class RadixJsonRpcClientTest {

	@Test
	public void getSelfTestError() {
		WebSocketClient wsClient = mock(WebSocketClient.class);
		when(wsClient.getStatus()).thenReturn(Observable.just(RadixClientStatus.OPEN));

		ReplaySubject<String> messages = ReplaySubject.create();
		when(wsClient.getMessages()).thenReturn(messages);
		when(wsClient.connect()).thenReturn(Completable.complete());
		when(wsClient.send(any())).thenReturn(false);

		RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(wsClient);

		TestObserver<NodeRunnerData> observer = new TestObserver<>();

		jsonRpcClient.getSelf().subscribe(observer);

		observer.assertValueCount(0);
		observer.assertError(t -> true);
	}

	@Test
	public void getSelfTest() {
		WebSocketClient wsClient = mock(WebSocketClient.class);
		when(wsClient.getStatus()).thenReturn(Observable.just(RadixClientStatus.OPEN));

		ReplaySubject<String> messages = ReplaySubject.create();
		when(wsClient.getMessages()).thenReturn(messages);
		when(wsClient.connect()).thenReturn(Completable.complete());

		JsonParser parser = new JsonParser();
		Gson gson = RadixJson.getGson();

		doAnswer(invocation -> {
			String msg = (String) invocation.getArguments()[0];
			JsonObject jsonObject = parser.parse(msg).getAsJsonObject();
			String id = jsonObject.get("id").getAsString();

			JsonObject data = new JsonObject();
			JsonObject system = new JsonObject();
			JsonObject shards = new JsonObject();
			shards.addProperty("low", -1);
			shards.addProperty("high", 1);
			system.add("shards", shards);
			data.add("system", system);

			JsonObject response = new JsonObject();
			response.addProperty("id", id);
			response.add("result", data);

			messages.onNext(gson.toJson(response));
			return true;
		}).when(wsClient).send(any());
		RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(wsClient);

		TestObserver<NodeRunnerData> observer = new TestObserver<>();

		jsonRpcClient.getSelf().subscribe(observer);

		observer.assertValueCount(1);
		observer.assertValue(data -> data.getShards().equals(Shards.range(-1, 1)));
	}

	@Test
	public void getAtomDoesNotExistTest() {
		WebSocketClient wsClient = mock(WebSocketClient.class);
		when(wsClient.getStatus()).thenReturn(Observable.just(RadixClientStatus.OPEN));

		ReplaySubject<String> messages = ReplaySubject.create();
		when(wsClient.getMessages()).thenReturn(messages);
		when(wsClient.connect()).thenReturn(Completable.complete());

		JsonParser parser = new JsonParser();
		Gson gson = RadixJson.getGson();

		doAnswer(invocation -> {
			String msg = (String) invocation.getArguments()[0];
			JsonObject jsonObject = parser.parse(msg).getAsJsonObject();
			String id = jsonObject.get("id").getAsString();

			JsonArray atoms = new JsonArray();

			JsonObject response = new JsonObject();
			response.addProperty("id", id);
			response.add("result", atoms);

			messages.onNext(gson.toJson(response));
			return true;
		}).when(wsClient).send(any());
		RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(wsClient);

		TestObserver<Atom> observer = new TestObserver<>();

		jsonRpcClient.getAtom(new EUID(1)).subscribe(observer);

		observer.assertValueCount(0);
		observer.assertComplete();
		observer.assertNoErrors();
	}

	@Test
	public void getAtomTest() {
		WebSocketClient wsClient = mock(WebSocketClient.class);
		when(wsClient.getStatus()).thenReturn(Observable.just(RadixClientStatus.OPEN));

		ReplaySubject<String> messages = ReplaySubject.create();
		when(wsClient.getMessages()).thenReturn(messages);
		when(wsClient.connect()).thenReturn(Completable.complete());

		JsonParser parser = new JsonParser();
		Gson gson = RadixJson.getGson();

		doAnswer(invocation -> {
			String msg = (String) invocation.getArguments()[0];
			JsonObject jsonObject = parser.parse(msg).getAsJsonObject();
			String id = jsonObject.get("id").getAsString();

			JsonArray atoms = new JsonArray();
			Atom atom = new PayloadAtom("Test", null, null, null, null, 1);
			atoms.add(gson.toJsonTree(atom, Atom.class));

			JsonObject response = new JsonObject();
			response.addProperty("id", id);
			response.add("result", atoms);

			messages.onNext(gson.toJson(response));
			return true;
		}).when(wsClient).send(any());
		RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(wsClient);

		TestObserver<Atom> observer = new TestObserver<>();

		jsonRpcClient.getAtom(new EUID(1)).subscribe(observer);

		observer.assertValue(atom -> atom.getAsTransactionAtom().getApplicationId().equals("Test"));
		observer.assertComplete();
		observer.assertNoErrors();
	}

	@Test
	public void getAtomsTest() {
		WebSocketClient wsClient = mock(WebSocketClient.class);
		when(wsClient.getStatus()).thenReturn(Observable.just(RadixClientStatus.OPEN));

		ReplaySubject<String> messages = ReplaySubject.create();
		when(wsClient.getMessages()).thenReturn(messages);
		when(wsClient.connect()).thenReturn(Completable.complete());

		JsonParser parser = new JsonParser();
		Gson gson = RadixJson.getGson();

		doAnswer(invocation -> {
			String msg = (String) invocation.getArguments()[0];
			JsonObject jsonObject = parser.parse(msg).getAsJsonObject();
			String id = jsonObject.get("id").getAsString();

			JsonObject response = new JsonObject();
			response.addProperty("id", id);
			response.add("result", new JsonObject());

			messages.onNext(gson.toJson(response));

			String subscriberId = jsonObject.get("params").getAsJsonObject().get("subscriberId").getAsString();
			JsonObject notification = new JsonObject();
			notification.addProperty("method", "Atoms.subscribeUpdate");
			JsonObject params = new JsonObject();
			params.addProperty("subscriberId", subscriberId);

			JsonArray atoms = new JsonArray();
			JsonElement atom = gson.toJsonTree(
				new PayloadAtom("Test", null, null, null, null, 1),
				Atom.class
			);
			atoms.add(atom);
			params.add("atoms", atoms);

			notification.add("params", params);

			messages.onNext(gson.toJson(notification));
			return true;
		}).when(wsClient).send(any());
		RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(wsClient);

		TestObserver<PayloadAtom> observer = new TestObserver<>();

		jsonRpcClient.getAtoms(new AtomQuery<>(new EUID(1), PayloadAtom.class)).subscribe(observer);

		observer.assertNoErrors();
		observer.assertValueCount(1);
		observer.assertValue(atom -> atom.getAsTransactionAtom().getApplicationId().equals("Test"));
	}

	@Test
	public void getAtomsCancelTest() {
		WebSocketClient wsClient = mock(WebSocketClient.class);
		when(wsClient.getStatus()).thenReturn(Observable.just(RadixClientStatus.OPEN));

		ReplaySubject<String> messages = ReplaySubject.create();
		when(wsClient.getMessages()).thenReturn(messages);
		when(wsClient.connect()).thenReturn(Completable.complete());

		JsonParser parser = new JsonParser();
		Gson gson = RadixJson.getGson();

		doAnswer(invocation -> {
			String msg = (String) invocation.getArguments()[0];
			JsonObject jsonObject = parser.parse(msg).getAsJsonObject();
			String id = jsonObject.get("id").getAsString();
			String method = jsonObject.get("method").getAsString();

			if (method.equals("Atoms.subscribe")) {

				JsonObject response = new JsonObject();
				response.addProperty("id", id);
				response.add("result", new JsonObject());

				messages.onNext(gson.toJson(response));
			} else if (method.equals("Subscription.cancel")) {
				String subscriberId = jsonObject.get("params").getAsJsonObject().get("subscriberId").getAsString();
				JsonObject notification = new JsonObject();
				notification.addProperty("method", "Atoms.subscribeUpdate");
				JsonObject params = new JsonObject();
				params.addProperty("subscriberId", subscriberId);
				JsonArray atoms = new JsonArray();
				JsonElement atom = gson.toJsonTree(
					new PayloadAtom("Test", null, null, null, null, 1),
					Atom.class
				);
				atoms.add(atom);
				params.add("atoms", atoms);

				notification.add("params", params);

				messages.onNext(gson.toJson(notification));
			}

			return true;
		}).when(wsClient).send(any());
		RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(wsClient);

		TestObserver<PayloadAtom> observer = new TestObserver<>();

		jsonRpcClient.getAtoms(new AtomQuery<>(new EUID(1), PayloadAtom.class))
			.subscribe(observer);
		observer.cancel();

		observer.assertSubscribed();
		observer.assertNoErrors();
		observer.assertValueCount(0);
	}

	@Test
	public void submitAtomTest() {
		WebSocketClient wsClient = mock(WebSocketClient.class);
		when(wsClient.getStatus()).thenReturn(Observable.just(RadixClientStatus.OPEN));

		ReplaySubject<String> messages = ReplaySubject.create();
		when(wsClient.getMessages()).thenReturn(messages);
		when(wsClient.connect()).thenReturn(Completable.complete());

		JsonParser parser = new JsonParser();
		Gson gson = RadixJson.getGson();

		doAnswer(invocation -> {
			String msg = (String) invocation.getArguments()[0];
			JsonObject jsonObject = parser.parse(msg).getAsJsonObject();
			String id = jsonObject.get("id").getAsString();
			String method = jsonObject.get("method").getAsString();

			if (method.equals("Universe.submitAtomAndSubscribe")) {
				JsonObject response = new JsonObject();
				response.addProperty("id", id);
				response.add("result", new JsonObject());

				messages.onNext(gson.toJson(response));

				String subscriberId = jsonObject.get("params").getAsJsonObject().get("subscriberId").getAsString();
				JsonObject notification = new JsonObject();
				notification.addProperty("method", "AtomSubmissionState.onNext");
				JsonObject params = new JsonObject();
				params.addProperty("subscriberId", subscriberId);
				params.addProperty("value", "STORED");

				notification.add("params", params);

				messages.onNext(gson.toJson(notification));
			}

			return true;
		}).when(wsClient).send(any());
		RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(wsClient);

		TestObserver<AtomSubmissionUpdate> observer = new TestObserver<>();

		jsonRpcClient.submitAtom(
			new PayloadAtom("Test", null, null, null, null, 1)
		).subscribe(observer);

		observer.assertNoErrors();
		observer.assertValueAt(observer.valueCount() - 1, update -> update.getState().equals(AtomSubmissionState.STORED));
		observer.assertComplete();
	}
}