package org.radix.network2.transport;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import nl.jqno.equalsverifier.EqualsVerifier;

public class DynamicTransportMetadataTest {

	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void equalsContract() {
	    EqualsVerifier.forClass(DynamicTransportMetadata.class).verify();
	}

	@Test
	public void testFromMap() {
		Map<String, Supplier<String>> metadata = new HashMap<>();
		metadata.put("a", () -> "b");
		DynamicTransportMetadata dtm = DynamicTransportMetadata.from(metadata);

		assertThat(dtm.get("a"), equalTo("b"));
		assertThat(dtm.get("b"), nullValue());
	}

	@Test
	public void testFromImmutableMap() {
		DynamicTransportMetadata dtm = DynamicTransportMetadata.from(
			ImmutableMap.of("a", () -> "b")
		);

		assertThat(dtm.get("a"), equalTo("b"));
		assertThat(dtm.get("b"), nullValue());
	}

	@Test
	public void testOfTwoArgs() {
		DynamicTransportMetadata dtm = DynamicTransportMetadata.of("a", () -> "b");

		assertThat(dtm.get("a"), equalTo("b"));
		assertThat(dtm.get("b"), nullValue());
	}

	@Test
	public void testOfFourArgs() {
		DynamicTransportMetadata dtm = DynamicTransportMetadata.of("a", () -> "b", "c", () -> "d");

		assertThat(dtm.get("a"), equalTo("b"));
		assertThat(dtm.get("c"), equalTo("d"));
		assertThat(dtm.get("e"), nullValue());
	}

	@Test
	public void testToString() {
		DynamicTransportMetadata dtm = DynamicTransportMetadata.of("a", () -> "b", "c", () -> "d");

		assertThat(dtm.toString(), containsString("a=b"));
		assertThat(dtm.toString(), containsString("c=d"));
	}
}
