package com.radix.regression;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.atommodel.rri.RRIParticle;
import com.radixdlt.client.atommodel.unique.UniqueParticle;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.AtomStatusEvent;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
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

import java.util.ArrayList;
import java.util.List;

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
			SpunParticle.down(new RRIParticle(rri)),
			SpunParticle.up(new UniqueParticle(rri.getAddress(), rri.getName()))
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
			SpunParticle.down(new RRIParticle(rri)),
			SpunParticle.up(new UniqueParticle(rri.getAddress(), rri.getName()))
		);
		observer.awaitCount(1);
		observer.assertValue(n -> n.getAtomStatus() == AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
		observer.dispose();
	}

	@Test
	public void testAtomEmpty() {
		TestObserver<AtomStatusEvent> observer = submitAtomAndObserve(0, false);
		observer.awaitCount(1, TestWaitStrategy.SLEEP_10MS, 5000);
		observer.assertValue(n -> n.getAtomStatus() == AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
		observer.dispose();
	}

	private TestObserver<AtomStatusEvent> submitAtomAndObserve(
		int messageSize,
		boolean addFee,
		SpunParticle... spunParticles
	) {
		List<ParticleGroup> particleGroups = new ArrayList<>();
		particleGroups.add(ParticleGroup.of(ImmutableList.copyOf(spunParticles)));

		String message = Strings.repeat("X", messageSize);
		if (addFee) {
			// FIXME: not really a fee
			message = "magic:0xdeadbeef" + message;
		}

		Atom unsignedAtom = Atom.create(particleGroups, message);
		// Sign and submit
		Atom signedAtom = this.identity.addSignature(unsignedAtom).blockingGet();

		TestObserver<AtomStatusEvent> observer = TestObserver.create(Util.loggingObserver("Submission"));

		final String subscriberId = UUID.randomUUID().toString();
		this.jsonRpcClient.observeAtomStatusNotifications(subscriberId)
			.doOnNext(n -> {
				if (n.getType() == NotificationType.START) {
					this.jsonRpcClient.sendGetAtomStatusNotifications(subscriberId, signedAtom.getAid()).blockingAwait();
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
		SpunParticle... spunParticles
	) {
		List<ParticleGroup> particleGroups = new ArrayList<>();
		particleGroups.add(ParticleGroup.of(ImmutableList.copyOf(spunParticles)));

		String message = Strings.repeat("X", messageSize);
		System.err.println(message.length());
		if (addFee) {
			// FIXME: not really a fee
			message = "magic:0xdeadbeef" + message;
		}

		Atom unsignedAtom = Atom.create(particleGroups, message);
		// Sign and submit
		Atom signedAtom = this.identity.addSignature(unsignedAtom).blockingGet();

		TestObserver<AtomStatusEvent> observer = TestObserver.create(Util.loggingObserver("Submission"));
		this.jsonRpcClient.pushAtom(signedAtom).subscribe(observer);
		return observer;
	}
}
