package com.radixdlt.tempo;

/**
 * An arbitrary store which can be opened, closed and reset.
 */
public interface Store {
	/**
	 * Resets this store and removes all contents.
	 */
	void reset();

	/**
	 * Opens this store and underlying resources.
	 */
	void open();

	/**
	 * Closes this store and underlying resources.
	 */
	void close();
}
