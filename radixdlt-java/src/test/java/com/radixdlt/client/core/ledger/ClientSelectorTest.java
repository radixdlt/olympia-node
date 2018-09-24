package com.radixdlt.client.core.ledger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.RadixJsonRpcClient;
import com.radixdlt.client.core.network.RadixNetwork;
import com.radixdlt.client.core.network.WebSocketClient.RadixClientStatus;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import java.io.IOException;
import java.util.Set;
import org.junit.Test;

public class ClientSelectorTest {
	@Test
	public void failedNodeConnectionTest() {
		RadixUniverseConfig config = mock(RadixUniverseConfig.class);
		RadixNetwork network = mock(RadixNetwork.class);
		RadixJsonRpcClient client = mock(RadixJsonRpcClient.class);
		when(client.getStatus()).thenReturn(Observable.just(RadixClientStatus.OPEN));
		when(client.getUniverse()).thenReturn(Single.error(new IOException()));
		when(network.getRadixClients(any(Set.class))).thenReturn(Observable.concat(Observable.just(client), Observable.never()));

		ClientSelector clientSelector = new ClientSelector(config, network, true);
		TestObserver<RadixJsonRpcClient> testObserver = TestObserver.create();
		clientSelector.getRadixClient(1L).subscribe(testObserver);

		testObserver.assertNoErrors();
		testObserver.assertNoValues();
	}
}