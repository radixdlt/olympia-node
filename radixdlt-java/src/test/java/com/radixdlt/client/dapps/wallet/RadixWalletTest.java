package com.radixdlt.client.dapps.wallet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.assets.Amount;
import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.address.RadixAddress;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import java.math.BigDecimal;
import org.junit.Test;

public class RadixWalletTest {
	@Test
	public void nullTest() {
		RadixApplicationAPI api = mock(RadixApplicationAPI.class);
		RadixWallet radixWallet = new RadixWallet(api);
		assertThatThrownBy(() -> radixWallet.getBalance(null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> radixWallet.getTransactions(null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> radixWallet.send(BigDecimal.ONE, null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> radixWallet.send(BigDecimal.ONE, "hi", null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> radixWallet.sendWhenAvailable(BigDecimal.ONE, null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> radixWallet.sendWhenAvailable(BigDecimal.ONE, null, "hi"))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	public void transferWhenAvailableTest() {
		RadixApplicationAPI api = mock(RadixApplicationAPI.class);
		Result result = mock(Result.class);
		when(result.toCompletable()).thenReturn(Completable.complete());
		when(api.getMyBalance(any())).thenReturn(Observable.just(Amount.of(new BigDecimal("1.0"), Asset.TEST)));
		when(api.sendTokens(any(), any(), any(), any())).thenReturn(result);
		RadixWallet radixWallet = new RadixWallet(api);
		RadixAddress radixAddress = mock(RadixAddress.class);
		TestObserver testObserver = TestObserver.create();
		radixWallet.sendWhenAvailable(new BigDecimal("1.0"), radixAddress).toCompletable().subscribe(testObserver);
		testObserver.assertComplete();
	}
}