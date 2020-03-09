package com.radix.regression;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
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
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SubmitIdenticalAtomsMultipleTimesTest {
	private RadixUniverse universe = RadixUniverse.create(RadixEnv.getBootstrapConfig());
	private RadixIdentity identity;
	private FeeMapper feeMapper = new PowFeeMapper(Atom::getHash, new ProofOfWorkBuilder());
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

	public void submitConflictingAtomXTimesConcurrently(int times) {
		long startTime = System.currentTimeMillis();
		long nonce = System.nanoTime(); // Same (sort of) random nonce for each message particle
		List<TestObserver<AtomStatusEvent>> observers = Lists.newArrayList();
		List<TestObserver<AtomStatusEvent>> submissions = Lists.newArrayList();

		for (int i = 0; i < times; ++i) {
			Atom atom = buildAtom(ImmutableMap.of(), true, startTime + "", SpunParticle.up(
				new MessageParticle.MessageParticleBuilder()
					.payload(new byte[10])
					.metaData("application", "message")
					.nonce(nonce)
					.from(universe.getAddressFrom(this.identity.getPublicKey()))
					.to(universe.getAddressFrom(this.identity.getPublicKey()))
					.build()
			));
			startTime += 1; // slightly different atom next time

			TestObserver<AtomStatusEvent> observer = TestObserver.create(Util.loggingObserver("Atom Status " + i));
			final String subscriberId = UUID.randomUUID().toString();
			this.jsonRpcClient.observeAtomStatusNotifications(subscriberId)
				.doOnNext(n -> {
					if (n.getType() == NotificationType.START) {
						this.jsonRpcClient.sendGetAtomStatusNotifications(subscriberId, atom.getAid()).blockingAwait();
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
		for (int i = 0; i < times; ++i) {
			TestObserver<AtomStatusEvent> observer = observers.get(i);
			observer.awaitCount(1);
			observer.assertValueCount(1);
			AtomStatus expectedStatus = (i == 0) ? AtomStatus.STORED : AtomStatus.CONFLICT_LOSER;
			observer.assertValueAt(0, notification -> notification.getAtomStatus() == expectedStatus);
			observer.dispose();
		}
	}

	private TestObserver<AtomStatusEvent> submitAtom(Atom atom) {
		TestObserver<AtomStatusEvent> observer = TestObserver.create();

		this.jsonRpcClient.pushAtom(atom).subscribe(observer);

		return observer;
	}

	private Atom buildAtom(Map<String, String> metaData, boolean addFee, String timestamp, SpunParticle<?>... spunParticles) {
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
		return this.identity.addSignature(unsignedAtom).blockingGet();
	}
}
