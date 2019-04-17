package com.radixdlt.client.core.ledger;

import com.radixdlt.client.atommodel.accounts.RadixAddress;

import io.reactivex.Observable;

/**
 * Temporary interface for retrieving atoms at an address from the network.
 * TODO: Refactor this out
 */
public interface AtomPuller {

	/**
	 * Fetches atoms and stores in an Atom Store
	 * FIXME: update interface to better reflect repository pattern
	 *
	 * @param address the address to pull atoms from
	 * @return An Observable corresponding to the atoms fetched and pushed to the store
	 */
	Observable<Object> pull(RadixAddress address);
}
