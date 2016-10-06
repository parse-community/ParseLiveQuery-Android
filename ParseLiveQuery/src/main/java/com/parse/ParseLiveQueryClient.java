package com.parse;


import android.util.Log;
import android.util.SparseArray;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import bolts.Continuation;
import bolts.Task;

import static com.parse.Parse.checkInit;

public class ParseLiveQueryClient<T extends ParseObject> {

    private static final String LOG_TAG = "ParseLiveQueryClient";

    private final Executor taskExecutor;
    private final String applicationId;
    private final String clientKey;
    private final SparseArray<Subscription<T>> subscriptions = new SparseArray<>();
    private final URI uri;
    private final WebSocketClientFactory webSocketClientFactory;
    private final WebSocketClient.WebSocketClientCallback webSocketClientCallback;

    private WebSocketClient webSocketClient;
    private int requestIdCount = 1;
    private boolean userDisconnected = false;

    public ParseLiveQueryClient(URI uri) {
        this(uri, new TubeSockWebSocketClient.TubeWebSocketClientFactory(), Task.BACKGROUND_EXECUTOR);
    }

    /* package */ ParseLiveQueryClient(URI uri, WebSocketClientFactory webSocketClientFactory, Executor taskExecutor) {
        checkInit();
        this.uri = uri;
        this.applicationId = ParsePlugins.get().applicationId();
        this.clientKey = ParsePlugins.get().clientKey();
        this.webSocketClientFactory = webSocketClientFactory;
        this.taskExecutor = taskExecutor;
        this.webSocketClientCallback = getWebSocketClientCallback();
    }


    public SubscriptionHandling<T> subscribe(ParseQuery<T> query) {
        int requestId = requestIdGenerator();
        Subscription<T> subscription = new Subscription<>(requestId, query);
        subscriptions.append(requestId, subscription);
        if (webSocketClient == null || (webSocketClient.getState() != WebSocketClient.State.CONNECTING && webSocketClient.getState() != WebSocketClient.State.CONNECTED)) {
            if (!userDisconnected) {
                reconnect();
            } else {
                Log.d(LOG_TAG, "Warning: The client was explicitly disconnected! You must explicitly call .reconnect() in order to process your subscriptions.");
            }
        } else if (webSocketClient.getState() == WebSocketClient.State.CONNECTED) {
            sendSubscription(subscription);
        }
        return subscription;
    }

    public void unsubscribe(final ParseQuery<T> query) {
        if (query != null) {
            for (int i = 0; i < subscriptions.size(); i++) {
                Subscription subscription = subscriptions.valueAt(i);
                if (query.equals(subscription.getQuery())) {
                    sendUnsubscription(subscription);
                }
            }
        }
    }

    public void unsubscribe(final ParseQuery<T> query, final SubscriptionHandling subscriptionHandling) {
        if (query != null && subscriptionHandling != null) {
            for (int i = 0; i < subscriptions.size(); i++) {
                Subscription subscription = subscriptions.valueAt(i);
                if (query.equals(subscription.getQuery()) && subscriptionHandling.equals(subscription)) {
                    sendUnsubscription(subscription);
                }
            }
        }
    }

    public void reconnect() {
        if (webSocketClient != null) {
            webSocketClient.close();
        }
        this.webSocketClient = webSocketClientFactory.createInstance(webSocketClientCallback, uri);
        this.webSocketClient.open();
        userDisconnected = false;
    }

    public void disconnect() {
        if (webSocketClient != null) {
            webSocketClient.close();
            webSocketClient = null;
            userDisconnected = true;
        }
    }

    // Private methods

    private synchronized int requestIdGenerator() {
        return requestIdCount++;
    }

    private Task<Void> handleOperationAsync(final String message) {
        return Task.call(new Callable<Void>() {
            public Void call() throws Exception {
                parseMessage(message);
                return null;
            }
        }, taskExecutor);
    }

    private Task<Void> sendOperationAsync(final ClientOperation clientOperation) {
        return Task.call(new Callable<Void>() {
            public Void call() throws Exception {
                JSONObject jsonEncoded = clientOperation.getJSONObjectRepresentation();
                String jsonString = jsonEncoded.toString();
                webSocketClient.send(jsonString);
                return null;
            }
        }, taskExecutor);
    }

