package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.network.RadixNetworkController;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.FetchAtomsRequestAction;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

import com.radixdlt.client.atommodel.accounts.RadixAddress;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;

public class RadixAtomPullerTest {
	@Test
	 public void when_many_subscribers_pull_atoms_on_the_same_address__fetch_atoms_action_should_be_emitted_once() {
		Observable<RadixNodeAction> actions = Observable.never();
		RadixNetworkController controller = mock(RadixNetworkController.class);
		when(controller.getActions()).thenReturn(actions);
		RadixAddress address = mock(RadixAddress.class);

		RadixAtomPuller radixAtomPuller = new RadixAtomPuller(controller, (a, b) -> { });

		List<TestObserver> observers = Stream.iterate(TestObserver.create(), t -> TestObserver.create()).limit(10)
			.collect(Collectors.toList());

		observers.forEach(observer -> radixAtomPuller.pull(address).subscribe());

		verify(controller, times(1)).dispatch(any(FetchAtomsRequestAction.class));
	}
}