package com.radixdlt.tempo.store;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.crypto.Hash;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Arrays;
import java.util.Objects;

@SerializerId2("tempo.sync.commitment_batch")
public final class CommitmentBatch {
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("commitments")
	@DsonOutput(DsonOutput.Output.ALL)
	private Hash[] commitments;

	@JsonProperty("positions")
	@DsonOutput(DsonOutput.Output.ALL)
	private long[] positions;

	private int computedSize;

	private CommitmentBatch() {
		// For serializer
		this(0);
	}

	public CommitmentBatch(int capacity) {
		this.commitments = new Hash[capacity];
		this.positions = new long[capacity];
		this.computedSize = 0;
	}

	public CommitmentBatch(Hash[] commitments, long[] positions) {
		this.commitments = Objects.requireNonNull(commitments, "commitments is required");
		this.positions = Objects.requireNonNull(positions, "positions is required");
		this.computedSize = commitments.length;
		if (commitments.length != positions.length) {
			throw new IllegalArgumentException("Commitments length must match positions length");
		}
	}

	public CommitmentBatch pushLast(CommitmentBatch other) {
		int otherSize = other.size();
		int myCapacity = this.capacity();
		if (otherSize > myCapacity) {
			throw new IllegalArgumentException("Other commitment batch is larger than this batch");
		}
		CommitmentBatch newBatch = new CommitmentBatch(myCapacity);
		int remaining = myCapacity - otherSize;
		if (remaining > 0) {
			System.arraycopy(this.commitments, myCapacity - remaining, newBatch.commitments, 0, remaining);
			System.arraycopy(this.positions, myCapacity - remaining, newBatch.positions, 0, remaining);
		}
		System.arraycopy(other.commitments, 0, newBatch.commitments, remaining, myCapacity);
		System.arraycopy(other.positions, 0, newBatch.positions, remaining, myCapacity);

		return newBatch;
	}

	public CommitmentBatch ensureCapacity(int desiredCapacity) {
		if (desiredCapacity < capacity()) {
			return this;
		}

		CommitmentBatch newBatch = new CommitmentBatch(desiredCapacity);
		System.arraycopy(this.commitments, 0, newBatch.commitments, 0, computedSize);
		System.arraycopy(this.positions, 0, newBatch.positions, 0, computedSize);
		return newBatch;
	}

	public long getFirstPosition() {
		return isEmpty() ? -1 : positions[0];
	}

	public long getLastPosition() {
		return isEmpty() ? -1 : positions[positions.length - 1];
	}

	public Hash[] getCommitments() {
		return commitments;
	}

	public long[] getPositions() {
		return positions;
	}

	public int size() {
		return computedSize;
	}

	public int capacity() {
		return positions.length;
	}

	public boolean isEmpty() {
		return size() <= 0;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		CommitmentBatch that = (CommitmentBatch) o;
		return Arrays.equals(commitments, that.commitments) &&
			Arrays.equals(positions, that.positions);
	}

	@Override
	public int hashCode() {
		int result = Arrays.hashCode(commitments);
		result = 31 * result + Arrays.hashCode(positions);
		return result;
	}
}
