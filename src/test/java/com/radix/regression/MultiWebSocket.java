package com.radix.regression;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.radix.utils.UInt256;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;

import io.reactivex.disposables.Disposable;
import io.reactivex.observers.TestObserver;

public class MultiWebSocket {
	@BeforeClass
	public static void setup() {
		if (!RadixUniverse.isInstantiated()) {
			RadixUniverse.bootstrap(Bootstrap.BETANET);
		}
	}

	private RadixIdentity identity;
    private RadixApplicationAPI api;

    @Before
    private void before() {
    	this.identity = RadixIdentities.createNew();
    	this.api = RadixApplicationAPI.create(identity);
    }

    @After
    public void after() {
    	this.identity = null;
    	this.api = null;
    }

    @Test
    public void testMintBurn() {
    	Disposable d = api.pull();
    	TestObserver<Object> observer = new TestObserver<>();
    	api.createToken(
    			"TEST",
    			"TEST",
    			"Test token",
    			UInt256.from(2_000_000_000L),
    			UInt256.from(1L),
    			TokenSupplyType.MUTABLE)
    	.toObservable()
    	.doOnNext(System.out::println)
    	.subscribe(observer);
    }

}
