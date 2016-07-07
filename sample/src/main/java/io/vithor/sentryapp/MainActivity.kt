package io.vithor.sentryapp

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import io.vithor.sentry.raven.DSN

import io.vithor.sentry.raven.Sentry
import io.vithor.sentry.raven.SentryEventBuilder


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val yourDSN = DSN.from("")

        Sentry.init(this, yourDSN, "0.4.2", object : Sentry.EventCaptureListener {

            override fun beforeCapture(builder: SentryEventBuilder): SentryEventBuilder {
                Log.d("Test", "Sentry event listener")
                builder.putExtra("test", "Sending test")
                builder.putTag("test", true)
                return builder
            }
        })
        //        Sentry.init(this, yourDSN);

        Sentry.captureMessage("OMG this works woooo")
    }

    override fun onResume() {
        super.onResume()
        if (1 == 1) {
            throw UnsupportedOperationException("Testo")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}
