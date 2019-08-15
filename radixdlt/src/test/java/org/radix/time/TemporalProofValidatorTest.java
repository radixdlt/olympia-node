package org.radix.time;

import com.google.common.collect.ImmutableMap;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.radixdlt.common.EUID;
import com.radixdlt.common.AID;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECSignature;
import com.radixdlt.crypto.Hash;
import com.radixdlt.crypto.CryptoException;
import org.radix.exceptions.ValidationException;
import org.radix.modules.Modules;
import org.radix.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import org.radix.validation.ValidationHandler;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

public class TemporalProofValidatorTest {

	@Before
	public void setUp() {
		Universe universe = mock(Universe.class);
		when(universe.getGenesis()).thenReturn(Collections.emptyList());

		RuntimeProperties runtimeProperties = mock(RuntimeProperties.class);
		when(runtimeProperties.get(eq("debug.nopow"), any())).thenReturn(false);

		NtpService ntpService = mock(NtpService.class);

		Modules.put(Universe.class, universe);
		Modules.put(RuntimeProperties.class, runtimeProperties);
		Modules.put(NtpService.class, ntpService);
		Modules.put(Serialization.class, Serialization.getDefault());

		ValidationHandler validationHandler = mock(ValidationHandler.class);
		Modules.put(ValidationHandler.class, validationHandler);
	}

	@After
	public void cleanup() {
		Modules.remove(Universe.class);
		Modules.remove(RuntimeProperties.class);
		Modules.remove(NtpService.class);
		Modules.remove(Serialization.class);
		Modules.remove(ValidationHandler.class);
	}

	@Test
	public void when_validating_temporal_proof_vertex_timestamp_after_universe_creation__exception_is_not_thrown() throws TemporalProofNotValidException {
		when(Modules.get(Universe.class).getTimestamp()).thenReturn(1L);

		TemporalVertex temporalVertex = mock(TemporalVertex.class);
		when(temporalVertex.getTimestamps()).thenReturn(ImmutableMap.of("testKey", 2L));

		TemporalProof temporalProof = mock(TemporalProof.class);
		when(temporalProof.getVertices()).thenReturn(Collections.singletonList(temporalVertex));

		TemporalProofValidator.checkTimestamps(temporalProof);
	}

	@Test
	public void when_validating_temporal_proof_vertex_timestamp_before_universe_creation__exception_is_thrown() {
		when(Modules.get(Universe.class).getTimestamp()).thenReturn(1L);

		TemporalVertex temporalVertex = mock(TemporalVertex.class);
		when(temporalVertex.getTimestamps()).thenReturn(ImmutableMap.of("testKey", 0L));

		TemporalProof temporalProof = mock(TemporalProof.class);
		when(temporalProof.getVertices()).thenReturn(Collections.singletonList(temporalVertex));

		Assertions.assertThatThrownBy(() -> TemporalProofValidator.checkTimestamps(temporalProof))
			.isInstanceOf(TemporalProofNotValidException.class)
			.hasMessageContaining("before Universe creation");
	}

	@Test
	public void when_validating_temporal_proof_vertex_timestamp_after_default_timestamp__exception_is_not_thrown() throws TemporalProofNotValidException {
		TemporalVertex temporalVertex = mock(TemporalVertex.class);
		when(temporalVertex.getTimestamps()).thenReturn(ImmutableMap.of(
			"testKey", 2L,
			"default", 1L
		));
		when(temporalVertex.getTimestamp()).thenReturn(1L);

		TemporalProof temporalProof = mock(TemporalProof.class);
		when(temporalProof.getVertices()).thenReturn(Collections.singletonList(temporalVertex));

		TemporalProofValidator.checkTimestamps(temporalProof);
	}

