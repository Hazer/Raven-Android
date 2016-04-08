package io.vithor.sentry.raven

import android.content.Context
import android.util.Log
import java.io.*
import java.util.*

/**
 * Created by Vithorio Polten on 2/25/16.
 */
internal class InternalStorage private constructor() {
    /**
     * @return the unsentRequests
     */
    val unsentRequests: ArrayList<SentryEventRequest>

    //    private object LazyHolder {
    //        private val instance by lazy { InternalStorage() }
    //    }

    init {
        val context = Sentry.sharedClient.context
        try {
            val unsetRequestsFile = File(context?.filesDir, FILE_NAME)
            if (!unsetRequestsFile.exists()) {
                writeObject(context, ArrayList<SentryEventRequest>())
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
                this.writeObject(Sentry.sharedClient.context, this.unsentRequests)
            }
        }
    }

    fun removeBuilder(request: SentryEventRequest) {
        synchronized (this) {
            Log.d(Sentry.TAG, "Removing request - " + request.uuid)
            this.unsentRequests.remove(request)
            this.writeObject(Sentry.sharedClient.context, this.unsentRequests)
        }
    }

    private fun writeObject(context: Context?, requests: ArrayList<SentryEventRequest>) {
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

    private fun readObject(context: Context?): ArrayList<SentryEventRequest> {
        try {
            val fis = context?.openFileInput(InternalStorage.FILE_NAME)
            val ois = ObjectInputStream(fis)
            val requests = ois.readObject() as? ArrayList<SentryEventRequest>
            ois.close()
            fis?.close()
            return requests ?: ArrayList()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: StreamCorruptedException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        }

        return ArrayList()
    }

    companion object {

        private val FILE_NAME = "unsent_requests"

        val instance: InternalStorage by lazy { InternalStorage() }
    }
}
