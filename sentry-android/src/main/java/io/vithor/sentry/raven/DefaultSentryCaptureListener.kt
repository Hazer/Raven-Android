package io.vithor.sentry.raven

import android.support.annotation.CallSuper
import android.util.Log

import java.util.ArrayList

/**
 * Created by Hazer on 2/25/16.
 */
open class DefaultSentryCaptureListener(val limit: Int = 5) : Sentry.SentryEventCaptureListener() {

    private val listeners = ArrayList<Sentry.SentryEventCaptureListener>()

    @CallSuper
    override fun beforeCapture(builder: SentryEventBuilder): SentryEventBuilder {
        for (listener in listeners) {
            listener.beforeCapture(builder)
        }
        return builder
    }

    fun addListener(listener: Sentry.SentryEventCaptureListener) {
        if (!listeners.contains(listener) && listeners.size < limit) {
            listeners.add(listener)
        } else {
            Log.e(javaClass.name, "Too much sentry listeners.")
        }
    }

    fun removeListener(listener: Sentry.SentryEventCaptureListener) {
        listeners.remove(listener)
    }

    fun clearListeners() {
        listeners.clear()
    }
}