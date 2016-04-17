package io.vithor.sentry.raven

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import com.github.kittinunf.fuel.core.Manager
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import io.vithor.sentry.raven.SentryEventBuilder.SentryEventLevel
import java.io.*

class Sentry //    private static final String DEFAULT_BASE_URL = "https://app.getsentry.com";

private constructor() {

    internal var context: Context? = null

    lateinit private var dsn: DSN
    lateinit private var packageName: String

    private var verifySsl: Boolean = true

    internal var release: String? = null

    internal var captureListener = DefaultSentryCaptureListener()

    private fun setupUncaughtExceptionHandler() {

        val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (currentHandler != null) {
            Log.d("Debugged", "current handler class=" + currentHandler.javaClass.name)
        }

        // don't register again if already registered
        if (currentHandler !is SentryUncaughtExceptionHandler) {
            // Register default exceptions handler
            Thread.setDefaultUncaughtExceptionHandler(
                    SentryUncaughtExceptionHandler(currentHandler, context))
        }

        sendAllCachedCapturedEvents()
    }

    abstract class EventCaptureListener {
        abstract fun beforeCapture(builder: SentryEventBuilder): SentryEventBuilder
    }

    companion object INSTANCE {

        internal val TAG = "Sentry"

        internal val sharedClient: Sentry by lazy { Sentry() }

        private fun init(captureListener: DefaultSentryCaptureListener? = null) {
            if (captureListener != null) {
                sharedClient.captureListener = captureListener
            }
        }

        //    public static void init(Context context, String dsn, SentryEventCaptureListener captureListener) {
        //        Sentry.init(context, DEFAULT_BASE_URL, dsn, captureListener);
        //    }
        //
        //    public static void init(Context context, String dsn) {
        //        Sentry.init(context, DEFAULT_BASE_URL, dsn, null);
        //    }

        @JvmOverloads fun init(context: Context, dsn: String, release: String? = null, captureListener: DefaultSentryCaptureListener? = null) {
            init(captureListener)
            sharedClient.context = context
            sharedClient.dsn = DSN(dsn)
            sharedClient.release = release
            sharedClient.packageName = context.packageName
            sharedClient.verifySsl = getVerifySsl(sharedClient.dsn)


            sharedClient.setupUncaughtExceptionHandler()
        }

        fun addCaptureListener(tag: String, listener: EventCaptureListener) {
            sharedClient.captureListener.addListener(tag, listener)
        }

        fun removeCaptureListener(tag: String) {
            sharedClient.captureListener.removeListener(tag)
        }

        private fun getVerifySsl(dsn: DSN): Boolean {
            val paramValue = Uri.parse(dsn.hostURI.toASCIIString()).getQueryParameter("verify_ssl")
            if (paramValue != null) {
                return Integer.parseInt(paramValue) == 1
            }
            return true
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

        private fun createXSentryAuthHeader(): String {
            var header = ""

            val dsn = sharedClient.dsn
            Log.d("Sentry", "URI - " + dsn.hostURI)

            header += "Sentry sentry_version=4,"
            header += "sentry_client=${sentryClientInfo},"
            header += "sentry_timestamp=${System.currentTimeMillis()},"
            header += "sentry_key=${dsn.publicKey},"
            header += "sentry_secret=${dsn.secretKey}"

            return header
        }

        private val projectId: String
            get() = sharedClient.dsn.projectId

        fun sendAllCachedCapturedEvents() {
            val unsentRequests = InternalStorage.instance.unsentRequests
            Log.d(TAG, "Sending up " + unsentRequests.size + " cached response(s)")
            for (request in unsentRequests) {
                doCaptureEventPost(request)
            }
        }

        /**
         * @param captureListener the captureListener to set
         */
        //        fun setCaptureListener(captureListener: DefaultSentryCaptureListener) {
        //            instance.captureListener = captureListener
        //        }

        //        fun getCaptureListener(): DefaultSentryCaptureListener {
        //            return instance.captureListener
        //        }

        fun captureMessage(message: String) {
            captureMessage(message, SentryEventLevel.INFO)
        }

        fun captureMessage(message: String, level: SentryEventLevel) {
            captureEvent(SentryEventBuilder().setMessage(message).setLevel(level))
        }

        fun captureException(t: Throwable) {
            captureException(t, SentryEventLevel.ERROR)
        }

        fun captureException(t: Throwable, level: SentryEventLevel) {
            val culprit = getCause(t, t.message ?: t.cause?.message ?: "")

            captureEvent(
                    SentryEventBuilder()
                            .setMessage(t.message ?: t.cause?.message ?: "")
                            .setCulprit(culprit)
                            .setLevel(level)
                            .setException(t)
            )
        }

        fun captureUncaughtException(context: Context, t: Throwable) {
            val result = StringWriter()
            val printWriter = PrintWriter(result)
            t.printStackTrace(printWriter)
            try {
                // Random number to avoid duplicate files
                val random = System.currentTimeMillis()

                // Embed version in stacktrace filename
                val stacktrace = File(getStacktraceLocation(context), "raven-" + random.toString() + ".stacktrace")
                Log.d(TAG, "Writing unhandled exception to: " + stacktrace.absolutePath)

                // Write the stacktrace to disk
                val oos = ObjectOutputStream(FileOutputStream(stacktrace))
                oos.writeObject(t)
                oos.flush()
                // Close up everything
                oos.close()
            } catch (ebos: Exception) {
                // Nothing much we can do about this - the game is over
                ebos.printStackTrace()
            }

            Log.d(TAG, result.toString())
        }

        internal fun getCause(t: Throwable?, culprit: String): String {
            var culpritL = culprit
            if (t != null) {
                for (stackTrace in t.stackTrace) {
                    if (stackTrace.toString().contains(sharedClient.packageName)) {
                        culpritL = stackTrace.toString()
                        break
                    }
                }
            }
            return culpritL
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

        fun captureEvent(builder: SentryEventBuilder) {
            var builder = builder
            builder.setRelease(sharedClient.release)


            //            if (Sentry.instance.captureListener != null) {

            try {
                builder = sharedClient.captureListener.beforeCapture(builder)

                //                if (builder == null) {
                //                    Log.e(Sentry.TAG, "SentryEventBuilder in captureEvent is null")
                //                    return
                //                }

                val request = SentryEventRequest(builder)
                //            } else {
                //                request = SentryEventRequest(builder)
                //            }

                Log.d(TAG, "Request - ${request.requestData}")

                // Check if on main thread - if not, run on main thread
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    doCaptureEventPost(request)
                } else if (sharedClient.context != null) {

                    val thread = object : HandlerThread("SentryThread") {

                    }
                    thread.start()
                    val runnable = Runnable { doCaptureEventPost(request) }
                    val h = Handler(thread.looper)
                    h.post(runnable)

                }
            } catch (e: Exception) {
                Log.e("Sentry", "Exception during Sentry capture.", e)
            }

        }

        private fun shouldAttemptPost(): Boolean {
            val pm = sharedClient.context?.packageManager
            val hasPerm = pm?.checkPermission(android.Manifest.permission.ACCESS_NETWORK_STATE, sharedClient.context?.packageName)
            if (hasPerm == PackageManager.PERMISSION_DENIED) {
                return true
            }

            val connectivityManager = sharedClient.context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            return activeNetworkInfo != null && activeNetworkInfo.isConnected
        }

        private fun doCaptureEventPost(sentryRequest: SentryEventRequest) {

            if (!shouldAttemptPost()) {
                InternalStorage.instance.addRequest(sentryRequest)
                return
            }

            val url = "${ sharedClient.dsn.hostURI }api/${ projectId }/store/"
            val postClient = url.httpPost()
                    .header(
                            "X-Sentry-Auth" to createXSentryAuthHeader(),
                            "User-Agent" to sentryClientInfo,
                            "Content-Type" to "text/html; charset=utf-8"
                    )
                    .body(sentryRequest.requestData ?: "", charset("UTF-8"))
                    .timeout(10000)

            if (!sharedClient.verifySsl) {

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
//
//        private fun createBaseAuthHeaders(httpPost: HttpPost) {
//            httpPost.setHeader("X-Sentry-Auth", createXSentryAuthHeader())
//            httpPost.setHeader("User-Agent", sentryClientInfo)
//            httpPost.setHeader("Content-Type", "text/html; charset=utf-8")
//        }

        private val sentryClientInfo: String
            get() = "sentry-raven-android/" + BuildConfig.LIBRARY_VERSION
    }

}
