package com.radixdlt.test.system;

import com.radixdlt.test.system.annotation.RadixSystemTest;

public class SmokeTesting extends SystemTest {

    @RadixSystemTest
    public void testCaseOne() {
        System.out.println("INSIDE TEST CASE 1");
    }

}
