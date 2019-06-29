package com.radix.regression;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.unique.PutUniqueIdAction;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.BootstrapConfig;
import com.radixdlt.client.core.address.RadixUniverseConfigs;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.AtomStatusNotification;
import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.RadixNode;
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
	private static final BootstrapConfig BOOTSTRAP_CONFIG;
	static {
		String bootstrapConfigName = System.getenv("RADIX_BOOTSTRAP_CONFIG");
		if (bootstrapConfigName != null) {
			BOOTSTRAP_CONFIG = Bootstrap.valueOf(bootstrapConfigName);
		} else {
			BOOTSTRAP_CONFIG = Bootstrap.LOCALHOST_SINGLENODE;
		}
	}

	private RadixJsonRpcClient rpcClient;
	private RadixApplicationAPI api;

	@Before
	public void setUp() {
		this.api = RadixApplicationAPI.create(BOOTSTRAP_CONFIG, RadixIdentities.createNew());
		this.api.discoverNodes();
		RadixNode node = this.api.getNetworkState()
			.filter(state -> !state.getNodes().isEmpty())
			.map(state -> state.getNodes().keySet().iterator().next())
			.blockingFirst();

		Request localhost = new Request.Builder().url(node.toString()).build();
		WebSocketClient webSocketClient = new WebSocketClient(listener -> HttpClients.getSslAllTrustingClient().newWebSocket(localhost, listener));
		webSocketClient.connect();
		webSocketClient.getState()
			.filter(WebSocketStatus.CONNECTED::equals)
			.blockingFirst();
		this.rpcClient = new RadixJsonRpcClient(webSocketClient);
	}

	@Test
	public void when_get_status_for_genesis_atoms__then_all_should_return_stored() {
		for (Atom atom : BOOTSTRAP_CONFIG.getConfig().getGenesis()) {
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
		Atom atom = this.api.buildAtom(new PutUniqueIdAction(api.getMyAddress(), "test"))
			.flatMap(this.api.getMyIdentity()::sign)
			.blockingGet();
		AID aid = atom.getAid();

		String subscriberId = UUID.randomUUID().toString();

		TestObserver<AtomStatusNotification> testObserver = TestObserver.create(Util.loggingObserver("Atom Status"));
		this.rpcClient.observeAtomStatusNotifications(subscriberId).subscribe(testObserver);
		this.rpcClient.sendGetAtomStatusNotifications(subscriberId, aid).blockingAwait();
		testObserver.assertNoValues();
		testObserver.assertNotComplete();

		this.api.submitAtom(atom).blockUntilComplete();

		testObserver.awaitCount(1);
		testObserver.assertValueAt(0, n -> n.getAtomStatus().equals(AtomStatus.STORED));
		this.rpcClient.closeAtomStatusNotifications(subscriberId).blockingAwait();
	}
}
