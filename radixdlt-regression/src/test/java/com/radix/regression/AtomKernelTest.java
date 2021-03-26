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

import com.google.common.base.Strings;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.AtomStatusEvent;
import com.radixdlt.client.core.atoms.Atoms;
import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.Notification;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NotificationType;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;

import io.reactivex.observers.TestObserver;
import io.reactivex.observers.BaseTestConsumer.TestWaitStrategy;

import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AtomKernelTest {
	private RadixIdentity identity;
	private RadixAddress address;
	private RadixJsonRpcClient jsonRpcClient;
	private WebSocketClient webSocketClient;

	@Before
	public void setUp() {
		this.identity = RadixIdentities.createNew();
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), this.identity);
		this.address = api.getAddress();
		api.discoverNodes();
		RadixNode node = api.getNetworkState()
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
		this.jsonRpcClient = new RadixJsonRpcClient(webSocketClient);
	}

	@After
	public void tearDown() {
		this.webSocketClient.close();
	}


	@Test
	public void testAtomTooBig() {
		final var rri = RRI.of(this.address, "toobig");
		TestObserver<?> observer = submitAtom(
			1 << 20,
			true,
			Atom.newBuilder()
				.virtualDown(new RRIParticle(rri))
				.up(new UniqueParticle(rri.getName(), rri.getAddress(), System.nanoTime()))
				.particleGroup()
		);

		observer.awaitTerminalEvent();
		observer.assertError(RuntimeException.class);
		observer.dispose();
	}

	@Test
	public void testAtomNoFee() {
		final var rri = RRI.of(this.address, "nofee");
		TestObserver<AtomStatusEvent> observer = submitAtomAndObserve(
			10,
			false,
			Atom.newBuilder()
				.virtualDown(new RRIParticle(rri))
				.up(new UniqueParticle(rri.getName(), rri.getAddress(), System.nanoTime()))
				.particleGroup()
		);
		observer.awaitCount(1);
		observer.assertValue(n -> n.getAtomStatus() == AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
		observer.dispose();
	}

	@Test
	public void testAtomEmpty() {
		TestObserver<AtomStatusEvent> observer = submitAtomAndObserve(0, false, Atom.newBuilder());
		observer.awaitCount(1, TestWaitStrategy.SLEEP_10MS, 5000);
		observer.assertValue(n -> n.getAtomStatus() == AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
		observer.dispose();
	}

	private TestObserver<AtomStatusEvent> submitAtomAndObserve(
		int messageSize,
		boolean addFee,
		TxLowLevelBuilder atomBuilder
	) {
		String message = Strings.repeat("X", messageSize);
		if (addFee) {
			// FIXME: not really a fee
			message = "magic:0xdeadbeef" + message;
		}

		atomBuilder.message(message);
		// Sign and submit
		var signedAtom = this.identity.addSignature(atomBuilder).blockingGet();

		TestObserver<AtomStatusEvent> observer = TestObserver.create(Util.loggingObserver("Submission"));

		final String subscriberId = UUID.randomUUID().toString();
		this.jsonRpcClient.observeAtomStatusNotifications(subscriberId)
			.doOnNext(n -> {
				if (n.getType() == NotificationType.START) {
					this.jsonRpcClient.sendGetAtomStatusNotifications(subscriberId, Atoms.atomIdOf(signedAtom)).blockingAwait();
					this.jsonRpcClient.pushAtom(signedAtom).blockingAwait();
				}
			})
			.filter(n -> n.getType().equals(NotificationType.EVENT))
			.map(Notification::getEvent)
			.subscribe(observer);

		return observer;
	}

	private TestObserver<?> submitAtom(
		int messageSize,
		boolean addFee,
		TxLowLevelBuilder atomBuilder
	) {

		String message = Strings.repeat("X", messageSize);
		System.err.println(message.length());
		if (addFee) {
			// FIXME: not really a fee
			message = "magic:0xdeadbeef" + message;
		}

		atomBuilder.message(message);
		// Sign and submit
		var signedAtom = this.identity.addSignature(atomBuilder).blockingGet();

		TestObserver<AtomStatusEvent> observer = TestObserver.create(Util.loggingObserver("Submission"));
		this.jsonRpcClient.pushAtom(signedAtom).subscribe(observer);
		return observer;
	}
}
