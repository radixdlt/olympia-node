package com.radixdlt.store.tree;

import com.radixdlt.store.tree.hash.Keccak256;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class PMTNodeTest {

    private static final byte[] NON_EMPTY_VALUE = "value".getBytes(StandardCharsets.UTF_8);
    private static final byte[] EMPTY_VALUE = new byte[0];
    private static final PMTKey ALL_NIBBLES = new PMTKey(new byte[]{0, 1, 0, 2});
    private static final PMTKey BRANCH_NIBBLE = new PMTKey(new byte[]{0});
    private static final PMTKey KEY_NIBBLES = new PMTKey(new byte[]{1, 0, 2});
    private static final Keccak256 KECCAK_256 = new Keccak256();

    @Test
    public void when_leaf_node_with_non_empty_value_is_deserialized__then_it_is_created_correctly() {
        // given
        PMTLeaf pmtLeaf = new PMTLeaf(ALL_NIBBLES, NON_EMPTY_VALUE);
        byte[] serializedLeaf = pmtLeaf.serialize();

        // when
        PMTNode deserialize = PMTNode.deserialize(serializedLeaf);

        // then
        assertEquals(pmtLeaf, deserialize);
    }

    @Test
    public void when_leaf_node_with_empty_value_is_deserialized__then_it_is_created_correctly() {
        // given
        PMTLeaf pmtLeaf = new PMTLeaf(ALL_NIBBLES, EMPTY_VALUE);
        byte[] serializedLeaf = pmtLeaf.serialize();

        // when
        PMTNode deserialize = PMTNode.deserialize(serializedLeaf);

        // then
        assertEquals(pmtLeaf, deserialize);
    }

    @Test
    public void when_extension_node_with_non_empty_value_is_deserialized__then_it_is_created_correctly() {
        // given
        PMTExt pmtExt = new PMTExt(ALL_NIBBLES, NON_EMPTY_VALUE);
        byte[] serializedLeaf = pmtExt.serialize();

        // when
        PMTNode deserialize = PMTNode.deserialize(serializedLeaf);


        // then
        assertEquals(pmtExt, deserialize);
    }

    @Test
    public void when_extension_node_with_empty_value_is_deserialized__then_it_is_created_correctly() {
        // given
        PMTExt pmtExt = new PMTExt(ALL_NIBBLES, EMPTY_VALUE);
        byte[] serializedLeaf = pmtExt.serialize();

        // when
        PMTNode deserialize = PMTNode.deserialize(serializedLeaf);

        // then
        assertEquals(pmtExt, deserialize);
    }

    @Test
    public void when_branch_node_with_non_empty_value_is_deserialized__then_it_is_created_correctly() {
        // given
        PMTLeaf pmtLeaf = new PMTLeaf(
                BRANCH_NIBBLE,
                KEY_NIBBLES,
                NON_EMPTY_VALUE
        );
        PMTBranch pmtBranch = new PMTBranch(
                NON_EMPTY_VALUE,
                new PMTBranch.PMTBranchChild(pmtLeaf.getBranchNibble(), PMT.represent(pmtLeaf, KECCAK_256))
        );
        byte[] serializedLeaf = pmtBranch.serialize();

        // when
        PMTNode deserialize = PMTNode.deserialize(serializedLeaf);

        // then
        PMTBranch expected = new PMTBranch(
                NON_EMPTY_VALUE,
                new PMTBranch.PMTBranchChild(pmtLeaf.getBranchNibble(), PMT.represent(pmtLeaf, KECCAK_256))
        );
        assertEquals(expected, deserialize);
    }

    @Test
    public void when_branch_node_with_empty_value_is_deserialized__then_it_is_created_correctly() {
        // given
        PMTLeaf pmtLeaf = new PMTLeaf(
                BRANCH_NIBBLE,
                KEY_NIBBLES,
                NON_EMPTY_VALUE
        );
        PMTBranch pmtBranch = new PMTBranch(
                EMPTY_VALUE,
                new PMTBranch.PMTBranchChild(pmtLeaf.getBranchNibble(), PMT.represent(pmtLeaf, KECCAK_256))
        );

        // when
        byte[] serializedLeaf = pmtBranch.serialize();
        PMTNode deserialize = PMTNode.deserialize(serializedLeaf);

        // then
        PMTBranch expected = new PMTBranch(
                EMPTY_VALUE,
                new PMTBranch.PMTBranchChild(pmtLeaf.getBranchNibble(), PMT.represent(pmtLeaf, KECCAK_256))
        );
        assertEquals(expected, deserialize);
    }
}
