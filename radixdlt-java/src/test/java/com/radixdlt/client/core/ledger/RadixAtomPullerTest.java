package com.radixdlt.client.core.ledger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.Atom;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.observers.TestObserver;
import java.math.BigInteger;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

public class RadixAtomPullerTest {
	@Test
	public void testClientAPICalledOnceWithManySubscibers() throws Exception {
		Consumer<Disposable> onSubscribe = mock(Consumer.class);
		Observable atoms = Observable.never().doOnSubscribe(onSubscribe);
		Function<EUID, Observable<Atom>> fetcher = mock(Function.class);
		when(fetcher.apply(any())).thenReturn(atoms);

		RadixAtomPuller radixAtomPuller = new RadixAtomPuller(fetcher, (a, b) -> { });

		List<TestObserver> observers = Stream.iterate(TestObserver.create(), t -> TestObserver.create()).limit(10)
			.collect(Collectors.toList());

		observers.forEach(observer -> radixAtomPuller.pull(new EUID(BigInteger.ONE)));

		verify(onSubscribe, times(1)).accept(any());
	}
}