package org.radix.time;

import com.radixdlt.common.EUID;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import com.radixdlt.universe.Universe;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Utility for validating {@link TemporalProof}s
 */
public class TemporalProofValidator {
	/**
	 * Validate the given temporal proof
	 * @param temporalProof The temporal proof
	 * @throws TemporalProofNotValidException if not valid
	 */
	public static void validate(TemporalProof temporalProof) throws TemporalProofNotValidException {
		Objects.requireNonNull(temporalProof, "temporalProof is required");

		checkSingleOrigin(temporalProof);
		checkTimestamps(temporalProof);
		checkSigned(temporalProof);
	}

	// @PackageLocalForTest
	static void checkSingleOrigin(TemporalProof temporalProof) throws TemporalProofNotValidException {
		boolean haveOrigin = false;
		for (TemporalVertex vertex : temporalProof.getVertices()) {
			if (vertex.getPrevious().equals(EUID.ZERO)) {
				if (!haveOrigin) {
					haveOrigin = true;
				} else {
					throw new TemporalProofNotValidException(String.format(
						"TemporalProof for %s appears to have multiple origins",
						temporalProof.getAID()), temporalProof);
				}
			}
		}
	}

	// @PackageLocalForTest
	static void checkTimestamps(TemporalProof temporalProof) throws TemporalProofNotValidException {
		for (TemporalVertex vertex : temporalProof.getVertices()) {
			for (String type : vertex.getTimestamps().keySet()) {
				long timestamp = vertex.getTimestamps().get(type);
				long defaultTimestamp = vertex.getTimestamp();
				if (!type.equals(Timestamps.DEFAULT) && timestamp < defaultTimestamp) {
					throw new TemporalProofNotValidException(String.format(
						"Vertex %s timestamp %s: %d is before DEFAULT timestamp of %d",
						vertex.getHID(), type, timestamp, defaultTimestamp), temporalProof);
				}

				long universeCreationTimestamp = Modules.get(Universe.class).getTimestamp();
				if (timestamp < universeCreationTimestamp) {
					throw new TemporalProofNotValidException(String.format(
						"Vertex %s timestamp %s: %d is before Universe creation of %d",
						vertex.getHID(), type, timestamp, universeCreationTimestamp), temporalProof);
				}

				long maxAllowedTimestampWithDrift = Modules.get(NtpService.class).getUTCTimeMS() + TimeUnit.MILLISECONDS.convert(Time.MAXIMUM_DRIFT, TimeUnit.SECONDS);
				if ((type.equals(Timestamps.CREATED) || type.equals(Timestamps.DEFAULT))
					&& timestamp > maxAllowedTimestampWithDrift) {
					throw new TemporalProofNotValidException(String.format(
						"Vertex %s timestamp %s: %d is after allowed drift time of %d",
						vertex.getHID(), type, timestamp, maxAllowedTimestampWithDrift), temporalProof);
				}
			}
		}
	}

	// @PackageLocalForTest
	static void checkSigned(TemporalProof temporalProof) throws TemporalProofNotValidException {
		Set<EUID> vertexNIDs = new HashSet<>(temporalProof.getVertices().size());
		byte[] hashBuffer = new byte[Hash.BYTES * 2];

		for (TemporalVertex vertex : temporalProof.getVertices()) {
			TemporalProof branch = temporalProof.getBranch(vertex, false);
			ECPublicKey ownerKey = vertex.getOwner();

			if (vertexNIDs.contains(ownerKey.getUID())) {
				Logging.getLogger().warn(String.format(
					"TemporalProof for %s already has vertex for NID %s",
					temporalProof.getAID(), ownerKey.getUID()));
			}

			System.arraycopy(branch.getHash().toByteArray(), 0, hashBuffer, 0, Hash.BYTES);
			System.arraycopy(vertex.getHash().toByteArray(), 0, hashBuffer, Hash.BYTES, Hash.BYTES);

			if (!ownerKey.verify(Hash.hash256(hashBuffer), vertex.getSignature())) {
				throw new TemporalProofNotValidException(String.format(
					"TemporalProof for %s vertex %s was not signed by owner",
					temporalProof.getAID(), vertex.getHID()), temporalProof);
			}

			vertexNIDs.add(ownerKey.getUID());
		}
	}
}
