package com.radixdlt.tempo;

import java.io.Closeable;

/**
 * An arbitrary resource which can be opened, closed and reset.
 */
// TODO figure out better scheme for resource allocation / ownership
public interface Resource extends Closeable {
	/**
	 * Resets this resource and removes all contents.
	 */
	void reset();

	// TODO remove explicit open, make open on instantiation
	/**
	 * Opens any underlying resources.
	 */
	void open();

	/**
	 * Closes any underlying resources.
	 */
	void close();
}
