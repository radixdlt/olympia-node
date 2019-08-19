package org.radix.network2.transport;

import org.junit.Test;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class TransportInfoTest {

	@Test
	public void equalsContract() {
	    EqualsVerifier.forClass(TransportInfo.class).suppress(Warning.NONFINAL_FIELDS).verify();
	}

	@Test
	public void testOf() {
		StaticTransportMetadata stm = StaticTransportMetadata.of("a", "b");
		TransportInfo info = TransportInfo.of("TEST", stm);

		assertThat(info.name(), equalTo("TEST"));
		assertThat(info.metadata(), equalTo(stm));
	}

	@Test
	public void testToString() {
		StaticTransportMetadata stm = StaticTransportMetadata.of("a", "b");
		TransportInfo info = TransportInfo.of("TEST", stm);

		assertThat(info.toString(), containsString("TEST"));
		assertThat(info.toString(), containsString("a=b"));
	}
}
