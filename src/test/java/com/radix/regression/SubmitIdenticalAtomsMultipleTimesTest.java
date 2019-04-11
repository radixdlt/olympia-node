package com.radix.regression;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.FeeMapper;
import com.radixdlt.client.application.translate.PowFeeMapper;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import com.radixdlt.client.core.pow.ProofOfWorkBuilder;
import io.reactivex.functions.Predicate;
import io.reactivex.observers.TestObserver;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class SubmitIdenticalAtomsMultipleTimesTest {
	private RadixUniverse universe = RadixUniverse.create(Bootstrap.LOCALHOST_SINGLENODE);
	private RadixIdentity identity;
	private FeeMapper feeMapper = new PowFeeMapper(Atom::getHash, new ProofOfWorkBuilder());
	private RadixJsonRpcClient jsonRpcClient;

	@Before
	public void setUp() {
		this.identity = RadixIdentities.createNew();
		Request localhost = new Request.Builder().url("ws://localhost:8080/rpc").build();
		WebSocketClient webSocketClient = new WebSocketClient(listener -> HttpClients.getSslAllTrustingClient().newWebSocket(localhost, listener));
		webSocketClient.connect();
		webSocketClient.getState()
			.filter(WebSocketStatus.CONNECTED::equals)
			.blockingFirst();
		this.jsonRpcClient = new RadixJsonRpcClient(webSocketClient);
	}

	@Test
	public void testSubmitSameAtomTwoTimes() {
		Atom atom = buildAtom(ImmutableMap.of(), true, System.currentTimeMillis() + "", SpunParticle.up(
			new MessageParticle.MessageParticleBuilder()
			.payload(new byte[10])
			.metaData("application", "message")
			.from(universe.getAddressFrom(this.identity.getPublicKey()))
			.to(universe.getAddressFrom(this.identity.getPublicKey()))
			.build()));

		submitAndAwaitResult(atom, state -> state.getState() == RadixJsonRpcClient.NodeAtomSubmissionState.STORED);
		submitAndAwaitResult(atom, state -> state.getState() == RadixJsonRpcClient.NodeAtomSubmissionState.VALIDATION_ERROR);
	}

	private void submitAndAwaitResult(Atom atom, Predicate<RadixJsonRpcClient.NodeAtomSubmissionUpdate> updatePredicate) {
		TestObserver<RadixJsonRpcClient.NodeAtomSubmissionUpdate> observer = submitAtom(atom);
		observer.awaitTerminalEvent(5, TimeUnit.SECONDS);
		observer.assertNoErrors();
		observer.assertComplete();
		observer.assertValueAt(1, updatePredicate);
	}

	private TestObserver<RadixJsonRpcClient.NodeAtomSubmissionUpdate> buildAndSubmitAtom(Map<String, String> metaData, boolean addFee, String timestamp, SpunParticle<?>... spunParticles) {
		Atom signedAtom = buildAtom(metaData, addFee, timestamp, spunParticles);

		return submitAtom(signedAtom);
	}

	private TestObserver<RadixJsonRpcClient.NodeAtomSubmissionUpdate> submitAtom(Atom atom) {
		TestObserver<RadixJsonRpcClient.NodeAtomSubmissionUpdate> observer = TestObserver.create();
		jsonRpcClient.submitAtom(atom)
			.doOnNext(update -> System.out.printf("%d %s %s%n",
				update.getTimestamp(),
				update.getState(),
				Optional.ofNullable(update.getData())
					.map(JsonElement::toString)
					.orElse("<none>")))
			.subscribe(observer);

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
