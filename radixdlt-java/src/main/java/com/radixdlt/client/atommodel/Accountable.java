package com.radixdlt.client.atommodel;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Set;

/**
 * Temporary interface for representing a particle which gets stored in a set of addresses.
 */
public interface Accountable {
	Set<RadixAddress> getAddresses();
}
