package com.radixdlt.client.application.translate;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import io.reactivex.Observable;
import java.util.Objects;

/**
 * Maps a high level application action to lower level spun particles used
 * to construct an atom given a context requirement which this interface describes
 * via requiredContext().
 */
public interface ContextRequiredActionToParticlesMapper {
	class RequiredShardContext {
		private final Class<? extends ApplicationState> storeClass;
		private final RadixAddress address;

		public RequiredShardContext(Class<? extends ApplicationState> storeClass, RadixAddress address) {
			Objects.requireNonNull(storeClass);
			Objects.requireNonNull(address);

			this.storeClass = storeClass;
			this.address = address;
		}

		/**
		 * Retrieves the type of application state needed for this requirement
		 *
		 * @return the type of application state
		 */
		public Class<? extends ApplicationState> storeClass() {
			return storeClass;
		}

		/**
		 * Retrieves the shardable address which needs to be queried to construct the application state
		 *
		 * @return the shardable address
		 */
		public RadixAddress address() {
			return address;
		}
	}

	/**
	 * Retrieves the necessary application state to be used in creating new particles
	 * given a high level action. The returned Observable describes the shardable and
	 * the type of state needed to create particles.
	 *
	 * @param action the action to get the required context about
	 * @return observable of required contexts required to create spun particles for the action
	 */
	Observable<RequiredShardContext> requiredContext(Action action);

	/**
	 * Creates new spun particles to be added to an atom given a high level
	 * action and application state
	 *
	 * @param action the action to map
	 * @param store application state ordered by
	 * @return observable of spun particles created given an action
	 */
	Observable<SpunParticle> mapToParticles(Action action, Observable<Observable<? extends ApplicationState>> store);
}
