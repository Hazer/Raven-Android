package io.vithor.sentry.raven

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.support.annotation.Keep
import android.util.Log
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import io.vithor.sentry.raven.SentryEventBuilder.SentryEventLevel
import java.io.*
import io.vithor.sentry.raven.YafDelegates.mustInitialize
import java.lang.ref.WeakReference

class Sentry private constructor(
        context: Context,
        internal val dsn: DSN,
        private val packageName: String,
        mainCaptureListener: Sentry.EventCaptureListener? = null) {

    internal val contextWeak: WeakReference<Context?>

    private val verifySsl: Boolean by lazy { dsn.verifySsl }

    internal val captureListener: DefaultSentryCaptureListener

    init {
        this.contextWeak = WeakReference(context.applicationContext)
        this.captureListener = DefaultSentryCaptureListener(mainCaptureListener = mainCaptureListener)
    }

    private fun setupUncaughtExceptionHandler() {

        val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (currentHandler != null) {
            Log.d("Debugged", "current handler class=" + currentHandler.javaClass.name)
        }

        // don'throwable register again if already registered
        if (currentHandler !is SentryUncaughtExceptionHandler) {
            // Register default exceptions handler
            Thread.setDefaultUncaughtExceptionHandler(SentryUncaughtExceptionHandler(currentHandler))
        }

        sendAllCachedCapturedEvents()
    }

    private fun doCaptureEventPost(sentryRequest: SentryEventRequest) {

        if (!shouldAttemptPost()) {
            InternalStorage.instance.addRequest(sentryRequest)
            return
        }

        val url = "${dsn.hostURI}api/${dsn.projectId}/store/"
        val postClient = url.httpPost()
                .header(
                        "X-Sentry-Auth" to createXSentryAuthHeader(),
                        "User-Agent" to sentryClientInfo,
                        "Content-Type" to "application/json; charset=utf-8"
                )
                .body(sentryRequest.requestData ?: "", charset("UTF-8"))
                .timeout(10000)

        if (!verifySsl) {

        }

        postClient.interrupt { request ->
            println("${request.cUrlString()} was interrupted and cancelled")
        }.responseString { request, response, result ->

            Log.d("Sentry cUrl", request.cUrlString())

            when (result) {
                is Result.Success -> {
                    Log.d(TAG, "SendEvent - ${response.httpStatusCode} ${result.value}")
                    InternalStorage.instance.removeBuilder(sentryRequest)
                }
                is Result.Failure -> {
                    Log.d(TAG, "SendEvent - ${response.httpStatusCode} ${result.error.message}", result.error.cause)
                    InternalStorage.instance.addRequest(sentryRequest)
                }
            }
        }
    }

    private fun shouldAttemptPost(): Boolean {
        val pm = contextWeak.get()?.packageManager
        val hasPerm = pm?.checkPermission(android.Manifest.permission.ACCESS_NETWORK_STATE, packageName)
        if (hasPerm == PackageManager.PERMISSION_DENIED) {
            return true
        }

        val connectivityManager = contextWeak.get()?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = connectivityManager.activeNetworkInfo
        return activeNetworkInfo != null && activeNetworkInfo.isConnected
    }

    fun captureEvent(builder: SentryEventBuilder) {
        try {
            val b = captureListener.beforeCapture(builder)
            val request = SentryEventRequest(b)

            Log.d(TAG, "Request - ${request.requestData}")

            // Check if on main thread - if not, run on main thread
            if (Looper.myLooper() == Looper.getMainLooper()) {
                doCaptureEventPost(request)
            } else if (contextWeak.get() != null) {

                val thread = object : HandlerThread("SentryThread") {

                }
                thread.start()
                val runnable = Runnable { doCaptureEventPost(request) }
                Handler(thread.looper).post(runnable)
            }
        } catch (e: Exception) {
            Log.e("Sentry", "Exception during Sentry capture.", e)
        }
    }

    fun sendAllCachedCapturedEvents() {
        val unsentRequests = InternalStorage.instance.getUnsentRequestsSafeThread()
        Log.d(TAG, "Sending up " + unsentRequests.size + " cached response(s)")
        for (request in unsentRequests) {
            doCaptureEventPost(request)
        }
    }

    private fun createXSentryAuthHeader(): String {
        Log.d("Sentry", "URI - " + dsn.hostURI)

        return """Sentry sentry_version=$sentryVersion,
        sentry_client=$sentryClientInfo,
        sentry_timestamp=${System.currentTimeMillis()},
        sentry_key=${dsn.publicKey},
        sentry_secret=${dsn.secretKey}"""
    }

    interface EventCaptureListener {
        fun beforeCapture(builder: SentryEventBuilder): SentryEventBuilder
    }

    companion object INSTANCE {

        internal val TAG = "Sentry"
        internal var sharedClient: Sentry by mustInitialize<Sentry>("Sentry::init must be called in your Application in order to use this framework.")

        internal var release: String? = null

        private val sentryClientInfo: String by lazy { "kotlin-raven-android/" + BuildConfig.LIBRARY_VERSION }

        private val sentryVersion: Int = 8

        fun init(context: Context, dsn: String, mainCaptureListener: EventCaptureListener) {
            init(context = context, dsn = dsn, release = null, mainCaptureListener = mainCaptureListener)
        }

        @JvmOverloads fun init(context: Context, dsn: String, release: String? = null, mainCaptureListener: EventCaptureListener? = null) {
            sharedClient = Sentry(context, DSN(dsn), context.packageName, mainCaptureListener)

            Sentry.release = release ?: getReleaseFromPackage(context)

            sharedClient.setupUncaughtExceptionHandler()
        }

        private fun getReleaseFromPackage(context: Context): String? {
            return AndroidHelper.version(context)
        }

        @Keep
        fun addCaptureListener(tag: String, listener: EventCaptureListener) {
            sharedClient.captureListener.addListener(tag, listener)
        }

        @Keep
        fun removeCaptureListener(tag: String) {
            sharedClient.captureListener.removeListener(tag)
        }

        private fun getAllGetParams(dsn: DSN): List<Pair<String, Any?>>? {
            val uri = Uri.parse(dsn.hostURI.toASCIIString())
            val paramNames = uri.queryParameterNames

            val params = mutableListOf<Pair<String, String?>>()
            for (name in paramNames) {
                params.add(
                        name to uri.getQueryParameter(name)
                )
            }
            //                        var params: List<NameValuePair>? = null
            //            try {
            //                params = URLEncodedUtils.parse(dsn.hostURI, "UTF-8")
            //            } catch (e: URISyntaxException) {
            //                e.printStackTrace()
            //            }
            return params
        }

        @JvmOverloads fun captureMessage(message: String, level: SentryEventLevel = SentryEventLevel.INFO) {
            sharedClient.captureEvent(SentryEventBuilder().setMessage(message).setLevel(level))
        }

        @Keep
        @JvmOverloads fun captureException(throwable: Throwable, level: SentryEventLevel = SentryEventLevel.ERROR) {
            val message = throwable.message ?: throwable.cause?.message
            val culprit = getCause(throwable, message)

            sharedClient.captureEvent(
                    SentryEventBuilder()
                            .setMessage(message ?: "")
                            .setCulprit(culprit)
                            .setLevel(level)
                            .setException(throwable)
            )
        }

        @Keep
        fun captureEvent(builder: SentryEventBuilder) {
            sharedClient.captureEvent(builder)
        }

        fun captureUncaughtException(context: Context, throwable: Throwable) {
            val result = StringWriter()
            val printWriter = PrintWriter(result)
            throwable.printStackTrace(printWriter)
            try {
                // Random number to avoid duplicate files
                val random = System.currentTimeMillis()

                // Embed version in stacktrace filename
                val stacktrace = File(getStacktraceLocation(context), "raven-" + random.toString() + ".stacktrace")
                Log.d(TAG, "Writing unhandled exception to: " + stacktrace.absolutePath)

                // Write the stacktrace to disk
                val oos = ObjectOutputStream(FileOutputStream(stacktrace))
                oos.writeObject(throwable)
                oos.flush()
                // Close up everything
                oos.close()
            } catch (ebos: Exception) {
                // Nothing much we can do about this - the game is over
                ebos.printStackTrace()
            }

            Log.d(TAG, result.toString())
        }

        internal fun getCause(throwable: Throwable?, culprit: String?): String {
            if (throwable != null) {
                for (stackTrace in throwable.stackTrace) {
                    if (stackTrace.toString().contains(sharedClient.packageName)) {
                        return stackTrace.toString()
                    }
                }
            }
            return culprit ?: ""
        }

        private fun getStacktraceLocation(context: Context): File {
            return File(context.cacheDir, "crashes")
        }

        private fun getStackTrace(t: Throwable): String {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            t.printStackTrace(pw)
            return sw.toString()
        }


    }

}
