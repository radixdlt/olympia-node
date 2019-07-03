package org.radix.api.services;

public interface SingleAtomListener {
	void onStored(boolean first);
	void onError(Throwable e);
}
