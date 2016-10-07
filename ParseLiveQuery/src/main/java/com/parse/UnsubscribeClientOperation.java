package com.parse;

import org.json.JSONException;
import org.json.JSONObject;

/* package */ class UnsubscribeClientOperation extends ClientOperation {

    private final int requestId;

    /* package */ UnsubscribeClientOperation(int requestId) {
        this.requestId = requestId;
    }

    @Override
    /* package */ JSONObject getJSONObjectRepresentation() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("op", "unsubscribe");
        jsonObject.put("requestId", requestId);
        return jsonObject;
    }
}
