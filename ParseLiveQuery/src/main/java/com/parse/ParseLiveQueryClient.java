package com.parse;

import java.net.URI;
import java.util.concurrent.Executor;

public interface ParseLiveQueryClient {

    <T extends ParseObject> SubscriptionHandling<T> subscribe(ParseQuery<T> query);

    <T extends ParseObject> void unsubscribe(final ParseQuery<T> query);

    <T extends ParseObject> void unsubscribe(final ParseQuery<T> query, final SubscriptionHandling<T> subscriptionHandling);

    void reconnect();

    void disconnect();

    class Factory {

        public static ParseLiveQueryClient getClient(URI uri) {
            return new ParseLiveQueryClientImpl(uri);
        }

        /* package */
        static ParseLiveQueryClient getClient(URI uri, WebSocketClientFactory webSocketClientFactory, Executor taskExecutor) {
            return new ParseLiveQueryClientImpl(uri, webSocketClientFactory, taskExecutor);
        }

    }

}
