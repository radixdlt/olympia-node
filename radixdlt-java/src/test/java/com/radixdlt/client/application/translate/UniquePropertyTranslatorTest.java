package com.radixdlt.client.application.translate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class UniquePropertyTranslatorTest {
	@Test
	public void nullPropertyTest() {
		UniquePropertyTranslator translator = new UniquePropertyTranslator();
		assertThat(translator.map(null)).isEmpty();
	}
}