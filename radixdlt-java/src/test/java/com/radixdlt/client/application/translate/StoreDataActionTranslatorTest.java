package com.radixdlt.client.application.translate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.application.actions.StoreDataAction;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.crypto.Encryptor;
import org.junit.Test;

public class StoreDataActionTranslatorTest {

	@Test
	public void testEncryptorCreation() {
		DataStoreTranslator dataStoreTranslator = DataStoreTranslator.getInstance();
		StoreDataAction storeDataAction = mock(StoreDataAction.class);
		Data data = mock(Data.class);
		Encryptor encryptor = mock(Encryptor.class);
		when(data.getBytes()).thenReturn(new byte[] {});
		when(data.getEncryptor()).thenReturn(encryptor);
		when(storeDataAction.getData()).thenReturn(data);
		when(storeDataAction.getSource()).thenReturn(mock(RadixAddress.class));
		assertThat(dataStoreTranslator.map(storeDataAction)).size().isEqualTo(2);
	}
}