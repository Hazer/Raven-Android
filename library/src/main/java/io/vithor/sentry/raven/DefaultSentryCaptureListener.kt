package io.vithor.sentry.raven

import android.support.annotation.CallSuper
import android.util.Log

/**
 * Created by Vithorio Polten on 2/25/16.
 */
internal class DefaultSentryCaptureListener(val limit: Int = 5, internal val mainCaptureListener: Sentry.EventCaptureListener? = null) {
    private val registeredListeners = mutableMapOf<String, Sentry.EventCaptureListener>()

    private val staticInfo = AndroidHelper.createStaticInfo()

    @CallSuper
    fun beforeCapture(builder: SentryEventBuilder): SentryEventBuilder {
        builder.setRelease(Sentry.release)

        builder.putTags(staticInfo.map)
        try {
            mainCaptureListener?.beforeCapture(builder)

            for (listener in registeredListeners.values) {
                listener.beforeCapture(builder)
            }
        } catch(e: Exception) {
            Log.e(javaClass.simpleName, "Failed applying capture listeners", e)
        }
        return builder
    }

    fun addListener(tag: String, eventListener: Sentry.EventCaptureListener) {
        if (registeredListeners.size < limit) {
            registeredListeners.put(tag, eventListener)
        } else {
            Log.e(javaClass.simpleName, "Too much sentry listeners.")
        }
    }

    fun removeListener(tag: String) {
        registeredListeners.remove(tag)
    }

    fun clearListeners() {
        registeredListeners.clear()
    }
}