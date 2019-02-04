package com.radixdlt.client.application.translate.data;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.crypto.ECKeyPair;
import io.reactivex.observers.TestObserver;
import org.junit.Test;

public class SendDecryptedMessageToParticleGroupsMapperTest {
	@Test
	public void testNoEncryption() {
		SendMessageToParticleGroupsMapper sendMessageToParticleGroupsMapper =
			new SendMessageToParticleGroupsMapper(() -> mock(ECKeyPair.class));
		SendMessageAction sendMessageAction = mock(SendMessageAction.class);
		when(sendMessageAction.getData()).thenReturn(new byte[] {});
		when(sendMessageAction.getFrom()).thenReturn(mock(RadixAddress.class));
		when(sendMessageAction.getTo()).thenReturn(mock(RadixAddress.class));
		when(sendMessageAction.encrypt()).thenReturn(false);
		TestObserver<ParticleGroup> testObserver = TestObserver.create();
		sendMessageToParticleGroupsMapper.mapToParticleGroups(sendMessageAction).subscribe(testObserver);
		testObserver.assertValueCount(1);
	}
}