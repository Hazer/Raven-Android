package io.vithor.sentry.raven

import android.content.Context

internal class SentryUncaughtExceptionHandler(private val defaultExceptionHandler: Thread.UncaughtExceptionHandler,
                                             private val context: Context?) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, e: Throwable?) {
        // Here you should have a more robust, permanent record of problems
        var builder: SentryEventBuilder = SentryEventBuilder(e, SentryEventBuilder.SentryEventLevel.FATAL)

        builder.setRelease(Sentry.sharedClient.release)

        builder = Sentry.sharedClient.captureListener.beforeCapture(builder)// ?: builder

        InternalStorage.instance.addRequest(SentryEventRequest(builder))

        //call original handler
        defaultExceptionHandler.uncaughtException(thread, e)
    }
}