package com.parse;

import com.parse.livequery.BuildConfig;
import com.parse.livequery.LiveQueryException;
import com.parse.livequery.ParseLiveQueryClient;
import com.parse.livequery.ParseLiveQueryClientCallbacks;
import com.parse.livequery.SubscriptionHandling;
import com.parse.livequery.WebSocketClient;
import com.parse.livequery.WebSocketClientFactory;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.Transcript;

import java.io.IOException;
import java.net.URI;

import com.parse.boltsinternal.Task;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class TestParseLiveQueryClient {

    private WebSocketClient webSocketClient;
    private WebSocketClient.WebSocketClientCallback webSocketClientCallback;
    private ParseLiveQueryClient parseLiveQueryClient;

    private ParseUser mockUser;

    @Before
    public void setUp() throws Exception {
        Parse.Configuration configuration = new Parse.Configuration.Builder(null)
                .applicationId("1234")
                .build();
        ParsePlugins.initialize(null, configuration);

        // Register a mock currentUserController to make getCurrentUser work
        mockUser = mock(ParseUser.class);
        ParseCurrentUserController currentUserController = mock(ParseCurrentUserController.class);
        when(currentUserController.getAsync(anyBoolean())).thenAnswer(new Answer<Task<ParseUser>>() {
            @Override
            public Task<ParseUser> answer(InvocationOnMock invocation) throws Throwable {
                return Task.forResult(mockUser);
            }
        });
        when(currentUserController.getCurrentSessionTokenAsync()).thenAnswer(new Answer<Task<String>>() {
            @Override
            public Task<String> answer(InvocationOnMock invocation) throws Throwable {
                return Task.forResult(mockUser.getSessionToken());
            }
        });
        ParseCorePlugins.getInstance().registerCurrentUserController(currentUserController);

        parseLiveQueryClient = ParseLiveQueryClient.Factory.getClient(new URI(""), new WebSocketClientFactory() {
            @Override
            public WebSocketClient createInstance(WebSocketClient.WebSocketClientCallback webSocketClientCallback, URI hostUrl) {
                TestParseLiveQueryClient.this.webSocketClientCallback = webSocketClientCallback;
                webSocketClient = mock(WebSocketClient.class);
                return webSocketClient;
            }
        }, new ImmediateExecutor());
        reconnect();
    }

    @After
    public void tearDown() throws Exception {
        ParseCorePlugins.getInstance().reset();
        ParsePlugins.reset();
    }

    @Test
    public void testSubscribeAfterSocketConnectBeforeConnectedOp() throws Exception {
        // Bug: https://github.com/parse-community/ParseLiveQuery-Android/issues/46
        ParseQuery<ParseObject> queryA = ParseQuery.getQuery("objA");
        ParseQuery<ParseObject> queryB = ParseQuery.getQuery("objB");
        clearConnection();

        // This will trigger connectIfNeeded(), which calls reconnect()
        SubscriptionHandling<ParseObject> subA = parseLiveQueryClient.subscribe(queryA);

        verify(webSocketClient, times(1)).open();
        verify(webSocketClient, never()).send(anyString());

        // Now the socket is open
        webSocketClientCallback.onOpen();
        when(webSocketClient.getState()).thenReturn(WebSocketClient.State.CONNECTED);
        // and we send op=connect
        verify(webSocketClient, times(1)).send(contains("\"op\":\"connect\""));

        // Now if we subscribe to queryB, we SHOULD NOT send the subscribe yet, until we get op=connected
        SubscriptionHandling<ParseObject> subB = parseLiveQueryClient.subscribe(queryB);
        verify(webSocketClient, never()).send(contains("\"op\":\"subscribe\""));

        // on op=connected, _then_ we should send both subscriptions
        webSocketClientCallback.onMessage(createConnectedMessage().toString());
        verify(webSocketClient, times(2)).send(contains("\"op\":\"subscribe\""));
    }

    @Test
    public void testSubscribeWhenSubscribedToCallback() throws Exception {
        SubscriptionHandling.HandleSubscribeCallback<ParseObject> subscribeMockCallback = mock(SubscriptionHandling.HandleSubscribeCallback.class);

        ParseQuery<ParseObject> parseQuery = new ParseQuery<>("test");
        createSubscription(parseQuery, subscribeMockCallback);

        verify(subscribeMockCallback, times(1)).onSubscribe(parseQuery);
    }

    @Test
    public void testUnsubscribeWhenSubscribedToCallback() throws Exception {
        ParseQuery<ParseObject> parseQuery = new ParseQuery<>("test");
        SubscriptionHandling<ParseObject> subscriptionHandling = createSubscription(parseQuery,
                mock(SubscriptionHandling.HandleSubscribeCallback.class));

        parseLiveQueryClient.unsubscribe(parseQuery);
        verify(webSocketClient, times(1)).send(any(String.class));

        SubscriptionHandling.HandleUnsubscribeCallback<ParseObject> unsubscribeMockCallback = mock(
                SubscriptionHandling.HandleUnsubscribeCallback.class);
        subscriptionHandling.handleUnsubscribe(unsubscribeMockCallback);
        webSocketClientCallback.onMessage(createUnsubscribedMessage(subscriptionHandling.getRequestId()).toString());

        verify(unsubscribeMockCallback, times(1)).onUnsubscribe(parseQuery);
    }

    @Test
    public void testErrorWhileSubscribing() throws Exception {
        ParseQuery.State state = mock(ParseQuery.State.class);
        when(state.constraints()).thenThrow(new RuntimeException("forced error"));

        ParseQuery.State.Builder builder = mock(ParseQuery.State.Builder.class);
        when(builder.build()).thenReturn(state);
        ParseQuery query = mock(ParseQuery.class);
        when(query.getBuilder()).thenReturn(builder);

        SubscriptionHandling handling = parseLiveQueryClient.subscribe(query);

        SubscriptionHandling.HandleErrorCallback<ParseObject> errorMockCallback = mock(SubscriptionHandling.HandleErrorCallback.class);
        handling.handleError(errorMockCallback);

        // Trigger a re-subscribe
        webSocketClientCallback.onMessage(createConnectedMessage().toString());

        // This will never get a chance to call op=subscribe, because an exception was thrown
        verify(webSocketClient, never()).send(anyString());

        ArgumentCaptor<LiveQueryException> errorCaptor = ArgumentCaptor.forClass(LiveQueryException.class);
        verify(errorMockCallback, times(1)).onError(eq(query), errorCaptor.capture());

        assertEquals("Error when subscribing", errorCaptor.getValue().getMessage());
        assertNotNull(errorCaptor.getValue().getCause());
    }

    @Test
    public void testErrorWhenSubscribedToCallback() throws Exception {
        ParseQuery<ParseObject> parseQuery = new ParseQuery<>("test");
        SubscriptionHandling<ParseObject> subscriptionHandling = createSubscription(parseQuery,
                mock(SubscriptionHandling.HandleSubscribeCallback.class));

        SubscriptionHandling.HandleErrorCallback<ParseObject> errorMockCallback = mock(SubscriptionHandling.HandleErrorCallback.class);
        subscriptionHandling.handleError(errorMockCallback);
        webSocketClientCallback.onMessage(createErrorMessage(subscriptionHandling.getRequestId()).toString());

        ArgumentCaptor<LiveQueryException> errorCaptor = ArgumentCaptor.forClass(LiveQueryException.class);
        verify(errorMockCallback, times(1)).onError(eq(parseQuery), errorCaptor.capture());

        LiveQueryException genericError = errorCaptor.getValue();
        assertTrue(genericError instanceof LiveQueryException.ServerReportedException);

        LiveQueryException.ServerReportedException serverError = (LiveQueryException.ServerReportedException) genericError;
        assertEquals(serverError.getError(), "testError");
        assertEquals(serverError.getCode(), 1);
        assertEquals(serverError.isReconnect(), true);
    }

    @Test
    public void testHeterogeneousSubscriptions() throws Exception {
        ParseObject.registerSubclass(MockClassA.class);
        ParseObject.registerSubclass(MockClassB.class);

        ParseQuery<MockClassA> query1 = ParseQuery.getQuery(MockClassA.class);
        ParseQuery<MockClassB> query2 = ParseQuery.getQuery(MockClassB.class);
        SubscriptionHandling<MockClassA> handle1 = parseLiveQueryClient.subscribe(query1);
        SubscriptionHandling<MockClassB> handle2 = parseLiveQueryClient.subscribe(query2);

        handle1.handleError(new SubscriptionHandling.HandleErrorCallback<MockClassA>() {
            @Override
            public void onError(ParseQuery<MockClassA> query, LiveQueryException exception) {
                throw new RuntimeException(exception);
            }
        });
        handle2.handleError(new SubscriptionHandling.HandleErrorCallback<MockClassB>() {
            @Override
            public void onError(ParseQuery<MockClassB> query, LiveQueryException exception) {
                throw new RuntimeException(exception);
            }
        });

        SubscriptionHandling.HandleEventCallback<MockClassA> eventMockCallback1 = mock(SubscriptionHandling.HandleEventCallback.class);
        SubscriptionHandling.HandleEventCallback<MockClassB> eventMockCallback2 = mock(SubscriptionHandling.HandleEventCallback.class);

        handle1.handleEvent(SubscriptionHandling.Event.CREATE, eventMockCallback1);
        handle2.handleEvent(SubscriptionHandling.Event.CREATE, eventMockCallback2);

        ParseObject parseObject1 = new MockClassA();
        parseObject1.setObjectId("testId1");

        ParseObject parseObject2 = new MockClassB();
        parseObject2.setObjectId("testId2");

        webSocketClientCallback.onMessage(createObjectCreateMessage(handle1.getRequestId(), parseObject1).toString());
        webSocketClientCallback.onMessage(createObjectCreateMessage(handle2.getRequestId(), parseObject2).toString());

        validateSameObject((SubscriptionHandling.HandleEventCallback) eventMockCallback1, (ParseQuery) query1, parseObject1);
        validateSameObject((SubscriptionHandling.HandleEventCallback) eventMockCallback2, (ParseQuery) query2, parseObject2);
    }

    @Test
    public void testCreateEventWhenSubscribedToCallback() throws Exception {
        ParseQuery<ParseObject> parseQuery = new ParseQuery<>("test");
        SubscriptionHandling<ParseObject> subscriptionHandling = createSubscription(parseQuery,
                mock(SubscriptionHandling.HandleSubscribeCallback.class));

        SubscriptionHandling.HandleEventCallback<ParseObject> eventMockCallback = mock(SubscriptionHandling.HandleEventCallback.class);
        subscriptionHandling.handleEvent(SubscriptionHandling.Event.CREATE, eventMockCallback);

        ParseObject parseObject = new ParseObject("Test");
        parseObject.setObjectId("testId");

        webSocketClientCallback.onMessage(createObjectCreateMessage(subscriptionHandling.getRequestId(), parseObject).toString());

        validateSameObject(eventMockCallback, parseQuery, parseObject);
    }

    @Test
    public void testEnterEventWhenSubscribedToCallback() throws Exception {
        ParseQuery<ParseObject> parseQuery = new ParseQuery<>("test");
        SubscriptionHandling<ParseObject> subscriptionHandling = createSubscription(parseQuery,
                mock(SubscriptionHandling.HandleSubscribeCallback.class));

        SubscriptionHandling.HandleEventCallback<ParseObject> eventMockCallback = mock(SubscriptionHandling.HandleEventCallback.class);
        subscriptionHandling.handleEvent(SubscriptionHandling.Event.ENTER, eventMockCallback);

        ParseObject parseObject = new ParseObject("Test");
        parseObject.setObjectId("testId");

        webSocketClientCallback.onMessage(createObjectEnterMessage(subscriptionHandling.getRequestId(), parseObject).toString());

        validateSameObject(eventMockCallback, parseQuery, parseObject);
    }

    @Test
    public void testUpdateEventWhenSubscribedToCallback() throws Exception {
        ParseQuery<ParseObject> parseQuery = new ParseQuery<>("test");
        SubscriptionHandling<ParseObject> subscriptionHandling = createSubscription(parseQuery,
                mock(SubscriptionHandling.HandleSubscribeCallback.class));

        SubscriptionHandling.HandleEventCallback<ParseObject> eventMockCallback = mock(SubscriptionHandling.HandleEventCallback.class);
        subscriptionHandling.handleEvent(SubscriptionHandling.Event.UPDATE, eventMockCallback);

        ParseObject parseObject = new ParseObject("Test");
        parseObject.setObjectId("testId");

        webSocketClientCallback.onMessage(createObjectUpdateMessage(subscriptionHandling.getRequestId(), parseObject).toString());

        validateSameObject(eventMockCallback, parseQuery, parseObject);
    }

    @Test
    public void testLeaveEventWhenSubscribedToCallback() throws Exception {
        ParseQuery<ParseObject> parseQuery = new ParseQuery<>("test");
        SubscriptionHandling<ParseObject> subscriptionHandling = createSubscription(parseQuery,
                mock(SubscriptionHandling.HandleSubscribeCallback.class));

        SubscriptionHandling.HandleEventCallback<ParseObject> eventMockCallback = mock(SubscriptionHandling.HandleEventCallback.class);
        subscriptionHandling.handleEvent(SubscriptionHandling.Event.LEAVE, eventMockCallback);

        ParseObject parseObject = new ParseObject("Test");
        parseObject.setObjectId("testId");

        webSocketClientCallback.onMessage(createObjectLeaveMessage(subscriptionHandling.getRequestId(), parseObject).toString());

        validateSameObject(eventMockCallback, parseQuery, parseObject);
    }

    @Test
    public void testDeleteEventWhenSubscribedToCallback() throws Exception {
        ParseQuery<ParseObject> parseQuery = new ParseQuery<>("test");
        SubscriptionHandling<ParseObject> subscriptionHandling = createSubscription(parseQuery,
                mock(SubscriptionHandling.HandleSubscribeCallback.class));

        SubscriptionHandling.HandleEventCallback<ParseObject> eventMockCallback = mock(SubscriptionHandling.HandleEventCallback.class);
        subscriptionHandling.handleEvent(SubscriptionHandling.Event.DELETE, eventMockCallback);

        ParseObject parseObject = new ParseObject("Test");
        parseObject.setObjectId("testId");

        webSocketClientCallback.onMessage(createObjectDeleteMessage(subscriptionHandling.getRequestId(), parseObject).toString());

        validateSameObject(eventMockCallback, parseQuery, parseObject);
    }

    @Test
    public void testCreateEventWhenSubscribedToAnyCallback() throws Exception {
        ParseQuery<ParseObject> parseQuery = new ParseQuery<>("test");
        SubscriptionHandling<ParseObject> subscriptionHandling = createSubscription(parseQuery,
                mock(SubscriptionHandling.HandleSubscribeCallback.class));

        SubscriptionHandling.HandleEventsCallback<ParseObject> eventsMockCallback = mock(SubscriptionHandling.HandleEventsCallback.class);
        subscriptionHandling.handleEvents(eventsMockCallback);

        ParseObject parseObject = new ParseObject("Test");
        parseObject.setObjectId("testId");

        webSocketClientCallback.onMessage(createObjectCreateMessage(subscriptionHandling.getRequestId(), parseObject).toString());

        ArgumentCaptor<ParseObject> objectCaptor = ArgumentCaptor.forClass(ParseObject.class);
        verify(eventsMockCallback, times(1)).onEvents(eq(parseQuery), eq(SubscriptionHandling.Event.CREATE), objectCaptor.capture());

        ParseObject newParseObject = objectCaptor.getValue();

        assertEquals(parseObject.getObjectId(), newParseObject.getObjectId());
    }

    @Test
    public void testSubscriptionStoppedAfterUnsubscribe() throws Exception {
        ParseQuery<ParseObject> parseQuery = new ParseQuery<>("test");
        SubscriptionHandling<ParseObject> subscriptionHandling = createSubscription(parseQuery,
                mock(SubscriptionHandling.HandleSubscribeCallback.class));

        SubscriptionHandling.HandleEventCallback<ParseObject> eventMockCallback = mock(SubscriptionHandling.HandleEventCallback.class);
        subscriptionHandling.handleEvent(SubscriptionHandling.Event.CREATE, eventMockCallback);

        SubscriptionHandling.HandleUnsubscribeCallback<ParseObject> unsubscribeMockCallback = mock(
                SubscriptionHandling.HandleUnsubscribeCallback.class);
        subscriptionHandling.handleUnsubscribe(unsubscribeMockCallback);

        parseLiveQueryClient.unsubscribe(parseQuery);
        verify(webSocketClient, times(1)).send(any(String.class));
        webSocketClientCallback.onMessage(createUnsubscribedMessage(subscriptionHandling.getRequestId()).toString());
        verify(unsubscribeMockCallback, times(1)).onUnsubscribe(parseQuery);

        ParseObject parseObject = new ParseObject("Test");
        parseObject.setObjectId("testId");
        webSocketClientCallback.onMessage(createObjectCreateMessage(subscriptionHandling.getRequestId(), parseObject).toString());

        ArgumentCaptor<ParseObject> objectCaptor = ArgumentCaptor.forClass(ParseObject.class);
        verify(eventMockCallback, times(0)).onEvent(eq(parseQuery), objectCaptor.capture());
    }

    @Test
    public void testSubscriptionReplayedAfterReconnect() throws Exception {
        SubscriptionHandling.HandleSubscribeCallback<ParseObject> subscribeMockCallback = mock(SubscriptionHandling.HandleSubscribeCallback.class);

        ParseQuery<ParseObject> parseQuery = new ParseQuery<>("test");
        createSubscription(parseQuery, subscribeMockCallback);

        parseLiveQueryClient.disconnect();
        reconnect();

        verify(webSocketClient, times(2)).send(any(String.class));
    }

    @Test
    public void testSessionTokenSentOnConnect() {
        when(mockUser.getSessionToken()).thenReturn("the token");
        parseLiveQueryClient.reconnect();
        webSocketClientCallback.onOpen();
        verify(webSocketClient, times(1)).send(contains("\"sessionToken\":\"the token\""));
    }

    @Test
    public void testEmptySessionTokenOnConnect() {
        parseLiveQueryClient.reconnect();
        webSocketClientCallback.onOpen();
        verify(webSocketClient, times(1)).send(not(contains("\"sessionToken\":")));
    }

    @Test
    public void testSessionTokenSentOnSubscribe() {
        when(mockUser.getSessionToken()).thenReturn("the token");
        when(webSocketClient.getState()).thenReturn(WebSocketClient.State.CONNECTED);
        parseLiveQueryClient.subscribe(ParseQuery.getQuery("Test"));
        verify(webSocketClient, times(1)).send(and(
                contains("\"op\":\"subscribe\""),
                contains("\"sessionToken\":\"the token\"")));
    }

    @Test
    public void testEmptySessionTokenOnSubscribe() {
        when(webSocketClient.getState()).thenReturn(WebSocketClient.State.CONNECTED);
        parseLiveQueryClient.subscribe(ParseQuery.getQuery("Test"));
        verify(webSocketClient, times(1)).send(contains("\"op\":\"connect\""));
        verify(webSocketClient, times(1)).send(and(
                contains("\"op\":\"subscribe\""),
                not(contains("\"sessionToken\":"))));
    }

    @Test
    public void testClientKeySentOnConnect() throws Exception {
        Parse.Configuration configuration = new Parse.Configuration.Builder(null)
                .applicationId("1234")
                .clientKey("1234")
                .build();
        ParsePlugins.reset();
        ParsePlugins.initialize(null, configuration);

        parseLiveQueryClient = ParseLiveQueryClient.Factory.getClient(new URI(""), new WebSocketClientFactory() {
            @Override
            public WebSocketClient createInstance(WebSocketClient.WebSocketClientCallback webSocketClientCallback, URI hostUrl) {
                TestParseLiveQueryClient.this.webSocketClientCallback = webSocketClientCallback;
                webSocketClient = mock(WebSocketClient.class);
                return webSocketClient;
            }
        }, new ImmediateExecutor());

        parseLiveQueryClient.reconnect();
        webSocketClientCallback.onOpen();
        verify(webSocketClient, times(1)).send(contains("\"clientKey\":\"1234\""));
    }

    @Test
    public void testEmptyClientKeyOnConnect() {
        parseLiveQueryClient.reconnect();
        webSocketClientCallback.onOpen();
        verify(webSocketClient, times(1)).send(not(contains("\"clientKey\":")));
    }

    @Test
    public void testCallbackNotifiedOnUnexpectedDisconnect() throws Exception {
        LoggingCallbacks callbacks = new LoggingCallbacks();
        parseLiveQueryClient.registerListener(callbacks);
        callbacks.transcript.assertNoEventsSoFar();

        // Unexpected close from the server:
        webSocketClientCallback.onClose();
        callbacks.transcript.assertEventsSoFar("onLiveQueryClientDisconnected: false");
    }

    @Test
    public void testCallbackNotifiedOnExpectedDisconnect() throws Exception {
        LoggingCallbacks callbacks = new LoggingCallbacks();
        parseLiveQueryClient.registerListener(callbacks);
        callbacks.transcript.assertNoEventsSoFar();

        parseLiveQueryClient.disconnect();
        verify(webSocketClient, times(1)).close();

        callbacks.transcript.assertNoEventsSoFar();
        // the client is a mock, so it won't actually invoke the callback automatically
        webSocketClientCallback.onClose();
        callbacks.transcript.assertEventsSoFar("onLiveQueryClientDisconnected: true");
    }

    @Test
    public void testCallbackNotifiedOnConnect() throws Exception {
        LoggingCallbacks callbacks = new LoggingCallbacks();
        parseLiveQueryClient.registerListener(callbacks);
        callbacks.transcript.assertNoEventsSoFar();

        reconnect();
        callbacks.transcript.assertEventsSoFar("onLiveQueryClientConnected");
    }

    @Test
    public void testCallbackNotifiedOnSocketError() throws Exception {
        LoggingCallbacks callbacks = new LoggingCallbacks();
        parseLiveQueryClient.registerListener(callbacks);
        callbacks.transcript.assertNoEventsSoFar();

        webSocketClientCallback.onError(new IOException("bad things happened"));
        callbacks.transcript.assertEventsSoFar("onSocketError: java.io.IOException: bad things happened",
                "onLiveQueryClientDisconnected: false");
    }

    @Test
    public void testCallbackNotifiedOnServerError() throws Exception {
        LoggingCallbacks callbacks = new LoggingCallbacks();
        parseLiveQueryClient.registerListener(callbacks);
        callbacks.transcript.assertNoEventsSoFar();

        webSocketClientCallback.onMessage(createErrorMessage(1).toString());
        callbacks.transcript.assertEventsSoFar("onLiveQueryError: com.parse.livequery.LiveQueryException$ServerReportedException: Server reported error; code: 1, error: testError, reconnect: true");
    }

    private SubscriptionHandling<ParseObject> createSubscription(ParseQuery<ParseObject> parseQuery,
            SubscriptionHandling.HandleSubscribeCallback<ParseObject> subscribeMockCallback) throws Exception {
        SubscriptionHandling<ParseObject> subscriptionHandling = parseLiveQueryClient.subscribe(parseQuery).handleSubscribe(subscribeMockCallback);
        webSocketClientCallback.onMessage(createSubscribedMessage(subscriptionHandling.getRequestId()).toString());
        return subscriptionHandling;
    }

    private void validateSameObject(SubscriptionHandling.HandleEventCallback<ParseObject> eventMockCallback,
            ParseQuery<ParseObject> parseQuery,
            ParseObject originalParseObject) {
        ArgumentCaptor<ParseObject> objectCaptor = ArgumentCaptor.forClass(ParseObject.class);
        verify(eventMockCallback, times(1)).onEvent(eq(parseQuery), objectCaptor.capture());

        ParseObject newParseObject = objectCaptor.getValue();

        assertEquals(originalParseObject.getClassName(), newParseObject.getClassName());
        assertEquals(originalParseObject.getObjectId(), newParseObject.getObjectId());
    }

    private void clearConnection() {
        webSocketClient = null;
        webSocketClientCallback = null;
    }

    private void reconnect() {
        parseLiveQueryClient.reconnect();
        webSocketClientCallback.onOpen();
        try {
            webSocketClientCallback.onMessage(createConnectedMessage().toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private static JSONObject createConnectedMessage() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("op", "connected");
        return jsonObject;
    }

    private static JSONObject createSubscribedMessage(int requestId) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("op", "subscribed");
        jsonObject.put("clientId", 1);
        jsonObject.put("requestId", requestId);
        return jsonObject;
    }

    private static JSONObject createUnsubscribedMessage(int requestId) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("op", "unsubscribed");
        jsonObject.put("requestId", requestId);
        return jsonObject;
    }

    private static JSONObject createErrorMessage(int requestId) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("op", "error");
        jsonObject.put("requestId", requestId);
        jsonObject.put("code", 1);
        jsonObject.put("error", "testError");
        jsonObject.put("reconnect", true);
        return jsonObject;
    }

    private static JSONObject createObjectCreateMessage(int requestId, ParseObject parseObject) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("op", "create");
        jsonObject.put("requestId", requestId);
        jsonObject.put("object", PointerEncoder.get().encodeRelatedObject(parseObject));
        return jsonObject;
    }

    private static JSONObject createObjectEnterMessage(int requestId, ParseObject parseObject) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("op", "enter");
        jsonObject.put("requestId", requestId);
        jsonObject.put("object", PointerEncoder.get().encodeRelatedObject(parseObject));
        return jsonObject;
    }

    private static JSONObject createObjectUpdateMessage(int requestId, ParseObject parseObject) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("op", "update");
        jsonObject.put("requestId", requestId);
        jsonObject.put("object", PointerEncoder.get().encodeRelatedObject(parseObject));
        return jsonObject;
    }

    private static JSONObject createObjectLeaveMessage(int requestId, ParseObject parseObject) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("op", "leave");
        jsonObject.put("requestId", requestId);
        jsonObject.put("object", PointerEncoder.get().encodeRelatedObject(parseObject));
        return jsonObject;
    }

    private static JSONObject createObjectDeleteMessage(int requestId, ParseObject parseObject) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("op", "delete");
        jsonObject.put("requestId", requestId);
        jsonObject.put("object", PointerEncoder.get().encodeRelatedObject(parseObject));
        return jsonObject;
    }

    private static class LoggingCallbacks implements ParseLiveQueryClientCallbacks {
        final Transcript transcript = new Transcript();

        @Override
        public void onLiveQueryClientConnected(ParseLiveQueryClient client) {
            transcript.add("onLiveQueryClientConnected");
        }

        @Override
        public void onLiveQueryClientDisconnected(ParseLiveQueryClient client, boolean userInitiated) {
            transcript.add("onLiveQueryClientDisconnected: " + userInitiated);
        }

        @Override
        public void onLiveQueryError(ParseLiveQueryClient client, LiveQueryException reason) {
            transcript.add("onLiveQueryError: " + reason);
        }

        @Override
        public void onSocketError(ParseLiveQueryClient client, Throwable reason) {
            transcript.add("onSocketError: " + reason);
        }
    }

    @ParseClassName("MockA")
    static class MockClassA extends ParseObject {
    }

    @ParseClassName("MockB")
    static class MockClassB extends ParseObject {
    }
}
