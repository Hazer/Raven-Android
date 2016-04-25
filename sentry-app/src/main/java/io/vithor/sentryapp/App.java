package io.vithor.sentryapp;

import android.app.Application;
import android.support.annotation.NonNull;
import android.util.Log;

import io.vithor.sentry.raven.DefaultSentryCaptureListener;
import io.vithor.sentry.raven.Sentry;
import io.vithor.sentry.raven.SentryEventBuilder;

/**
 * Created by Hazer on 2/26/16.
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        String yourDSN = "";
        Sentry.INSTANCE.init(this, yourDSN, "0.4.2", new DefaultSentryCaptureListener() {

            @NonNull
            @Override
            public SentryEventBuilder beforeCapture(@NonNull SentryEventBuilder builder) {
                Log.d("Test", "Sentry event listener");
                builder.putExtra("test", "Sending test");
                builder.putTag("test", true);
                return super.beforeCapture(builder);
            }
        });

        Sentry.INSTANCE.captureMessage("OMG this works woooo");
    }
}
