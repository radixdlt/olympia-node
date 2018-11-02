package com.radixdlt.client.atommodel.quarks;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.List;

public class AccountableQuarkTest {
	@Test
	public void testNullConstruction() {
		Assertions.assertThatThrownBy(() -> new AccountableQuark((RadixAddress) null));
		Assertions.assertThatThrownBy(() -> new AccountableQuark((List<RadixAddress>) null));
	}
}