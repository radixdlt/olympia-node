package com.radixdlt.client.dapps.wallet;

import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.radixdlt.client.application.RadixApplicationAPI;
import org.junit.Test;

public class RadixWalletTest {
	@Test
	public void nullTest() {
		RadixApplicationAPI api = mock(RadixApplicationAPI.class);
		RadixWallet radixWallet = new RadixWallet(api);
		assertThatThrownBy(() -> radixWallet.getXRDBalance(null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> radixWallet.getXRDTransactions(null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> radixWallet.transferXRD(1, null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> radixWallet.transferXRD(1, null, "hi"))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> radixWallet.transferXRDWhenAvailable(1, null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> radixWallet.transferXRDWhenAvailable(1, null, "hi"))
			.isInstanceOf(NullPointerException.class);
	}
}