package com.radixdlt.client.wallet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.atoms.Consumable;
import com.radixdlt.client.core.atoms.TransactionAtom;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.identity.RadixIdentity;
import com.radixdlt.client.core.ledger.RadixLedger;
import com.radixdlt.client.core.address.RadixAddress;
import io.reactivex.Observable;
import java.security.interfaces.ECKey;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
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
	public void testWalletCanSendOnLoad() throws Exception {
		ECKeyPair ecKeyPair = mock(ECKeyPair.class);
		ECPublicKey publicKey = mock(ECPublicKey.class);
		RadixUniverse universe = mock(RadixUniverse.class);
		RadixLedger ledger = mock(RadixLedger.class);
		when(universe.getLedger()).thenReturn(ledger);
		RadixAddress address = mock(RadixAddress.class);
		RadixAddress toAddress = mock(RadixAddress.class);
		TransactionAtom transactionAtom = mock(TransactionAtom.class);
		Consumable consumable = mock(Consumable.class);
		com.radixdlt.client.core.atoms.Consumer consumer = mock(com.radixdlt.client.core.atoms.Consumer.class);
		io.reactivex.functions.Consumer<UnsignedAtom> subscriber = mock(io.reactivex.functions.Consumer.class);

		when(publicKey.toECKeyPair()).thenReturn(ecKeyPair);
		when(publicKey.toByteArray()).thenReturn(new byte[33]);
		when(publicKey.length()).thenReturn(33);
		when(address.getPublicKey()).thenReturn(publicKey);
		when(address.ownsKey(any(ECPublicKey.class))).thenReturn(true);
		when(address.ownsKey(any(ECKeyPair.class))).thenReturn(true);
		when(ledger.getMagic()).thenReturn(1);

		when(consumer.quantity()).thenReturn(1L);

		when(consumable.toConsumer()).thenReturn(consumer);
		when(consumable.getDson()).thenReturn(
			new byte[] {0}, new byte[] {1}, new byte[] {2}, new byte[] {3}, new byte[] {4},
			new byte[] {5}, new byte[] {6}, new byte[] {7}, new byte[] {8}, new byte[] {9}
		);
		when(consumable.getAssetId()).thenReturn(Asset.XRD.getId());
		when(consumable.isConsumable()).thenReturn(true);
		when(consumable.getAsConsumable()).thenReturn(consumable);
		when(consumable.isAbstractConsumable()).thenReturn(true);
		when(consumable.getAsAbstractConsumable()).thenReturn(consumable);
		when(transactionAtom.getParticles()).thenReturn(Collections.singletonList(consumable));
		ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
		when(ledger.getAllAtoms(any(), any())).thenReturn(Observable.create(emitter -> {
			IntStream.range(0, 10).forEach(i ->
				scheduledExecutorService.schedule(
					() -> emitter.onNext(transactionAtom),
					i * 100,
					TimeUnit.MILLISECONDS
				)
			);
			scheduledExecutorService.shutdown();
		}));

		RadixWallet wallet = new RadixWallet(universe);
		wallet.createXRDTransaction(10, address, toAddress, null, false, null)
			.subscribe(subscriber, Throwable::printStackTrace);

		scheduledExecutorService.awaitTermination(3, TimeUnit.SECONDS);
		TimeUnit.SECONDS.sleep(3); // Wait for debouncer and POW

		verify(subscriber, times(1)).accept(any());
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
				update -> {},
				throwable -> errorHandler.accept(throwable.getCause())
			);

		verify(errorHandler, times(1)).accept(new InsufficientFundsException(Asset.XRD, 0, 10));
	}
}