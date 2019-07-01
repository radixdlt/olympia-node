package com.radix.regression;

import com.radix.TestEnv;
import io.reactivex.disposables.Disposable;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ActionExecutionException;
import com.radixdlt.client.application.translate.data.DecryptedMessage;
import com.radixdlt.client.application.translate.data.DecryptedMessage.EncryptionState;
import com.radixdlt.client.application.translate.data.SendMessageAction;
import com.radixdlt.client.atommodel.accounts.RadixAddress;

import io.reactivex.Completable;
import io.reactivex.observers.TestObserver;

/**
 * RLAU-162, RLAU-88, RLAU-89
 */
public class SendReceiveDataTransactionTest {
	@Test
	public void given_an_account_owner_listening_to_own_messages__when_owner_sends_a_message_from_another_account_to_itself__then_the_client_should_be_notified_of_error_and_not_receive_any_message() throws Exception {

		// Given account owner listening to own messages
		RadixApplicationAPI api = RadixApplicationAPI.create(TestEnv.getBootstrapConfig(), RadixIdentities.createNew());
		TestObserver<DecryptedMessage> messageListener = TestObserver.create(Util.loggingObserver("MessageListener"));
		api.getMessages().subscribe(messageListener);

		// When owner sends message from another account
		RadixAddress sourceAddress = api.getAddressFromKey(RadixIdentities.createNew().getPublicKey());
		Action sendMessageAction = new SendMessageAction(new byte[] {0}, sourceAddress, api.getMyAddress(), false);
		Completable sendMessageStatus = api.execute(sendMessageAction).toCompletable();

		// Then client should be notified of error
		TestObserver<Object> submissionObserver = TestObserver.create(Util.loggingObserver("Submission"));
		sendMessageStatus.subscribe(submissionObserver);
		submissionObserver.awaitTerminalEvent();
		submissionObserver.assertError(ActionExecutionException.class);

		// And not receive any messages
		messageListener.await(5, TimeUnit.SECONDS);
		messageListener
			.assertNoErrors()
			.assertEmpty()
			.dispose();
	}

	@Test
	public void given_two_account_owners_listening_to_own_messages__when_one_sends_a_message_to_the_other__then_both_should_receive_message() {

		// Given two account owners listening to own messages
		TestObserver<DecryptedMessage> messageListener1 = new TestObserver<>(Util.loggingObserver("MessageListener1"));
		TestObserver<DecryptedMessage> messageListener2 = new TestObserver<>(Util.loggingObserver("MessageListener2"));
		RadixApplicationAPI api1 = RadixApplicationAPI.create(TestEnv.getBootstrapConfig(), RadixIdentities.createNew());
		RadixApplicationAPI api2 = RadixApplicationAPI.create(TestEnv.getBootstrapConfig(), RadixIdentities.createNew());
		api1.getMessages().subscribe(messageListener1);
		api2.getMessages().subscribe(messageListener2);
		Disposable d1 = api1.pull();
		Disposable d2 = api2.pull();

		// When one sends a message to the other
		byte[] message = new byte[] {1, 2, 3, 4};
		Completable sendMessageStatus = api1.sendMessage(message, false, api2.getMyAddress()).toCompletable();

		// Then both owners should receive the message
		sendMessageStatus.blockingAwait();
		messageListener1.awaitCount(1)
			.assertValueAt(0, msg -> Arrays.equals(message, msg.getData()))
			.assertValueAt(0, msg -> msg.getFrom().equals(api1.getMyAddress()))
			.assertValueAt(0, msg -> msg.getTo().equals(api2.getMyAddress()))
			.assertValueAt(0, msg -> msg.getEncryptionState().equals(EncryptionState.NOT_ENCRYPTED))
			.dispose();
		messageListener2.awaitCount(1)
			.assertValueAt(0, msg -> Arrays.equals(message, msg.getData()))
			.assertValueAt(0, msg -> msg.getFrom().equals(api1.getMyAddress()))
			.assertValueAt(0, msg -> msg.getTo().equals(api2.getMyAddress()))
			.assertValueAt(0, msg -> msg.getEncryptionState().equals(EncryptionState.NOT_ENCRYPTED))
			.dispose();
		d1.dispose();
		d2.dispose();
	}

	@Test
	public void given_an_account_owner_listening_to_own_messages__when_owner_sends_message_to_itself__then_owner_should_receive_message() {

		// Given an account owner listening to own messages
		TestObserver<DecryptedMessage> messageListener = new TestObserver<>(Util.loggingObserver("MessageListener"));
		RadixApplicationAPI api = RadixApplicationAPI.create(TestEnv.getBootstrapConfig(), RadixIdentities.createNew());
		api.getMessages().subscribe(messageListener);

		// When owner sends message to himself
		byte[] message = new byte[] {1, 2, 3, 4};
		Completable sendMessageStatus = api.sendMessage(message, false).toCompletable();

		// Then owner should receive the message
		sendMessageStatus.blockingAwait();
		messageListener.awaitCount(1)
			.assertValueAt(0, msg -> Arrays.equals(message, msg.getData()))
			.assertValueAt(0, msg -> msg.getFrom().equals(api.getMyAddress()))
			.assertValueAt(0, msg -> msg.getTo().equals(api.getMyAddress()))
			.assertValueAt(0, msg -> msg.getEncryptionState().equals(EncryptionState.NOT_ENCRYPTED))
			.dispose();
	}

	@Test
	public void given_a_client_listening_to_messages_in_another_account__when_other_account_sends_message_to_itself__then_client_should_receive_message() {

		// Given a client listening to messages in another account
		TestObserver<DecryptedMessage> clientListener = new TestObserver<>(Util.loggingObserver("MessageListener"));
		RadixApplicationAPI clientApi = RadixApplicationAPI.create(TestEnv.getBootstrapConfig(), RadixIdentities.createNew());
		RadixApplicationAPI otherAccount = RadixApplicationAPI.create(TestEnv.getBootstrapConfig(), RadixIdentities.createNew());
		clientApi.getMessages(otherAccount.getMyAddress()).subscribe(clientListener);
		Disposable d = clientApi.pull(otherAccount.getMyAddress());

		// When the other account sends message to itself
		byte[] message = new byte[] {1, 2, 3, 4};
		Completable sendMessageStatus = otherAccount.sendMessage(message, false).toCompletable();

		// Then client should receive the message
		sendMessageStatus.blockingAwait();
		clientListener.awaitCount(1)
			.assertValueAt(0, msg -> Arrays.equals(message, msg.getData()))
			.assertValueAt(0, msg -> msg.getFrom().equals(otherAccount.getMyAddress()))
			.assertValueAt(0, msg -> msg.getTo().equals(otherAccount.getMyAddress()))
			.assertValueAt(0, msg -> msg.getEncryptionState().equals(EncryptionState.NOT_ENCRYPTED))
			.dispose();

		d.dispose();
	}
}
