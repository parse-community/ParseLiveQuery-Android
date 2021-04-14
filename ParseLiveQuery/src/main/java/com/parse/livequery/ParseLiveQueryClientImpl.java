package com.parse.livequery;

import android.util.Log;

import com.parse.PLog;
import com.parse.Parse;
import com.parse.ParseDecoder;
import com.parse.ParseObject;
import com.parse.ParsePlugins;
import com.parse.ParseQuery;
import com.parse.ParseUser;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import com.parse.boltsinternal.Continuation;
import com.parse.boltsinternal.Task;
import okhttp3.OkHttpClient;

class ParseLiveQueryClientImpl implements ParseLiveQueryClient {

    private static final String LOG_TAG = "ParseLiveQueryClient";

    private final Executor taskExecutor;
    private final String applicationId;
    private final String clientKey;
    private final ConcurrentHashMap<Integer, Subscription<? extends ParseObject>> subscriptions = new ConcurrentHashMap<>();
    private final URI uri;
    private final WebSocketClientFactory webSocketClientFactory;
    private final WebSocketClient.WebSocketClientCallback webSocketClientCallback;

    private final List<ParseLiveQueryClientCallbacks> mCallbacks = new ArrayList<>();

    private WebSocketClient webSocketClient;
    private int requestIdCount = 1;
    private boolean userInitiatedDisconnect = false;
    private boolean hasReceivedConnected = false;

    /* package */ ParseLiveQueryClientImpl() {
        this(getDefaultUri());
    }

    /* package */ ParseLiveQueryClientImpl(URI uri) {
        this(uri, new OkHttp3SocketClientFactory(new OkHttpClient()), Task.BACKGROUND_EXECUTOR);
    }

    /* package */ ParseLiveQueryClientImpl(URI uri, WebSocketClientFactory webSocketClientFactory) {
        this(uri, webSocketClientFactory, Task.BACKGROUND_EXECUTOR);
    }

    /* package */ ParseLiveQueryClientImpl(WebSocketClientFactory webSocketClientFactory) {
        this(getDefaultUri(), webSocketClientFactory, Task.BACKGROUND_EXECUTOR);
    }

    /* package */ ParseLiveQueryClientImpl(URI uri, WebSocketClientFactory webSocketClientFactory, Executor taskExecutor) {
        Parse.checkInit();
        this.uri = uri;
        this.applicationId = ParsePlugins.get().applicationId();
        this.clientKey = ParsePlugins.get().clientKey();
        this.webSocketClientFactory = webSocketClientFactory;
        this.taskExecutor = taskExecutor;
        this.webSocketClientCallback = getWebSocketClientCallback();
    }

