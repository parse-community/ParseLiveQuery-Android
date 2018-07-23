package com.parse.livequery;

import com.parse.ParseObject;
import com.parse.ParseQuery;

public interface SubscriptionHandling<T extends ParseObject> {

    /**
     * Register a callback for when an event occurs.
     *
     * @param callback The callback to register.
     * @return The same SubscriptionHandling, for easy chaining.
     */
    SubscriptionHandling<T> handleEvents(Subscription.HandleEventsCallback<T> callback);

    /**
     * Register a callback for when an event occurs.
     *
     * @param event    The event type to handle. You should pass one of the enum cases in Event
     * @param callback The callback to register.
     * @return The same SubscriptionHandling, for easy chaining.
     */
    SubscriptionHandling<T> handleEvent(Subscription.Event event, Subscription.HandleEventCallback<T> callback);

    /**
     * Register a callback for when an event occurs.
     *
     * @param callback The callback to register.
     * @return The same SubscriptionHandling, for easy chaining.
     */
    SubscriptionHandling<T> handleError(Subscription.HandleErrorCallback<T> callback);

    /**
     * Register a callback for when a client succesfully subscribes to a query.
     *
     * @param callback The callback to register.
     * @return The same SubscriptionHandling, for easy chaining.
     */
    SubscriptionHandling<T> handleSubscribe(Subscription.HandleSubscribeCallback<T> callback);

    /**
     * Register a callback for when a query has been unsubscribed.
     *
     * @param callback The callback to register.
     * @return The same SubscriptionHandling, for easy chaining.
     */
    SubscriptionHandling<T> handleUnsubscribe(Subscription.HandleUnsubscribeCallback<T> callback);

    int getRequestId();

    interface HandleEventsCallback<T extends ParseObject> {
        void onEvents(ParseQuery<T> query, Subscription.Event event, T object);
    }

    interface HandleEventCallback<T extends ParseObject> {
        void onEvent(ParseQuery<T> query, T object);
    }

    interface HandleErrorCallback<T extends ParseObject> {
        void onError(ParseQuery<T> query, LiveQueryException exception);
    }

    interface HandleSubscribeCallback<T extends ParseObject> {
        void onSubscribe(ParseQuery<T> query);
    }

    interface HandleUnsubscribeCallback<T extends ParseObject> {
        void onUnsubscribe(ParseQuery<T> query);
    }

    enum Event {
        CREATE, ENTER, UPDATE, LEAVE, DELETE
    }

}
