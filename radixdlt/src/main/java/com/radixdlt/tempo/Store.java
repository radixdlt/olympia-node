package com.radixdlt.tempo;

import java.io.Closeable;

/**
 * An arbitrary store which can be opened, closed and reset.
 */
public interface Store extends Closeable {
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
