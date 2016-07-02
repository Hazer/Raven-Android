package io.vithor.sentry.raven

import android.content.Context
import android.util.Log
import java.io.*

/**
 * Created by Vithorio Polten on 2/25/16.
 */
internal class InternalStorage private constructor() {

    /**
     * @return the unsentRequests
     */
    private val unsentRequests: MutableList<SentryEventRequest>

    fun getUnsentRequestsSafeThread(): List<SentryEventRequest> {
        val copy = mutableListOf<SentryEventRequest>()
        synchronized (this) {
            copy.addAll(unsentRequests)
        }
        return copy
    }

    init {
        val context = Sentry.sharedClient.contextWeak.get()
        try {
            val unsetRequestsFile = File(context?.filesDir, FILE_NAME)
            if (!unsetRequestsFile.exists()) {
                writeObject(context, mutableListOf<SentryEventRequest>())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        this.unsentRequests = this.readObject(context)
    }

    fun addRequest(request: SentryEventRequest) {
        synchronized (this) {
            Log.d(Sentry.TAG, "Adding request - " + request.uuid)
            if (!this.unsentRequests.contains(request)) {
                this.unsentRequests.add(request)
                this.writeObject(Sentry.sharedClient.contextWeak.get(), this.unsentRequests)
            }
        }
    }

    fun removeBuilder(request: SentryEventRequest) {
        synchronized (this) {
            Log.d(Sentry.TAG, "Removing request - " + request.uuid)
            this.unsentRequests.remove(request)
            this.writeObject(Sentry.sharedClient.contextWeak.get(), this.unsentRequests)
        }
    }

    private fun writeObject(context: Context?, requests: List<SentryEventRequest>) {
        try {
            val fos = context?.openFileOutput(InternalStorage.FILE_NAME, Context.MODE_PRIVATE)
            val oos = ObjectOutputStream(fos)
            oos.writeObject(requests)
            oos.close()
            fos?.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    private fun readObject(context: Context?): MutableList<SentryEventRequest> {
        try {
            val fis = context?.openFileInput(InternalStorage.FILE_NAME)
            val ois = ObjectInputStream(fis)
            @Suppress("UNCHECKED_CAST")
            val requests = ois.readObject() as? MutableList<SentryEventRequest> ?: mutableListOf()
            ois.close()
            fis?.close()
            return requests
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: StreamCorruptedException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        }

        return mutableListOf()
    }

    companion object {

        private val FILE_NAME = "unsent_requests"

        val instance: InternalStorage by lazy { InternalStorage() }
    }
}
