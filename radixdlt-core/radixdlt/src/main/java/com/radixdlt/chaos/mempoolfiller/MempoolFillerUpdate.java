package com.radixdlt.chaos.mempoolfiller;

public final class MempoolFillerUpdate {
    private final boolean enabled;

    private MempoolFillerUpdate(boolean enabled) {
        this.enabled = enabled;
    }

    public static MempoolFillerUpdate create(boolean enabled) {
        return new MempoolFillerUpdate(enabled);
    }

    public boolean enabled() {
        return enabled;
    }
}
