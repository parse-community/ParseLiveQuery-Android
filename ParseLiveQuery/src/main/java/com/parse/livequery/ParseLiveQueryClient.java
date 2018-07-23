package com.parse.livequery;

import com.parse.ParseObject;
import com.parse.ParseQuery;

import java.net.URI;
import java.util.concurrent.Executor;

public interface ParseLiveQueryClient {
    <T extends ParseObject> SubscriptionHandling<T> subscribe(ParseQuery<T> query);

    <T extends ParseObject> void unsubscribe(final ParseQuery<T> query);

    <T extends ParseObject> void unsubscribe(final ParseQuery<T> query, final SubscriptionHandling<T> subscriptionHandling);

    void connectIfNeeded();

    void reconnect();

    void disconnect();

    void registerListener(ParseLiveQueryClientCallbacks listener);

    void unregisterListener(ParseLiveQueryClientCallbacks listener);

    class Factory {

        public static ParseLiveQueryClient getClient() {
            return new ParseLiveQueryClientImpl();
        }

        public static ParseLiveQueryClient getClient(WebSocketClientFactory webSocketClientFactory) {
            return new ParseLiveQueryClientImpl(webSocketClientFactory);
        }

        public static ParseLiveQueryClient getClient(URI uri) {
            return new ParseLiveQueryClientImpl(uri);
        }

        public static ParseLiveQueryClient getClient(URI uri, WebSocketClientFactory webSocketClientFactory) {
            return new ParseLiveQueryClientImpl(uri, webSocketClientFactory);
        }

        public static ParseLiveQueryClient getClient(URI uri, WebSocketClientFactory webSocketClientFactory, Executor taskExecutor) {
            return new ParseLiveQueryClientImpl(uri, webSocketClientFactory, taskExecutor);
        }

    }
}
