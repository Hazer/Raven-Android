package com.joshdholtz.sentry;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;

import com.joshdholtz.sentry.SentryEventBuilder.SentryEventLevel;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class Sentry {

    Context context;

    private DSN dsn;
    private String packageName;
    private int verifySsl;
    private String release;

    private SentryEventCaptureListener captureListener;

    static final String TAG = "Sentry";
//    private static final String DEFAULT_BASE_URL = "https://app.getsentry.com";

    private Sentry() {

    }

    static Sentry getInstance() {
        return LazyHolder.instance;
    }

    private static class LazyHolder {
        private static Sentry instance = new Sentry();
    }

    private static void init(SentryEventCaptureListener captureListener) {
        getInstance().captureListener = captureListener;
    }

//    public static void init(Context context, String dsn, SentryEventCaptureListener captureListener) {
//        Sentry.init(context, DEFAULT_BASE_URL, dsn, captureListener);
//    }
//
//    public static void init(Context context, String dsn) {
//        Sentry.init(context, DEFAULT_BASE_URL, dsn, null);
//    }

    public static void init(Context context, String dsn, String release, SentryEventCaptureListener captureListener) {
        init(captureListener);
        getInstance().context = context;
        getInstance().dsn = new DSN(dsn);
        getInstance().release = release;
        getInstance().packageName = context.getPackageName();
        getInstance().verifySsl = getVerifySsl(dsn);


        getInstance().setupUncaughtExceptionHandler();
    }

    public static void init(Context context, String dsn) {
        init(context, dsn, null, null);
    }

    private static int getVerifySsl(String dsn) {
        int verifySsl = 1;
        List<NameValuePair> params = getAllGetParams(dsn);
        for (NameValuePair param : params) {
            if (param.getName().equals("verify_ssl"))
                return Integer.parseInt(param.getValue());
        }
        return verifySsl;
    }

    private static List<NameValuePair> getAllGetParams(String dsn) {
        List<NameValuePair> params = null;
        try {
            params = URLEncodedUtils.parse(new URI(dsn), "UTF-8");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return params;
    }

    private void setupUncaughtExceptionHandler() {

        UncaughtExceptionHandler currentHandler = Thread.getDefaultUncaughtExceptionHandler();
        if (currentHandler != null) {
            Log.d("Debugged", "current handler class=" + currentHandler.getClass().getName());
        }

        // don't register again if already registered
        if (!(currentHandler instanceof SentryUncaughtExceptionHandler)) {
            // Register default exceptions handler
            Thread.setDefaultUncaughtExceptionHandler(
                    new SentryUncaughtExceptionHandler(currentHandler, context));
        }

        sendAllCachedCapturedEvents();
    }

    private static String createXSentryAuthHeader() {
        String header = "";

        DSN dsn = getInstance().dsn;
        Log.d("Sentry", "URI - " + dsn.getHostURI());

        header += "Sentry sentry_version=4,";
        header += "sentry_client="+ getSentryClientInfo() + ",";
        header += "sentry_timestamp=" + System.currentTimeMillis() + ",";
        header += "sentry_key=" + dsn.getPublicKey() + ",";
        header += "sentry_secret=" + dsn.getSecretKey();

        return header;
    }

    private static String getProjectId() {
        return getInstance().dsn.getProjectId();
    }

    public static void sendAllCachedCapturedEvents() {
        ArrayList<SentryEventRequest> unsentRequests = InternalStorage.getInstance().getUnsentRequests();
        Log.d(Sentry.TAG, "Sending up " + unsentRequests.size() + " cached response(s)");
        for (SentryEventRequest request : unsentRequests) {
            Sentry.doCaptureEventPost(request);
        }
    }

    /**
     * @param captureListener the captureListener to set
     */
    public static void setCaptureListener(SentryEventCaptureListener captureListener) {
        Sentry.getInstance().captureListener = captureListener;
    }

    public static void captureMessage(String message) {
        Sentry.captureMessage(message, SentryEventLevel.INFO);
    }

    public static void captureMessage(String message, SentryEventLevel level) {
        Sentry.captureEvent(new SentryEventBuilder()
                .setMessage(message)
                .setLevel(level)
        );
    }

    public static void captureException(Throwable t) {
        Sentry.captureException(t, SentryEventLevel.ERROR);
    }

    public static void captureException(Throwable t, SentryEventLevel level) {
        String culprit = getCause(t, t.getMessage());

        Sentry.captureEvent(new SentryEventBuilder()
                .setMessage(t.getMessage())
                .setCulprit(culprit)
                .setLevel(level)
                .setException(t)
        );
    }

    public static void captureUncaughtException(Context context, Throwable t) {
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);
        t.printStackTrace(printWriter);
        try {
            // Random number to avoid duplicate files
            long random = System.currentTimeMillis();

            // Embed version in stacktrace filename
            File stacktrace = new File(getStacktraceLocation(context), "raven-" + String.valueOf(random) + ".stacktrace");
            Log.d(TAG, "Writing unhandled exception to: " + stacktrace.getAbsolutePath());

            // Write the stacktrace to disk
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(stacktrace));
            oos.writeObject(t);
            oos.flush();
            // Close up everything
            oos.close();
        } catch (Exception ebos) {
            // Nothing much we can do about this - the game is over
            ebos.printStackTrace();
        }

        Log.d(TAG, result.toString());
    }

    static String getCause(Throwable t, String culprit) {
        for (StackTraceElement stackTrace : t.getStackTrace()) {
            if (stackTrace.toString().contains(Sentry.getInstance().packageName)) {
                culprit = stackTrace.toString();
                break;
            }
        }

        return culprit;
    }

    private static File getStacktraceLocation(Context context) {
        return new File(context.getCacheDir(), "crashes");
    }

    private static String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }

    public static void captureEvent(SentryEventBuilder builder) {
        builder.setRelease(getInstance().release);

        final SentryEventRequest request;
        if (Sentry.getInstance().captureListener != null) {

            builder = Sentry.getInstance().captureListener.beforeCapture(builder);
            if (builder == null) {
                Log.e(Sentry.TAG, "SentryEventBuilder in captureEvent is null");
                return;
            }

            request = new SentryEventRequest(builder);
        } else {
            request = new SentryEventRequest(builder);
        }

        Log.d(TAG, "Request - " + request.getRequestData());

        // Check if on main thread - if not, run on main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            doCaptureEventPost(request);
        } else if (Sentry.getInstance().context != null) {

            HandlerThread thread = new HandlerThread("SentryThread") {
            };
            thread.start();
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    doCaptureEventPost(request);
                }
            };
            Handler h = new Handler(thread.getLooper());
            h.post(runnable);

        }

    }

    private static boolean shouldAttemptPost() {
        PackageManager pm = Sentry.getInstance().context.getPackageManager();
        int hasPerm = pm.checkPermission(android.Manifest.permission.ACCESS_NETWORK_STATE, Sentry.getInstance().context.getPackageName());
        if (hasPerm == PackageManager.PERMISSION_DENIED) {
            return true;
        }

        ConnectivityManager connectivityManager = (ConnectivityManager) Sentry.getInstance().context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public static class ExSSLSocketFactory extends SSLSocketFactory {
        SSLContext sslContext = SSLContext.getInstance("TLS");

        public ExSSLSocketFactory(KeyStore truststore) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
            super(truststore);
            TrustManager x509TrustManager = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            };

            sslContext.init(null, new TrustManager[]{x509TrustManager}, null);
        }

        public ExSSLSocketFactory(SSLContext context) throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException {
            super(null);
            sslContext = context;
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, UnknownHostException {
            return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
        }

        @Override
        public Socket createSocket() throws IOException {
            return sslContext.getSocketFactory().createSocket();
        }
    }

    public static HttpClient getHttpsClient(HttpClient client) {
        try {
            X509TrustManager x509TrustManager = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain,
                                               String authType) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain,
                                               String authType) throws CertificateException {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{x509TrustManager}, null);
            SSLSocketFactory sslSocketFactory = new ExSSLSocketFactory(sslContext);
            sslSocketFactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            ClientConnectionManager clientConnectionManager = client.getConnectionManager();
            SchemeRegistry schemeRegistry = clientConnectionManager.getSchemeRegistry();
            schemeRegistry.register(new Scheme("https", sslSocketFactory, 443));
            return new DefaultHttpClient(clientConnectionManager, client.getParams());
        } catch (Exception ex) {
            return null;
        }
    }

    private static void doCaptureEventPost(final SentryEventRequest request) {

        if (!shouldAttemptPost()) {
            InternalStorage.getInstance().addRequest(request);
            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {

                HttpClient httpClient;
                if (Sentry.getInstance().verifySsl != 0) {
                    httpClient = new DefaultHttpClient();
                } else {
                    httpClient = getHttpsClient(new DefaultHttpClient());
                }
                HttpPost httpPost = new HttpPost(Sentry.getInstance().dsn.getHostURI().toString() + "api/" + getProjectId() + "/store/");

                int TIMEOUT_MILLISEC = 10000;  // = 20 seconds
                HttpParams httpParams = httpPost.getParams();
                HttpConnectionParams.setConnectionTimeout(httpParams, TIMEOUT_MILLISEC);
                HttpConnectionParams.setSoTimeout(httpParams, TIMEOUT_MILLISEC);

                boolean success = false;
                try {
                    createBaseAuthHeaders(httpPost);

                    httpPost.setEntity(new StringEntity(request.getRequestData(), "utf-8"));
                    HttpResponse httpResponse = httpClient.execute(httpPost);

                    int status = httpResponse.getStatusLine().getStatusCode();
                    byte[] byteResp = null;

                    // Gets the input stream and unpackages the response into a command
                    if (httpResponse.getEntity() != null) {
                        try {
                            InputStream in = httpResponse.getEntity().getContent();
                            byteResp = this.readBytes(in);

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    String stringResponse = null;
                    Charset charsetInput = Charset.forName("UTF-8");
                    CharsetDecoder decoder = charsetInput.newDecoder();
                    CharBuffer cbuf = null;
                    try {
                        cbuf = decoder.decode(ByteBuffer.wrap(byteResp));
                        stringResponse = cbuf.toString();
                    } catch (CharacterCodingException e) {
                        e.printStackTrace();
                    }

                    success = (status == 200);

                    Log.d(TAG, "SendEvent - " + status + " " + stringResponse);
                } catch (ClientProtocolException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (success) {
                    InternalStorage.getInstance().removeBuilder(request);
                } else {
                    InternalStorage.getInstance().addRequest(request);
                }

                return null;
            }

            private byte[] readBytes(InputStream inputStream) throws IOException {
                // this dynamically extends to take the bytes you read
                ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

                // this is storage overwritten on each iteration with bytes
                int bufferSize = 1024;
                byte[] buffer = new byte[bufferSize];

                // we need to know how may bytes were read to write them to the byteBuffer
                int len = 0;
                while ((len = inputStream.read(buffer)) != -1) {
                    byteBuffer.write(buffer, 0, len);
                }

                // and then we can return your byte array.
                return byteBuffer.toByteArray();
            }

        }.execute();

    }

    private static void createBaseAuthHeaders(HttpPost httpPost) {
        httpPost.setHeader("X-Sentry-Auth", createXSentryAuthHeader());
        httpPost.setHeader("User-Agent", getSentryClientInfo());
        httpPost.setHeader("Content-Type", "text/html; charset=utf-8");
    }

    @NonNull
    private static String getSentryClientInfo() {
        return "sentry-raven-android/" + BuildConfig.LIBRARY_VERSION;
    }

    private class SentryUncaughtExceptionHandler implements UncaughtExceptionHandler {

        private UncaughtExceptionHandler defaultExceptionHandler;
        private Context context;

        // constructor
        public SentryUncaughtExceptionHandler(UncaughtExceptionHandler pDefaultExceptionHandler, Context context) {
            defaultExceptionHandler = pDefaultExceptionHandler;
            this.context = context;
        }

        @Override
        public void uncaughtException(Thread thread, Throwable e) {
            // Here you should have a more robust, permanent record of problems
            SentryEventBuilder builder = new SentryEventBuilder(e, SentryEventLevel.FATAL);

            builder.setRelease(getInstance().release);

            if (Sentry.getInstance().captureListener != null) {
                builder = Sentry.getInstance().captureListener.beforeCapture(builder);
            }

            if (builder != null) {
                InternalStorage.getInstance().addRequest(new SentryEventRequest(builder));
            } else {
                Log.e(Sentry.TAG, "SentryEventBuilder in uncaughtException is null");
            }

            //call original handler
            defaultExceptionHandler.uncaughtException(thread, e);
        }
    }

    public abstract static class SentryEventCaptureListener {
        public abstract SentryEventBuilder beforeCapture(SentryEventBuilder builder);
    }

}
