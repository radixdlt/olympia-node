package com.radixdlt.client.core.ledger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.atoms.ApplicationPayloadAtom;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.network.RadixJsonRpcClient;
import com.radixdlt.client.core.network.RadixNetwork;
import com.radixdlt.client.core.network.WebSocketClient.RadixClientStatus;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;
import java.math.BigInteger;
import java.util.Set;
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
		RadixJsonRpcClient client = mock(RadixJsonRpcClient.class);
		RadixNetwork network = mock(RadixNetwork.class);
		when(network.getRadixClients(any(Long.class))).thenReturn(Single.just(client).toObservable());
		when(network.getRadixClients(any(Set.class))).thenReturn(Single.just(client).toObservable());
		when(client.getAtoms(any())).thenReturn(Observable.just(atom, atom));
		when(client.getStatus()).thenReturn(Observable.just(RadixClientStatus.OPEN));
		when(client.checkAPIVersion()).thenReturn(Single.just(true));

		RadixUniverseConfig config = mock(RadixUniverseConfig.class);

		when(client.getUniverse()).thenReturn(Single.just(config));

		RadixLedger ledger = new RadixLedger(config, network);
		ledger.getAllAtoms(new EUID(BigInteger.ONE), ApplicationPayloadAtom.class)
			.subscribe(observer);

		verify(observer, times(1)).accept(any());
	}
}