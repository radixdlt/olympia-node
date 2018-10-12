package com.radixdlt.client.application.translate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.application.actions.DataStore;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.core.crypto.Encryptor;
import org.junit.Test;

public class DataStoreTranslatorTest {

	@Test
	public void testEncryptorCreation() {
		DataStoreTranslator dataStoreTranslator = DataStoreTranslator.getInstance();
		DataStore dataStore = mock(DataStore.class);
		Data data = mock(Data.class);
		Encryptor encryptor = mock(Encryptor.class);
		when(data.getBytes()).thenReturn(new byte[] {});
		when(data.getEncryptor()).thenReturn(encryptor);
		when(dataStore.getData()).thenReturn(data);
		assertThat(dataStoreTranslator.map(dataStore)).size().isEqualTo(2);
	}
}