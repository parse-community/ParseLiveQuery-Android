package com.parse;

import org.json.JSONException;
import org.json.JSONObject;

/* package */ class ConnectClientOperation extends ClientOperation {

    private final String applicationId;
    private final String sessionToken;

    /* package */ ConnectClientOperation(String applicationId, String sessionToken) {
        this.applicationId = applicationId;
        this.sessionToken = sessionToken;
    }

    @Override
    /* package */ JSONObject getJSONObjectRepresentation() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("op", "connect");
        jsonObject.put("applicationId", applicationId);
        jsonObject.put("sessionToken", sessionToken);
        return jsonObject;
    }
}
