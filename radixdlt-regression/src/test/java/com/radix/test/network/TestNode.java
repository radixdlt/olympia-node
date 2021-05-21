package com.radix.test.network;

import com.radix.test.Utils;

public class TestNode {

    private final String jsonRpcRootUrl;
    private final String nodeApiRootUrl;
    private final String faucetRootUrl;
    private final String dockerContainerId;

    /**
     * @return null if all arguments are empty or null
     */
    public static TestNode create(String jsonRpcRootUrl, String nodeApiRootUrl, String faucetRootUrl,
                                  String dockerContainerId) {
        if (Utils.isNullOrEmpty(jsonRpcRootUrl) && Utils.isNullOrEmpty(nodeApiRootUrl)
                && Utils.isNullOrEmpty(dockerContainerId)) {
            return null;
        }
        return new TestNode(jsonRpcRootUrl, nodeApiRootUrl, faucetRootUrl, dockerContainerId);
    }

    private TestNode(String jsonRpcRootUrl, String nodeApiRootUrl, String faucetRootUrl, String dockerContainerId) {
        this.jsonRpcRootUrl = jsonRpcRootUrl;
        this.nodeApiRootUrl = nodeApiRootUrl;
        this.faucetRootUrl = faucetRootUrl;
        this.dockerContainerId = dockerContainerId;
    }

    public String getJsonRpcRootUrl() {
        return jsonRpcRootUrl;
    }

    public String getNodeApiRootUrl() {
        return nodeApiRootUrl;
    }

    public String getDockerContainerId() {
        return dockerContainerId;
    }

    public String getFaucetRootUrl() {
        return faucetRootUrl;
    }

    public String toString() {
        return String.format("JSON-RPC:%s, Node API:%s, docker ID:%s, faucet:%s", jsonRpcRootUrl, nodeApiRootUrl,
                dockerContainerId, faucetRootUrl);
    }

    public boolean isFaucetEnabled() {
        return !Utils.isNullOrEmpty(faucetRootUrl);
    }
}
