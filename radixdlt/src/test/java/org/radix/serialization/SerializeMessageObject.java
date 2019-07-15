package org.radix.serialization;

import java.util.function.Supplier;

/**
 * Raft of tests for serialization of objects.
 * <p>
 * This class extends {@link SerializeObject} to set up databases
 * required by most classes that extend {@link org.radix.network.messaging.Message}.
 *
 * @param <T> The type under test.
 */

public abstract class SerializeMessageObject<T> extends SerializeObject<T> {
	protected SerializeMessageObject(Class<T> cls, Supplier<T> factory) {
		super(cls, factory);
	}
}
