package com.radix.acceptance.unsubscribe_account;

import com.radix.TestEnv;
import com.radix.regression.Util;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.AtomStatusNotification;
import com.radixdlt.client.core.ledger.AtomObservation;
import com.radixdlt.client.core.network.RadixNode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Lists;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.data.SendMessageAction;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.crypto.ECKeyPairGenerator;
import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.jsonrpc.AtomQuery;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;

import cucumber.api.java.After;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.reactivex.observers.TestObserver;

/**
 * See <a href="https://radixdlt.atlassian.net/browse/RLAU-94">RLAU-59</a>.
 */
public class UnsubscribeAccount {
	private WebSocketClient webSocketClient;
	private RadixJsonRpcClient jsonRpcClient;

	private RadixApplicationAPI api;

	private Atom atom;
	private Atom otherAtom;

	private RadixIdentity identity;
	private String uuid;
	private TestObserver<AtomObservation> observer;

	private RadixAddress otherAccount;
	private String otherUuid;
	private TestObserver<AtomObservation> otherObserver;

	@After
	public void after() {
		this.atom = null;

		this.jsonRpcClient = null;

		this.uuid = null;
		this.otherUuid = null;

		this.observer.dispose();
		this.observer = null;

		if (this.otherObserver != null) {
			this.otherObserver.dispose();
			this.otherObserver = null;
		}

		this.webSocketClient.close();
		this.webSocketClient.getState()
			.doOnNext(System.out::println)
			.filter(WebSocketStatus.DISCONNECTED::equals)
			.firstOrError()
			.timeout(10, TimeUnit.SECONDS)
			.blockingGet();
		this.webSocketClient = null;
	}

	@Given("^a node connected websocket client who has an atom subscription to an empty account$")
	public void a_websocket_client_who_has_an_atom_subscription_to_an_empty_account() throws Throwable {
		setupWebSocket();

		this.jsonRpcClient = new RadixJsonRpcClient(this.webSocketClient);
		this.uuid = UUID.randomUUID().toString();
		this.observer = TestObserver.create();
		this.jsonRpcClient.observeAtoms(this.uuid)
			.subscribe(this.observer);

		TestObserver<Object> completionObserver = TestObserver.create();
		this.jsonRpcClient.sendAtomsSubscribe(this.uuid, new AtomQuery(this.api.getMyAddress()))
			.subscribe(completionObserver);
		completionObserver.awaitTerminalEvent(3, TimeUnit.SECONDS);
		completionObserver.assertComplete();

		this.observer.awaitCount(1);
		this.observer.assertValue(AtomObservation::isHead);
	}

	@Given("^the websocket client has an atom subscription to another account$")
	public void the_websocket_client_has_an_atom_subscription_to_another_account() throws Throwable {
		this.jsonRpcClient = new RadixJsonRpcClient(this.webSocketClient);

		this.otherAccount = api.getAddressFromKey(ECKeyPairGenerator.newInstance().generateKeyPair().getPublicKey());

		this.otherUuid = UUID.randomUUID().toString();
		this.otherObserver = TestObserver.create(Util.loggingObserver("Other observer"));
		this.jsonRpcClient.observeAtoms(this.otherUuid)
			.subscribe(this.otherObserver);

		TestObserver<Object> completionObserver = TestObserver.create();
		this.jsonRpcClient.sendAtomsSubscribe(this.otherUuid, new AtomQuery(this.otherAccount))
			.subscribe(completionObserver);
		completionObserver.awaitTerminalEvent(3, TimeUnit.SECONDS);
		completionObserver.assertComplete();

		this.otherObserver.awaitCount(1);
		this.otherObserver.assertValue(AtomObservation::isHead);
	}

	@When("the client sends a cancel subscription request to his account$")
	public void the_client_sends_a_cancel_subscription_request_to_his_account() throws Throwable {
		TestObserver<Object> completionObserver = TestObserver.create();
		this.jsonRpcClient.cancelAtomsSubscribe(this.uuid).subscribe(completionObserver);
		completionObserver.awaitTerminalEvent(3, TimeUnit.SECONDS);
		completionObserver.assertComplete();
	}

	@When("the client sends a subscribe request to his account in another subscription$")
	public void the_client_sends_a_subscribe_request_to_his_account_in_another_subscription() throws Throwable {
		this.otherUuid = UUID.randomUUID().toString();

		this.otherObserver = TestObserver.create();
		this.jsonRpcClient.observeAtoms(this.otherUuid)
			.subscribe(this.otherObserver);

		TestObserver<Object> completionObserver = TestObserver.create();
		this.jsonRpcClient.sendAtomsSubscribe(this.otherUuid, new AtomQuery(this.api.getMyAddress()))
			.subscribe(completionObserver);
		completionObserver.awaitTerminalEvent(3, TimeUnit.SECONDS);
		completionObserver.assertComplete();
	}

