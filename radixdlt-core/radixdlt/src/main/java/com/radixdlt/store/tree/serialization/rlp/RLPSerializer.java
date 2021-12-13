package com.radixdlt.store.tree.serialization.rlp;

import com.radixdlt.store.tree.*;
import com.radixdlt.store.tree.serialization.PMTNodeSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.radixdlt.store.tree.PMTBranch.NUMBER_OF_NIBBLES;

public class RLPSerializer implements PMTNodeSerializer {

    @Override
    public byte[] serialize(PMTNode node) {
        if (node instanceof PMTBranch) {
            return serializeBranchNode(node);
        } else if (node instanceof PMTExt) {
            return serializeLeafOrExtensionNode(node, PMTExt.ODD_PREFIX, PMTExt.EVEN_PREFIX);
        } else {
            return serializeLeafOrExtensionNode(node, PMTLeaf.ODD_PREFIX, PMTLeaf.EVEN_PREFIX);
        }
    }

    private byte[] serializeLeafOrExtensionNode(PMTNode node, int oddPrefix, int evenPrefix) {
        var nibblesWithPrefix = TreeUtils.applyPrefix(node.getKey().getRaw(), oddPrefix, evenPrefix);
        byte[] bytesWithPrefix = TreeUtils.fromNibblesToBytes(nibblesWithPrefix);
        return RLP.encodeList(
                RLP.encodeElement(bytesWithPrefix),
                RLP.encodeElement(node.getValue())
        );
    }

    private byte[] serializeBranchNode(PMTNode node) {
        var list = new byte[PMTBranch.NUMBER_OF_NIBBLES + 1][];
        byte[][] slices = ((PMTBranch) node).getChildren();
        for (int i = 0; i < slices.length; i++) {
            list[i] = RLP.encodeElement(slices[i]);
        }
        list[slices.length] = RLP.encodeElement(node.getValue() == null ? new byte[0] : node.getValue());
        return RLP.encodeList(list);
    }

    @Override
    public PMTNode deserialize(byte[] serializedNode) {
        Object[] result = (Object[]) RLP.decode(serializedNode, 0).getDecoded();
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
}
