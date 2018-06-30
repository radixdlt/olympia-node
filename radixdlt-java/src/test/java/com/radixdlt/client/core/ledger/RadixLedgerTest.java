package com.radixdlt.client.core.ledger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.ApplicationPayloadAtom;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.network.RadixClient;
import com.radixdlt.client.core.network.RadixNetwork;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;
import java.math.BigInteger;
import org.junit.Test;

public class RadixLedgerTest {

	@Test
	public void testFilterOutDuplicateAtoms() throws Exception {
		Atom atom = new AtomBuilder()
			.type(ApplicationPayloadAtom.class)
			.applicationId("Test")
			.payload("Hello")
			.addDestination(new EUID(BigInteger.ONE))
			.build()
			.getRawAtom();

		Consumer<ApplicationPayloadAtom> observer = mock(Consumer.class);
		RadixClient client = mock(RadixClient.class);
		RadixNetwork network = mock(RadixNetwork.class);
		when(network.getRadixClient(any(Long.class))).thenReturn(Single.just(client));
		when(client.getAtoms(any())).thenReturn(Observable.just(atom, atom));
		RadixLedger ledger = new RadixLedger(0, network);
		ledger.getAllAtoms(new EUID(BigInteger.ONE), ApplicationPayloadAtom.class)
			.subscribe(observer);

		verify(observer, times(1)).accept(any());
	}
}