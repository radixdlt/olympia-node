package org.radix.api.services;

import com.radixdlt.common.AID;

public interface SingleAtomListener {
	void onStored(boolean first);
	void onError(AID atomId, Throwable e);
}
