package com.joshdholtz.sentry;

import org.json.JSONObject;

import java.io.Serializable;
import java.util.UUID;

/**
 * Created by Hazer on 2/25/16.
 */
class SentryEventRequest implements Serializable {
    private String requestData;
    UUID uuid;

    public SentryEventRequest(SentryEventBuilder builder) {
        this.requestData = new JSONObject(builder.getEvent()).toString();
        this.uuid = UUID.randomUUID();
    }

    /**
     * @return the requestData
     */
    public String getRequestData() {
        return requestData;
    }

    /**
     * @return the uuid
     */
    public UUID getUuid() {
        return uuid;
    }

    @Override
    public boolean equals(Object other) {
        SentryEventRequest otherRequest = (SentryEventRequest) other;

        if (this.uuid != null && otherRequest.uuid != null) {
            return uuid.equals(otherRequest.uuid);
        }

        return false;
    }

}
