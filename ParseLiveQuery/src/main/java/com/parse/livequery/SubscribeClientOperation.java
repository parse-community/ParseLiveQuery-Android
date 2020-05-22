package com.parse.livequery;

import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.PointerEncoder;
import com.parse.livequery.ClientOperation;

import org.json.JSONException;
import org.json.JSONObject;

class SubscribeClientOperation<T extends ParseObject> extends ClientOperation {

    private final int requestId;
    private final ParseQuery.State<T> state;
    private final String sessionToken;

    /* package */ SubscribeClientOperation(int requestId, ParseQuery.State<T> state, String sessionToken) {
        this.requestId = requestId;
        this.state = state;
        this.sessionToken = sessionToken;
    }

    @Override
    /* package */ JSONObject getJSONObjectRepresentation() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("op", "subscribe");
        jsonObject.put("requestId", requestId);
        jsonObject.put("sessionToken", sessionToken);

        JSONObject queryJsonObject = new JSONObject();
        queryJsonObject.put("className", state.className());


        PointerEncoder pointerEncoder = PointerEncoder.get();
        queryJsonObject.put("where", pointerEncoder.encode(state.constraints()));

        if(state.selectedKeys() != null)
            queryJsonObject.put("fields", pointerEncoder.encode(state.selectedKeys()));


        jsonObject.put("query", queryJsonObject);

        return jsonObject;
    }
}
