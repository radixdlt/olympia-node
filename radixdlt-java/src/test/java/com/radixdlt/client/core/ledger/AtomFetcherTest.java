package com.radixdlt.client.core.ledger;

import java.util.function.Function;

import org.junit.Test;
import org.radix.common.ID.EUID;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.network.RadixJsonRpcClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;

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
		RadixAddress address = mock(RadixAddress.class);
		EUID uid = mock(EUID.class);
		when(address.getUID()).thenReturn(uid);
		atomFetcher.fetchAtoms(address).subscribe(testObserver);
		testObserver.awaitCount(1);
		testObserver.assertValue(atom);
	}
}