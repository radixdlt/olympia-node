package com.radix.regression;

import com.radixdlt.client.application.translate.data.SendMessageAction;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.RadixAddress;

import io.reactivex.disposables.Disposable;
import java.util.stream.Stream;

import org.junit.Test;

import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.RadixApplicationAPIBuilder;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.data.AtomToDecryptedMessageMapper;
import com.radixdlt.client.application.translate.data.DecryptedMessage;
import com.radixdlt.client.application.translate.data.DecryptedMessage.EncryptionState;
import com.radixdlt.client.application.translate.tokens.BurnTokensAction;
import com.radixdlt.client.application.translate.tokens.BurnTokensActionMapper;
import com.radixdlt.client.application.translate.tokens.TokenBalanceReducer;
import com.radixdlt.client.application.translate.data.SendMessageToParticleGroupsMapper;
import com.radixdlt.client.core.RadixUniverse;

import io.reactivex.Completable;
import io.reactivex.observers.TestObserver;

public class SendReceiveEncryptedDataTransactionTest {
	/**
	 * RLAU-90
	 */
	@Test
	public void given_i_own_a_key_pair_associated_with_an_address_and_listening_to_messages__when_i_send_an_endecryptable_message_to_myself__then_i_will_receive_a_message_marked_that_it_is_not_decryptable() {
		// Given I own a key pair associated with an address and listening to messages
		RadixApplicationAPI normalApi = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), RadixIdentities.createNew());
		TestObserver<DecryptedMessage> messageListener = TestObserver.create(Util.loggingObserver("MessageListener"));
		Disposable d = normalApi.pull();

		// When I send a message to myself encrypted with a different key
		SendMessageToParticleGroupsMapper msgMapper = new SendMessageToParticleGroupsMapper(
				ECKeyPair::generateNew,
				sendMsg -> Stream.of(ECKeyPair.generateNew().getPublicKey())
		);
		RadixApplicationAPI sendMessageWithDifferentKeyApi = new RadixApplicationAPIBuilder()
			.defaultFeeMapper()
			.universe(RadixUniverse.create(RadixEnv.getBootstrapConfig()))
			.identity(normalApi.getIdentity())
			.addStatefulParticlesMapper(BurnTokensAction.class, new BurnTokensActionMapper()) // Needed for fees
			.addStatelessParticlesMapper(SendMessageAction.class, msgMapper)
			.addReducer(new TokenBalanceReducer()) // Needed for fees
			.addAtomMapper(new AtomToDecryptedMessageMapper()) // Needed for fees
			.build();
		RadixAddress faucetAddress = TokenUtilities.requestTokensFor(sendMessageWithDifferentKeyApi);

		normalApi.observeMessages()
			.filter(msg -> !faucetAddress.equals(msg.getFrom()))
			.subscribe(messageListener);

		Result msgSendResult = sendMessageWithDifferentKeyApi.sendMessage(new byte[] {0, 1, 2, 3}, true);
		msgSendResult.toObservable().subscribe(Util.loggingObserver("MessageSender"));
		Completable sendMessageStatus = msgSendResult.toCompletable();

		// Then I will receive a message marked that it is not decryptable along with the encrypted data bytes
		sendMessageStatus.blockingAwait();
		messageListener.awaitCount(1)
			.assertValue(msg -> msg.getEncryptionState().equals(EncryptionState.CANNOT_DECRYPT))
			.assertValue(msg -> msg.getTo().equals(normalApi.getAddress()))
			.assertValue(msg -> msg.getFrom().equals(normalApi.getAddress()))
			.assertValue(msg -> msg.getData().length > 0)
			.dispose();
		d.dispose();
	}

}
