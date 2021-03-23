package com.radixdlt.utils;

public class AWSSecretsOutputOptions {
    private Boolean enableAwsSecrets;
    private Boolean recreateAwsSecrets;
    private String networkName;

    public AWSSecretsOutputOptions(Boolean enableAwsSecrets, Boolean recreateAwsSecrets, String networkName) {
        this.enableAwsSecrets = enableAwsSecrets;
        this.recreateAwsSecrets = recreateAwsSecrets;
        this.networkName = networkName;
    }

    public Boolean getEnableAwsSecrets() {
        return enableAwsSecrets;
    }

    public Boolean getRecreateAwsSecrets() {
        return recreateAwsSecrets;
    }

    public String getNetworkName() {
        return networkName;
    }

}
