package com.sea.letterlib.server;

import android.net.Uri;

import com.sea.letterlib.IServerListener;

import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;

class MyChannelProgressiveFutureListener implements ChannelProgressiveFutureListener {

    private IServerListener sendListener;
    private String address;
    private Uri uri;
    private long time;

    void setParameter(String address, Uri uri) {
        this.address = address;
        this.uri = uri;
    }

    void setProgressListener(IServerListener listener) {
        sendListener = listener;
    }

    @Override
    public void operationProgressed(ChannelProgressiveFuture future, final long progress, final long total) {
        long readTime = System.currentTimeMillis();
        if ((readTime - time) < 500) return;
        time = readTime;
        if (sendListener != null)
            sendListener.onSendProgress(address, uri, progress, total);
    }

    @Override
    public void operationComplete(ChannelProgressiveFuture future) {
        if (future.isSuccess()) {
            if (sendListener != null) sendListener.onSendCompleted(address, uri);
        } else {
            if (sendListener != null) sendListener.onSendError(address, uri);
        }
    }
}