	@Test
	public void when_validating_temporal_proof_vertex_timestamp_before_default_timestamp__exception_is_thrown() {
		TemporalVertex temporalVertex = mock(TemporalVertex.class);
		when(temporalVertex.getTimestamps()).thenReturn(ImmutableMap.of(
			"testKey", 1L,
			"default", 2L
		));
		when(temporalVertex.getTimestamp()).thenReturn(2L);

		TemporalProof temporalProof = mock(TemporalProof.class);
		when(temporalProof.getVertices()).thenReturn(Collections.singletonList(temporalVertex));

		Assertions.assertThatThrownBy(() -> TemporalProofValidator.checkTimestamps(temporalProof))
			.isInstanceOf(TemporalProofNotValidException.class)
			.hasMessageContaining("before DEFAULT timestamp");
	}

	@Test
	public void when_validating_temporal_proof_vertex_timestamp_within_drift__exception_is_not_thrown() throws TemporalProofNotValidException {
		when(Modules.get(NtpService.class).getUTCTimeMS()).thenReturn(1000L);

		TemporalVertex temporalVertex = mock(TemporalVertex.class);
		when(temporalVertex.getTimestamps()).thenReturn(ImmutableMap.of(
			"created", 1L,
			"default", 1L,
			"irrelevant", 999999999L
		));

		TemporalProof temporalProof = mock(TemporalProof.class);
		when(temporalProof.getVertices()).thenReturn(Collections.singletonList(temporalVertex));

		TemporalProofValidator.checkTimestamps(temporalProof);
	}

	@Test
	public void when_validating_temporal_proof_vertex_timestamp_outside_drift__exception_is_thrown() {
		when(Modules.get(NtpService.class).getUTCTimeMS()).thenReturn(1000L);

		TemporalVertex temporalVertex = mock(TemporalVertex.class);
		when(temporalVertex.getTimestamps()).thenReturn(ImmutableMap.of(
			"created", (Time.MAXIMUM_DRIFT + 2) * 1000L,
			"default", 1L,
			"irrelevant", 999999999L
		));

		TemporalProof temporalProof = mock(TemporalProof.class);
		when(temporalProof.getVertices()).thenReturn(Collections.singletonList(temporalVertex));

		Assertions.assertThatThrownBy(() -> TemporalProofValidator.checkTimestamps(temporalProof))
			.isInstanceOf(TemporalProofNotValidException.class)
			.hasMessageContaining("after allowed drift time");
	}

	@Test
	public void when_validating_temporal_proof_vertices_signed_by_owner__exception_is_not_thrown() throws CryptoException, ValidationException {
		final ECKeyPair nodeKey = new ECKeyPair();
		Hash hash = new Hash("0000000000000000000000000000000000000000000000000000000000000000");

		TemporalProof temporalProof = new TemporalProof(AID.from(hash.toByteArray()));
		// vertex is automagically signed in addVertex..
		TemporalVertex temporalVertex = new TemporalVertex(nodeKey.getPublicKey(), 10L, 1L, Hash.ZERO_HASH, EUID.ZERO);
		temporalProof.add(temporalVertex, nodeKey);

		TemporalProofValidator.checkSigned(temporalProof);
	}

	@Test
	public void when_validating_temporal_proof_vertices_not_signed_by_owner__exception_is_thrown() throws CryptoException, TemporalProofNotValidException {
		final ECKeyPair ownerKey = new ECKeyPair();

		TemporalVertex temporalVertex = mock(TemporalVertex.class);
		when(temporalVertex.getOwner()).thenReturn(ownerKey.getPublicKey());
		when(temporalVertex.getSignature()).thenReturn(new ECSignature());
		when(temporalVertex.getHash()).thenReturn(Hash.ZERO_HASH);
		when(temporalVertex.getHID()).thenReturn(EUID.ONE);
		when(temporalVertex.getPrevious()).thenReturn(EUID.ZERO);

		Hash hash = new Hash("0000000000000000000000000000000000000000000000000000000000000000");
		TemporalProof temporalProof = new TemporalProof(AID.ZERO, Collections.singletonList(temporalVertex));

		Assertions.assertThatThrownBy(() -> TemporalProofValidator.checkSigned(temporalProof))
			.isInstanceOf(TemporalProofNotValidException.class)
			.hasMessageContaining("not signed by owner");
	}
}