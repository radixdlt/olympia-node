package com.radix.regression;

import org.junit.Test;

import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.identifiers.RRI;
import java.math.BigDecimal;

public class CreateTokenWithoutDescriptionTest {

	@Test
	public void createMultiIssuanceTokenWithoutDescription() {
		RadixIdentity testIdentity = RadixIdentities.createNew();
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), testIdentity);
		TokenUtilities.requestTokensFor(api);
		RRI tokenRRI1 = RRI.of(api.getAddress(), "TESTTOKEN1");
		api.createMultiIssuanceToken(tokenRRI1, "TESTTOKEN1").blockUntilComplete();
		RRI tokenRRI2 = RRI.of(api.getAddress(), "TESTTOKEN2");
		api.createMultiIssuanceToken(tokenRRI2, "TESTTOKEN2", null).blockUntilComplete();
	}

	@Test
	public void createFixedIssuanceTokenWithoutDescription() {
		RadixIdentity testIdentity = RadixIdentities.createNew();
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), testIdentity);
		TokenUtilities.requestTokensFor(api);
		RRI tokenRRI1 = RRI.of(api.getAddress(), "TESTTOKEN3XX");
		api.createFixedSupplyToken(tokenRRI1, "TESTTOKEN3XX", null, BigDecimal.ONE).blockUntilComplete();
	}
}
