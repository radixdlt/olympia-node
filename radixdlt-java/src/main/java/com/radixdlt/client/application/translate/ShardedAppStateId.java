package com.radixdlt.client.application.translate;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Objects;

/**
 * Identifier for application state for a particular address.
 */
public final class ShardedAppStateId {
	private final Class<? extends ApplicationState> stateClass;
	private final RadixAddress address;

	private ShardedAppStateId(Class<? extends ApplicationState> stateClass, RadixAddress address) {
		Objects.requireNonNull(stateClass);
		Objects.requireNonNull(address);

		this.stateClass = stateClass;
		this.address = address;
	}

	public static ShardedAppStateId of(Class<? extends ApplicationState> stateClass, RadixAddress address) {
		return new ShardedAppStateId(stateClass, address);
	}

	/**
	 * Retrieves the type of application state needed for this requirement
	 *
	 * @return the type of application state
	 */
	public Class<? extends ApplicationState> stateClass() {
		return this.stateClass;
	}

	/**
	 * Retrieves the shardable address which needs to be queried to construct the application state
	 *
	 * @return the shardable address
	 */
	public RadixAddress address() {
		return this.address;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ShardedAppStateId)) {
			return false;
		}

		ShardedAppStateId r = (ShardedAppStateId) o;
		return r.stateClass.equals(stateClass) && r.address.equals(address);
	}

	@Override
	public int hashCode() {
		return Objects.hash(stateClass, address);
	}

	@Override
	public String toString() {
		return address + "/" + stateClass.getSimpleName();
	}
}
