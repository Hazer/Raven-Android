package io.vithor.sentry

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import com.vithor.sentry.BuildConfig
import io.vithor.sentry.SentryEventBuilder.SentryEventLevel
import org.apache.http.NameValuePair
import org.apache.http.client.ClientProtocolException
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.http.conn.scheme.Scheme
import org.apache.http.conn.ssl.SSLSocketFactory
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.params.HttpConnectionParams
import java.io.*
import java.lang.Thread.UncaughtExceptionHandler
import java.net.Socket
import java.net.URISyntaxException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.security.*
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class Sentry //    private static final String DEFAULT_BASE_URL = "https://app.getsentry.com";

private constructor() {

    internal var context: Context? = null

    lateinit private var dsn: DSN
    lateinit private var packageName: String
    private var verifySsl: Int = 0
    private var release: String? = null

    lateinit private var captureListener: DefaultSentryCaptureListener

    private fun setupUncaughtExceptionHandler() {

        val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (currentHandler != null) {
            Log.d("Debugged", "current handler class=" + currentHandler.javaClass.name)
        }

        // don't register again if already registered
        if (currentHandler !is io.Sentry.SentryUncaughtExceptionHandler) {
            // Register default exceptions handler
            Thread.setDefaultUncaughtExceptionHandler(
                    SentryUncaughtExceptionHandler(currentHandler, context))
        }

        io.Sentry.INSTANCE.sendAllCachedCapturedEvents()
    }

    private inner class SentryUncaughtExceptionHandler// constructor
    (private val defaultExceptionHandler: UncaughtExceptionHandler, private val context: Context?) : UncaughtExceptionHandler {

        override fun uncaughtException(thread: Thread, e: Throwable) {
            // Here you should have a more robust, permanent record of problems
            var builder: SentryEventBuilder = SentryEventBuilder(e, SentryEventLevel.FATAL)

            builder.setRelease(io.Sentry.INSTANCE.instance.release)

            builder = io.Sentry.INSTANCE.instance.captureListener.beforeCapture(builder)// ?: builder

            //            if (builder != null) {
            InternalStorage.instance.addRequest(SentryEventRequest(builder))
            //            } else {
            //                Log.e(Sentry.TAG, "SentryEventBuilder in uncaughtException is null")
            //            }

            //call original handler
            defaultExceptionHandler.uncaughtException(thread, e)
        }
    }

    abstract class SentryEventCaptureListener {
        abstract fun beforeCapture(builder: SentryEventBuilder): SentryEventBuilder
    }

    companion object INSTANCE {

        internal val TAG = "Sentry"

        internal val instance: io.Sentry by lazy { io.Sentry() }

        private fun init(captureListener: DefaultSentryCaptureListener = DefaultSentryCaptureListener()) {
            io.Sentry.INSTANCE.instance.captureListener = captureListener
        }

        //    public static void init(Context context, String dsn, SentryEventCaptureListener captureListener) {
        //        Sentry.init(context, DEFAULT_BASE_URL, dsn, captureListener);
        //    }
        //
        //    public static void init(Context context, String dsn) {
        //        Sentry.init(context, DEFAULT_BASE_URL, dsn, null);
        //    }

        @JvmOverloads fun init(context: Context, dsn: String, release: String? = null, captureListener: DefaultSentryCaptureListener = DefaultSentryCaptureListener()) {
            io.Sentry.INSTANCE.init(captureListener)
            io.Sentry.INSTANCE.instance.context = context
            io.Sentry.INSTANCE.instance.dsn = DSN(dsn)
            io.Sentry.INSTANCE.instance.release = release
            io.Sentry.INSTANCE.instance.packageName = context.packageName
            io.Sentry.INSTANCE.instance.verifySsl = io.Sentry.INSTANCE.getVerifySsl(io.vithor.Sentry.INSTANCE.instance.dsn)


            io.Sentry.INSTANCE.instance.setupUncaughtExceptionHandler()
        }

        private fun getVerifySsl(dsn: DSN): Int {
            val verifySsl = 1
            val params = io.Sentry.INSTANCE.getAllGetParams(dsn)
            if (params != null) {
                for (param in params) {
                    if (param.name == "verify_ssl")
                        return Integer.parseInt(param.value)
                }
            }
            return verifySsl
        }

        private fun getAllGetParams(dsn: DSN): List<NameValuePair>? {
            var params: List<NameValuePair>? = null
            try {
                params = URLEncodedUtils.parse(dsn.hostURI, "UTF-8")
            } catch (e: URISyntaxException) {
                e.printStackTrace()
            }
            return params
        }

        private fun createXSentryAuthHeader(): String {
            var header = ""

            val dsn = io.Sentry.INSTANCE.instance.dsn
            Log.d("Sentry", "URI - " + dsn.hostURI)

            header += "Sentry sentry_version=4,"
            header += "sentry_client=${io.Sentry.INSTANCE.sentryClientInfo},"
            header += "sentry_timestamp=${System.currentTimeMillis()},"
            header += "sentry_key=${dsn.publicKey},"
            header += "sentry_secret=${dsn.secretKey}"

            return header
        }

        private val projectId: String
            get() = io.Sentry.INSTANCE.instance.dsn.projectId

        fun sendAllCachedCapturedEvents() {
            val unsentRequests = InternalStorage.instance.unsentRequests
            Log.d(io.Sentry.INSTANCE.TAG, "Sending up " + unsentRequests.size + " cached response(s)")
            for (request in unsentRequests) {
                io.Sentry.INSTANCE.doCaptureEventPost(request)
            }
        }

        /**
         * @param captureListener the captureListener to set
         */
        fun setCaptureListener(captureListener: DefaultSentryCaptureListener) {
            io.Sentry.INSTANCE.instance.captureListener = captureListener
        }

        fun getCaptureListener(): DefaultSentryCaptureListener {
            return io.Sentry.INSTANCE.instance.captureListener
        }

        fun captureMessage(message: String) {
            io.Sentry.INSTANCE.captureMessage(message, SentryEventLevel.INFO)
        }

        fun captureMessage(message: String, level: SentryEventLevel) {
            io.Sentry.INSTANCE.captureEvent(SentryEventBuilder().setMessage(message).setLevel(level))
        }

        fun captureException(t: Throwable) {
            io.Sentry.INSTANCE.captureException(t, SentryEventLevel.ERROR)
        }

        fun captureException(t: Throwable, level: SentryEventLevel) {
            val culprit = io.Sentry.INSTANCE.getCause(t, t.message ?: t.cause?.message ?: "")

            io.Sentry.INSTANCE.captureEvent(
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
                Log.d(io.Sentry.INSTANCE.TAG, "Writing unhandled exception to: " + stacktrace.absolutePath)

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

            Log.d(io.Sentry.INSTANCE.TAG, result.toString())
        }

        internal fun getCause(t: Throwable, culprit: String): String {
            var culpritL = culprit
            for (stackTrace in t.stackTrace) {
                if (stackTrace.toString().contains(io.Sentry.INSTANCE.instance.packageName)) {
                    culpritL = stackTrace.toString()
                    break
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
            builder!!.setRelease(io.Sentry.INSTANCE.instance.release)

            val request: SentryEventRequest
//            if (Sentry.instance.captureListener != null) {

                builder = io.Sentry.INSTANCE.instance.captureListener.beforeCapture(builder)
//                if (builder == null) {
//                    Log.e(Sentry.TAG, "SentryEventBuilder in captureEvent is null")
//                    return
//                }

                request = SentryEventRequest(builder)
//            } else {
//                request = SentryEventRequest(builder)
//            }

            Log.d(io.Sentry.INSTANCE.TAG, "Request - " + request.requestData)

            // Check if on main thread - if not, run on main thread
            if (Looper.myLooper() == Looper.getMainLooper()) {
                io.Sentry.INSTANCE.doCaptureEventPost(request)
            } else if (io.Sentry.INSTANCE.instance.context != null) {

                val thread = object : HandlerThread("SentryThread") {

                }
                thread.start()
                val runnable = Runnable { io.Sentry.INSTANCE.doCaptureEventPost(request) }
                val h = Handler(thread.looper)
                h.post(runnable)

            }

        }

        private fun shouldAttemptPost(): Boolean {
            val pm = io.Sentry.INSTANCE.instance.context?.packageManager
            val hasPerm = pm?.checkPermission(android.Manifest.permission.ACCESS_NETWORK_STATE, io.Sentry.INSTANCE.instance.context?.packageName)
            if (hasPerm == PackageManager.PERMISSION_DENIED) {
                return true
            }

            val connectivityManager = io.Sentry.INSTANCE.instance.context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            return activeNetworkInfo != null && activeNetworkInfo.isConnected
        }

        fun getHttpsClient(client: HttpClient): HttpClient? {
            try {
                val x509TrustManager = object : X509TrustManager {
                    @Throws(CertificateException::class)
                    override fun checkClientTrusted(chain: Array<X509Certificate>,
                                                    authType: String) {
                    }

                    @Throws(CertificateException::class)
                    override fun checkServerTrusted(chain: Array<X509Certificate>,
                                                    authType: String) {
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate>? {
                        return null
                    }
                }

                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf<TrustManager>(x509TrustManager), null)
                val sslSocketFactory = ExSSLSocketFactory(sslContext)
                sslSocketFactory.hostnameVerifier = SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER
                val clientConnectionManager = client.connectionManager
                val schemeRegistry = clientConnectionManager?.schemeRegistry
                schemeRegistry?.register(Scheme("https", sslSocketFactory, 443))
                return DefaultHttpClient(clientConnectionManager, client.params)
            } catch (ex: Exception) {
                return null
            }

        }

        private fun doCaptureEventPost(request: SentryEventRequest) {

            if (!io.Sentry.INSTANCE.shouldAttemptPost()) {
                InternalStorage.instance.addRequest(request)
                return
            }

            object : AsyncTask<Void, Void, Void>() {
                override fun doInBackground(vararg params: Void): Void? {

                    val httpClient: HttpClient
                    if (io.Sentry.INSTANCE.instance.verifySsl != 0) {
                        httpClient = DefaultHttpClient()
                    } else {
                        httpClient = io.Sentry.INSTANCE.getHttpsClient(DefaultHttpClient()) ?: DefaultHttpClient()
                    }
                    val httpPost = HttpPost(io.vithor.Sentry.INSTANCE.instance.dsn!!.hostURI.toString() + "api/" + io.vithor.Sentry.INSTANCE.projectId + "/store/")

                    val TIMEOUT_MILLISEC = 10000  // = 20 seconds
                    val httpParams = httpPost.params
                    HttpConnectionParams.setConnectionTimeout(httpParams, TIMEOUT_MILLISEC)
                    HttpConnectionParams.setSoTimeout(httpParams, TIMEOUT_MILLISEC)

                    var success = false
                    try {
                        io.Sentry.INSTANCE.createBaseAuthHeaders(httpPost)

                        httpPost.entity = StringEntity(request.requestData, "utf-8")
                        val httpResponse = httpClient.execute(httpPost)

                        val status = httpResponse.statusLine.statusCode
                        var byteResp: ByteArray? = null

                        // Gets the input stream and unpackages the response into a command
                        if (httpResponse.entity != null) {
                            try {
                                val `in` = httpResponse.entity.content
                                byteResp = this.readBytes(`in`)

                            } catch (e: IOException) {
                                e.printStackTrace()
                            }

                        }

                        var stringResponse: String? = null
                        val charsetInput = Charset.forName("UTF-8")
                        val decoder = charsetInput.newDecoder()
                        var cbuf: CharBuffer?
                        try {
                            cbuf = decoder.decode(ByteBuffer.wrap(byteResp))
                            stringResponse = cbuf!!.toString()
                        } catch (e: CharacterCodingException) {
                            e.printStackTrace()
                        }

                        success = status == 200

                        Log.d(io.Sentry.INSTANCE.TAG, "SendEvent - $status $stringResponse")
                    } catch (e: ClientProtocolException) {
                        e.printStackTrace()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    if (success) {
                        InternalStorage.instance.removeBuilder(request)
                    } else {
                        InternalStorage.instance.addRequest(request)
                    }

                    return null
                }

                @Throws(IOException::class)
                private fun readBytes(inputStream: InputStream): ByteArray {
                    // this dynamically extends to take the bytes you read
                    val byteBuffer = ByteArrayOutputStream()

                    // this is storage overwritten on each iteration with bytes
                    val bufferSize = 1024
                    val buffer = ByteArray(bufferSize)

                    // we need to know how may bytes were read to write them to the byteBuffer

                    while (true) {
                        val length = inputStream.read(buffer)
                        if (length == -1)
                            break
                        byteBuffer.write(buffer, 0, length)
                    }

                    // and then we can return your byte array.
                    return byteBuffer.toByteArray()
                }

            }.execute()

        }

        private fun createBaseAuthHeaders(httpPost: HttpPost) {
            httpPost.setHeader("X-Sentry-Auth", io.Sentry.INSTANCE.createXSentryAuthHeader())
            httpPost.setHeader("User-Agent", io.Sentry.INSTANCE.sentryClientInfo)
            httpPost.setHeader("Content-Type", "text/html; charset=utf-8")
        }

        private val sentryClientInfo: String
            get() = "sentry-raven-android/" + BuildConfig.LIBRARY_VERSION
    }

}
