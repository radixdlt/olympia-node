package com.radix.regression;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.FeeMapper;
import com.radixdlt.client.application.translate.PowFeeMapper;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.RadixUniverse;
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
import com.radixdlt.client.core.pow.ProofOfWorkBuilder;
import io.reactivex.observers.TestObserver;
import io.reactivex.observers.BaseTestConsumer.TestWaitStrategy;

import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AtomKernelTest {
	private RadixUniverse universe = RadixUniverse.create(RadixEnv.getBootstrapConfig());
	private RadixIdentity identity;
	private FeeMapper feeMapper = new PowFeeMapper(Atom::getHash, new ProofOfWorkBuilder());
	private RadixJsonRpcClient jsonRpcClient;
	private WebSocketClient webSocketClient;

	@Before
	public void setUp() {
		this.identity = RadixIdentities.createNew();
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), this.identity);
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
		TestObserver<?> observer = submitAtom(ImmutableMap.of(), true, System.currentTimeMillis() + "", SpunParticle.up(new MessageParticle.MessageParticleBuilder()
			.payload(new byte[1 << 20])
			.metaData("application", "message")
			.from(universe.getAddressFrom(this.identity.getPublicKey()))
			.to(universe.getAddressFrom(this.identity.getPublicKey()))
			.build()));

		observer.awaitTerminalEvent();
		observer.assertError(RuntimeException.class);
		observer.dispose();
	}

	@Test
	public void testAtomNoFee() {
		TestObserver<AtomStatusEvent> observer = submitAtomAndObserve(ImmutableMap.of(), false, System.currentTimeMillis() + "", SpunParticle.up(new MessageParticle.MessageParticleBuilder()
			.payload(new byte[10])
			.metaData("application", "message")
			.from(universe.getAddressFrom(this.identity.getPublicKey()))
			.to(universe.getAddressFrom(this.identity.getPublicKey()))
			.build()));
		observer.awaitCount(1);
		observer.assertValue(n -> n.getAtomStatus() == AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
		observer.dispose();
	}

	@Test
	public void testAtomBadPowFee() {
		TestObserver<AtomStatusEvent> observer = submitAtomAndObserve(ImmutableMap.of(
			Atom.METADATA_POW_NONCE_KEY, "1337"
		), false, System.currentTimeMillis() + "", SpunParticle.up(new MessageParticle.MessageParticleBuilder()
			.payload(new byte[10])
			.metaData("application", "message")
			.from(universe.getAddressFrom(this.identity.getPublicKey()))
			.to(universe.getAddressFrom(this.identity.getPublicKey()))
			.build()));
		observer.awaitCount(1, TestWaitStrategy.SLEEP_10MS, 10000);
		observer.assertValue(n -> n.getAtomStatus() == AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
		observer.dispose();
	}

	@Test
	public void testAtomInvalidTimestamp() {
		TestObserver<AtomStatusEvent> observer = submitAtomAndObserve(ImmutableMap.of(), false, "invalid", SpunParticle.up(new MessageParticle.MessageParticleBuilder()
			.payload(new byte[10])
			.metaData("application", "message")
			.from(universe.getAddressFrom(this.identity.getPublicKey()))
			.to(universe.getAddressFrom(this.identity.getPublicKey()))
			.build()));
		observer.awaitCount(1, TestWaitStrategy.SLEEP_10MS, 10000);
		observer.assertValue(n -> n.getAtomStatus() == AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
		observer.dispose();
	}

	@Test
	public void testAtomOldTimestamp() {
		TestObserver<AtomStatusEvent> observer = submitAtomAndObserve(ImmutableMap.of(), false, "100", SpunParticle.up(new MessageParticle.MessageParticleBuilder()
			.payload(new byte[10])
			.metaData("application", "message")
			.from(universe.getAddressFrom(this.identity.getPublicKey()))
			.to(universe.getAddressFrom(this.identity.getPublicKey()))
			.build()));
		observer.awaitCount(1, TestWaitStrategy.SLEEP_10MS, 5000);
		observer.assertValue(n -> n.getAtomStatus() == AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
		observer.dispose();
	}

	@Test
	public void testAtomEmpty() {
		TestObserver<?> observer = submitAtom(ImmutableMap.of(), false, System.currentTimeMillis() + "");
		observer.awaitTerminalEvent();
		observer.assertError(RuntimeException.class);
		observer.dispose();
	}

	private TestObserver<AtomStatusEvent> submitAtomAndObserve(
		Map<String, String> metaData,
		boolean addFee,
		String timestamp,
		SpunParticle<?>... spunParticles
	) {
		List<ParticleGroup> particleGroups = new ArrayList<>();
		particleGroups.add(ParticleGroup.of(ImmutableList.copyOf(spunParticles), metaData));

		Map<String, String> atomMetaData = new HashMap<>();
		atomMetaData.putAll(metaData);
		atomMetaData.put("timestamp", timestamp);

		if (addFee) {
			atomMetaData.putAll(feeMapper.map(Atom.create(particleGroups, atomMetaData), universe, this.identity.getPublicKey()).getFirst());
		}

		Atom unsignedAtom = Atom.create(particleGroups, atomMetaData);
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
		Map<String, String> metaData,
		boolean addFee,
		String timestamp,
		SpunParticle<?>... spunParticles
	) {
		List<ParticleGroup> particleGroups = new ArrayList<>();
		particleGroups.add(ParticleGroup.of(ImmutableList.copyOf(spunParticles), metaData));

		Map<String, String> atomMetaData = new HashMap<>();
		atomMetaData.putAll(metaData);
		atomMetaData.put("timestamp", timestamp);

		if (addFee) {
			atomMetaData.putAll(feeMapper.map(Atom.create(particleGroups, atomMetaData), universe, this.identity.getPublicKey()).getFirst());
		}

		Atom unsignedAtom = Atom.create(particleGroups, atomMetaData);
		// Sign and submit
		Atom signedAtom = this.identity.addSignature(unsignedAtom).blockingGet();

		TestObserver<AtomStatusEvent> observer = TestObserver.create(Util.loggingObserver("Submission"));
		this.jsonRpcClient.pushAtom(signedAtom).subscribe(observer);
		return observer;
	}
}
