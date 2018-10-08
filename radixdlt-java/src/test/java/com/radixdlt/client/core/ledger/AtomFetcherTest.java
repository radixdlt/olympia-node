package com.radixdlt.client.core.ledger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.network.RadixJsonRpcClient;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import java.math.BigInteger;
import java.util.function.Function;
import org.junit.Test;

public class AtomFetcherTest {
	@Test
	public void firstNodeGetAtomsFail() {
		RadixJsonRpcClient bad = mock(RadixJsonRpcClient.class);
		RadixJsonRpcClient good = mock(RadixJsonRpcClient.class);
		Atom atom = mock(Atom.class);
		when(atom.getTimestamp()).thenReturn(1L);

		when(bad.getAtoms(any())).thenReturn(Observable.error(new RuntimeException()));
		when(good.getAtoms(any())).thenReturn(Observable.just(atom));

		Function<Long, Single<RadixJsonRpcClient>> clientSelector = mock(Function.class);
		when(clientSelector.apply(any())).thenReturn(Single.just(bad), Single.just(good));

		AtomFetcher atomFetcher = new AtomFetcher(clientSelector);
		TestObserver<Atom> testObserver = TestObserver.create();
		atomFetcher.fetchAtoms(new EUID(BigInteger.ONE)).subscribe(testObserver);
		testObserver.awaitCount(1);
		testObserver.assertValue(atom);
	}
}