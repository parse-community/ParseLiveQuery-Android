package com.parse;

import android.util.Log;

import com.firebase.tubesock.WebSocket;
import com.firebase.tubesock.WebSocketEventHandler;
import com.firebase.tubesock.WebSocketException;
import com.firebase.tubesock.WebSocketMessage;

import java.net.URI;
import java.util.Locale;

/* package */ class TubeSockWebSocketClient implements WebSocketClient {

    private static final String LOG_TAG = "TubeSockWebSocketClient";

    private final WebSocketClientCallback webSocketClientCallback;
    private final WebSocket webSocket;
    private State state = State.NONE;

    private final WebSocketEventHandler handler = new WebSocketEventHandler() {
        public void onOpen() {
            setState(State.CONNECTED);
            webSocketClientCallback.onOpen();
        }

        public void onMessage(WebSocketMessage message) {
            if (message.isText()) {
                webSocketClientCallback.onMessage(message.getText());
            } else {
                Log.d(LOG_TAG, String.format(Locale.US, "Socket got into inconsistent state and received %s instead.", message));
            }
        }

        public void onClose() {
            setState(State.DISCONNECTED);
            webSocketClientCallback.onClose();
        }

        public void onError(WebSocketException e) {
            webSocketClientCallback.onError(e);
        }

        public void onLogMessage(String msg) {
        }
    };

    private TubeSockWebSocketClient(WebSocketClientCallback webSocketClientCallback, URI hostUrl) {
        this.webSocketClientCallback = webSocketClientCallback;
        webSocket = new WebSocket(hostUrl);
        webSocket.setEventHandler(handler);
    }

    @Override
    public synchronized void open() {
        if (State.NONE == state) {
            webSocket.connect();
            setState(State.CONNECTING);
        }
    }

    @Override
    public synchronized void close() {
        setState(State.DISCONNECTING);
        webSocket.close();
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

    /* package */ static class TubeWebSocketClientFactory implements WebSocketClientFactory {
        @Override
        public WebSocketClient createInstance(WebSocketClientCallback webSocketClientCallback, URI hostUrl) {
            return new TubeSockWebSocketClient(webSocketClientCallback, hostUrl);
        }
    }

}
