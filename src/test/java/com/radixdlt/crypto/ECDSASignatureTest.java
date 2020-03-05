package com.radixdlt.crypto;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class ECDSASignatureTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(ECDSASignature.class)
				.suppress(Warning.NONFINAL_FIELDS) // serialization prevents us from making `r` and `s` final.
				.withIgnoredFields("version") // only used for serialization
				.verify();
	}
}
