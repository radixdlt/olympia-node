package com.radixdlt.client.core.ledger;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

import com.radixdlt.client.atommodel.accounts.RadixAddress;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.atoms.AtomObservation;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.observers.TestObserver;

public class RadixAtomPullerTest {
	@Test
	public void testClientAPICalledOnceWithManySubscibers() throws Exception {
		Consumer<Disposable> onSubscribe = mock(Consumer.class);
		Observable atoms = Observable.never().doOnSubscribe(onSubscribe);

		Function<RadixAddress, Observable<AtomObservation>> fetcher = mock(Function.class);
		when(fetcher.apply(any())).thenReturn(atoms);
		RadixAddress address = mock(RadixAddress.class);

		RadixAtomPuller radixAtomPuller = new RadixAtomPuller(fetcher, (a, b) -> { });

		List<TestObserver> observers = Stream.iterate(TestObserver.create(), t -> TestObserver.create()).limit(10)
			.collect(Collectors.toList());

		observers.forEach(observer -> radixAtomPuller.pull(address).subscribe());

		verify(onSubscribe, times(1)).accept(any());
	}
}