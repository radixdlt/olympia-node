package org.radix.utils;

@FunctionalInterface
public interface InterruptibleSupplier<T> {
	T get() throws InterruptedException;
}
