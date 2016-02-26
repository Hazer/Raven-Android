# Raven-Android - Sentry Client for Android (Fork from [Sentry-Android](https://github.com/joshdholtz/Sentry-Android) originally made by [joshdholtz](https://github.com/joshdholtz))
It does what every Sentry client needs to do

Below is an example of how to register Raven-Android to handle uncaught exceptions

```xml
<!-- REQUIRED to send captures to Sentry -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- OPTIONAL but makes Sentry-Android smarter -->
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

``` java
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        
        String yourDSN = "https://publicKey:secretKey@sentry.mydomain.com:8080/2";
        String yourReleaseVersion = "0.4.2";

        Sentry.INSTANCE.init(this, yourDSN, yourReleaseVersion, new DefaultSentryCaptureListener() {

            @NonNull
            @Override
            public SentryEventBuilder beforeCapture(@NonNull SentryEventBuilder builder) {
                Log.d("Test", "Sentry event listener");
                try {
                    builder.getExtra().put("test", "Sending test");
                    builder.getTags().put("test", true);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return super.beforeCapture(builder);
            }
        });

        Sentry.INSTANCE.captureMessage("OMG this works woooo");
    }
}
```

### Updates

Version | Changes
--- | ---
**0.1.0** | Initial release (thanks [joshdholtz](https://github.com/joshdholtz))

## How To Get Started

### Gradle
Available in jitpack.io
```
repositories {
	...
	maven { url "https://jitpack.io" }
}
```

```
compile 'com.github.Hazer:Raven-Android:0.1.0'
```

## This Is How We Do It

### Permissions in manifest

The AndroidManifest.xml requires the permission `android.permission.INTERNET` and would like the permission `android.permission.ACCESS_NETWORK_STATE` even though optional.

```xml
<!-- REQUIRED to send captures to Sentry -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- OPTIONAL but makes Sentry-Android smarter -->
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### Capture a message
``` java
Sentry.INSTANCE.captureMessage("Something significant may have happened");
```

``` kotlin
Sentry.captureMessage("Something significant may have happened");
```

### Capture a caught exception
``` java
try {
	JSONObject obj = new JSONObjet();
} catch (JSONException e) { 
	Sentry.INSTANCE.captureException(e);
}
```

``` kotlin
TODO: Missing doc
```

### Capture custom event
``` java
Sentry.INSTANCE.captureEvent(new Sentry.SentryEventBuilder()
	.setMessage("Being awesome")
	.setCulprit("Josh Holtz")
	.setTimestamp(System.currentTimeMillis())
);
```

``` kotlin
TODO: Missing doc
```

### Set a listener to intercept the SentryEventBuilder before each capture
TODO: Update info
TODO: Missing doc
``` java
// CALL THIS BEFORE CALLING Sentry.init
// Sets a listener to intercept the SentryEventBuilder before 
// each capture to set values that could change state
Sentry.INSTANCE.setCaptureListener(new DefaultSentryCaptureListener() {

	@Override
	public SentryEventBuilder beforeCapture(SentryEventBuilder builder) {
		
		// Needs permission - <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
		ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
		NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

		// Sets extra key if wifi is connected
		try {
			builder.getExtra().put("wifi", String.valueOf(mWifi.isConnected()));
			builder.getTags().put("tag_1", "value_1");
		} catch (JSONException e) {}
		
		return builder;
	}
	
});

```

``` kotlin
TODO: Missing doc
```

## License

Sentry-Android is available under the MIT license. See the LICENSE file for more info.
