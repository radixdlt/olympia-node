package com.radix.regression;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.unique.PutUniqueIdAction;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.address.RadixUniverseConfigs;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.AtomStatusNotification;
import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import io.reactivex.observers.TestObserver;
import java.util.UUID;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Test;
import org.radix.common.ID.AID;

public class AtomStatusTest {
	private RadixJsonRpcClient rpcClient;

	@Before
	public void setUp() {
		Request localhost = new Request.Builder().url("ws://localhost:8080/rpc").build();
		WebSocketClient webSocketClient = new WebSocketClient(listener -> HttpClients.getSslAllTrustingClient().newWebSocket(localhost, listener));
		webSocketClient.connect();
		webSocketClient.getState()
			.filter(WebSocketStatus.CONNECTED::equals)
			.blockingFirst();
		this.rpcClient = new RadixJsonRpcClient(webSocketClient);
	}

	@Test
	public void when_get_status_for_genesis_atoms__then_all_should_return_stored() {
		for (Atom atom : RadixUniverseConfigs.getLocalnet().getGenesis()) {
			TestObserver<AtomStatus> atomStatusTestObserver = TestObserver.create();
			this.rpcClient.getAtomStatus(atom.getAid()).subscribe(atomStatusTestObserver);
			atomStatusTestObserver.awaitTerminalEvent();
			atomStatusTestObserver.assertValue(AtomStatus.STORED);
		}
	}

	@Test
	public void when_get_status_for_unknown_atom__then_should_return_does_not_exist() {
		TestObserver<AtomStatus> atomStatusTestObserver = TestObserver.create();
		this.rpcClient.getAtomStatus(AID.from("deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
			.subscribe(atomStatusTestObserver);
		atomStatusTestObserver.awaitTerminalEvent();
		atomStatusTestObserver.assertValue(AtomStatus.DOES_NOT_EXIST);
	}

	@Test
	public void given_a_subscription_to_status_notifications__when_the_atom_is_stored__a_store_notification_should_be_sent() {
		RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.LOCALHOST_SINGLENODE, RadixIdentities.createNew());
		Atom atom = api.buildAtom(new PutUniqueIdAction(api.getMyAddress(), "test"))
			.flatMap(api.getMyIdentity()::sign)
			.blockingGet();
		AID aid = atom.getAid();

		String subscriberId = UUID.randomUUID().toString();

		TestObserver<AtomStatusNotification> testObserver = TestObserver.create(Util.loggingObserver("Atom Status"));
		rpcClient.observeAtomStatusNotifications(subscriberId).subscribe(testObserver);
		rpcClient.sendGetAtomStatusNotifications(subscriberId, aid).blockingAwait();
		testObserver.assertNoValues();
		testObserver.assertNotComplete();

		api.submitAtom(atom).blockUntilComplete();

		testObserver.awaitCount(1);
		testObserver.assertValueAt(0, n -> n.getAtomStatus().equals(AtomStatus.STORED));
		rpcClient.closeAtomStatusNotifications(subscriberId).blockingAwait();
	}
}
