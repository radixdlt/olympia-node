package com.radixdlt.client.atommodel.tokens;

import com.radixdlt.client.application.translate.tokens.TokenTypeReference;
import com.radixdlt.client.atommodel.Fungible;
import com.radixdlt.client.atommodel.Ownable;

public interface ConsumableTokens extends Fungible, Ownable {
	TokenTypeReference getTokenTypeReference();
}
