package io.vithor.sentry.raven

import java.io.Serializable
import java.util.*

/**
 * Created by Vithorio Polten on 2/25/16.
 */
internal class SentryEventRequest(builder: SentryEventBuilder) : Serializable {
    /**
     * @return the requestData
     */
    val requestData: String?

    /**
     * @return the uuid
     */
    val uuid: UUID

    init {
        this.requestData = builder.build()
        this.uuid = UUID.randomUUID()
    }

    override fun equals(other: Any?): Boolean {
        val otherRequest = other as? SentryEventRequest
        return uuid == otherRequest?.uuid
    }

    override fun hashCode(): Int{
        var result = requestData?.hashCode() ?: 0
        result += 31 * result + uuid.hashCode()
        return result
    }
}
