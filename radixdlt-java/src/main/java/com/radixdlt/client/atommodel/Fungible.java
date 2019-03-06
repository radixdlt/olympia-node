package com.radixdlt.client.atommodel;

import org.radix.utils.UInt256;

/**
 * Temporary interface for representing an amount of interchangeable Particles.
 */
public interface Fungible {
	UInt256 getAmount();

	long getPlanck();

	long getNonce();
}
