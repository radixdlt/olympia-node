package org.radix.network2.transport;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import nl.jqno.equalsverifier.EqualsVerifier;

public class StaticTransportMetadataTest {

	@Test
	public void equalsContract() {
	    EqualsVerifier.forClass(StaticTransportMetadata.class).verify();
	}

	@Test
	public void testEmpty() {
		StaticTransportMetadata stm = StaticTransportMetadata.empty();

		assertThat(stm.toString(), containsString("{}")); // The empty map
	}

	@Test
	public void testFromMap() {
		Map<String, String> metadata = new HashMap<>();
		metadata.put("a", "b");
		StaticTransportMetadata stm = StaticTransportMetadata.from(metadata);

		assertThat(stm.get("a"), equalTo("b"));
		assertThat(stm.get("b"), nullValue());
	}

	@Test
	public void testFromImmutableMap() {
		StaticTransportMetadata stm = StaticTransportMetadata.from(
			ImmutableMap.of("a", "b")
		);

		assertThat(stm.get("a"), equalTo("b"));
		assertThat(stm.get("b"), nullValue());
	}

	@Test
	public void testOfTwoArgs() {
		StaticTransportMetadata stm = StaticTransportMetadata.of("a", "b");

		assertThat(stm.get("a"), equalTo("b"));
		assertThat(stm.get("b"), nullValue());
	}

	@Test
	public void testOfFourArgs() {
		StaticTransportMetadata stm = StaticTransportMetadata.of("a", "b", "c", "d");

		assertThat(stm.get("a"), equalTo("b"));
		assertThat(stm.get("c"), equalTo("d"));
		assertThat(stm.get("e"), nullValue());
	}

	@Test
	public void testToString() {
		StaticTransportMetadata stm = StaticTransportMetadata.of("a", "b", "c", "d");

		assertThat(stm.toString(), containsString("a=b"));
		assertThat(stm.toString(), containsString("c=d"));
	}
}
