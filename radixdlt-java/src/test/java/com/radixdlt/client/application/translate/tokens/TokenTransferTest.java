package com.radixdlt.client.application.translate.tokens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.application.identity.UnencryptedData;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.math.BigDecimal;
import org.junit.Test;

public class TokenTransferTest {

	@Test
	public void testNoAttachment() {
		RadixAddress from = mock(RadixAddress.class);
		RadixAddress to = mock(RadixAddress.class);
		TokenClassReference token = mock(TokenClassReference.class);
		TokenTransfer tokenTransfer = new TokenTransfer(from, to, token, BigDecimal.ONE, null, 1L);
		assertThat(tokenTransfer.toString()).isNotNull();
		assertThat(tokenTransfer.getAttachment()).isNotPresent();
		assertThat(tokenTransfer.getAttachmentAsString()).isNotPresent();
	}

	@Test
	public void testWithAttachment() {
		RadixAddress from = mock(RadixAddress.class);
		RadixAddress to = mock(RadixAddress.class);
		TokenClassReference token = mock(TokenClassReference.class);
		UnencryptedData attachment = mock(UnencryptedData.class);
		when(attachment.getData()).thenReturn("Hello".getBytes());
		TokenTransfer tokenTransfer = new TokenTransfer(from, to, token, BigDecimal.ONE, attachment, 1L);
		assertThat(tokenTransfer.toString()).isNotNull();
		assertThat(tokenTransfer.getAttachment()).isPresent();
		assertThat(tokenTransfer.getAttachmentAsString()).get().isEqualTo("Hello");
	}
}