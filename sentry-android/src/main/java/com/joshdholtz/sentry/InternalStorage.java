package com.joshdholtz.sentry;

import android.content.Context;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.util.ArrayList;

/**
 * Created by Hazer on 2/25/16.
 */
class InternalStorage {

    private final static String FILE_NAME = "unsent_requests";
    private ArrayList<SentryEventRequest> unsentRequests;

    static InternalStorage getInstance() {
        return LazyHolder.instance;
    }

    private static class LazyHolder {
        private static InternalStorage instance = new InternalStorage();
    }

    private InternalStorage() {
        this.unsentRequests = this.readObject(Sentry.getInstance().context);
    }

    /**
     * @return the unsentRequests
     */
    public ArrayList<SentryEventRequest> getUnsentRequests() {
        return unsentRequests;
    }

    public void addRequest(SentryEventRequest request) {
        synchronized (this) {
            Log.d(Sentry.TAG, "Adding request - " + request.uuid);
            if (!this.unsentRequests.contains(request)) {
                this.unsentRequests.add(request);
                this.writeObject(Sentry.getInstance().context, this.unsentRequests);
            }
        }
    }

    public void removeBuilder(SentryEventRequest request) {
        synchronized (this) {
            Log.d(Sentry.TAG, "Removing request - " + request.uuid);
            this.unsentRequests.remove(request);
            this.writeObject(Sentry.getInstance().context, this.unsentRequests);
        }
    }

    private void writeObject(Context context, ArrayList<SentryEventRequest> requests) {
        try {
            FileOutputStream fos = context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(requests);
            oos.close();
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ArrayList<SentryEventRequest> readObject(Context context) {
        try {
            FileInputStream fis = context.openFileInput(FILE_NAME);
            ObjectInputStream ois = new ObjectInputStream(fis);
            ArrayList<SentryEventRequest> requests = (ArrayList<SentryEventRequest>) ois.readObject();
            return requests;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (StreamCorruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return new ArrayList<SentryEventRequest>();
    }
}
