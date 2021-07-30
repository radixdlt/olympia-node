package com.radixdlt.test.system;

import com.radixdlt.test.RadixNetworkTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

public class SystemTest {

    private RadixNetworkTest test;

    @BeforeEach
    public void setup(TestInfo testInfo) {
        System.out.println(testInfo);
    }

    public SystemTest() {

    }

}
