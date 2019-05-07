package com.radix.acceptance.sending_a_data_transaction;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.atomic.AtomicAction;
import com.radixdlt.client.application.translate.data.DecryptedMessage;
import com.radixdlt.client.application.translate.data.SendMessageAction;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomReceivedAction;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.reactivex.observers.BaseTestConsumer.TestWaitStrategy;
import io.reactivex.observers.TestObserver;

import java.util.Arrays;
import java.util.List;

import static com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType.COLLISION;
import static com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType.STORED;
import static com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType.VALIDATION_ERROR;

/**
 * See <a href="https://radixdlt.atlassian.net/browse/RLAU-94">RLAU-94</a>.
 */
public class SendingADataTransaction {
	private static final long TIMEOUT_MS = 10_000L; // Timeout in milliseconds

	private RadixApplicationAPI api;
	private RadixIdentity identity;
	private RadixApplicationAPI otherApi;
	private RadixIdentity otherIdentity;

	private final List<TestObserver<SubmitAtomAction>> observers = Lists.newArrayList();

	@Given("^I have access to a suitable Radix network$")
	public void i_have_access_to_a_suitable_Radix_network() {
		this.identity = RadixIdentities.createNew();
		this.otherIdentity = RadixIdentities.createNew();
		this.api = RadixApplicationAPI.defaultBuilder()
			.bootstrap(Bootstrap.LOCALHOST_SINGLENODE)
			.identity(this.identity)
			.build();
		this.otherApi = RadixApplicationAPI.defaultBuilder()
			.bootstrap(Bootstrap.LOCALHOST_SINGLENODE)
			.identity(this.otherIdentity)
			.build();

		this.observers.clear();
	}

	private void createAtomic(RadixApplicationAPI api, Action... actions) {
		TestObserver<SubmitAtomAction> observer = new TestObserver<>();

		api.execute(new AtomicAction(
			actions
		))
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);

		this.observers.add(observer);
	}

	private void awaitAtomStatus(int atomNumber, SubmitAtomResultActionType... finalStates) {
		ImmutableSet<SubmitAtomResultActionType> finalStatesSet = ImmutableSet.<SubmitAtomResultActionType>builder()
			.addAll(Arrays.asList(finalStates))
			.build();

		this.observers.get(atomNumber - 1)
			.awaitCount(4, TestWaitStrategy.SLEEP_100MS, TIMEOUT_MS)
			.assertSubscribed()
			.assertNoTimeout()
			.assertNoErrors()
			.assertValueAt(0, SubmitAtomRequestAction.class::isInstance)
			.assertValueAt(1, SubmitAtomSendAction.class::isInstance)
			.assertValueAt(2, SubmitAtomReceivedAction.class::isInstance)
			.assertValueAt(3, SubmitAtomResultAction.class::isInstance)
			.assertValueAt(3, i -> finalStatesSet.contains(SubmitAtomResultAction.class.cast(i).getType()));
	}

	@Then("^I can observe the atom being accepted$")
	public void i_can_observe_the_atom_being_accepted() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_accepted(observers.size());
	}

	@Then("^I can observe atom (\\d+) being accepted$")
	public void i_can_observe_atom_being_accepted(int atomNumber) {
		awaitAtomStatus(atomNumber, STORED);
	}

	@Then("^I can observe the atom being rejected with a validation error$")
	public void i_can_observe_the_atom_being_rejected_as_a_validation_error() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_rejected_as_a_validation_error(observers.size());
	}

	@Then("^I can observe atom (\\d+) being rejected with a validation error$")
	public void i_can_observe_atom_being_rejected_as_a_validation_error(int atomNumber) {
		awaitAtomStatus(atomNumber, VALIDATION_ERROR);
	}

	@Then("^I can observe the atom being rejected with an error$")
	public void i_can_observe_atom_being_rejected_with_an_error() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_rejected_with_an_error(observers.size());
	}

	@Then("^I can observe atom (\\d+) being rejected with an error$")
	public void i_can_observe_atom_being_rejected_with_an_error(int atomNumber) {
		awaitAtomStatus(atomNumber, COLLISION, VALIDATION_ERROR);
	}

	@When("^I submit a message with \"([^\"]*)\" to another client claiming to be another client$")
	public void i_submit_a_message_with_to_another_client_claiming_to_be_another_client(String message) {
		createAtomic(this.api, new SendMessageAction(message.getBytes(), this.otherApi.getMyAddress(), this.otherApi.getMyAddress(), false));
	}

	@When("^I submit a message with \"([^\"]*)\" to another client$")
	public void i_submit_a_message_with_to_another_client(String message) {
		createAtomic(this.api, new SendMessageAction(message.getBytes(), this.api.getMyAddress(), this.otherApi.getMyAddress(), false));
	}

	@When("^I submit a message with \"([^\"]*)\" to myself$")
	public void i_submit_a_message_with_to_myself(String message) {
		createAtomic(this.api, new SendMessageAction(message.getBytes(), this.api.getMyAddress(), this.api.getMyAddress(), false));
	}

	@When("^I can observe a message with \"([^\"]*)\"$")
	public void i_can_observe_a_message_with(String message) {
		TestObserver<DecryptedMessage> messageTestObserver = new TestObserver<>();
		this.api.getMessages().subscribe(messageTestObserver);
		messageTestObserver.awaitCount(1);
		messageTestObserver.assertSubscribed();
		messageTestObserver.assertNoErrors();
		messageTestObserver.assertValue(m -> new String(m.getData()).equals(message));
		messageTestObserver.dispose();
	}

	@When("^I can observe a message with \"([^\"]*)\" from myself$")
	public void i_can_observe_a_message_with_from_myself(String message) {
		TestObserver<DecryptedMessage> messageTestObserver = new TestObserver<>();
		this.api.getMessages().subscribe(messageTestObserver);
		messageTestObserver.awaitCount(1);
		messageTestObserver.assertSubscribed();
		messageTestObserver.assertNoErrors();
		messageTestObserver.assertValue(m -> new String(m.getData()).equals(message) && m.getFrom().equals(this.api.getMyAddress()));
		messageTestObserver.dispose();
	}

	@When("^another client can observe a message with \"([^\"]*)\"$")
	public void another_client_can_observe_a_message_with(String message) {
		TestObserver<DecryptedMessage> messageTestObserver = new TestObserver<>();
		this.otherApi.getMessages().subscribe(messageTestObserver);
		messageTestObserver.awaitCount(1);
		messageTestObserver.assertSubscribed();
		messageTestObserver.assertNoErrors();
		messageTestObserver.assertValue(m -> new String(m.getData()).equals(message));
		messageTestObserver.dispose();
	}
}
