package com.parse;

import org.json.JSONException;
import org.json.JSONObject;

/* package */ class SubscribeClientOperation<T extends ParseObject> extends ClientOperation {

    private final int requestId;
    private final ParseQuery.State<T> state;

    /* package */ SubscribeClientOperation(int requestId, final ParseQuery.State<T> state) {
        this.requestId = requestId;
        this.state = state;
    }

    @Override
    /* package */ JSONObject getJSONObjectRepresentation() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("op", "subscribe");
        jsonObject.put("requestId", requestId);

        JSONObject queryJsonObject = state.toJSON(PointerEncoder.get());

        jsonObject.put("query", queryJsonObject);

        return jsonObject;
    }
}
