package com.radixdlt.client.core.network.jsonrpc;

import java.util.Collections;

import org.junit.Test;
import org.radix.common.ID.EUID;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.client.GsonJson;
import org.radix.serialization2.client.Serialize;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radixdlt.client.core.atoms.Atom;

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
}