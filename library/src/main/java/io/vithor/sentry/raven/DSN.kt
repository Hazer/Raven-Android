package io.vithor.sentry.raven

import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/**
 * Created by Vithorio Polten on 2/25/16.
 */
internal class DSN(dsnString: String) {
    companion object {
        val DEFAULT_PROTOCOL = "https"
        val DEFAULT_HOST = "app.getsentry.com"
        internal val DEFAULT_BASE_URL by lazy {
            return@lazy "$DEFAULT_PROTOCOL://$DEFAULT_HOST"
        }
    }

    private val dsnUri: URI

    val hostURI: URI

    val path: String?

    val host: String
        get() {
            return dsnUri.host ?: DEFAULT_HOST
        }
    val port: Int
        get() {
            return dsnUri.port
        }

    val protocol: String? = extractProtocolInfo()

    val userKeys: UserKeys = extractUserKeys()

    lateinit var projectId: String
        private set

    val publicKey: String
       get() {
           return userKeys.publicKey
       }

    val secretKey: String
        get() {
            return userKeys.secretKey
        }

    val options: MutableMap<String, String?> by lazy { extractOptions() }

    val verifySsl: Boolean by lazy { try { options["verify_ssl"]?.toInt() == 1 } catch (e: NumberFormatException) { true } }

//    lateinit var protocolSettings: MutableList<List<String>>

    init {
        try {
            // Mandatory elements are the [.host], [.publicKey], [.secretKey] and [.projectId].

            dsnUri = URI(dsnString)

            path = extractPathInfo()

            hostURI = makeHostURI(dsnUri)
        } catch (e: URISyntaxException) {
            throw InvalidDsnException("Impossible to determine Sentry's URI from the DSN '${dsnString}'", e)
        } catch (e: IllegalStateException) {
            throw InvalidDsnException("Impossible to determine Sentry's URI from the DSN '${dsnString}'", e)
        }
    }

    private fun makeHostURI(dsn: URI): URI {
        try {
            return URI(protocol, null, dsn.host, dsn.port, dsn.path, null, null)
        } catch (e: URISyntaxException) {
            throw InvalidDsnException("Impossible to determine Sentry's URI from the DSN '$dsn'", e)
        }
    }

    /**
     * Extracts the path and the project ID from the DSN provided as an `URI`.
     */
    private fun extractPathInfo(): String? {
        val uriPath = dsnUri.path ?: return null
        val slashIndex = uriPath.lastIndexOf("/")
        if (slashIndex == -1) {
            throwMissingElements("project id")
        }
        val projectIdStart = slashIndex + 1
        val path = uriPath.substring(0, projectIdStart)

        projectId = uriPath.substring(projectIdStart)

        if (projectId.isEmpty()) {
            throwMissingElements("project id")
        }
        return path
    }



    /**
     * Extracts the scheme and additional protocol options from the DSN provided as an `URI`.
     */
    private fun extractProtocolInfo(): String {
        val scheme = dsnUri.scheme ?: return DEFAULT_PROTOCOL
        val schemeDetails = scheme.split("\\+".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
//        protocolSettings = mutableListOf(schemeDetails.toList().subList(0, schemeDetails.size - 1))
        return schemeDetails[schemeDetails.size - 1]
    }

    /**
     * Extracts the public and secret keys from the DSN provided as an `URI`.
     */
    private fun extractUserKeys(): UserKeys {
        val userInfo: String? = dsnUri.userInfo
        if (userInfo != null) {
            val userDetails = userInfo.split(":".toRegex())
            if (userDetails.size < 2) {
                throw InvalidDsnException("Invalid DSN, the following properties aren'throwable set {public key${if (userDetails.size == 0) ", secret key" else ""}}")
            }
            return UserKeys(userDetails[0], userDetails[1])
        }
        throw InvalidDsnException("Invalid DSN, the following properties aren'throwable set {public key, secret key}")
    }

    /**
     * Extracts the DSN options from the DSN provided as an {@code URI}.
     */
    private fun extractOptions(): MutableMap<String, String?> {
        val query = dsnUri.query
        if (query.isNullOrEmpty()) return mutableMapOf()

        val foundOptions = mutableMapOf<String, String?>()

        for (optionPair in query.split("&")) {
            try {
                val pairDetails = optionPair.split("=")
                val key = URLDecoder.decode(pairDetails[0], "UTF-8")
                val value = if (pairDetails.size > 1) URLDecoder.decode(pairDetails[1], "UTF-8") else null
                foundOptions.put(key, value)
            } catch (e: UnsupportedEncodingException) {
                throw IllegalArgumentException("Impossible to decode the query parameter '$optionPair'", e)
            }
        }
        return foundOptions
    }


    private fun throwMissingElements(vararg elements: String) {
        throw InvalidDsnException("Invalid DSN, the following properties aren'throwable set '${elements.joinToString(", ")}'")
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
        return "Dsn{uri=$dsnUri}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as DSN

        if (dsnUri != other.dsnUri) return false

        return true
    }

    data class UserKeys(
            val publicKey: String,
            val secretKey: String
    )

    inner class InvalidDsnException : RuntimeException {
        constructor(message: String, cause: Throwable) : super(message, cause)

        constructor(message: String) : super(message)
    }
}
