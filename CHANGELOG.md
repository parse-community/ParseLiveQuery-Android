## Changelog

### master

### 1.2.2
- CHANGE: Change OkHttp dependency to allow for Android versions < 5.0

### 1.2.1
> NOTE:
>
> This release requires Parse Android SDK >=`1.24.2` as dependency because of the transient depencency on the internalized bolts module.
- CHANGE: Changed bolts references to use internalized bolts depedency. See [#1036](https://github.com/parse-community/Parse-SDK-Android/issues/1036) for details. Thanks to [Manuel Trezza](https://github.com/mtrezza)

### 1.2.0
- Upgrade to avoid depending on outdated Bolts (#107)

### 1.1.0
- Repackage from com.parse to com.parse.livequery
- Bumps to use the latest dependency on JitPack for compatibility

### 1.0.6
- Safely call close on the websocket client with synchronized calls
thanks to @mmimeault (#83)

### 1.0.5
- Back the subscriptions by a thread safe collection map thanks to @mmimeault (#80)

### 1.0.4
- Change package name thanks to @hermanliang (#64)

### 1.0.3
- Fix race condition by ensuring that op=connected has been received before sending a new subscribe event thanks to @jhansche (#48)

### 1.0.2
- Dispatch better disconnect events thanks to @jhansche (#39)

### 1.0.1
- getClient() method can get URL inferred from Parse.initialize() call thanks to @hermanliang (#30)
- Bump to support Android API 25 (#32)
- Bump to Parse Android 1.14.1 dependency (#32).
- Switch from TubeSock library to OkHttp3 web scokets (#28)
- Fix Locale lint errors thanks to @jhansche (#23)
- Connect/disconnect on background executor thanks to @jhansche (#22)
- Refactor ParseLiveQueryClient not to be typed thanks to @jhansche (#27)

### 1.0.0
- Initial 1.0.0 release
