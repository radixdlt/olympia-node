package com.radixdlt.client.application.translate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.application.actions.StoreData;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.core.crypto.Encryptor;
import org.junit.Test;

public class StoreDataTranslatorTest {

	@Test
	public void testEncryptorCreation() {
		DataStoreTranslator dataStoreTranslator = DataStoreTranslator.getInstance();
		StoreData storeData = mock(StoreData.class);
		Data data = mock(Data.class);
		Encryptor encryptor = mock(Encryptor.class);
		when(data.getBytes()).thenReturn(new byte[] {});
		when(data.getEncryptor()).thenReturn(encryptor);
		when(storeData.getData()).thenReturn(data);
		assertThat(dataStoreTranslator.map(storeData)).size().isEqualTo(2);
	}
}