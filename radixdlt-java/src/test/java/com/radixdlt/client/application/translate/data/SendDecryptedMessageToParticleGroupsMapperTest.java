package com.radixdlt.client.application.translate.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.crypto.ECKeyPair;
import org.junit.Test;
import com.radixdlt.identifiers.EUID;

public class SendDecryptedMessageToParticleGroupsMapperTest {
	@Test
	public void testNoEncryption() {
		RadixAddress address = mock(RadixAddress.class);
		when(address.getUID()).thenReturn(mock(EUID.class), mock(EUID.class));

		SendMessageToParticleGroupsMapper sendMessageToParticleGroupsMapper =
			new SendMessageToParticleGroupsMapper(() -> mock(ECKeyPair.class));
		SendMessageAction sendMessageAction = mock(SendMessageAction.class);
		when(sendMessageAction.getData()).thenReturn(new byte[] {});
		when(sendMessageAction.getFrom()).thenReturn(address);
		when(sendMessageAction.getTo()).thenReturn(address);
		when(sendMessageAction.encrypt()).thenReturn(false);
		assertThat(sendMessageToParticleGroupsMapper.mapToParticleGroups(sendMessageAction)).hasSize(1);
	}
}