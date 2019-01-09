package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.AtomObservation;
import java.util.function.Function;

import com.radixdlt.client.core.network.websocket.WebSocketException;
import org.junit.Test;
import org.radix.common.ID.EUID;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;

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
		AtomObservation atomObservation = mock(AtomObservation.class);
		when(atomObservation.getAtom()).thenReturn(atom);

		when(bad.getAtoms(any())).thenReturn(Observable.error(new WebSocketException()));
		when(good.getAtoms(any())).thenReturn(Observable.just(atomObservation));

		Function<Long, Single<RadixJsonRpcClient>> clientSelector = mock(Function.class);
		when(clientSelector.apply(any())).thenReturn(Single.just(bad), Single.just(good));

		AtomFetcher atomFetcher = new AtomFetcher(clientSelector);
		TestObserver<AtomObservation> testObserver = TestObserver.create();
		RadixAddress address = mock(RadixAddress.class);
		EUID uid = mock(EUID.class);
		when(address.getUID()).thenReturn(uid);
		atomFetcher.fetchAtoms(address).subscribe(testObserver);
		testObserver.awaitCount(1);
		testObserver.assertValue(atomObservation);
	}
}