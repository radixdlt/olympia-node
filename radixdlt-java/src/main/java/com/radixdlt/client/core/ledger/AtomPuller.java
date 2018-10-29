package com.radixdlt.client.core.ledger;

import com.radixdlt.client.atommodel.accounts.RadixAddress;

import io.reactivex.disposables.Disposable;

public interface AtomPuller {
	Disposable pull(RadixAddress address);
}
