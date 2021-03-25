/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radix.regression;

import com.radix.test.utils.TokenUtilities;
import com.radixdlt.atom.Atom;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Transaction;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.unique.PutUniqueIdAction;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.AtomStatusEvent;
import com.radixdlt.client.core.atoms.Atoms;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.Notification;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NotificationType;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import io.reactivex.observers.TestObserver;

import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.radixdlt.identifiers.AID;

public class AtomStatusTest {
	private RadixJsonRpcClient rpcClient;
	private RadixApplicationAPI api;
	private WebSocketClient webSocketClient;

	@Before
	public void setUp() {
		this.api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), RadixIdentities.createNew());
		this.api.discoverNodes();
		RadixNode node = this.api.getNetworkState()
			.filter(state -> !state.getNodes().isEmpty())
			.map(state -> state.getNodes().iterator().next())
			.blockingFirst();

		this.webSocketClient = new WebSocketClient(listener ->
			HttpClients.getSslAllTrustingClient().newWebSocket(node.getWebSocketEndpoint(), listener)
		);
		webSocketClient.connect();
		webSocketClient.getState()
			.filter(WebSocketStatus.CONNECTED::equals)
			.blockingFirst();
		this.rpcClient = new RadixJsonRpcClient(webSocketClient);
	}

	@After
	public void tearDown() {
		this.webSocketClient.close();
	}

	@Test
	public void when_get_status_for_genesis_atoms__then_all_should_return_stored() {
		List<Atom> atoms = RadixEnv.getBootstrapConfig().getConfig().getGenesis();
		for (var atom : atoms) {
			TestObserver<AtomStatus> atomStatusTestObserver = TestObserver.create();
			this.rpcClient.getAtomStatus(Atoms.atomIdOf(atom)).subscribe(atomStatusTestObserver);
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
		TokenUtilities.requestTokensFor(this.api);
		Transaction transaction = api.createTransaction();
		RRI unique = RRI.of(api.getAddress(), "test");
		transaction.stage(PutUniqueIdAction.create(unique));
		var atom = api.getIdentity().addSignature(transaction.buildAtom())
			.blockingGet();

		String subscriberId = UUID.randomUUID().toString();

		TestObserver<AtomStatusEvent> testObserver = TestObserver.create(Util.loggingObserver("Atom Status"));
		this.rpcClient.observeAtomStatusNotifications(subscriberId)
			.doOnNext(n -> {
				if (n.getType() == NotificationType.START) {
					this.rpcClient.sendGetAtomStatusNotifications(subscriberId, Atoms.atomIdOf(atom)).blockingAwait();
					this.rpcClient.pushAtom(atom).blockingAwait();
				}
			})
			.filter(n -> n.getType().equals(NotificationType.EVENT))
			.map(Notification::getEvent)
			.subscribe(testObserver);

		testObserver.awaitCount(1);
		testObserver.assertValueAt(0, n -> n.getAtomStatus().equals(AtomStatus.STORED));
		this.rpcClient.closeAtomStatusNotifications(subscriberId).blockingAwait();
	}
}
