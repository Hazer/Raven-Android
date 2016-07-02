package io.vithor.sentry.raven

internal class SentryUncaughtExceptionHandler(
        private val defaultExceptionHandler: Thread.UncaughtExceptionHandler) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, e: Throwable?) {
        // Here you should have a more robust, permanent record of problems
        val builder = Sentry.sharedClient.captureListener.beforeCapture(SentryEventBuilder(e, SentryEventBuilder.SentryEventLevel.FATAL))// ?: builder

        InternalStorage.instance.addRequest(SentryEventRequest(builder))

        //call original handler
        defaultExceptionHandler.uncaughtException(thread, e)
    }
}