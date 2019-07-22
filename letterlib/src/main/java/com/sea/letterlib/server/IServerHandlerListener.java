package com.sea.letterlib.server;

import com.sea.letterlib.IServerListener;

public interface IServerHandlerListener extends IServerListener {
    void onServerRead(String url, CustomResponse response);
}