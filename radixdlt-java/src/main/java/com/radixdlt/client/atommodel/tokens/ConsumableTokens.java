package com.radixdlt.client.atommodel.tokens;

import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.atommodel.Fungible;
import com.radixdlt.client.atommodel.Ownable;
import com.radixdlt.client.core.atoms.particles.Particle;
import java.util.Map;
import org.radix.utils.UInt256;

/**
 * Particle types that makes tokens instances *consumable*
 */
public interface ConsumableTokens extends Fungible, Ownable {
	/**
	 * Get the token type that this Particle type makes consumable
	 * @return The consumable type
	 */
	TokenDefinitionReference getTokenDefinitionReference();

	/**
	 * Get the granularity of the consumable token type
	 * @return The consumable type's granularity
	 */
	UInt256 getGranularity();

	Map<Class<? extends Particle>, TokenPermission> getTokenPermissions();
}
