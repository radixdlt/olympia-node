package com.radix.acceptance.unsubscribe_account;

import com.radix.regression.Util;
import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI.Transaction;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.AtomStatusEvent;
import com.radixdlt.client.core.ledger.AtomObservation;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.Notification;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NotificationType;
import com.radixdlt.crypto.ECKeyPair;
import io.reactivex.Observable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Lists;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.data.SendMessageAction;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.jsonrpc.AtomQuery;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
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
	public void a_websocket_client_who_has_an_atom_subscription_to_an_empty_account() {
		setupWebSocket();

		this.jsonRpcClient = new RadixJsonRpcClient(this.webSocketClient);
		this.uuid = UUID.randomUUID().toString();
		this.observer = TestObserver.create();
		this.jsonRpcClient.observeAtoms(this.uuid)
			.flatMap(n -> {
				if (n.getType() == NotificationType.START) {
					return this.jsonRpcClient.sendAtomsSubscribe(this.uuid, new AtomQuery(this.api.getAddress())).andThen(Observable.empty());
				} else {
					return Observable.just(n.getEvent());
				}
			})
			.filter(TokenUtilities::isNotFaucetAtomObservation)
			.subscribe(this.observer);

		this.observer.awaitCount(1);
		this.observer.assertValue(AtomObservation::isHead);
	}

	@Given("^the websocket client has an atom subscription to another account$")
	public void the_websocket_client_has_an_atom_subscription_to_another_account() {
		this.jsonRpcClient = new RadixJsonRpcClient(this.webSocketClient);

		this.otherAccount = api.getAddress(ECKeyPair.generateNew().getPublicKey());

		this.otherUuid = UUID.randomUUID().toString();
		this.otherObserver = TestObserver.create(Util.loggingObserver("Other observer"));
		this.jsonRpcClient.observeAtoms(this.otherUuid)
			.flatMap(n -> {
				if (n.getType() == NotificationType.START) {
					return this.jsonRpcClient.sendAtomsSubscribe(this.otherUuid, new AtomQuery(this.api.getAddress())).andThen(Observable.empty());
				} else {
					return Observable.just(n.getEvent());
				}
			})
			.filter(TokenUtilities::isNotFaucetAtomObservation)
			.subscribe(this.otherObserver);

		this.otherObserver.awaitCount(1);
		this.otherObserver.assertValue(AtomObservation::isHead);
	}

	@When("the client sends a cancel subscription request to his account$")
	public void the_client_sends_a_cancel_subscription_request_to_his_account() {
		TestObserver<Object> completionObserver = TestObserver.create();
		this.jsonRpcClient.cancelAtomsSubscribe(this.uuid).subscribe(completionObserver);
		completionObserver.awaitTerminalEvent(3, TimeUnit.SECONDS);
		completionObserver.assertComplete();
	}

	@When("the client sends a subscribe request to his account in another subscription$")
	public void the_client_sends_a_subscribe_request_to_his_account_in_another_subscription() {
		this.otherUuid = UUID.randomUUID().toString();

		this.otherObserver = TestObserver.create();
		this.jsonRpcClient.observeAtoms(this.otherUuid)
			.flatMap(n -> {
				if (n.getType() == NotificationType.START) {
					return this.jsonRpcClient.sendAtomsSubscribe(this.otherUuid, new AtomQuery(this.api.getAddress())).andThen(Observable.empty());
				} else {
					return Observable.just(n.getEvent());
				}
			})
			.filter(TokenUtilities::isNotFaucetAtomObservation)
			.subscribe(this.otherObserver);
	}

	@When("the client sends a message to himself$")
	public void the_client_sends_a_message_to_himself() {
		Transaction transaction = this.api.createTransaction();
		transaction.stage(SendMessageAction.create(this.api.getAddress(), this.api.getAddress(), new byte[]{1}, false));
		Atom unsignedAtom = transaction.buildAtom();
		this.atom = this.identity.addSignature(unsignedAtom).blockingGet();

		TestObserver<AtomStatusEvent> atomSubmission = TestObserver.create(Util.loggingObserver("Atom Submission"));
		final String subscriberId = UUID.randomUUID().toString();
		this.jsonRpcClient.observeAtomStatusNotifications(subscriberId)
			.doOnNext(n -> {
				if (n.getType() == NotificationType.START) {
					this.jsonRpcClient.sendGetAtomStatusNotifications(subscriberId, this.atom.getAid()).blockingAwait();
					this.jsonRpcClient.pushAtom(this.atom).blockingAwait();
				}
			})
			.filter(n -> n.getType().equals(NotificationType.EVENT))
			.map(Notification::getEvent)
			.subscribe(atomSubmission);

		atomSubmission.awaitCount(1);
		atomSubmission.assertValue(n -> n.getAtomStatus() == AtomStatus.STORED);
		atomSubmission.dispose();
	}

	@When("the client sends another message to himself$")
	public void the_client_sends_another_message_to_himself() throws InterruptedException {
		Thread.sleep(2000); // Might be a slight delay between websocket and API, so let API catch up so fees don't fail
		Transaction transaction = this.api.createTransaction();
		transaction.stage(SendMessageAction.create(this.api.getAddress(), this.api.getAddress(), new byte[]{1, 2}, false));
		Atom unsignedAtom = transaction.buildAtom();
		this.otherAtom = this.identity.addSignature(unsignedAtom).blockingGet();

		TestObserver<AtomStatusEvent> atomSubmission = TestObserver.create(Util.loggingObserver("Atom Submission"));
		final String subscriberId = UUID.randomUUID().toString();
		this.jsonRpcClient.observeAtomStatusNotifications(subscriberId)
			.doOnNext(n -> {
				if (n.getType() == NotificationType.START) {
					this.jsonRpcClient.sendGetAtomStatusNotifications(subscriberId, this.otherAtom.getAid()).blockingAwait();
					this.jsonRpcClient.pushAtom(this.otherAtom).blockingAwait();
				}
			})
			.filter(n -> n.getType().equals(NotificationType.EVENT))
			.map(Notification::getEvent)
			.subscribe(atomSubmission);

		atomSubmission.awaitCount(1);
		atomSubmission.assertValue(n -> n.getAtomStatus() == AtomStatus.STORED);
		atomSubmission.dispose();
	}

	@When("the client sends a message to the other account$")
	public void the_client_sends_a_message_to_the_other_account() {
		Transaction transaction = this.api.createTransaction();
		transaction.stage(SendMessageAction.create(this.api.getAddress(), this.otherAccount, new byte[]{3}, false));
		Atom unsignedAtom = transaction.buildAtom();
		this.atom = this.identity.addSignature(unsignedAtom).blockingGet();

		TestObserver<AtomStatusEvent> atomSubmission = TestObserver.create(Util.loggingObserver("Atom Submission"));
		final String subscriberId = UUID.randomUUID().toString();
		this.jsonRpcClient.observeAtomStatusNotifications(subscriberId)
			.doOnNext(n -> {
				if (n.getType() == NotificationType.START) {
					this.jsonRpcClient.sendGetAtomStatusNotifications(subscriberId, this.atom.getAid()).blockingAwait();
					this.jsonRpcClient.pushAtom(this.atom).blockingAwait();
				}
			})
			.filter(n -> n.getType().equals(NotificationType.EVENT))
			.map(Notification::getEvent)
			.subscribe(atomSubmission);

		atomSubmission.awaitCount(1);
		atomSubmission.assertValue(n -> n.getAtomStatus() == AtomStatus.STORED);
		atomSubmission.dispose();
	}

	@Then("^the client should not receive any new atom notifications in his account$")
	public void the_client_should_not_receive_any_new_atom_notifications_in_his_account() throws InterruptedException {
		observer.await(5, TimeUnit.SECONDS);
		observer.assertValue(AtomObservation::isHead);
		observer.assertNotComplete();
	}

	@Then("^the client should receive the sent atom in the other subscription$")
	public void the_client_should_receive_the_sent_atom_in_the_other_subscription() {
		otherObserver.awaitCount(2);
		otherObserver.assertValueAt(0, AtomObservation::isHead);
		otherObserver.assertValueAt(1, AtomObservation::isStore);
		otherObserver.assertValueAt(1, o -> o.getAtom().equals(this.atom));
		otherObserver.assertNotComplete();
	}

	@Then("the client should receive both atom messages in the other subscription$")
	public void the_client_should_receive_both_atom_messages_in_the_other_subscription() {
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
		this.api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), this.identity);
		TokenUtilities.requestTokensFor(this.api);
		api.pull(); // Make sure token balances updated for fees
		this.api.discoverNodes();
		RadixNode node = this.api.getNetworkState()
			.filter(state -> !state.getNodes().isEmpty())
			.map(state -> state.getNodes().iterator().next())
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
