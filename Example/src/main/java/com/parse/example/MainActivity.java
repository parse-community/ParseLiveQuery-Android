package com.parse.example;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.parse.GetCallback;
import com.parse.Parse;
import com.parse.ParseException;
import com.parse.ParseLiveQueryClient;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.SubscriptionHandling;
import com.parse.interceptors.ParseLogInterceptor;

import java.net.URI;
import java.net.URISyntaxException;

public class MainActivity extends AppCompatActivity {

    String URL = "http://192.168.3.9:1337/parse/";
    String wsURL = "ws://192.168.3.9:1337/parse/";
    String applicationId = "mytest";
    String DEBUG_TAG = "debug";

    Room mRoom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ParseObject.registerSubclass(Room.class);
        ParseObject.registerSubclass(Message.class);

        Parse.setLogLevel(Parse.LOG_LEVEL_DEBUG);
        Parse.initialize(new Parse.Configuration.Builder(this)
                .applicationId(applicationId) // should correspond to APP_ID env variable
                .clientKey("clientKey")  // set explicitly blank unless clientKey is configured on Parse server
                .addNetworkInterceptor(new ParseLogInterceptor())
                .server(URL).build());

        ParseQuery<Room> roomParseQuery = ParseQuery.getQuery(Room.class);
        // fixme - why isn't it retrieving
        roomParseQuery.whereEqualTo("name", "test");
        roomParseQuery.getFirstInBackground(new GetCallback<Room>() {
            @Override
            public void done(Room room, ParseException e) {
                if (e != null) {
                    Log.d(DEBUG_TAG, "Found exception" + e);
                    e.printStackTrace();
                } else {
                    Log.d(DEBUG_TAG, "Found room: " + room);
                }
                mRoom = room;

                ParseLiveQueryClient parseLiveQueryClient = ParseLiveQueryClient.Factory.getClient();

                if (parseLiveQueryClient != null) {
                    // op=subscribe, className=Message, roomName=test, requestId=1
                    // op=subscribe, className=Message, roomName=null, requestId=1, order=createdAt
                    ParseQuery<Message> parseQuery = ParseQuery.getQuery(Message.class);
                    // FIXME
                    parseQuery.whereEqualTo("roomName", "test");

                    // FIXME (rhu) - parse query hates created at
//                    parseQuery.orderByAscending("createdAt");

                    SubscriptionHandling<Message> subscriptionHandling = parseLiveQueryClient
                            .subscribe(parseQuery);
                    subscriptionHandling.handleEvent(SubscriptionHandling.Event.CREATE,
                            new SubscriptionHandling.HandleEventCallback<Message>() {
                                @Override
                                public void onEvent(ParseQuery<Message> query, Message object) {
                                    Log.d(DEBUG_TAG, "Message" + object);
                                }
                            });
                }
            }
        });


    }
}
