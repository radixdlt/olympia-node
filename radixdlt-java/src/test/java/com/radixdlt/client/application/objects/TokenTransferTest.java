package com.radixdlt.client.application.objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.address.RadixAddress;
import org.junit.Test;

public class TokenTransferTest {

	@Test
	public void testNoAttachment() {
		RadixAddress from = mock(RadixAddress.class);
		RadixAddress to = mock(RadixAddress.class);
		Asset token = mock(Asset.class);
		when(token.getSubUnits()).thenReturn(1);
		TokenTransfer tokenTransfer = new TokenTransfer(from, to, token, 1, null, 1L);
		assertThat(tokenTransfer.toString()).isNotNull();
		assertThat(tokenTransfer.getAttachment()).isNotPresent();
		assertThat(tokenTransfer.getAttachmentAsString()).isNotPresent();
	}

	@Test
	public void testWithAttachment() {
		RadixAddress from = mock(RadixAddress.class);
		RadixAddress to = mock(RadixAddress.class);
		Asset token = mock(Asset.class);
		when(token.getSubUnits()).thenReturn(1);
		UnencryptedData attachment = mock(UnencryptedData.class);
		when(attachment.getData()).thenReturn("Hello".getBytes());
		TokenTransfer tokenTransfer = new TokenTransfer(from, to, token, 1, attachment, 1L);
		assertThat(tokenTransfer.toString()).isNotNull();
		assertThat(tokenTransfer.getAttachment()).isPresent();
		assertThat(tokenTransfer.getAttachmentAsString()).get().isEqualTo("Hello");
	}
}