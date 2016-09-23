package com.parse;

import java.net.URI;
import java.util.concurrent.Executor;

public interface ParseLiveQueryClient<T extends ParseObject> {

    SubscriptionHandling<T> subscribe(ParseQuery<T> query);

    void unsubscribe(final ParseQuery<T> query);

    void unsubscribe(final ParseQuery<T> query, final SubscriptionHandling subscriptionHandling);

    void reconnect();

    void disconnect();

    class Factory {

        public static <T extends ParseObject> ParseLiveQueryClient<T> getClient(URI uri) {
            return new ParseLiveQueryClientImpl<>(uri);
        }

        /* package */
        static <T extends ParseObject> ParseLiveQueryClient<T> getClient(URI uri, WebSocketClientFactory webSocketClientFactory, Executor taskExecutor) {
            return new ParseLiveQueryClientImpl<>(uri, webSocketClientFactory, taskExecutor);
        }

    }

}
