package org.radix.api.jsonrpc;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.json.JSONObject;
import org.junit.Test;
import org.mockito.InOrder;
import org.radix.api.AtomQuery;
import org.radix.api.observable.AtomEventDto;
import org.radix.api.observable.Disposable;
import org.radix.api.observable.Observable;
import org.radix.api.observable.ObservedAtomEvents;
import org.radix.api.services.AtomsService;

import com.radixdlt.atoms.Atom;
import com.radixdlt.serialization.Serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class AtomsSubscribeEpicTest {

	@Test
	public void testSingleAtom() throws IOException {
		AtomsService atomsService = mock(AtomsService.class);
		ObservedAtomEvents observedAtomEvents = mock(ObservedAtomEvents.class);
		Atom atom = mock(Atom.class);
		AtomEventDto atomEventDto = mock(AtomEventDto.class);
		when(atomEventDto.getAtom()).thenReturn(atom);
		when(observedAtomEvents.atomEvents()).thenReturn(Stream.of(atomEventDto));
		when(observedAtomEvents.isHead()).thenReturn(true);

		Observable<ObservedAtomEvents> observable = mock(Observable.class);
		Disposable disposable = mock(Disposable.class);
		when(observable.subscribe(any())).thenAnswer(invocation -> {
			((Consumer<ObservedAtomEvents>) invocation.getArguments()[0]).accept(observedAtomEvents);
			return disposable;
		});
		when(atomsService.getAtomEvents(any())).thenReturn(observable);
		Serialization serializer = mock(Serialization.class);
		JSONObject jsonAtom = mock(JSONObject.class);
		when(serializer.toJsonObject(same(atom), any())).thenReturn(jsonAtom);
		Consumer<JSONObject> callback = mock(Consumer.class);
		AtomQuery atomQuery = mock(AtomQuery.class);
		AtomsSubscribeEpic epic = new AtomsSubscribeEpic(atomsService, serializer, json -> atomQuery, callback);
		epic.action(new JSONObject()
			.put("id", 0)
			.put("method", "Atoms.subscribe")
			.put("params", new JSONObject()
				.put("subscriberId", "Hi")
				.put("query", new JSONObject()
					.put("address", "JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor")
				)
			)
		);

		assertThat(epic.numObservers()).isEqualTo(1);

		epic.action(new JSONObject()
			.put("id", 1)
			.put("method", "Atoms.cancel")
			.put("params", new JSONObject()
				.put("subscriberId", "Hi")
			)
		);

		InOrder inOrder = inOrder(callback);
		inOrder.verify(callback, times(1)).accept(argThat(o ->
			o.has("result") && o.getJSONObject("result").getBoolean("success")
		));
		inOrder.verify(callback, times(1)).accept(argThat(o ->
			o.has("method") && o.has("params")
				&& o.getString("method").equals("Atoms.subscribeUpdate")
				&& o.getJSONObject("params").getJSONArray("atomEvents").length() == 1
		));
		inOrder.verify(callback, times(1)).accept(argThat(o ->
			o.has("result") && o.getJSONObject("result").getBoolean("success")
		));
		assertThat(epic.numObservers()).isEqualTo(0);
	}
}