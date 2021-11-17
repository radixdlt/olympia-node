package com.radixdlt.consensus;

import org.junit.Test;

import java.util.HashMap;

import static org.mockito.Mockito.mock;

public class TimestampedECDSASignaturesTest {
    @Test(expected = NullPointerException.class)
    public void deserializationWithInvalidMapThrowsException1() {
        var map = new HashMap<String, TimestampedECDSASignature>();
        map.put(null, mock(TimestampedECDSASignature.class));

        TimestampedECDSASignatures.from(map);
    }

    @Test(expected = NullPointerException.class)
    public void deserializationWithInvalidMapThrowsException2() {
        var map = new HashMap<String, TimestampedECDSASignature>();
        map.put("not null string", null);

        TimestampedECDSASignatures.from(map);
    }
}