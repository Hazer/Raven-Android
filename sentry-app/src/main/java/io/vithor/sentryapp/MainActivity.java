package io.vithor.sentryapp;

import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import org.json.JSONException;

import io.vithor.sentry.raven.DefaultSentryCaptureListener;
import io.vithor.sentry.raven.Sentry;
import io.vithor.sentry.raven.SentryEventBuilder;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String yourDSN = "";
        Sentry.INSTANCE.init(this, yourDSN, "0.4.2", new DefaultSentryCaptureListener() {

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
//        Sentry.init(this, yourDSN);
        Sentry.INSTANCE.captureMessage("OMG this works woooo");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
