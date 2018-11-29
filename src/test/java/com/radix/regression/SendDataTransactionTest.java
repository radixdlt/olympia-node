package com.radix.regression;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ActionExecutionException;
import com.radixdlt.client.application.translate.data.DecryptedMessage;
import com.radixdlt.client.application.translate.data.DecryptedMessage.EncryptionState;
import com.radixdlt.client.application.translate.data.SendMessageAction;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import io.reactivex.Completable;
import io.reactivex.observers.TestObserver;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * RLAU-162
 */
public class SendDataTransactionTest {

	@BeforeClass
	public static void setup() {
		if (!RadixUniverse.isInstantiated()) {
			RadixUniverse.bootstrap(Bootstrap.BETANET);
		}
	}

	@Test
	public void given_an_account_owner_listening_to_own_messages__when_owner_sends_a_message_from_another_account_to_itself__then_the_client_should_be_notified_of_error_and_not_receive_any_message() throws Exception {

		// Given account owner listening to own messages
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixIdentities.createNew());
		TestObserver<DecryptedMessage> messageListener = TestObserver.create();
		api.getMessages().subscribe(messageListener);

		// When owner sends message from another account
		RadixAddress sourceAddress = RadixUniverse.getInstance().getAddressFrom(RadixIdentities.createNew().getPublicKey());
		Action sendMessageAction = new SendMessageAction(new byte[] {0}, sourceAddress, api.getMyAddress(), false);
		TestObserver<AtomSubmissionUpdate> submissionStatus = TestObserver.create();
		api.execute(sendMessageAction).toCompletable()
			.subscribe(submissionStatus);

		// Then client should be notified of error
		submissionStatus.awaitTerminalEvent();
		submissionStatus.assertError(ActionExecutionException.class);

		// And not receive any messages
		messageListener.await(5, TimeUnit.SECONDS);
		messageListener.assertNoErrors();
		messageListener.assertEmpty();
	}

	@Test
	public void given_two_account_owners_listening_to_own_messages__when_one_sends_a_message_to_the_other__then_both_should_receive_message() {

		// Given two account owners listening to own messages
		TestObserver<DecryptedMessage> messageListener1 = new TestObserver<>();
		TestObserver<DecryptedMessage> messageListener2 = new TestObserver<>();
		RadixApplicationAPI api1 = RadixApplicationAPI.create(RadixIdentities.createNew());
		RadixApplicationAPI api2 = RadixApplicationAPI.create(RadixIdentities.createNew());
		api1.getMessages().subscribe(messageListener1);
		api2.getMessages().subscribe(messageListener2);

		byte[] message = new byte[] {1, 2, 3, 4};

		// When one sends a message to the other
		Completable sendMessageStatus = api1.sendMessage(message, false, api2.getMyAddress()).toCompletable();
		sendMessageStatus.blockingAwait();

		// Then both owners should receive the message
		messageListener1.awaitCount(1)
			.assertValueAt(0, msg -> Arrays.equals(message, msg.getData()))
			.assertValueAt(0, msg -> msg.getFrom().equals(api1.getMyAddress()))
			.assertValueAt(0, msg -> msg.getTo().equals(api2.getMyAddress()))
			.assertValueAt(0, msg -> msg.getEncryptionState().equals(EncryptionState.NOT_ENCRYPTED));
		messageListener2.awaitCount(1)
			.assertValueAt(0, msg -> Arrays.equals(message, msg.getData()))
			.assertValueAt(0, msg -> msg.getFrom().equals(api1.getMyAddress()))
			.assertValueAt(0, msg -> msg.getTo().equals(api2.getMyAddress()))
			.assertValueAt(0, msg -> msg.getEncryptionState().equals(EncryptionState.NOT_ENCRYPTED));
	}

	@Test
	public void given_an_account_owner_listening_to_own_messages__when_owner_sends_message_to_itself__then_owner_should_receive_message() {

		// Given an account owner listening to own messages
		TestObserver<DecryptedMessage> messageListener = new TestObserver<>();
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixIdentities.createNew());
		api.getMessages().subscribe(messageListener);

		byte[] message = new byte[] {1, 2, 3, 4};

		// When owner sends message to himself
		Completable sendMessageStatus = api.sendMessage(message, false).toCompletable();
		sendMessageStatus.blockingAwait();

		// Then owner should receive the message
		messageListener.awaitCount(1)
			.assertValueAt(0, msg -> Arrays.equals(message, msg.getData()))
			.assertValueAt(0, msg -> msg.getFrom().equals(api.getMyAddress()))
			.assertValueAt(0, msg -> msg.getTo().equals(api.getMyAddress()))
			.assertValueAt(0, msg -> msg.getEncryptionState().equals(EncryptionState.NOT_ENCRYPTED));
	}
}
