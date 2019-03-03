package com.radixdlt.client.atommodel.tokens;

import com.radixdlt.client.application.translate.tokens.TokenTypeReference;
import com.radixdlt.client.atommodel.Fungible;
import com.radixdlt.client.atommodel.Ownable;
import com.radixdlt.client.core.atoms.Hashable;
import org.radix.utils.UInt256;

public interface ConsumingTokens extends Fungible, Ownable, Hashable {
	TokenTypeReference getTokenTypeReference();

	UInt256 getGranularity();
}
