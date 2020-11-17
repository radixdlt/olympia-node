package com.radixdlt.consensus.liveness;

public class ExponentialPacemakerTimeoutCalculator implements PacemakerTimeoutCalculator {

    private final long timeoutMilliseconds;
    private final double rate;
    private final int maxExponent;

    public ExponentialPacemakerTimeoutCalculator(long timeoutMilliseconds, double rate, int maxExponent) {
        if (timeoutMilliseconds <= 0) {
            throw new IllegalArgumentException("timeoutMilliseconds must be > 0 but was " + timeoutMilliseconds);
        }
        if (rate <= 1.0) {
            throw new IllegalArgumentException("rate must be > 1.0, but was " + rate);
        }
        if (maxExponent < 0) {
            throw new IllegalArgumentException("maxExponent must be >= 0, but was " + maxExponent);
        }
        double maxTimeout = timeoutMilliseconds * Math.pow(rate, maxExponent);
        if (maxTimeout > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Maximum timeout value of " + maxTimeout + " is too large");
        }

        this.timeoutMilliseconds = timeoutMilliseconds;
        this.rate = rate;
        this.maxExponent = maxExponent;
    }

    @Override
    public long timeout(long uncommittedViews) {
        double exponential = Math.pow(this.rate, Math.min(this.maxExponent, uncommittedViews));
        return Math.round(this.timeoutMilliseconds * exponential);
    }
}
