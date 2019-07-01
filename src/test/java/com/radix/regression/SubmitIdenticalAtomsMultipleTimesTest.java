package com.radix.regression;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radix.TestEnv;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.FeeMapper;
import com.radixdlt.client.application.translate.PowFeeMapper;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.AtomStatusNotification;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SubmitIdenticalAtomsMultipleTimesTest {
	private RadixUniverse universe = RadixUniverse.create(TestEnv.getBootstrapConfig());
	private RadixIdentity identity;
	private FeeMapper feeMapper = new PowFeeMapper(Atom::getHash, new ProofOfWorkBuilder());
	private RadixJsonRpcClient jsonRpcClient;

	@Before
	public void setUp() {
		this.identity = RadixIdentities.createNew();
		RadixApplicationAPI api = RadixApplicationAPI.create(TestEnv.getBootstrapConfig(), this.identity);
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
	public void testSubmitSameAtomTwoTimesConcurrently() {
		submitSameAtomXTimesConcurrently(2);
	}

	@Test
	public void testSubmitSameAtomManyTimesConcurrently() {
		submitSameAtomXTimesConcurrently(16);
	}

	public void submitSameAtomXTimesConcurrently(int times) {
		Atom atom = buildAtom(ImmutableMap.of(), true, System.currentTimeMillis() + "", SpunParticle.up(
			new MessageParticle.MessageParticleBuilder()
			.payload(new byte[10])
			.metaData("application", "message")
			.from(universe.getAddressFrom(this.identity.getPublicKey()))
			.to(universe.getAddressFrom(this.identity.getPublicKey()))
			.build()));

		TestObserver<AtomStatusNotification> observer = TestObserver.create(Util.loggingObserver("Atom Status"));
		final String subscriberId = UUID.randomUUID().toString();
		this.jsonRpcClient.observeAtomStatusNotifications(subscriberId).subscribe(observer);
		this.jsonRpcClient.sendGetAtomStatusNotifications(subscriberId, atom.getAid()).blockingAwait();

		List<TestObserver> submissions =
			IntStream.range(0, times)
			.mapToObj(x -> submitAtom(atom))
			.collect(Collectors.toList()); // collect to make sure all get submitted


		submissions.forEach(submission -> {
			submission.awaitTerminalEvent();
			submission.assertNoErrors();
			submission.assertComplete();
		});

		observer.awaitCount(1);
		observer.assertValue(notification -> notification.getAtomStatus() == AtomStatus.STORED);
		observer.dispose();
	}

	private TestObserver submitAtom(Atom atom) {
		TestObserver observer = TestObserver.create();

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
			atomMetaData.putAll(feeMapper.map(new Atom(particleGroups, atomMetaData), universe, this.identity.getPublicKey()).getFirst());
		}

		UnsignedAtom unsignedAtom = new UnsignedAtom(new Atom(particleGroups, atomMetaData));
		// Sign and submit
		return this.identity.sign(unsignedAtom).blockingGet();
	}
}