    private static URI getDefaultUri() {
        String url = ParsePlugins.get().server();
        if (url.contains("https")) {
            url = url.replaceFirst("https", "wss");
        } else {
            url = url.replaceFirst("http", "ws");
        }
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public <T extends ParseObject> SubscriptionHandling<T> subscribe(ParseQuery<T> query) {
        int requestId = requestIdGenerator();
        Subscription<T> subscription = new Subscription<>(requestId, query);
        subscriptions.put(requestId, subscription);

        if (isConnected()) {
            sendSubscription(subscription);
        } else if (userInitiatedDisconnect) {
            Log.w(LOG_TAG, "Warning: The client was explicitly disconnected! You must explicitly call .reconnect() in order to process your subscriptions.");
        } else {
            connectIfNeeded();
        }

        return subscription;
    }

    public void connectIfNeeded() {
        switch (getWebSocketState()) {
            case CONNECTED:
                // nothing to do
                break;
            case CONNECTING:
                // just wait for it to finish connecting
                break;

            case NONE:
            case DISCONNECTING:
            case DISCONNECTED:
                reconnect();
                break;

            default:

                break;
        }
    }

    @Override
    public <T extends ParseObject> void unsubscribe(final ParseQuery<T> query) {
        if (query != null) {
            for (Subscription<? extends ParseObject> subscription : subscriptions.values()) {
                if (query.equals(subscription.getQuery())) {
                    sendUnsubscription(subscription);
                }
            }
        }
    }

    @Override
    public <T extends ParseObject> void unsubscribe(final ParseQuery<T> query, final SubscriptionHandling<T> subscriptionHandling) {
        if (query != null && subscriptionHandling != null) {
            for (Subscription<? extends ParseObject> subscription : subscriptions.values()) {
                if (query.equals(subscription.getQuery()) && subscriptionHandling.equals(subscription)) {
                    sendUnsubscription(subscription);
                }
            }
        }
    }

    @Override
    public synchronized void reconnect() {
        if (webSocketClient != null) {
            webSocketClient.close();
        }

        userInitiatedDisconnect = false;
        hasReceivedConnected = false;
        webSocketClient = webSocketClientFactory.createInstance(webSocketClientCallback, uri);
        webSocketClient.open();
    }

    @Override
    public synchronized void disconnect() {
        if (webSocketClient != null) {
            webSocketClient.close();
            webSocketClient = null;
        }

        userInitiatedDisconnect = true;
        hasReceivedConnected = false;
    }

    @Override
    public void registerListener(ParseLiveQueryClientCallbacks listener) {
        mCallbacks.add(listener);
    }

    @Override
    public void unregisterListener(ParseLiveQueryClientCallbacks listener) {
        mCallbacks.remove(listener);
    }

    // Private methods

    private synchronized int requestIdGenerator() {
        return requestIdCount++;
    }

    private WebSocketClient.State getWebSocketState() {
        WebSocketClient.State state = webSocketClient == null ? null : webSocketClient.getState();
        return state == null ? WebSocketClient.State.NONE : state;
    }

    private boolean isConnected() {
        return hasReceivedConnected && inAnyState(WebSocketClient.State.CONNECTED);
    }

    private boolean inAnyState(WebSocketClient.State... states) {
        return Arrays.asList(states).contains(getWebSocketState());
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
                if (Parse.getLogLevel() <= Parse.LOG_LEVEL_DEBUG) {
                    Log.d(LOG_TAG, "Sending over websocket: " + jsonString);
                }
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
                    hasReceivedConnected = true;
                    dispatchConnected();
                    Log.v(LOG_TAG, "Connected, sending pending subscription");
                    for (Subscription<? extends ParseObject> subscription : subscriptions.values()) {
                        sendSubscription(subscription);
                    }
                    break;
                case "redirect":
                    String url = jsonObject.getString("url");
                    // TODO: Handle redirect.
                    Log.d(LOG_TAG, "Redirect is not yet handled");
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

    private void dispatchConnected() {
        for (ParseLiveQueryClientCallbacks callback : mCallbacks) {
            callback.onLiveQueryClientConnected(this);
        }
    }

    private void dispatchDisconnected() {
        for (ParseLiveQueryClientCallbacks callback : mCallbacks) {
            callback.onLiveQueryClientDisconnected(this, userInitiatedDisconnect);
        }
    }


    private void dispatchServerError(LiveQueryException exc) {
        for (ParseLiveQueryClientCallbacks callback : mCallbacks) {
            callback.onLiveQueryError(this, exc);
        }
    }

    private void dispatchSocketError(Throwable reason) {
        userInitiatedDisconnect = false;

        for (ParseLiveQueryClientCallbacks callback : mCallbacks) {
            callback.onSocketError(this, reason);
        }

        dispatchDisconnected();
    }

    private <T extends ParseObject> void handleSubscribedEvent(JSONObject jsonObject) throws JSONException {
        final int requestId = jsonObject.getInt("requestId");
        final Subscription<T> subscription = subscriptionForRequestId(requestId);
        if (subscription != null) {
            subscription.didSubscribe(subscription.getQuery());
        }
    }

    private <T extends ParseObject> void handleUnsubscribedEvent(JSONObject jsonObject) throws JSONException {
        final int requestId = jsonObject.getInt("requestId");
        final Subscription<T> subscription = subscriptionForRequestId(requestId);
        if (subscription != null) {
            subscription.didUnsubscribe(subscription.getQuery());
            subscriptions.remove(requestId);
        }
    }

    private <T extends ParseObject> void handleObjectEvent(Subscription.Event event, JSONObject jsonObject) throws JSONException {
        final int requestId = jsonObject.getInt("requestId");
        final Subscription<T> subscription = subscriptionForRequestId(requestId);
        if (subscription != null) {
            T object = ParseObject.fromJSON(jsonObject.getJSONObject("object"), subscription.getQueryState().className(), ParseDecoder.get(), subscription.getQueryState().selectedKeys());
            subscription.didReceive(event, subscription.getQuery(), object);
        }
    }

    private <T extends ParseObject> void handleErrorEvent(JSONObject jsonObject) throws JSONException {
        int requestId = jsonObject.getInt("requestId");
        int code = jsonObject.getInt("code");
        String error = jsonObject.getString("error");
        Boolean reconnect = jsonObject.getBoolean("reconnect");
        final Subscription<T> subscription = subscriptionForRequestId(requestId);
        LiveQueryException exc = new LiveQueryException.ServerReportedException(code, error, reconnect);

        if (subscription != null) {
            subscription.didEncounter(exc, subscription.getQuery());
        }

        dispatchServerError(exc);
    }

    private <T extends ParseObject> Subscription<T> subscriptionForRequestId(int requestId) {
        //noinspection unchecked
        return (Subscription<T>) subscriptions.get(requestId);
    }

    private <T extends ParseObject> void sendSubscription(final Subscription<T> subscription) {
        ParseUser.getCurrentSessionTokenAsync().onSuccess(new Continuation<String, Void>() {
            @Override
            public Void then(Task<String> task) throws Exception {
                String sessionToken = task.getResult();
                SubscribeClientOperation<T> op = new SubscribeClientOperation<>(subscription.getRequestId(), subscription.getQueryState(), sessionToken);

                // dispatch errors
                sendOperationAsync(op).continueWith(new Continuation<Void, Void>() {
                    public Void then(Task<Void> task) {
                        Exception error = task.getError();
                        if (error != null) {
                            if (error instanceof RuntimeException) {
                                subscription.didEncounter(new LiveQueryException.UnknownException(
                                        "Error when subscribing", (RuntimeException) error), subscription.getQuery());
                            }
                        }
                        return null;
                    }
                });
                return null;
            }
        });
    }

    private void sendUnsubscription(Subscription subscription) {
        sendOperationAsync(new UnsubscribeClientOperation(subscription.getRequestId()));
    }

    private WebSocketClient.WebSocketClientCallback getWebSocketClientCallback() {
        return new WebSocketClient.WebSocketClientCallback() {
            @Override
            public void onOpen() {
                hasReceivedConnected = false;
                Log.v(LOG_TAG, "Socket opened");
                ParseUser.getCurrentSessionTokenAsync().onSuccessTask(new Continuation<String, Task<Void>>() {
                    @Override
                    public Task<Void> then(Task<String> task) throws Exception {
                        String sessionToken = task.getResult();
                        return sendOperationAsync(new ConnectClientOperation(applicationId, sessionToken, clientKey));
                    }
                }).continueWith(new Continuation<Void, Void>() {
                    public Void then(Task<Void> task) {
                        Exception error = task.getError();
                        if (error != null) {
                            Log.e(LOG_TAG, "Error when connection client", error);
                        }
                        return null;
                    }
                });
            }

            @Override
            public void onMessage(String message) {
                Log.v(LOG_TAG, "Socket onMessage " + message);
                handleOperationAsync(message).continueWith(new Continuation<Void, Void>() {
                    public Void then(Task<Void> task) {
                        Exception error = task.getError();
                        if (error != null) {
                            Log.e(LOG_TAG, "Error handling message", error);
                        }
                        return null;
                    }
                });
            }

            @Override
            public void onClose() {
                Log.v(LOG_TAG, "Socket onClose");
                hasReceivedConnected = false;
                dispatchDisconnected();
            }

            @Override
            public void onError(Throwable exception) {
                PLog.e(LOG_TAG, "Socket onError", exception);
                hasReceivedConnected = false;
                dispatchSocketError(exception);
            }

            @Override
            public void stateChanged() {
                PLog.v(LOG_TAG, "Socket stateChanged");
            }
        };
    }
}
