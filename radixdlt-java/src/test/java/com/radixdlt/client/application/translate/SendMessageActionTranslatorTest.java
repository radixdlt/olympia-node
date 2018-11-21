package com.radixdlt.client.application.translate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.application.actions.SendMessageAction;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.crypto.Encryptor;
import org.junit.Test;

public class SendMessageActionTranslatorTest {

	@Test
	public void testEncryptorCreation() {
		SendMessageTranslator sendMessageTranslator = SendMessageTranslator.getInstance();
		SendMessageAction sendMessageAction = mock(SendMessageAction.class);
		Data data = mock(Data.class);
		Encryptor encryptor = mock(Encryptor.class);
		when(data.getBytes()).thenReturn(new byte[] {});
		when(data.getEncryptor()).thenReturn(encryptor);
		when(sendMessageAction.getData()).thenReturn(data);
		when(sendMessageAction.getFrom()).thenReturn(mock(RadixAddress.class));
		assertThat(sendMessageTranslator.map(sendMessageAction)).size().isEqualTo(2);
	}
}