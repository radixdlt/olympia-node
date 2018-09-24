package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.EUID;
import io.reactivex.disposables.Disposable;

public interface AtomPuller {
	Disposable pull(EUID euid);
}
