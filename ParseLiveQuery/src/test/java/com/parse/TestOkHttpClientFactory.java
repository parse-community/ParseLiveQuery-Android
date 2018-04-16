package com.parse;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.net.URI;

import okhttp3.OkHttpClient;

import static com.parse.WebSocketClient.State.NONE;

@RunWith(MockitoJUnitRunner.class)
public class TestOkHttpClientFactory {

    @Mock
    private OkHttpClient okHttpClientMock;

    @Mock
    private WebSocketClient.WebSocketClientCallback webSocketClientCallbackMock;

    private OkHttp3SocketClientFactory okHttp3SocketClientFactory;
    private WebSocketClient webSocketClient;

    @Before
    public void setUp() throws Exception {
        okHttp3SocketClientFactory = new OkHttp3SocketClientFactory(okHttpClientMock);
        webSocketClient = okHttp3SocketClientFactory.createInstance(webSocketClientCallbackMock, new URI("http://www.test.com"));
    }

    @After
    public void tearDown() {
        webSocketClient.close();
    }

    @Test
    public void testClientCloseWithoutOpenShouldBeNoOp()  {
        Assert.assertEquals(NONE, webSocketClient.getState());
        webSocketClient.close();
        webSocketClient.send("test");
        Assert.assertEquals(NONE, webSocketClient.getState());
    }

}
