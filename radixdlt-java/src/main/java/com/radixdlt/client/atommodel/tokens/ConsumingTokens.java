package com.radixdlt.client.atommodel.tokens;

import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.atommodel.Fungible;
import com.radixdlt.client.atommodel.Ownable;
import org.radix.utils.UInt256;

/**
 * Particle types that *consumes* tokens instances
 */
public interface ConsumingTokens extends Fungible, Ownable {
	/**
	 * Get the token type that this Particle type consumes
	 * @return The consumed type
	 */
	TokenDefinitionReference getTokenTypeReference();

	/**
	 * Get the granularity of the consumed token type
	 * @return The consumed type's granularity
	 */
	UInt256 getGranularity();
}
