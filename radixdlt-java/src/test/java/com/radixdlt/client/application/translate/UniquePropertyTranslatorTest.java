package com.radixdlt.client.application.translate;

import static org.mockito.Mockito.mock;

import com.radixdlt.client.core.atoms.AtomBuilder;
import org.junit.Test;

public class UniquePropertyTranslatorTest {
	@Test
	public void nullPropertyTest() {
		UniquePropertyTranslator translator = new UniquePropertyTranslator();
		AtomBuilder atomBuilder = mock(AtomBuilder.class);
		translator.translate(null, atomBuilder);
	}
}