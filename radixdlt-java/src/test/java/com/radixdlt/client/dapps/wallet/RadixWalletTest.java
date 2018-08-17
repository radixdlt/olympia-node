package com.radixdlt.client.dapps.wallet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.identity.RadixIdentity;
import com.radixdlt.client.core.ledger.RadixLedger;
import com.radixdlt.client.core.address.RadixAddress;
import io.reactivex.Observable;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class RadixWalletTest {

	@Test
	public void testZeroTransactionWallet() throws Exception {
		RadixUniverse universe = mock(RadixUniverse.class);
		RadixLedger ledger = mock(RadixLedger.class);
		when(universe.getLedger()).thenReturn(ledger);
		RadixAddress address = mock(RadixAddress.class);
		io.reactivex.functions.Consumer<Long> subscriber = mock(io.reactivex.functions.Consumer.class);
		when(ledger.getAllAtoms(any(), any())).thenReturn(Observable.empty());

		RadixWallet wallet = new RadixWallet(universe);
		wallet.getSubUnitBalance(address, Asset.XRD).subscribe(subscriber);
		TimeUnit.SECONDS.sleep(1);
		verify(subscriber, times(1)).accept(0L);
	}

	@Test
	public void createTransactionWithNoFunds() throws Exception {
		RadixUniverse universe = mock(RadixUniverse.class);
		RadixLedger ledger = mock(RadixLedger.class);
		when(universe.getLedger()).thenReturn(ledger);
		RadixAddress address = mock(RadixAddress.class);
		RadixAddress toAddress = mock(RadixAddress.class);
		io.reactivex.functions.Consumer<Throwable> errorHandler = mock(io.reactivex.functions.Consumer.class);
		when(ledger.getAllAtoms(any(), any())).thenReturn(Observable.empty());
		RadixIdentity radixIdentity = mock(RadixIdentity.class);
		when(universe.getAddressFrom(any())).thenReturn(address);

		RadixWallet wallet = new RadixWallet(universe);
		wallet.transferXRD(10, radixIdentity, toAddress)
			.subscribe(
				update -> {
				},
				throwable -> errorHandler.accept(throwable.getCause())
			);

		verify(errorHandler, times(1)).accept(new InsufficientFundsException(Asset.XRD, 0, 10));
	}
}
