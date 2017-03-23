package com.parse;

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

import java.net.URI;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Executor;

import bolts.Task;

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

    private PauseableExecutor executor;
    private WebSocketClient webSocketClient;
    private WebSocketClient.WebSocketClientCallback webSocketClientCallback;
    private ParseLiveQueryClient<ParseObject> parseLiveQueryClient;

    private ParseUser mockUser;

    @Before
    public void setUp() throws Exception {
        ParsePlugins.initialize("1234", "1234");

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

        executor = new PauseableExecutor();

        parseLiveQueryClient = ParseLiveQueryClient.Factory.getClient(new URI(""), new WebSocketClientFactory() {
            @Override
            public WebSocketClient createInstance(WebSocketClient.WebSocketClientCallback webSocketClientCallback, URI hostUrl) {
                TestParseLiveQueryClient.this.webSocketClientCallback = webSocketClientCallback;
                webSocketClient = mock(WebSocketClient.class);
                return webSocketClient;
            }
        }, executor);
        reconnect();
    }

    @After
    public void tearDown() throws Exception {
        ParseCorePlugins.getInstance().reset();
        ParsePlugins.reset();
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
        when(state.toJSON(any(ParseEncoder.class))).thenThrow(new RuntimeException("forced error"));

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
        when(mockUser.getSessionToken()).thenReturn("the token");
        when(webSocketClient.getState()).thenReturn(WebSocketClient.State.CONNECTED);
        parseLiveQueryClient.subscribe(ParseQuery.getQuery("Test"));
        verify(webSocketClient, times(1)).send(contains("\"op\":\"connect\""));
        verify(webSocketClient, times(1)).send(and(
                contains("\"op\":\"subscribe\""),
                contains("\"sessionToken\":\"the token\"")));
    }

    @Test
    public void testDisconnectOnBackgroundThread() throws Exception {
        executor.pause();

        parseLiveQueryClient.disconnect();
        verify(webSocketClient, never()).close();
        assertTrue(executor.advanceOne());
        verify(webSocketClient, times(1)).close();
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

        assertEquals(originalParseObject.getObjectId(), newParseObject.getObjectId());
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

    private static class PauseableExecutor implements Executor {
        private boolean isPaused = false;
        private final Queue<Runnable> queue = new LinkedList<>();

        void pause() {
            isPaused = true;
        }

        void unpause() {
            if (isPaused) {
                isPaused = false;

                //noinspection StatementWithEmptyBody
                while (advanceOne()) {
                    // keep going
                }
            }
        }

        boolean advanceOne() {
            Runnable next = queue.poll();
            if (next != null) next.run();
            return next != null;
        }

        @Override
        public void execute(Runnable runnable) {
            if (isPaused) {
                queue.add(runnable);
            } else {
                runnable.run();
            }
        }
    }
}
