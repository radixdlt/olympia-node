package com.radixdlt.client.application.translate.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.crypto.ECKeyPair;
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
		assertThat(sendMessageToParticleGroupsMapper.mapToParticleGroups(sendMessageAction)).hasSize(1);
	}
}