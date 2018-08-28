package com.radixdlt.client.application.translate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.client.application.actions.DataStore;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.crypto.Encryptor;
import io.reactivex.observers.TestObserver;
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
		AtomBuilder atomBuilder = mock(AtomBuilder.class);

		TestObserver testObserver = TestObserver.create();
		dataStoreTranslator.translate(dataStore, atomBuilder).subscribe(testObserver);
		testObserver.assertNoErrors();
		testObserver.assertComplete();
		verify(atomBuilder, times(1)).setEncryptorParticle(any());

	}
}