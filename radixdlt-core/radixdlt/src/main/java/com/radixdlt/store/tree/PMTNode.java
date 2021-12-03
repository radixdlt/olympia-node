package com.radixdlt.store.tree;

import com.radixdlt.store.tree.serialization.rlp.RLP;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import static com.radixdlt.store.tree.PMTBranch.NUMBER_OF_NIBBLES;

public abstract sealed class PMTNode implements Cloneable permits PMTBranch, PMTExt, PMTLeaf {

	public enum NodeType {
		LEAF,
		EXTENSION,
		BRANCH,
		EMPTY
	}

	public static final int DB_SIZE_COND = 32;

	protected byte[] hash;
	protected NodeType nodeType;
	protected PMTKey branchNibble;
	protected PMTKey keyNibbles;
	protected byte[] value;

	protected PMTKey getBranchNibble() {
		return branchNibble;
	}

	public PMTKey getKey() {
		return this.keyNibbles;
	}

	public NodeType getNodeType() {
		return nodeType;
	}

	public byte[] getValue() {
		return value;
	}

	public PMTNode setValue(byte[] value) {
		if (this.value == value) {
			throw new IllegalArgumentException("Nothing changed");
		} else {
			this.value = value;
		}
		return this;
	}

	public abstract byte[] serialize();

	public static PMTNode deserialize(byte[] node) {
		Object[] result = (Object[]) RLP.decode(node, 0).getDecoded();
		if (isDeserializedNodeALeafOrExtension(result.length)) {
			var prefixedNibbles  = PMTPath.intoNibbles(asBytes(result[0]));
			byte firstNibble = prefixedNibbles[0];
			byte[] value = asBytes(result[1]);
			if (isSerializedNodeALeaf(firstNibble)) {
				var key = getPmtKey(prefixedNibbles, firstNibble, PMTLeaf.EVEN_PREFIX, PMTLeaf.ODD_PREFIX);
				return new PMTLeaf(key, value);
			} else { // extension
				var key = getPmtKey(prefixedNibbles, firstNibble, PMTExt.EVEN_PREFIX, PMTExt.ODD_PREFIX);
				return new PMTExt(key, value);
			}
		} else { // otherwise, it is a branch node
			byte[][] children = new byte[NUMBER_OF_NIBBLES][];
			for (int i = 0; i < NUMBER_OF_NIBBLES; i++) {
				children[i] = asBytes(result[i]);
			}
			return new PMTBranch(children, asBytes(result[NUMBER_OF_NIBBLES]));
		}
	}

	private static PMTKey getPmtKey(byte[] prefixedNibbles, byte firstNibble, int evenPrefix, int oddPrefix) {
		if (firstNibble == evenPrefix) {
			// we added a zero after the prefix that we need to ignore now
			return new PMTKey(Arrays.copyOfRange(prefixedNibbles, 2, prefixedNibbles.length));
		} else if (firstNibble == oddPrefix) {
			return new PMTKey(Arrays.copyOfRange(prefixedNibbles, 1, prefixedNibbles.length));
		} else {
			throw new IllegalStateException(
				String.format("Unexpected nibble prefix when deserializing tree node: %s", firstNibble)
			);
		}
	}

	private static boolean isDeserializedNodeALeafOrExtension(int numberOfFields) {
		return numberOfFields == 2;
	}

	private static boolean isDeserializedNodeALeafWithEvenNumberOfNibbles(byte firstNibble) {
		return firstNibble == PMTLeaf.EVEN_PREFIX;
	}

	private static boolean isDeserializedNodeALeafWithOddNumberOfNibbles(byte firstNibble) {
		return firstNibble == PMTLeaf.ODD_PREFIX;
	}

	private static boolean isSerializedNodeALeaf(byte firstNibble) {
		return isDeserializedNodeALeafWithEvenNumberOfNibbles(firstNibble)
				|| isDeserializedNodeALeafWithOddNumberOfNibbles(firstNibble);
	}

	private static byte[] asBytes(Object value) {
		if (isString(value)) {
			return ((String) value).getBytes(StandardCharsets.UTF_8);
		} else if (isBytes(value)) {
			return (byte[]) value;
		}
		return new byte[0];
	}

	private static boolean isString(Object value) {
		return value instanceof String;
	}

	private static boolean isBytes(Object value) {
		return value instanceof byte[];
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		PMTNode pmtNode = (PMTNode) o;
		return Arrays.equals(hash, pmtNode.hash)
				&& nodeType == pmtNode.nodeType
				&& Objects.equals(branchNibble, pmtNode.branchNibble)
				&& Objects.equals(keyNibbles, pmtNode.keyNibbles)
				&& Arrays.equals(value, pmtNode.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(hash), nodeType, branchNibble, keyNibbles, Arrays.hashCode(value));
	}
}
