package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.network.RadixNetworkController;
import com.radixdlt.client.core.network.actions.FetchAtomsRequestAction;
import io.reactivex.disposables.Disposable;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

import com.radixdlt.identifiers.RadixAddress;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.reactivex.observers.TestObserver;

public class RadixAtomPullerTest {
	@Test
	 public void when_many_subscribers_pull_atoms_on_the_same_address__fetch_atoms_action_should_be_emitted_once() throws Exception {
		RadixNetworkController controller = mock(RadixNetworkController.class);
		RadixAddress address = mock(RadixAddress.class);

		RadixAtomPuller radixAtomPuller = new RadixAtomPuller(controller);

		List<TestObserver> observers = Stream.iterate(TestObserver.create(), t -> TestObserver.create()).limit(10)
			.collect(Collectors.toList());

		List<Disposable> disposables = observers.stream()
			.map(observer -> radixAtomPuller.pull(address).subscribe())
			.collect(Collectors.toList());

		verify(controller, times(1)).dispatch(argThat(t -> {
			if (!(t instanceof FetchAtomsRequestAction)) {
				return false;
			}

			FetchAtomsRequestAction fetchAtomsAction = (FetchAtomsRequestAction) t;
			return fetchAtomsAction.getAddress().equals(address);
		}));

		disposables.forEach(Disposable::dispose);
	}
}