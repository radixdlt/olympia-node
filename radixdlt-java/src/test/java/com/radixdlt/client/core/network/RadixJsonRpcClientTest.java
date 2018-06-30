package com.radixdlt.client.core.network;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radixdlt.client.core.atoms.Shards;
import com.radixdlt.client.core.network.WebSocketClient.RadixClientStatus;
import com.radixdlt.client.core.serialization.RadixJson;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import io.reactivex.subjects.ReplaySubject;
import org.junit.Test;
import org.mockito.stubbing.Answer;

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

		doAnswer((Answer) invocation -> {
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
}