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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.atom.Atom;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.AtomStatusEvent;
import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.atom.SpunParticle;
import com.radixdlt.atom.Atoms;
import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.Notification;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NotificationType;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import com.radixdlt.identifiers.RRI;

import io.reactivex.observers.TestObserver;
import io.reactivex.observers.BaseTestConsumer.TestWaitStrategy;

import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SubmitIdenticalAtomsMultipleTimesTest {
	private RadixUniverse universe = RadixUniverse.create(RadixEnv.getBootstrapConfig());
	private RadixIdentity identity;
	private RadixJsonRpcClient jsonRpcClient;

	@Before
	public void setUp() {
		this.identity = RadixIdentities.createNew();
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), this.identity);
		api.discoverNodes();
		RadixNode node = api.getNetworkState()
			.filter(state -> !state.getNodes().isEmpty())
			.map(state -> state.getNodes().iterator().next())
			.blockingFirst();

		WebSocketClient webSocketClient = new WebSocketClient(listener ->
			HttpClients.getSslAllTrustingClient().newWebSocket(node.getWebSocketEndpoint(), listener)
		);
		webSocketClient.connect();
		webSocketClient.getState()
			.filter(WebSocketStatus.CONNECTED::equals)
			.blockingFirst();
		this.jsonRpcClient = new RadixJsonRpcClient(webSocketClient);
	}

	@Test
	public void testSubmitConflictingAtomTwoTimesConcurrently() {
		submitConflictingAtomXTimesConcurrently(2);
	}

	@Test
	public void testSubmitConflictingAtomManyTimesConcurrently() {
		submitConflictingAtomXTimesConcurrently(16);
	}

	@Test
	public void testSubmitSameAtomManyTimes() {
		submitSameAtomXTimesSequentially(4);
	}

	private void submitSameAtomXTimesSequentially(int times) {
		final var rri = RRI.of(this.universe.getAddressFrom(this.identity.getPublicKey()), "notunique");
		final var rriParticle = new RRIParticle(rri);
		final var uniqueParticle = new UniqueParticle(rri.getName(), rri.getAddress(), System.nanoTime());

		for (int i = 0; i < times; ++i) {
			Atom atom = buildAtom(0, SpunParticle.down(rriParticle), SpunParticle.up(uniqueParticle));

			TestObserver<AtomStatusEvent> observer = TestObserver.create(Util.loggingObserver("Atom Status " + i));
			final String subscriberId = UUID.randomUUID().toString();
			this.jsonRpcClient.observeAtomStatusNotifications(subscriberId)
				.doOnNext(n -> {
					if (n.getType() == NotificationType.START) {
						this.jsonRpcClient.sendGetAtomStatusNotifications(subscriberId, Atoms.getAid(atom)).blockingAwait();
					}
				})
				.filter(n -> n.getType().equals(NotificationType.EVENT))
				.map(Notification::getEvent)
				.subscribe(observer);
			TestObserver<AtomStatusEvent> submission = submitAtom(atom);
			submission.awaitTerminalEvent();
			submission.assertNoErrors();
			submission.assertComplete();

			observer.awaitCount(1);
			observer.assertValueCount(1);
			AtomStatus expectedStatus = i == 0 ? AtomStatus.STORED : AtomStatus.CONFLICT_LOSER;
			observer.assertValueAt(0, notification -> notification.getAtomStatus() == expectedStatus);
			observer.dispose();
		}
	}

	private void submitConflictingAtomXTimesConcurrently(int times) {
		List<TestObserver<AtomStatusEvent>> observers = Lists.newArrayList();
		List<TestObserver<AtomStatusEvent>> submissions = Lists.newArrayList();

		var counter = 1234L;

		final var rri = RRI.of(this.universe.getAddressFrom(this.identity.getPublicKey()), "notunique");
		final var rriParticle = new RRIParticle(rri);
		final var uniqueParticle = new UniqueParticle(rri.getName(), rri.getAddress(), System.nanoTime());

		for (int i = 0; i < times; ++i) {
			Atom atom = buildAtom(counter++, SpunParticle.down(rriParticle), SpunParticle.up(uniqueParticle));

			TestObserver<AtomStatusEvent> observer = TestObserver.create(Util.loggingObserver("Atom Status " + i));
			final String subscriberId = UUID.randomUUID().toString();
			this.jsonRpcClient.observeAtomStatusNotifications(subscriberId)
				.doOnNext(n -> {
					if (n.getType() == NotificationType.START) {
						this.jsonRpcClient.sendGetAtomStatusNotifications(subscriberId, Atoms.getAid(atom)).blockingAwait();
					}
				})
				.filter(n -> n.getType().equals(NotificationType.EVENT))
				.map(Notification::getEvent)
				.subscribe(observer);
			observers.add(observer);
			submissions.add(submitAtom(atom));
		}

		submissions.forEach(submission -> {
			submission.awaitTerminalEvent();
			submission.assertNoErrors();
			submission.assertComplete();
		});
		boolean foundCommit = false;
		for (int i = 0; i < times; ++i) {
			TestObserver<AtomStatusEvent> observer = observers.get(i);
			observer.awaitCount(1, TestWaitStrategy.SLEEP_10MS, 5000);

			// TODO: re-enable at some point? Have to come up with a better api strategy with mempool
            // TODO: currently, it is possible for an atom to be gossiped back and forth between nodes
			// observer.assertValueCount(1);

			if (!foundCommit) {
				AtomStatusEvent atomStatusEvent = observer.values().get(0);
				foundCommit = atomStatusEvent.getAtomStatus() == AtomStatus.STORED;
			} else {
				observer.assertValueAt(0, notification -> notification.getAtomStatus() == AtomStatus.CONFLICT_LOSER);
			}

			observer.dispose();
		}
	}

	private TestObserver<AtomStatusEvent> submitAtom(Atom atom) {
		TestObserver<AtomStatusEvent> observer = TestObserver.create();

		this.jsonRpcClient.pushAtom(atom).subscribe(observer);

		return observer;
	}

	private Atom buildAtom(long counter, SpunParticle... spunParticles) {
		List<ParticleGroup> particleGroups = new ArrayList<>();
		particleGroups.add(ParticleGroup.of(ImmutableList.copyOf(spunParticles)));

		// Warning: fake fee, plus counter to make AID different
		String message = "magic:0xdeadbeef:" + counter;

		Atom unsignedAtom = new Atom(particleGroups, message);
		// Sign and submit
		return this.identity.addSignature(unsignedAtom).blockingGet();
	}
}
