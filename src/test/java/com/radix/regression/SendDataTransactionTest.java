package com.radix.regression;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ActionExecutionException;
import com.radixdlt.client.application.translate.data.DecryptedMessage;
import com.radixdlt.client.application.translate.data.SendMessageAction;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import io.reactivex.observers.TestObserver;
import java.util.concurrent.TimeUnit;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * RLAU-162
 */
public class SendDataTransactionTest {
	private static RadixApplicationAPI api;

	@BeforeClass
	public static void setup() {
		if (!RadixUniverse.isInstantiated()) {
			RadixUniverse.bootstrap(Bootstrap.BETANET);
		}
		api = RadixApplicationAPI.create(RadixIdentities.createNew());
	}

	@Test
	public void given_an_account_owner_listening_to_own_messages__when_the_owner_sends_a_message_from_another_account_to_itself__then_the_client_should_be_notified_of_error_and_not_receive_any_message() throws Exception {
		// Given account owner listening to own messages
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
}
