# Parse LiveQuery Client for Android
[![Build Status][build-status-svg]][build-status-link]
[![Coverage Status][coverage-status-svg]][coverage-status-link]
[![Maven Central][maven-svg]][maven-link]
[![License][license-svg]][license-link]

`ParseQuery` is one of the key concepts for Parse. It allows you to retrieve `ParseObject`s by specifying some conditions, making it easy to build apps such as a dashboard, a todo list or even some strategy games. However, `ParseQuery` is based on a pull model, which is not suitable for apps that need real-time support.

Suppose you are building an app that allows multiple users to edit the same file at the same time. `ParseQuery` would not be an ideal tool since you can not know when to query from the server to get the updates.

To solve this problem, we introduce Parse LiveQuery. This tool allows you to subscribe to a `ParseQuery` you are interested in. Once subscribed, the server will notify clients whenever a `ParseObject` that matches the `ParseQuery` is created or updated, in real-time.

## Setup Server

Parse LiveQuery contains two parts, the LiveQuery server and the LiveQuery clients. In order to use live queries, you need to set up both of them.

The easiest way to setup the LiveQuery server is to make it run with the [Open Source Parse Server](https://github.com/parse-community/parse-server/wiki/Parse-LiveQuery#server-setup).

## Setup Client
Download [the latest JAR][latest] or define in Gradle:

```groovy
dependencies {
  compile 'com.parse:parse-livequery-android:1.0.6'
}
```

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].

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

## Build commands
Everything can done through the supplied gradle wrapper:

### Compile a JAR
```
./gradlew clean jarRelease
```
Outputs can be found in `ParseLiveQuery/build/libs/`

### Run the Tests
```
./gradlew clean testDebug
```
Results can be found in `ParseLiveQuery/build/reports/`

### Get Code Coverage Reports
```
./gradlew clean jacocoTestReport
```
Results can be found in `ParseLiveQuery/build/reports/`

## How Do I Contribute?
We want to make contributing to this project as easy and transparent as possible. Please refer to the [Contribution Guidelines](CONTRIBUTING.md).

-----

As of April 5, 2017, Parse, LLC has transferred this code to the parse-community organization, and will no longer be contributing to or distributing this code.

 [parse.com]: https://www.parse.com/products/android
 [guide]: https://www.parse.com/docs/android/guide
 [blog]: https://blog.parse.com/

 [latest]: https://search.maven.org/remote_content?g=com.parse&a=parse-livequery-android&v=LATEST
 [snap]: https://oss.sonatype.org/content/repositories/snapshots/

 [build-status-svg]: https://img.shields.io/travis/parse-community/ParseLiveQuery-Android/master.svg
 [build-status-link]: https://travis-ci.org/parse-community/ParseLiveQuery-Android/branches
 [coverage-status-svg]: https://img.shields.io/codecov/c/github/parse-community/ParseLiveQuery-Android/master.svg
 [coverage-status-link]: https://codecov.io/github/parse-community/ParseLiveQuery-Android?branch=master
 [maven-svg]: https://maven-badges.herokuapp.com/maven-central/com.parse/parse-livequery-android/badge.svg?style=flat
 [maven-link]: https://maven-badges.herokuapp.com/maven-central/com.parse/parse-livequery-android

 [license-svg]: https://img.shields.io/badge/license-BSD-lightgrey.svg
 [license-link]: https://github.com/parse-community/ParseLiveQuery-Android/blob/master/LICENSE
