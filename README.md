# Parse LiveQuery Client for Android
[![License][license-svg]][license-link] [![Build Status][build-status-svg]][build-status-link] [![](https://jitpack.io/v/parse-community/ParseLiveQuery-Android.svg)](https://jitpack.io/#parse-community/ParseLiveQuery-Android)

`ParseQuery` is one of the key concepts for Parse. It allows you to retrieve `ParseObject`s by specifying some conditions, making it easy to build apps such as a dashboard, a todo list or even some strategy games. However, `ParseQuery` is based on a pull model, which is not suitable for apps that need real-time support.

Suppose you are building an app that allows multiple users to edit the same file at the same time. `ParseQuery` would not be an ideal tool since you can not know when to query from the server to get the updates.

To solve this problem, we introduce Parse LiveQuery. This tool allows you to subscribe to a `ParseQuery` you are interested in. Once subscribed, the server will notify clients whenever a `ParseObject` that matches the `ParseQuery` is created or updated, in real-time.

## Setup Server

Parse LiveQuery contains two parts, the LiveQuery server and the LiveQuery clients. In order to use live queries, you need to set up both of them.

The easiest way to setup the LiveQuery server is to make it run with the [Open Source Parse Server](https://github.com/parse-community/parse-server/wiki/Parse-LiveQuery#server-setup).

## Dependency

Add this in your root `build.gradle` file (**not** your module `build.gradle` file):

```gradle
allprojects {
	repositories {
		...
		maven { url "https://jitpack.io" }
	}
}
```

Then, add the library to your project `build.gradle`
```gradle
dependencies {
    implementation 'com.github.parse-community:ParseLiveQuery-Android:latest.version.here'
}
```

## Use Client

The LiveQuery client interface is based around the concept of `Subscriptions`. You can register any `ParseQuery` for live updates from the associated live query server, by simply calling `subscribe()` on the client:
```java
// Parse.initialize should be called first

ParseLiveQueryClient parseLiveQueryClient = ParseLiveQueryClient.Factory.getClient();
```

### Creating Live Queries

Live querying depends on creating a subscription to a `ParseQuery`:

```java
ParseQuery<Message> parseQuery = ParseQuery.getQuery(Message.class);

SubscriptionHandling<ParseObject> subscriptionHandling = parseLiveQueryClient.subscribe(parseQuery)
```

Once you've subscribed to a query, you can `handle` events on them, like so:
```java
subscriptionHandling.handleEvents(new SubscriptionHandling.HandleEventsCallback<ParseObject>() {
    @Override
    public void onEvents(ParseQuery<ParseObject> query, SubscriptionHandling.Event event, ParseObject object) {
        // HANDLING all events
    }
})
```

You can also handle a single type of event, if that's all you're interested in:
```java
subscriptionHandling.handleEvent(SubscriptionHandling.Event.CREATE, new SubscriptionHandling.HandleEventCallback<ParseObject>() {
    @Override
    public void onEvent(ParseQuery<ParseObject> query, ParseObject object) {
        // HANDLING create event
    }
})
```

Handling errors is and other events is similar, take a look at the `SubscriptionHandling` class for more information.

## Advanced Usage

If you wish to pass in your own OkHttpClient instance for troubleshooting or custom configs, you can instantiate the client as follows:

```java
ParseLiveQueryClient parseLiveQueryClient = ParseLiveQueryClient.Factory.getClient(new OkHttp3SocketClientFactory(new OkHttpClient()));
```

The URL is determined by the Parse initialization, but you can override by specifying a `URI` object:

```java
ParseLiveQueryClient parseLiveQueryClient = ParseLiveQueryClient.Factory.getClient(new URI("wss://myparseinstance.com"));
```

Note: The expected protocol for URI is `ws` instead of `http`, like in this example: `URI("ws://192.168.0.1:1337/1")`.

## How Do I Contribute?
We want to make contributing to this project as easy and transparent as possible. Please refer to the [Contribution Guidelines](https://github.com/parse-community/Parse-SDK-Android/blob/master/CONTRIBUTING.md).

-----

As of April 5, 2017, Parse, LLC has transferred this code to the parse-community organization, and will no longer be contributing to or distributing this code.

 [build-status-svg]: https://travis-ci.org/parse-community/ParseLiveQuery-Android.svg?branch=master
 [build-status-link]: https://travis-ci.org/parse-community/ParseLiveQuery-Android

 [license-svg]: https://img.shields.io/badge/license-BSD-lightgrey.svg
 [license-link]: https://github.com/parse-community/ParseLiveQuery-Android/blob/master/LICENSE
