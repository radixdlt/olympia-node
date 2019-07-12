package com.radixdlt.atomos;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.Test;

public class RadixAddressTest {
	@Test
	public void when_an_address_is_created_with_same_string__they_should_be_equal_and_have_same_hashcode() {
		RadixAddress address0 = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
		RadixAddress address1 = RadixAddress.from(address0.toString());
		assertThat(address0).isEqualTo(address1);
		assertThat(address0).hasSameHashCodeAs(address1);
	}
}