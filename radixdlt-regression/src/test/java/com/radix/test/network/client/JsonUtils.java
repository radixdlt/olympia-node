package com.radix.test.network.client;

import com.radixdlt.client.lib.api.AccountAddress;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Helps construct requests to /node
 */
public final class JsonUtils {

    private JsonUtils() {

    }

    public static JSONObject createTransferTokensBody(AccountAddress to, String tokenRri, long amount) {
        JSONObject body = createActionJsonBody("TransferTokens");
        JSONObject params = body.getJSONArray("actions").getJSONObject(0).getJSONObject("params");
        params.put("to", to.toAccountAddress());
        params.put("rri", tokenRri);
        params.put("amount", amount);
        return body;
    }

    /**
     * creates the body needed when POSTing to the node api
     */
    public static JSONObject createActionJsonBody(String actionName) {
        final JSONObject parent = new JSONObject();
        JSONArray actions = new JSONArray();
        final JSONObject action = new JSONObject();
        action.put("action", actionName);
        final JSONObject params = new JSONObject();
        action.put("params", params);
        actions.put(action);
        params.put("actions", actions);
        return parent;
    }
}
