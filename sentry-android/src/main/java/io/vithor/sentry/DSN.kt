package io.vithor.sentry

import java.net.URI
import java.net.URISyntaxException

/**
 * Created by Hazer on 2/25/16.
 */
internal class DSN(dsnString: String?) {
    //    private static final String DEFAULT_BASE_URL = "https://app.getsentry.com";

    val hostURI: URI
    //    private ArrayList<String> protocolSettings;
    var path: String? = null
        private set
    var port: Int = 0
        private set
    var protocol: String? = null
        private set

    lateinit var projectId: String
        private set
    lateinit var host: String
        private set
    lateinit var publicKey: String
        private set
    lateinit var secretKey: String
        private set


    init {
        if (dsnString == null)
            throw InvalidDsnException("The sentry DSN must be provided and not be null")

        val dsn: URI
        try {
            dsn = URI(dsnString)
            extractHostInfo(dsn)
        } catch (e: URISyntaxException) {
            throw InvalidDsnException("Impossible to determine Sentry's URI from the DSN '${dsnString}'", e)
        } catch (e: IllegalStateException) {
            throw InvalidDsnException("Impossible to determine Sentry's URI from the DSN '${dsnString}'", e)
        }

        // Mandatory elements are the [.host], [.publicKey], [.secretKey] and [.projectId].
        extractProtocolInfo(dsn)
        extractUserKeys(dsn)
        extractPathInfo(dsn)
        //        extractOptions(dsn);

        hostURI = makeHostURI(dsn)
    }

    private fun makeHostURI(dsn: URI): URI {
        try {
            return URI(protocol, null, host, port, path, null, null)
        } catch (e: URISyntaxException) {
            throw InvalidDsnException("Impossible to determine Sentry's URI from the DSN '$dsn'", e)
        }

    }

    /**
     * Extracts the path and the project ID from the DSN provided as an `URI`.

     * @param dsnUri DSN as an URI.
     */
    private fun extractPathInfo(dsnUri: URI) {
        val uriPath = dsnUri.path ?: return
        val slashIndex = uriPath.lastIndexOf("/")
        if (slashIndex == -1) {
            throwMissingElements("project id")
        }
        val projectIdStart = slashIndex + 1
        path = uriPath.substring(0, projectIdStart)
        projectId = uriPath.substring(projectIdStart)

        if (projectId.isEmpty()) {
            throwMissingElements("project id")
        }
    }

    /**
     * Extracts the hostname and port of the Sentry server from the DSN provided as an `URI`.

     * @param dsnUri DSN as an URI.
     */
    private fun extractHostInfo(dsnUri: URI) {
        host = dsnUri.host
        port = dsnUri.port
    }

    /**
     * Extracts the scheme and additional protocol options from the DSN provided as an `URI`.

     * @param dsnUri DSN as an URI.
     */
    private fun extractProtocolInfo(dsnUri: URI) {
        val scheme = dsnUri.scheme ?: return
        val schemeDetails = scheme.split("\\+".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
        //        protocolSettings.addAll(Arrays.asList(schemeDetails).subList(0, schemeDetails.length - 1));
        protocol = schemeDetails[schemeDetails.size - 1]
    }

    /**
     * Extracts the public and secret keys from the DSN provided as an `URI`.

     * @param dsnUri DSN as an URI.
     */
    private fun extractUserKeys(dsnUri: URI) {
        val userInfo = dsnUri.userInfo ?: return
        val userDetails = userInfo.split(":".toRegex()).toTypedArray()
        if (userDetails.size == 0) {
            throwMissingElements("public key", "secret key")
        }
        publicKey = userDetails[0]
        if (userDetails.size > 1) {
            secretKey = userDetails[1]
        } else {
            throwMissingElements("secret key")
        }
    }
    //
    //    /**
    //     * Extracts the DSN options from the DSN provided as an {@code URI}.
    //     *
    //     * @param dsnUri DSN as an URI.
    //     */
    //    private void extractOptions(URI dsnUri) {
    //        String query = dsnUri.getQuery();
    //        if (query == null || query.isEmpty())
    //            return;
    //        for (String optionPair : query.split("&")) {
    //            try {
    //                String[] pairDetails = optionPair.split("=");
    //                String key = URLDecoder.decode(pairDetails[0], "UTF-8");
    //                String value = pairDetails.length > 1 ? URLDecoder.decode(pairDetails[1], "UTF-8") : null;
    //                options.put(key, value);
    //            } catch (UnsupportedEncodingException e) {
    //                throw new IllegalArgumentException("Impossible to decode the query parameter '" + optionPair + "'", e);
    //            }
    //        }
    //    }


    private fun throwMissingElements(vararg elements: String) {
        throw InvalidDsnException("Invalid DSN, the following properties aren't set '${elements.joinToString(", ")}'")
    }

    override fun hashCode(): Int {
        var result = publicKey.hashCode()
        result = 31 * result + projectId.hashCode()
        result = 31 * result + host.hashCode()
        result = 31 * result + port
        result = 31 * result + (path?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "Dsn{uri=${hostURI}}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as io.DSN

        if (hostURI != other.hostURI) return false

        return true
    }

    private inner class InvalidDsnException : RuntimeException {
        constructor(message: String, cause: Exception) : super(message, cause) {
        }

        constructor(message: String) : super(message) {
        }
    }
}
