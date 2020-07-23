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

import com.radixdlt.client.serialization.GsonJson;
import com.radixdlt.client.serialization.Serialize;
import io.reactivex.functions.Cancellable;
import java.util.Collections;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.Test;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.serialization.DsonOutput.Output;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radixdlt.client.core.atoms.Atom;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.observers.TestObserver;

public class RadixJsonRpcClientTest {

	@Test
	public void getSelfTestError() {
		PersistentChannel channel = mock(PersistentChannel.class);

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

		AtomicReference<Consumer<String>> listener = new AtomicReference<>();
		doAnswer(a -> {
			Consumer<String> l = a.getArgument(0);
			listener.set(l);
			return mock(Cancellable.class);
		}).when(channel).addListener(any());

		JsonParser parser = new JsonParser();

		when(channel.sendMessage(any())).then(invocation -> {
			String msg = (String) invocation.getArguments()[0];
			JsonObject jsonObject = parser.parse(msg).getAsJsonObject();
			String id = jsonObject.get("id").getAsString();

			JsonArray atoms = new JsonArray();

			JsonObject response = new JsonObject();
			response.addProperty("id", id);
			response.add("result", atoms);

			listener.get().accept(GsonJson.getInstance().stringFromGson(response));
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
		AtomicReference<Consumer<String>> listener = new AtomicReference<>();
		doAnswer(a -> {
			Consumer<String> l = a.getArgument(0);
			listener.set(l);
			return mock(Cancellable.class);
		}).when(channel).addListener(any());

		JsonParser parser = new JsonParser();

		when(channel.sendMessage(any())).then(invocation -> {
			String msg = (String) invocation.getArguments()[0];
			JsonObject jsonObject = parser.parse(msg).getAsJsonObject();
			String id = jsonObject.get("id").getAsString();

			JsonArray atoms = new JsonArray();
			Atom atom = Atom.create(Collections.emptyList());
			String atomJson = Serialize.getInstance().toJson(atom, Output.API);
			atoms.add(parser.parse(atomJson));

			JsonObject response = new JsonObject();
			response.addProperty("id", id);
			response.add("result", atoms);

			listener.get().accept(GsonJson.getInstance().stringFromGson(response));
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
