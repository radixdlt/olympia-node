package com.radixdlt.tempo;

import java.io.Closeable;

/**
 * An arbitrary resource which can be opened, closed and reset.
 */
// TODO figure out better scheme for resource allocation / ownership
public interface Resource extends Closeable {
	/**
	 * Closes any underlying resources.
	 */
	void close();
}
