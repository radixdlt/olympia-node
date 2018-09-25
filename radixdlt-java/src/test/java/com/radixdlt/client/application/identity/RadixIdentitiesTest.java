package com.radixdlt.client.application.identity;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.Writer;
import org.junit.Test;

public class RadixIdentitiesTest {

	@Test
	public void newEncryptedIdentityWriterTest() throws Exception {
		Writer writer = mock(Writer.class);
		RadixIdentities.createNewEncryptedIdentity(writer, "");
		verify(writer, times(1)).flush();
	}
}