	@When("the client sends a message to himself$")
	public void the_client_sends_a_message_to_himself() throws Throwable {
		TestObserver<AtomStatusNotification> atomSubmission = TestObserver.create(Util.loggingObserver("Atom Submission"));
		final String subscriberId = UUID.randomUUID().toString();
		this.jsonRpcClient.observeAtomStatusNotifications(subscriberId).subscribe(atomSubmission);

		this.atom = this.api.buildAtom(new SendMessageAction(new byte[]{1}, this.api.getMyAddress(), this.api.getMyAddress(), false))
			.flatMap(this.identity::sign)
			.blockingGet();

		this.jsonRpcClient.observeAtomStatusNotifications(subscriberId).subscribe(atomSubmission);
		this.jsonRpcClient.sendGetAtomStatusNotifications(subscriberId, this.atom.getAid()).blockingAwait();
		this.jsonRpcClient.pushAtom(this.atom).blockingAwait();

		atomSubmission.awaitCount(1);
		atomSubmission.assertValue(n -> n.getAtomStatus() == AtomStatus.STORED);
		atomSubmission.dispose();
	}

	@When("the client sends another message to himself$")
	public void the_client_sends_another_message_to_himself() throws Throwable {
		TestObserver<AtomStatusNotification> atomSubmission = TestObserver.create(Util.loggingObserver("Atom Submission"));
		final String subscriberId = UUID.randomUUID().toString();
		this.jsonRpcClient.observeAtomStatusNotifications(subscriberId).subscribe(atomSubmission);


		this.otherAtom = this.api.buildAtom(new SendMessageAction(new byte[]{2}, this.api.getMyAddress(), this.api.getMyAddress(), false))
			.flatMap(this.identity::sign)
			.blockingGet();

		this.jsonRpcClient.sendGetAtomStatusNotifications(subscriberId, this.otherAtom.getAid()).blockingAwait();
		this.jsonRpcClient.pushAtom(this.otherAtom).blockingAwait();

		atomSubmission.awaitCount(1);
		atomSubmission.assertValue(n -> n.getAtomStatus() == AtomStatus.STORED);
		atomSubmission.dispose();
	}

	@When("the client sends a message to the other account$")
	public void the_client_sends_a_message_to_the_other_account() throws Throwable {
		TestObserver<AtomStatusNotification> atomSubmission = TestObserver.create(Util.loggingObserver("Atom Submission"));
		final String subscriberId = UUID.randomUUID().toString();
		this.jsonRpcClient.observeAtomStatusNotifications(subscriberId).subscribe(atomSubmission);

		this.atom = this.api.buildAtom(new SendMessageAction(new byte[]{3}, this.api.getMyAddress(), this.otherAccount, false))
			.flatMap(this.identity::sign)
			.blockingGet();

		this.jsonRpcClient.sendGetAtomStatusNotifications(subscriberId, this.atom.getAid()).blockingAwait();
		this.jsonRpcClient.pushAtom(this.atom).blockingAwait();

		atomSubmission.awaitCount(1);
		atomSubmission.assertValue(n -> n.getAtomStatus() == AtomStatus.STORED);
		atomSubmission.dispose();
	}

	@Then("^the client should not receive any new atom notifications in his account$")
	public void the_client_should_not_receive_any_new_atom_notifications_in_his_account() throws Throwable {
		observer.await(5, TimeUnit.SECONDS);
		observer.assertValue(AtomObservation::isHead);
		observer.assertNotComplete();
	}

	@Then("^the client should receive the sent atom in the other subscription$")
	public void the_client_should_receive_the_sent_atom_in_the_other_subscription() throws Throwable {
		otherObserver.awaitCount(2);
		otherObserver.assertValueAt(0, AtomObservation::isHead);
		otherObserver.assertValueAt(1, AtomObservation::isStore);
		otherObserver.assertValueAt(1, o -> o.getAtom().equals(this.atom));
		otherObserver.assertNotComplete();
	}

	@Then("the client should receive both atom messages in the other subscription$")
	public void the_client_should_receive_both_atom_messages_in_the_other_subscription() throws Throwable {
		// Some special magic here, as the atoms are not necessarily sent in the same order
		List<Atom> atoms = Lists.newArrayList(this.atom, this.otherAtom);
		otherObserver.awaitCount(3);
		otherObserver.assertValueAt(0, AtomObservation::isStore);
		otherObserver.assertValueAt(0, o -> atoms.remove(o.getAtom()));
		otherObserver.assertValueAt(1, AtomObservation::isStore);
		otherObserver.assertValueAt(1, o -> atoms.remove(o.getAtom()));
		otherObserver.assertNotComplete();
	}

	private void setupWebSocket() {
		this.identity = RadixIdentities.createNew();
		this.api = RadixApplicationAPI.create(TestEnv.getBootstrapConfig(), this.identity);
		this.api.discoverNodes();
		RadixNode node = this.api.getNetworkState()
			.filter(state -> !state.getNodes().isEmpty())
			.map(state -> state.getNodes().keySet().iterator().next())
			.blockingFirst();

		this.webSocketClient = new WebSocketClient(listener ->
			HttpClients.getSslAllTrustingClient().newWebSocket(node.getWebSocketEndpoint(), listener)
		);
		this.webSocketClient.connect();
		this.webSocketClient.getState()
			.filter(WebSocketStatus.CONNECTED::equals)
			.blockingFirst();
	}
}
