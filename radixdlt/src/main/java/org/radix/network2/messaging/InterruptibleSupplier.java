package org.radix.network2.messaging;

@FunctionalInterface
interface InterruptibleSupplier<T> {
	T get() throws InterruptedException;
}
