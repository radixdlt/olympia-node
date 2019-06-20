package com.radixdlt.client.application.translate.tokens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.RRI;
import java.math.BigDecimal;
import org.junit.Test;
import org.radix.utils.RadixConstants;

public class TokenTransferTest {

	@Test
	public void testNoAttachment() {
		RadixAddress from = mock(RadixAddress.class);
		RadixAddress to = mock(RadixAddress.class);
		RRI token = mock(RRI.class);
		TokenTransfer tokenTransfer = new TokenTransfer(from, to, token, BigDecimal.ONE, null, 1L);
		assertThat(tokenTransfer.toString()).isNotNull();
		assertThat(tokenTransfer.getAttachment()).isNotPresent();
		assertThat(tokenTransfer.getAttachmentAsString()).isNotPresent();
	}

	@Test
	public void testWithAttachment() {
		RadixAddress from = mock(RadixAddress.class);
		RadixAddress to = mock(RadixAddress.class);
		RRI token = mock(RRI.class);
		byte[] attachment = "Hello".getBytes(RadixConstants.STANDARD_CHARSET);
		TokenTransfer tokenTransfer = new TokenTransfer(from, to, token, BigDecimal.ONE, attachment, 1L);
		assertThat(tokenTransfer.toString()).isNotNull();
		assertThat(tokenTransfer.getAttachment()).isPresent();
		assertThat(tokenTransfer.getAttachmentAsString()).get().isEqualTo("Hello");
	}
}