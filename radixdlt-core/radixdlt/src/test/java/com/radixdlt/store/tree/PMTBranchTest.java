package com.radixdlt.store.tree;

import org.junit.Assert;
import org.junit.Test;

public class PMTBranchTest {

    @Test
    public void when_two_PMTBranchChild_objects_have_the_same_content__then_equals_returns_true() {
        PMTBranch.PMTBranchChild a = new PMTBranch.PMTBranchChild(new PMTKey(new byte[]{1}), new byte[]{2, 3});
        PMTBranch.PMTBranchChild b = new PMTBranch.PMTBranchChild(new PMTKey(new byte[]{1}), new byte[]{2, 3});

        Assert.assertEquals(a, b);
    }

    @Test
    public void when_two_PMTBranchChild_objects_dont_have_the_same_content__then_equals_returns_false() {
        PMTBranch.PMTBranchChild a = new PMTBranch.PMTBranchChild(new PMTKey(new byte[]{1}), new byte[]{2, 2});
        PMTBranch.PMTBranchChild b = new PMTBranch.PMTBranchChild(new PMTKey(new byte[]{1}), new byte[]{2, 3});

        Assert.assertNotEquals(a, b);
    }
}
