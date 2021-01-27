package com.radix.acceptance.balance;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Test;

import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.LocalRadixIdentity;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import io.reactivex.disposables.Disposable;

public class AddressBalance {

	private RadixApplicationAPI api;
	private LocalRadixIdentity identity1;
	private LocalRadixIdentity identity2;
	private RRI tokenRRI;

	@Before
	public void setUp() {
		LocalRadixIdentity ownerIdentity = RadixIdentities.createNew();
		this.identity1 = RadixIdentities.createNew();
		this.identity2 = RadixIdentities.createNew();
		this.api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), ownerIdentity);
		TokenUtilities.requestTokensFor(this.api);
		this.tokenRRI = RRI.of(api.getAddress(), "BALANCETEST");
		this.api.createFixedSupplyToken(tokenRRI, "BALANCETEST", "TEST", new BigDecimal(1000)).blockUntilComplete();
	}

	@Test
	public void testAddressBalance() throws InterruptedException {
		RadixApplicationAPI api1 = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), identity1);
		CountDownLatch countDown = new CountDownLatch(2);
		AtomicLong b1 = new AtomicLong(0);
		RadixAddress address1 = api1.getAddress(identity1.getPublicKey());
		api1.observeBalance(address1, tokenRRI).subscribe(b -> {
			b1.set(b.longValue());
			countDown.countDown();
		});
		AtomicLong b2 = new AtomicLong(0);
		RadixAddress address2 = api1.getAddress(identity2.getPublicKey());
		api1.observeBalance(address2, tokenRRI).subscribe(b -> {
			b2.set(b.longValue());
			countDown.countDown();
		});
		api.sendTokens(tokenRRI, address1, new BigDecimal(10)).blockUntilComplete();
		api.sendTokens(tokenRRI, address2, new BigDecimal(10)).blockUntilComplete();
		Disposable disp1 = api1.pull(address1);
		Disposable disp2 = api1.pull(address2);
		boolean ok = countDown.await(30, TimeUnit.SECONDS);
		if (!ok) {
			fail("Test time out " + countDown.getCount());
		} else {
			assertEquals(10, b1.longValue());
			assertEquals(10, b2.longValue());
			disp1.dispose();
			disp2.dispose();
		}
	}
}
