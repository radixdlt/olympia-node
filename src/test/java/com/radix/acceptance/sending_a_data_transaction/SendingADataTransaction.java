package com.radix.acceptance.sending_a_data_transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Transaction;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.data.DecryptedMessage;
import com.radixdlt.client.application.translate.data.SendMessageAction;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomReceivedAction;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.reactivex.observers.BaseTestConsumer.TestWaitStrategy;
import io.reactivex.observers.TestObserver;

import java.util.Arrays;
import java.util.List;

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
		this.api.pull();
		this.otherApi = RadixApplicationAPI.defaultBuilder()
			.bootstrap(Bootstrap.LOCALHOST_SINGLENODE)
			.identity(this.otherIdentity)
			.build();
		this.otherApi.pull();

		this.observers.clear();
	}

	private void createAtomic(RadixApplicationAPI api, Action... actions) {
		TestObserver<SubmitAtomAction> observer = new TestObserver<>();

		Transaction transaction = api.createTransaction();
		for (Action action : actions) {
			transaction.execute(action);
		}
		transaction.commit()
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);

		this.observers.add(observer);
	}

	private void awaitAtomStatus(int atomNumber, AtomStatus... finalStates) {
		ImmutableSet<AtomStatus> finalStatesSet = ImmutableSet.<AtomStatus>builder()
			.addAll(Arrays.asList(finalStates))
			.build();

		TestObserver<SubmitAtomAction> testObserver = this.observers.get(atomNumber - 1);
		testObserver.awaitTerminalEvent();
		testObserver.assertNoErrors();
		testObserver.assertNoTimeout();
		List<SubmitAtomAction> events = testObserver.values();
		assertThat(events).extracting(o -> o.getClass().toString())
			.startsWith(
				SubmitAtomRequestAction.class.toString(),
				SubmitAtomSendAction.class.toString()
			);
		assertThat(events).last()
			.isInstanceOf(SubmitAtomStatusAction.class)
			.<AtomStatus>extracting(o -> SubmitAtomStatusAction.class.cast(o).getStatusNotification().getAtomStatus())
			.isIn(finalStatesSet);
	}

	@Then("^I can observe the atom being accepted$")
	public void i_can_observe_the_atom_being_accepted() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_accepted(observers.size());
	}

	@Then("^I can observe atom (\\d+) being accepted$")
	public void i_can_observe_atom_being_accepted(int atomNumber) {
		awaitAtomStatus(atomNumber, AtomStatus.STORED);
	}

	@Then("^I can observe the atom being rejected with a validation error$")
	public void i_can_observe_the_atom_being_rejected_as_a_validation_error() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_rejected_as_a_validation_error(observers.size());
	}

	@Then("^I can observe atom (\\d+) being rejected with a validation error$")
	public void i_can_observe_atom_being_rejected_as_a_validation_error(int atomNumber) {
		awaitAtomStatus(atomNumber, AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
	}

	@Then("^I can observe the atom being rejected with an error$")
	public void i_can_observe_atom_being_rejected_with_an_error() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_rejected_with_an_error(observers.size());
	}

	@Then("^I can observe atom (\\d+) being rejected with an error$")
	public void i_can_observe_atom_being_rejected_with_an_error(int atomNumber) {
		awaitAtomStatus(atomNumber, AtomStatus.EVICTED_CONFLICT_LOSER, AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
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
