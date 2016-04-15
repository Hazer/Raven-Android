package io.vithor.sentry.raven

import android.support.annotation.CallSuper
import android.util.Log
import java.util.*

/**
 * Created by Vithorio Polten on 2/25/16.
 */
open class DefaultSentryCaptureListener(val limit: Int = 5) : Sentry.EventCaptureListener() {

    private val listeners = HashMap<String, Sentry.EventCaptureListener>()

    @CallSuper
    override fun beforeCapture(builder: SentryEventBuilder): SentryEventBuilder {
        try {
            for (listener in listeners.values) {
                listener.beforeCapture(builder)
            }
        } catch(e: Exception) {
            Log.e(javaClass.simpleName, "Failed applying capture listeners", e)
        }
        return builder
    }

    fun addListener(tag: String, eventListener: Sentry.EventCaptureListener) {
        if (!listeners.contains(tag) && listeners.size < limit) {
            listeners.put(tag, eventListener)
        } else {
            Log.e(javaClass.simpleName, "Too much sentry listeners.")
        }
    }

    fun removeListener(tag: String) {
        listeners.remove(tag)
    }

    fun clearListeners() {
        listeners.clear()
    }
}