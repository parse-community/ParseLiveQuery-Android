package com.parse.livequery;

import com.parse.ParseObject;
import com.parse.ParseQuery;

import java.util.ArrayList;
import java.util.List;

class Subscription<T extends ParseObject> implements SubscriptionHandling<T> {

    private final List<HandleEventsCallback<T>> handleEventsCallbacks = new ArrayList<>();
    private final List<HandleErrorCallback<T>> handleErrorCallbacks = new ArrayList<>();
    private final List<HandleSubscribeCallback<T>> handleSubscribeCallbacks = new ArrayList<>();
    private final List<HandleUnsubscribeCallback<T>> handleUnsubscribeCallbacks = new ArrayList<>();

    private final int requestId;
    private final ParseQuery<T> query;
    private final ParseQuery.State<T> state;

    /* package */ Subscription(int requestId, ParseQuery<T> query) {
        this.requestId = requestId;
        this.query = query;
        this.state = query.getBuilder().build();
    }

    @Override
    public Subscription<T> handleEvents(HandleEventsCallback<T> callback) {
        handleEventsCallbacks.add(callback);
        return this;
    }

    @Override
    public Subscription<T> handleEvent(final Event event, final HandleEventCallback<T> callback) {
        return handleEvents(new HandleEventsCallback<T>() {
            @Override
            public void onEvents(ParseQuery<T> query, Event callbackEvent, T object) {
                if (callbackEvent == event) {
                    callback.onEvent(query, object);
                }
            }
        });
    }

    @Override
    public Subscription<T> handleError(HandleErrorCallback<T> callback) {
        handleErrorCallbacks.add(callback);
        return this;
    }

    @Override
    public Subscription<T> handleSubscribe(HandleSubscribeCallback<T> callback) {
        handleSubscribeCallbacks.add(callback);
        return this;
    }

    @Override
    public Subscription<T> handleUnsubscribe(HandleUnsubscribeCallback<T> callback) {
        handleUnsubscribeCallbacks.add(callback);
        return this;
    }

    @Override
    public int getRequestId() {
        return requestId;
    }

    /* package */ ParseQuery<T> getQuery() {
        return query;
    }

    /* package */ ParseQuery.State<T> getQueryState() {
        return state;
    }

    /**
     * Tells the handler that an event has been received from the live query server.
     *
     * @param event The event that has been received from the server.
     * @param query The query that the event occurred on.
     */
    /* package */ void didReceive(Event event, ParseQuery<T> query, T object) {
        for (HandleEventsCallback<T> handleEventsCallback : handleEventsCallbacks) {
            handleEventsCallback.onEvents(query, event, object);
        }
    }

    /**
     * Tells the handler that an error has been received from the live query server.
     *
     * @param error The error that the server has encountered.
     * @param query The query that the error occurred on.
     */
    /* package */ void didEncounter(LiveQueryException error, ParseQuery<T> query) {
        for (HandleErrorCallback<T> handleErrorCallback : handleErrorCallbacks) {
            handleErrorCallback.onError(query, error);
        }
    }

    /**
     * Tells the handler that a query has been successfully registered with the server.
     * - note: This may be invoked multiple times if the client disconnects/reconnects.
     *
     * @param query The query that has been subscribed.
     */
    /* package */ void didSubscribe(ParseQuery<T> query) {
        for (HandleSubscribeCallback<T> handleSubscribeCallback : handleSubscribeCallbacks) {
            handleSubscribeCallback.onSubscribe(query);
        }
    }

    /**
     * Tells the handler that a query has been successfully deregistered from the server.
     * - note: This is not called unless `unregister()` is explicitly called.
     *
     * @param query The query that has been unsubscribed.
     */
    /* package */ void didUnsubscribe(ParseQuery<T> query) {
        for (HandleUnsubscribeCallback<T> handleUnsubscribeCallback : handleUnsubscribeCallbacks) {
            handleUnsubscribeCallback.onUnsubscribe(query);
        }
    }

}
