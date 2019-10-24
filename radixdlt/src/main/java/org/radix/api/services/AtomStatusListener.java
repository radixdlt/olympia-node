package org.radix.api.services;

/**
 * Interface for listening to the status of an Atom.
 * TODO: cleanup method signatures
 */
public interface AtomStatusListener {
	void onStored();
	void onError(Throwable e);
}
