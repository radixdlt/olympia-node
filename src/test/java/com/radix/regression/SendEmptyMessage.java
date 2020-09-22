package com.radix.regression;

import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.core.RadixEnv;
import org.junit.Test;

public class SendEmptyMessage {
	private static final byte[] EMPTY_BYTES = new byte[0];

	@Test
	public void sending_empty_message__should_all_be_successful() throws Exception {
		RadixIdentity id = RadixIdentities.createNew();
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), id);
		TokenUtilities.requestTokensFor(api);

		Result result0 = api.sendMessage(EMPTY_BYTES, false);
		result0.toObservable().subscribe(System.out::println);
		result0.blockUntilComplete();
	}
}
