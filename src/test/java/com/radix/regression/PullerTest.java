package com.radix.regression;

import com.radixdlt.client.core.atoms.particles.RRI;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse.Ledger;
import com.radixdlt.client.core.ledger.AtomObservation;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

public class PullerTest {
	void printAtomObservation(String what, AtomObservation atomObservation, Semaphore s) {
		System.out.println(what + ": " + atomObservation);
		if (atomObservation.isHead() && s != null) {
			s.release();
		}
	}

	void printTokenError(Throwable t) {
		System.out.println("While fetching native token events");
		t.printStackTrace();
	}

	@Test
	public void test() throws InterruptedException {
		RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.LOCALHOST_SINGLENODE, RadixIdentities.createNew());

		RRI rri = api.getNativeTokenRef();

		Ledger l = api.getLedger();

		System.out.println("Pull using atomPuller from native token address");
		Semaphore head1Semaphore = new Semaphore(0);
		Observable<AtomObservation> obs1 = l.getAtomPuller().pull(rri.getAddress());
		Disposable d1 = obs1.subscribe(ao -> printAtomObservation("head1", ao, head1Semaphore), this::printTokenError);

		Assert.assertTrue("Timeout awaiting head1Semaphore", head1Semaphore.tryAcquire(5, TimeUnit.SECONDS));
		d1.dispose();

		TimeUnit.SECONDS.sleep(2);

		System.out.println("Pull using atomPuller from native token address (2)");
		Semaphore head2Semaphore = new Semaphore(0);
		Disposable d2 = l.getAtomPuller().pull(rri.getAddress())
			.subscribe(ao -> printAtomObservation("head2", ao, head2Semaphore), this::printTokenError);

		Assert.assertTrue("Timeout awaiting head2Semaphore", head2Semaphore.tryAcquire(5, TimeUnit.SECONDS));
		d2.dispose();

		System.out.println("Subscribe to atom store for native token address");
		Semaphore head3Semaphore = new Semaphore(0);
		l.getAtomStore().getAtoms(rri.getAddress())
			.subscribe(ao -> printAtomObservation("head3", ao, head3Semaphore), this::printTokenError);

		Assert.assertTrue("Timeout awaiting head3Semaphore", head3Semaphore.tryAcquire(5, TimeUnit.SECONDS));
	}
}
