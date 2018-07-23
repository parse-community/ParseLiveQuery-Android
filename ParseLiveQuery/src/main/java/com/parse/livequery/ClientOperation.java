package com.parse.livequery;

import org.json.JSONException;
import org.json.JSONObject;

abstract class ClientOperation {
     abstract JSONObject getJSONObjectRepresentation() throws JSONException;
}
