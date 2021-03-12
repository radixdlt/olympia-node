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

import org.json.JSONObject;
import org.junit.Test;
import org.radix.api.AtomQuery;
import org.radix.api.observable.AtomEventDto;
import org.radix.api.observable.Disposable;
import org.radix.api.observable.Observable;
import org.radix.api.observable.ObservedAtomEvents;
import org.radix.api.services.AtomsService;

import com.radixdlt.atommodel.Atom;
import com.radixdlt.serialization.Serialization;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

public class AtomsSubscribeEpicTest {
	public static final JSONObject SUBSCRIBE_REQUEST = jsonObject()
		.put("id", 0)
		.put("method", "Atoms.subscribe")
		.put("params", jsonObject()
			.put("subscriberId", "Hi")
			.put("query", jsonObject().put("address", "JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor")));

	public static final JSONObject CANCEL_REQUEST = jsonObject()
		.put("id", 1)
		.put("method", "Atoms.cancel")
		.put("params", jsonObject().put("subscriberId", "Hi"));

	interface ObservedAtomEventsObservable extends Observable<ObservedAtomEvents> {
		// Nothing here
	}

	interface ConsumerJSONObject extends Consumer<JSONObject> {
		// Nothing here
	}

	@Test
	public void testSingleAtom() {
		var atomsService = mock(AtomsService.class);
		var observedAtomEvents = mock(ObservedAtomEvents.class);
		var atom = mock(Atom.class);
		var atomEventDto = mock(AtomEventDto.class);

		when(atomEventDto.getAtom()).thenReturn(atom);
		when(observedAtomEvents.atomEvents()).thenReturn(Stream.of(atomEventDto));
		when(observedAtomEvents.isHead()).thenReturn(true);

		var observable = mock(ObservedAtomEventsObservable.class);
		var disposable = mock(Disposable.class);

		when(observable.subscribe(any())).thenAnswer(invocation -> {
			// SuppressWarnings required here - no other way to have correct type args
			@SuppressWarnings("unchecked")
			var consumer = (Consumer<ObservedAtomEvents>) invocation.getArguments()[0];
			consumer.accept(observedAtomEvents);
			return disposable;
		});

		when(atomsService.getAtomEvents(any())).thenReturn(observable);

		var serializer = mock(Serialization.class);
		var jsonAtom = mock(JSONObject.class);
		when(serializer.toJsonObject(same(atom), any())).thenReturn(jsonAtom);

		var callback = mock(ConsumerJSONObject.class);
		var atomQuery = mock(AtomQuery.class);
		var epic = new AtomsSubscribeEpic(atomsService, serializer, json -> atomQuery, callback);

		epic.action(SUBSCRIBE_REQUEST);

		assertThat(epic.numObservers()).isEqualTo(1);

		epic.action(CANCEL_REQUEST);

		var inOrder = inOrder(callback);
		inOrder.verify(callback, times(1))
			.accept(argThat(o -> o.has("result")
				&& o.getJSONObject("result").getBoolean("success")));

		inOrder.verify(callback, times(1))
			.accept(argThat(o -> o.has("method")
				&& o.has("params")
				&& o.getString("method").equals("Atoms.subscribeUpdate")
				&& o.getJSONObject("params").getJSONArray("atomEvents").length() == 1));

		inOrder.verify(callback, times(1))
			.accept(argThat(o -> o.has("result")
				&& o.getJSONObject("result").getBoolean("success")));

		assertThat(epic.numObservers()).isEqualTo(0);
	}
}