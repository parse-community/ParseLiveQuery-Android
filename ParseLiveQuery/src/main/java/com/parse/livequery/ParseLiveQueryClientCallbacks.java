package com.parse.livequery;

public interface ParseLiveQueryClientCallbacks {
    void onLiveQueryClientConnected(ParseLiveQueryClient client);

    void onLiveQueryClientDisconnected(ParseLiveQueryClient client, boolean userInitiated);

    void onLiveQueryError(ParseLiveQueryClient client, LiveQueryException reason);

    void onSocketError(ParseLiveQueryClient client, Throwable reason);
}
