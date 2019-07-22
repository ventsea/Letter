package com.sea.letterlib;

import android.net.Uri;

public interface IServerListener {
    void onServerStart();

    void onServerStop();

    void onSendStarted(String remoteAddress, Uri uri);

    void onSendProgress(String remoteAddress, Uri uri, long progress, long total);

    void onSendCompleted(String remoteAddress, Uri uri);

    void onSendError(String remoteAddress, Uri uri);
}
