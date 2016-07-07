package io.vithor.sentry.raven

import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/**
 * Created by Vithorio Polten on 2/25/16.
 * Mandatory {@link #host}, {@link #publicKey}, {@link #secretKey} and {@link #projectId}.
 */
data class DSN(
        val host: String,
        val userKeys: UserKeys,
        val projectId: String,
        val port: Int = 80,
        val path: String = "/",
        val protocol: Protocol = Protocol(),
        val options: Map<String, String?> = emptyMap()
) {
    data class UserKeys(
            val publicKey: String,
            val secretKey: String
    )

    data class Protocol(
            val name: String = "https",
            val settings: Set<String> = emptySet<String>()
    )

    val publicKey: String
        get() {
            return userKeys.publicKey
        }

    val secretKey: String
        get() {
            return userKeys.secretKey
        }

    val verifySsl: Boolean by lazy {
        try {
            options["verify_ssl"]?.toInt() == 1
        } catch (e: NumberFormatException) {
            true
        }
    }

    val uri: URI

    init {
        try {
            uri = URI(protocol.name, null, host, port, path, null, null)
        } catch (e: URISyntaxException) {
            throw InvalidDsnException("Impossible to determine Sentry's URI from the DSN", e)
        }
    }

    override fun toString(): String {
        return "Dsn{uri=$uri}"
    }

    companion object {
        fun from(dsnString: String): DSN {
            try {
                return from(URI.create(dsnString))
            } catch (e: URISyntaxException) {
                throw InvalidDsnException("Impossible to determine Sentry's URI from the DSN '${dsnString}'", e)
            } catch (e: IllegalStateException) {
                throw InvalidDsnException("Impossible to determine Sentry's URI from the DSN '${dsnString}'", e)
            }
        }

        fun from(dsnURI: URI): DSN {
            validatePath(dsnURI)

            val (path, projectId) = dsnURI.path.let { uriPath ->
                val projectIdStart = uriPath.lastIndexOf("/") + 1
                return@let Pair(
                        uriPath.substring(0, projectIdStart),
                        uriPath.substring(projectIdStart)
                )
            }

            val protocol = dsnURI.scheme?.let { scheme ->
                val schemeDetails = scheme.split("\\+".toRegex()).dropLastWhile({ it.isEmpty() })
                return@let Protocol(
                        name = schemeDetails[schemeDetails.size - 1],
                        settings = schemeDetails.subList(0, schemeDetails.size - 1).toSet())
            }

            val (publicKey, secretKey) = dsnURI.userInfo.split(':').apply {
                when (size) {
                    0 -> throw InvalidDsnException("Invalid DSN, missing publicKey and secretKey.")
                    1 -> throw InvalidDsnException("Invalid DSN, missing secretKey.")
                }
            }

            val options = dsnURI.query?.let { query ->
                return@let mutableMapOf<String, String?>().apply {
                    for (optionPair in query.split('&')) {
                        try {
                            val (key, value) = optionPair.split('=').apply {
                                map { URLDecoder.decode(it, "UTF-8") }
                            }
                            put(key, value)
                        } catch (e: UnsupportedEncodingException) {
                            throw IllegalArgumentException("Impossible to decode the query parameter '$optionPair'", e)
                        }
                    }
                }
            }

            return DSN(
                    host = dsnURI.host,
                    port = dsnURI.port,
                    path = path,
                    projectId = projectId,
                    protocol = protocol ?: Protocol(),
                    userKeys = UserKeys(publicKey = publicKey, secretKey = secretKey),
                    options = options ?: emptyMap()
            )
        }

        private fun validatePath(dsnURI: URI) {
            if (dsnURI.path == null) {
                throw InvalidDsnException("Invalid DSN, missing path and projectId for uri.")
            }
        }
    }
}