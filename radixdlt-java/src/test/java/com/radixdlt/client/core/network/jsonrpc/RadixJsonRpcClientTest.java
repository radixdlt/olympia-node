package com.radixdlt.client.core.network.jsonrpc;

import com.radixdlt.client.core.ledger.AtomEvent;
import com.radixdlt.client.core.ledger.AtomEvent.AtomEventType;
import java.util.Collections;

import org.junit.Test;
import org.radix.common.ID.EUID;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.client.GsonJson;
import org.radix.serialization2.client.Serialize;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.ledger.AtomObservation;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NodeAtomSubmissionState;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NodeAtomSubmissionUpdate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import io.reactivex.subjects.ReplaySubject;

public class RadixJsonRpcClientTest {

	@Test
	public void getSelfTestError() {
		PersistentChannel channel = mock(PersistentChannel.class);

		when(channel.getMessages()).thenReturn(Observable.never());
		when(channel.sendMessage(any())).thenReturn(false);

		RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(channel);

		TestObserver<NodeRunnerData> observer = new TestObserver<>();

		jsonRpcClient.getInfo().subscribe(observer);

		observer.assertValueCount(0);
		observer.assertError(t -> true);
	}

	@Test
	public void getAtomDoesNotExistTest() {
		PersistentChannel channel = mock(PersistentChannel.class);

		ReplaySubject<String> messages = ReplaySubject.create();
		when(channel.getMessages()).thenReturn(messages);

		JsonParser parser = new JsonParser();

		when(channel.sendMessage(any())).then(invocation -> {
			String msg = (String) invocation.getArguments()[0];
			JsonObject jsonObject = parser.parse(msg).getAsJsonObject();
			String id = jsonObject.get("id").getAsString();

			JsonArray atoms = new JsonArray();

			JsonObject response = new JsonObject();
			response.addProperty("id", id);
			response.add("result", atoms);

			messages.onNext(GsonJson.getInstance().stringFromGson(response));
			return true;
		});
		RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(channel);

		TestObserver<Atom> observer = new TestObserver<>();

		jsonRpcClient.getAtom(new EUID(1)).subscribe(observer);

		observer.assertValueCount(0);
		observer.assertComplete();
		observer.assertNoErrors();
	}

	@Test
	public void getAtomTest() {
		PersistentChannel channel = mock(PersistentChannel.class);
		ReplaySubject<String> messages = ReplaySubject.create();
		when(channel.getMessages()).thenReturn(messages);

		JsonParser parser = new JsonParser();

		when(channel.sendMessage(any())).then(invocation -> {
			String msg = (String) invocation.getArguments()[0];
			JsonObject jsonObject = parser.parse(msg).getAsJsonObject();
			String id = jsonObject.get("id").getAsString();

			JsonArray atoms = new JsonArray();
			Atom atom = new Atom(Collections.emptyList(), 0L);
			String atomJson = Serialize.getInstance().toJson(atom, Output.API);
			atoms.add(parser.parse(atomJson));

			JsonObject response = new JsonObject();
			response.addProperty("id", id);
			response.add("result", atoms);

			messages.onNext(GsonJson.getInstance().stringFromGson(response));
			return true;
		});
		RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(channel);

		TestObserver<Atom> observer = new TestObserver<>();

		jsonRpcClient.getAtom(new EUID(1)).subscribe(observer);

		observer.assertValueCount(1);
		observer.assertComplete();
		observer.assertNoErrors();
	}

	@Test
	public void getAtomEventsTest() {
		PersistentChannel channel = mock(PersistentChannel.class);

		ReplaySubject<String> messages = ReplaySubject.create();
		when(channel.getMessages()).thenReturn(messages);

		JsonParser parser = new JsonParser();

		when(channel.sendMessage(any())).then(invocation -> {
			String msg = (String) invocation.getArguments()[0];
			JsonObject jsonObject = parser.parse(msg).getAsJsonObject();
			String id = jsonObject.get("id").getAsString();

			JsonObject response = new JsonObject();
			response.addProperty("id", id);
			response.add("result", new JsonObject());

			messages.onNext(GsonJson.getInstance().stringFromGson(response));

			String subscriberId = jsonObject.get("params").getAsJsonObject().get("subscriberId").getAsString();
			JsonObject notification = new JsonObject();
			notification.addProperty("method", "Atoms.subscribeUpdate");
			JsonObject params = new JsonObject();
			params.addProperty("subscriberId", subscriberId);

			JsonArray atomEvents = new JsonArray();
			Atom atom = new Atom(Collections.emptyList(), 0L);
			AtomEvent atomEvent = new AtomEvent(atom, AtomEventType.STORE);
			JsonElement atomEventJson = parser.parse(Serialize.getInstance().toJson(atomEvent, Output.API));
			atomEvents.add(atomEventJson);
			params.add("atomEvents", atomEvents);
			params.addProperty("isHead", false);

			notification.add("params", params);

			messages.onNext(GsonJson.getInstance().stringFromGson(notification));
			return true;
		});
		RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(channel);

		TestObserver<AtomObservation> observer = new TestObserver<>();

		RadixAddress address = mock(RadixAddress.class);

		jsonRpcClient.getAtoms(new AtomQuery(address)).subscribe(observer);

		observer.assertNoErrors();
		observer.assertValueCount(1);
	}

	@Test
	public void getAtomsCancelTest() {
		PersistentChannel channel = mock(PersistentChannel.class);

		ReplaySubject<String> messages = ReplaySubject.create();
		when(channel.getMessages()).thenReturn(messages);

		JsonParser parser = new JsonParser();

		when(channel.sendMessage(any())).then(invocation -> {
			String msg = (String) invocation.getArguments()[0];
			JsonObject jsonObject = parser.parse(msg).getAsJsonObject();
			String id = jsonObject.get("id").getAsString();
			String method = jsonObject.get("method").getAsString();

			if (method.equals("Atoms.subscribe")) {

				JsonObject response = new JsonObject();
				response.addProperty("id", id);
				response.add("result", new JsonObject());

				messages.onNext(GsonJson.getInstance().stringFromGson(response));
			} else if (method.equals("Atoms.cancel")) {
				String subscriberId = jsonObject.get("params").getAsJsonObject().get("subscriberId").getAsString();
				JsonObject notification = new JsonObject();
				notification.addProperty("method", "Atoms.subscribeUpdate");
				JsonObject params = new JsonObject();
				params.addProperty("subscriberId", subscriberId);
				Atom atomObject = new Atom(Collections.emptyList(), 0L);
				JsonElement atom = parser.parse(Serialize.getInstance().toJson(atomObject, Output.API));
				JsonArray atoms = new JsonArray();
				atoms.add(atom);
				params.add("atoms", atoms);

				notification.add("params", params);

				messages.onNext(GsonJson.getInstance().stringFromGson(notification));
			}

			return true;
		});
		RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(channel);

		TestObserver<AtomObservation> observer = new TestObserver<>();

		RadixAddress address = mock(RadixAddress.class);

		jsonRpcClient.getAtoms(new AtomQuery(address))
			.subscribe(observer);
		observer.cancel();

		observer.assertSubscribed();
		observer.assertNoErrors();
		observer.assertValueCount(0);
	}
}