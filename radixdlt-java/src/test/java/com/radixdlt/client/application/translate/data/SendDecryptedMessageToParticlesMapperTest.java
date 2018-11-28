package com.radixdlt.client.application.translate.data;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.crypto.ECKeyPair;
import io.reactivex.observers.TestObserver;
import org.junit.Test;

public class SendDecryptedMessageToParticlesMapperTest {
	@Test
	public void testNoEncryption() {
		SendMessageToParticlesMapper sendMessageToParticlesMapper = new SendMessageToParticlesMapper(() -> mock(ECKeyPair.class));
		SendMessageAction sendMessageAction = mock(SendMessageAction.class);
		when(sendMessageAction.getData()).thenReturn(new byte[] {});
		when(sendMessageAction.getFrom()).thenReturn(mock(RadixAddress.class));
		when(sendMessageAction.getTo()).thenReturn(mock(RadixAddress.class));
		when(sendMessageAction.encrypt()).thenReturn(false);
		TestObserver<SpunParticle> testObserver = TestObserver.create();
		sendMessageToParticlesMapper.map(sendMessageAction).subscribe(testObserver);
		testObserver.assertValueCount(1);
	}
}