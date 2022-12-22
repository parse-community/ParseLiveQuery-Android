package com.parse.livequery;

import org.json.JSONException;
import org.json.JSONObject;

class ConnectClientOperation extends ClientOperation {

    private final String applicationId;
    private final String sessionToken;
    private final String clientKey;

    ConnectClientOperation(String applicationId, String sessionToken, String clientKey) {
        this.applicationId = applicationId;
        this.sessionToken = sessionToken;
        this.clientKey = clientKey;
    }

    @Override
    JSONObject getJSONObjectRepresentation() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("op", "connect");
        jsonObject.put("applicationId", applicationId);
        jsonObject.put("sessionToken", sessionToken);
        jsonObject.put("clientKey", clientKey);
        return jsonObject;
    }
}
