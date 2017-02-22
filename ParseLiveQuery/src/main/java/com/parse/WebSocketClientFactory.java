package com.parse;

import java.net.URI;

/* package */ interface WebSocketClientFactory {

    WebSocketClient createInstance(WebSocketClient.WebSocketClientCallback webSocketClientCallback, URI hostUrl);

}
