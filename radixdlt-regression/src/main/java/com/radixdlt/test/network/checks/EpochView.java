package com.radixdlt.test.network.checks;

public class EpochView {

    private int epoch;
    private int view;

    public EpochView(int epoch, int view) {
        this.epoch = epoch;
        this.view = view;
    }

    public int getEpoch() {
        return epoch;
    }

    public void setEpoch(int epoch) {
        this.epoch = epoch;
    }

    public int getView() {
        return view;
    }

    public void setView(int view) {
        this.view = view;
    }
}
