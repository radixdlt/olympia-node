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

import com.google.common.collect.ImmutableSet;
import com.radix.test.utils.TokenUtilities;
import com.radixdlt.atom.Atom;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.atom.AtomBuilder;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.AtomStatusEvent;
import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import com.radixdlt.identifiers.RadixAddress;

import io.reactivex.observers.BaseTestConsumer.TestWaitStrategy;
import io.reactivex.observers.TestObserver;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.UUID;

public class ValidatorRegistrationTest {
	private RadixUniverse universe = RadixUniverse.create(RadixEnv.getBootstrapConfig());
	private RadixIdentity identity;
	private RadixAddress address;
	private RadixJsonRpcClient jsonRpcClient;
	private WebSocketClient webSocketClient;

	@Before
	public void setUp() {
		this.identity = RadixIdentities.createNew();
		this.address = universe.getAddressFrom(this.identity.getPublicKey());
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), this.identity);
		api.discoverNodes();
		RadixNode node = api.getNetworkState()
			.filter(state -> !state.getNodes().isEmpty())
			.map(state -> state.getNodes().iterator().next())
			.blockingFirst();

		TokenUtilities.requestTokensFor(api);

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
	@Ignore("Ignoring until api is fixed.")
	public void when_registering_unregistering_and_reregistering_validator__then_validator_is_registererd() {
		// create a new public key identity
		final RadixIdentity radixIdentity = RadixIdentities.createNew();

		// initialize api layer
		RadixApplicationAPI api1 = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), radixIdentity);
		TokenUtilities.requestTokensFor(api1);

		// register for the first time
		ImmutableSet<RadixAddress> allowedDelegators = ImmutableSet.of(api1.getAddress());
		String url = "https://www.radixdlt.com";
		api1.registerValidator(api1.getAddress(), allowedDelegators, url).blockUntilComplete();

		// check the validator state was stored properly
		RadixApplicationAPI api2 = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), radixIdentity);
		api2.pullOnce(api1.getAddress()).blockingAwait();
		RegisteredValidatorParticle storedParticle = api2.getAtomStore().getUpParticles(api1.getAddress(), null)
			.filter(RegisteredValidatorParticle.class::isInstance)
			.map(RegisteredValidatorParticle.class::cast)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("no RegisteredValidatorParticle found"));
		Assertions.assertThat(storedParticle.getUrl()).isEqualTo(url);
		Assertions.assertThat(storedParticle.getAllowedDelegators()).isEqualTo(allowedDelegators);

		// unregister
		api1.unregisterValidator(api1.getAddress()).blockUntilComplete();

		// and re-register
		api1.registerValidator(api1.getAddress(), ImmutableSet.of()).blockUntilComplete();
	}

	@Test
	public void when_registering_twice__then_second_registration_fails() {
		TestObserver<AtomStatusEvent> observer = submitAtom(
			Atom.newBuilder()
				.addParticleGroup(ParticleGroup.builder()
					.virtualSpinDown(new UnregisteredValidatorParticle(address, 0))
					.spinDown(new RegisteredValidatorParticle(address, 1))
					.spinDown(new RegisteredValidatorParticle(address, 2))
					.build())
		);

		observer.awaitCount(1, TestWaitStrategy.SLEEP_10MS, 10000);
		observer.assertValue(n -> n.getAtomStatus() == AtomStatus.CONFLICT_LOSER);
		observer.dispose();
	}

	@Test
	public void when_unregistering_twice__then_second_registration_fails() {
		TestObserver<AtomStatusEvent> observer = submitAtom(
			Atom.newBuilder()
				.addParticleGroup(ParticleGroup.builder()
					.virtualSpinDown(new UnregisteredValidatorParticle(address, 0))
					.spinDown(new UnregisteredValidatorParticle(address, 1))
					.build())
		);

		observer.awaitCount(1, TestWaitStrategy.SLEEP_10MS, 10000);
		observer.assertValue(n -> n.getAtomStatus() == AtomStatus.CONFLICT_LOSER);
		observer.dispose();
	}

	private TestObserver<AtomStatusEvent> submitAtom(AtomBuilder atomBuilder) {
		return submitAtom(true, atomBuilder);
	}

	private TestObserver<AtomStatusEvent> submitAtom(
		boolean addFee,
		AtomBuilder atomBuilder
	) {
		String message = null;
		if (addFee) {
			// Warning: fake fee
			message = "magic:0xdeadbeef";
		}

		atomBuilder.message(message);
		// Sign and submit
		var signedAtom = this.identity.addSignature(atomBuilder).blockingGet().buildAtom();

		TestObserver<AtomStatusEvent> observer = TestObserver.create(Util.loggingObserver("Submission"));

		final String subscriberId = UUID.randomUUID().toString();
		this.jsonRpcClient.observeAtomStatusNotifications(subscriberId)
			.doOnNext(n -> {
				if (n.getType() == RadixJsonRpcClient.NotificationType.START) {
					this.jsonRpcClient.sendGetAtomStatusNotifications(subscriberId, signedAtom.getAID()).blockingAwait();
					this.jsonRpcClient.pushAtom(signedAtom).blockingAwait();
				}
			})
			.filter(n -> n.getType().equals(RadixJsonRpcClient.NotificationType.EVENT))
			.map(RadixJsonRpcClient.Notification::getEvent)
			.subscribe(observer);

		return observer;
	}
}
