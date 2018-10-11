package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.RadixAddress;

import io.reactivex.disposables.Disposable;

public interface AtomPuller {
	Disposable pull(RadixAddress address);
}
