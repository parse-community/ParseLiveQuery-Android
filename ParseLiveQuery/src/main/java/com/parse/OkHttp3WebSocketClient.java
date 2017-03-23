package com.parse;

import android.util.Log;

import java.net.URI;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocketListener;
import okio.ByteString;

/* package */ class OkHttp3WebSocketClient implements WebSocketClient {

    private static final String LOG_TAG = "OkHttpWebSocketClient";

    private final WebSocketClientCallback webSocketClientCallback;
    private okhttp3.WebSocket webSocket;
    private State state = State.NONE;
    private OkHttpClient client;
    private final String url;
    private final int STATUS_CODE = 200;
    private final String CLOSING_MSG = "User invoked close";

    private final WebSocketListener handler = new WebSocketListener() {
        @Override
        public void onOpen(okhttp3.WebSocket webSocket, Response response) {
            setState(State.CONNECTED);
            webSocketClientCallback.onOpen();
        }

        @Override
        public void onMessage(okhttp3.WebSocket webSocket, String text) {
            webSocketClientCallback.onMessage(text);
        }

        @Override
        public void onMessage(okhttp3.WebSocket webSocket, ByteString bytes) {
            Log.w(LOG_TAG, String.format(Locale.US, "Socket got into inconsistent state and received %s instead.", bytes.toString()));
        }

        @Override
        public void onClosed(okhttp3.WebSocket webSocket, int code, String reason) {
            setState(State.DISCONNECTED);
            webSocketClientCallback.onClose();
        }

        @Override
        public void onFailure(okhttp3.WebSocket webSocket, Throwable t, Response response) {
            webSocketClientCallback.onError(t);
        }
    };

    private OkHttp3WebSocketClient(WebSocketClientCallback webSocketClientCallback, URI hostUrl) {
        this.webSocketClientCallback = webSocketClientCallback;
        client = new OkHttpClient();
        url = hostUrl.toString();
    }

    @Override
    public synchronized void open() {
        if (State.NONE == state) {
            // OkHttp3 connects as soon as the socket is created so do it here.
            Request request = new Request.Builder()
                    .url(url)
                    .build();

            webSocket = client.newWebSocket(request, handler);
            setState(State.CONNECTING);
        }
    }

    @Override
    public synchronized void close() {
        setState(State.DISCONNECTING);
        webSocket.close(STATUS_CODE, CLOSING_MSG);
    }

    @Override
    public void send(String message) {
        if (state == State.CONNECTED) {
            webSocket.send(message);
        }
    }

    @Override
    public State getState() {
        return state;
    }

    private synchronized void setState(State newState) {
        this.state = newState;
        this.webSocketClientCallback.stateChanged();
    }

    /* package */ static class OkHttp3SocketClientFactory implements WebSocketClientFactory {
        @Override
        public WebSocketClient createInstance(WebSocketClientCallback webSocketClientCallback, URI hostUrl) {
            return new OkHttp3WebSocketClient(webSocketClientCallback, hostUrl);
        }
    }

}
