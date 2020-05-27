package com.radixdlt.client.examples;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.core.Bootstrap;

public class ValidatorExample {
	public static void main(String[] args) {
		// Create a new public key identity
		final RadixIdentity radixIdentity = RadixIdentities.createNew();

		// Initialize api layer
		RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.LOCALHOST, radixIdentity);

		// register for the first time
		System.out.println("registering " + api.getAddress());
		api.registerValidator(api.getAddress()).blockUntilComplete();
		System.out.println("registered " + api.getAddress());

		// unregister
		System.out.println("unregistering " + api.getAddress());
		api.unregisterValidator(api.getAddress()).blockUntilComplete();
		System.out.println("unregistered " + api.getAddress());

		// and re-register
		System.out.println("registering " + api.getAddress());
		api.registerValidator(api.getAddress()).blockUntilComplete();
		System.out.println("registered " + api.getAddress());
	}
}
