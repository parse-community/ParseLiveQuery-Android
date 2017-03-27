package com.parse;

public interface ParseLiveQueryClientCallbacks {
    void onLiveQueryClientConnected(ParseLiveQueryClient client);

    void onLiveQueryClientDisconnected(ParseLiveQueryClient client);

    void onLiveQueryError(ParseLiveQueryClient client, LiveQueryException reason);

    void onSocketError(ParseLiveQueryClient client, Throwable reason);
}
