package com.radix.regression;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.RadixApplicationAPIBuilder;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.PowFeeMapper;
import com.radixdlt.client.application.translate.data.DecryptedMessage;
import com.radixdlt.client.application.translate.data.DecryptedMessage.EncryptionState;
import com.radixdlt.client.application.translate.data.SendMessageToParticlesMapper;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.crypto.ECKeyPairGenerator;
import com.radixdlt.client.core.pow.ProofOfWorkBuilder;
import io.reactivex.Completable;
import io.reactivex.observers.TestObserver;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.BeforeClass;
import org.junit.Test;

public class SendReceiveEncryptedDataTransactionTest {
	@BeforeClass
	public static void setup() {
		if (!RadixUniverse.isInstantiated()) {
			RadixUniverse.bootstrap(Bootstrap.BETANET);
		}
	}

	/**
	 * RLAU-90
	 */
	@Test
	public void given_i_own_a_key_pair_associated_with_an_address_and_listening_to_messages__when_i_send_an_endecryptable_message_to_myself__then_i_will_receive_a_message_marked_that_it_is_not_decryptable() {
		// Given I own a key pair associated with an address and listening to messages
		RadixApplicationAPI normalApi = RadixApplicationAPI.create(RadixIdentities.createNew());
		TestObserver<DecryptedMessage> messageListener = TestObserver.create(Util.loggingObserver("MessageListener"));
		normalApi.getMessages().subscribe(messageListener);

		// When I send a message to myself encrypted with a different key
		ECKeyPairGenerator ecKeyPairGenerator = ECKeyPairGenerator.newInstance();
		SendMessageToParticlesMapper msgMapper = new SendMessageToParticlesMapper(
			ecKeyPairGenerator::generateKeyPair,
			sendMsg -> Stream.of(ECKeyPairGenerator.newInstance().generateKeyPair().getPublicKey())
		);
		RadixApplicationAPI sendMessageWithDifferentKeyApi = new RadixApplicationAPIBuilder()
			.defaultFeeMapper()
			.identity(normalApi.getMyIdentity())
			.addStatelessParticlesMapper(msgMapper)
			.build();
		Result msgSendResult = sendMessageWithDifferentKeyApi.sendMessage(new byte[] {0, 1, 2, 3}, true);
		msgSendResult.toObservable().subscribe(Util.loggingObserver("MessageSender"));
		Completable sendMessageStatus = msgSendResult.toCompletable();

		// Then I will receive a message marked that it is not decryptable along with the encrypted data bytes
		sendMessageStatus.blockingAwait();
		messageListener.awaitCount(1)
			.assertValue(msg -> msg.getEncryptionState().equals(EncryptionState.CANNOT_DECRYPT))
			.assertValue(msg -> msg.getTo().equals(normalApi.getMyAddress()))
			.assertValue(msg -> msg.getFrom().equals(normalApi.getMyAddress()))
			.assertValue(msg -> msg.getData().length > 0)
			.dispose();
	}

}
