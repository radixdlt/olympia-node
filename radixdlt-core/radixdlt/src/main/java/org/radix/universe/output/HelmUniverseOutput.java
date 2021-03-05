package org.radix.universe.output;

public class HelmUniverseOutput {
    private Boolean outputHelmValues;

    public HelmUniverseOutput(Boolean outputHelmValues, String helmValuesPath) {
        this.outputHelmValues = outputHelmValues;
        this.helmValuesPath = helmValuesPath;
    }

    private String helmValuesPath;

    public Boolean getOutputHelmValues() {
        return outputHelmValues;
    }

    public String getHelmValuesPath() {
        return helmValuesPath;
    }
}
