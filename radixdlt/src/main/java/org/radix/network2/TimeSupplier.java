package org.radix.network2;

/**
 * Represents a supplier of time in milliseconds since 0:00, January 1,
 * 1970 UTC.
 */
@FunctionalInterface
public interface TimeSupplier {
	/**
     * Returns the current time as the number of milliseconds since
     * 00:00, January 1, 1970 UTC.
     *
     * @return the difference in milliseconds between the current
     *         time and 00:00, January 1, 1970 UTC.
     */
	long currentTime();
}
