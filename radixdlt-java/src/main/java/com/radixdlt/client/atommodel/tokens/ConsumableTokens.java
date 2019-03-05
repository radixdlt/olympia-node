package com.radixdlt.client.atommodel.tokens;

import com.radixdlt.client.application.translate.tokens.TokenTypeReference;
import com.radixdlt.client.atommodel.Fungible;
import com.radixdlt.client.atommodel.Ownable;
import org.radix.utils.UInt256;

/**
 * Particle types that makes tokens instances *consumable*
 */
public interface ConsumableTokens extends Fungible, Ownable {
	/**
	 * Get the token type that this Particle type makes consumable
	 * @return THe consumable type
	 */
	TokenTypeReference getTokenTypeReference();

	/**
	 * Get the granularity of the consumable token type
	 * @return The consumable type's granularity
	 */
	UInt256 getGranularity();
}
