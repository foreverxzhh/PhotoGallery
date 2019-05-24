package com.hua.photogallery;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ThumbnailDownloader<T> extends HandlerThread {
    private static final String TAG = "ThumbnailDownloader";
    private static final int MESSAGE_DOWNLOAD = 0;

    private Boolean mHasQuit = false;
    private Handler mRequestHandler;
    private ConcurrentMap<T, String> mRequestMap = new ConcurrentHashMap<>();
    private Handler mResponseHandler;
    private ThumbnailDownListener<T> mThumbnailDownListener;

    public ThumbnailDownloader(Handler responseHandler) {
        super(TAG);
        mResponseHandler = responseHandler;
    }

    public void setThumbnailDownListener(ThumbnailDownListener<T> listener) {
        mThumbnailDownListener = listener;
    }

    public interface ThumbnailDownListener<T> {
        void onThumbnailDownloaded(T target, Bitmap thumbnail);
    }

    @Override
    public boolean quit() {
        mHasQuit = true;
        return super.quit();
    }

    public void queueThumbnail(T target, String url) {
        Log.d("haha", "Got a URL:" + url);

        if (url == null) {
            mRequestMap.remove(target);
        } else {
            mRequestMap.put(target, url);
            mRequestHandler.obtainMessage(MESSAGE_DOWNLOAD, target).sendToTarget();
        }
    }

    public void cleanQueue() {
        mResponseHandler.removeMessages(MESSAGE_DOWNLOAD);
        mRequestMap.clear();
    }

    @Override
    protected void onLooperPrepared() {
        mRequestHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_DOWNLOAD) {
                    T target = (T) msg.obj;
                    handleRequest(target);
                }
            }
        };
    }

    private void handleRequest(final T target) {
        final String url = mRequestMap.get(target);
        if (url != null) {
            try {
                byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(url);
                final Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
                mResponseHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mRequestMap.get(target) == url) {
                            mRequestMap.remove(target);
                            mThumbnailDownListener.onThumbnailDownloaded(target, bitmap);
                        }
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}