    private void parseMessage(String message) throws LiveQueryException {
        try {
            JSONObject jsonObject = new JSONObject(message);
            String rawOperation = jsonObject.getString("op");

            switch (rawOperation) {
                case "connected":
                    Log.d(LOG_TAG, "Connected, sending pending subscription");
                    for (int i = 0; i < subscriptions.size(); i++) {
                        sendSubscription(subscriptions.valueAt(i));
                    }
                    break;
                case "redirect":
                    String url = jsonObject.getString("url");
                    // TODO: Handle redirect.
                    break;
                case "subscribed":
                    handleSubscribedEvent(jsonObject);
                    break;
                case "unsubscribed":
                    handleUnsubscribedEvent(jsonObject);
                    break;
                case "enter":
                    handleObjectEvent(Subscription.Event.ENTER, jsonObject);
                    break;
                case "leave":
                    handleObjectEvent(Subscription.Event.LEAVE, jsonObject);
                    break;
                case "update":
                    handleObjectEvent(Subscription.Event.UPDATE, jsonObject);
                    break;
                case "create":
                    handleObjectEvent(Subscription.Event.CREATE, jsonObject);
                    break;
                case "delete":
                    handleObjectEvent(Subscription.Event.DELETE, jsonObject);
                    break;
                case "error":
                    handleErrorEvent(jsonObject);
                    break;
                default:
                    throw new LiveQueryException.InvalidResponseException(message);
            }
        } catch (JSONException e) {
            throw new LiveQueryException.InvalidResponseException(message);
        }

    }

    private void handleSubscribedEvent(JSONObject jsonObject) throws JSONException {
        final int requestId = jsonObject.getInt("requestId");
        final Subscription<T> subscription = subscriptionForRequestId(requestId);
        if (subscription != null) {
            subscription.didSubscribe(subscription.getQuery());
        }
    }

    private void handleUnsubscribedEvent(JSONObject jsonObject) throws JSONException {
        final int requestId = jsonObject.getInt("requestId");
        final Subscription<T> subscription = subscriptionForRequestId(requestId);
        if (subscription != null) {
            subscription.didUnsubscribe(subscription.getQuery());
            subscriptions.remove(requestId);
        }
    }

    private void handleObjectEvent(Subscription.Event event, JSONObject jsonObject) throws JSONException {
        final int requestId = jsonObject.getInt("requestId");
        final Subscription<T> subscription = subscriptionForRequestId(requestId);
        if (subscription != null) {
            T object = ParseObject.fromJSON(jsonObject.getJSONObject("object"), subscription.getQueryState().className(), subscription.getQueryState().selectedKeys() == null);
            subscription.didReceive(event, subscription.getQuery(), object);
        }
    }

    private void handleErrorEvent(JSONObject jsonObject) throws JSONException {
        int requestId = jsonObject.getInt("requestId");
        int code = jsonObject.getInt("code");
        String error = jsonObject.getString("error");
        Boolean reconnect = jsonObject.getBoolean("reconnect");
        final Subscription<T> subscription = subscriptionForRequestId(requestId);
        if (subscription != null) {
            subscription.didEncounter(new LiveQueryException.ServerReportedException(code, error, reconnect), subscription.getQuery());
        }
    }

    private Subscription<T> subscriptionForRequestId(int requestId) {
        return subscriptions.get(requestId);
    }

    private void sendSubscription(Subscription<T> subscription) {
        sendOperationAsync(new SubscribeClientOperation<>(subscription.getRequestId(), subscription.getQueryState()));
    }

    private void sendUnsubscription(Subscription subscription) {
        sendOperationAsync(new UnsubscribeClientOperation(subscription.getRequestId()));
    }

    private WebSocketClient.WebSocketClientCallback getWebSocketClientCallback() {
        return new WebSocketClient.WebSocketClientCallback() {
            @Override
            public void onOpen() {
                Log.d(LOG_TAG, "Socket opened");
                sendOperationAsync(new ConnectClientOperation(applicationId, "")).continueWith(new Continuation<Void, Void>() {
                    public Void then(Task<Void> task) {
                        Exception error = task.getError();
                        if (error != null) {
                            Log.d(LOG_TAG, "Error", error);
                        }
                        return null;
                    }
                });
            }

            @Override
            public void onMessage(String message) {
                Log.d(LOG_TAG, "Socket onMessage " + message);
                handleOperationAsync(message).continueWith(new Continuation<Void, Void>() {
                    public Void then(Task<Void> task) {
                        Exception error = task.getError();
                        if (error != null) {
                            Log.d(LOG_TAG, "Error", error);
                        }
                        return null;
                    }
                });
            }

            @Override
            public void onClose() {
                Log.d(LOG_TAG, "Socket onClose");
            }

            @Override
            public void onError(Exception exception) {
                Log.e(LOG_TAG, "Socket onError", exception);
            }

            @Override
            public void stateChanged() {
                Log.d(LOG_TAG, "Socket stateChanged");
            }
        };
    }

}
