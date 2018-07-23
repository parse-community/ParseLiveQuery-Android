package com.parse.livequery;

import java.net.URI;

public interface WebSocketClientFactory {

    WebSocketClient createInstance(WebSocketClient.WebSocketClientCallback webSocketClientCallback, URI hostUrl);

}
