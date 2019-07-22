package com.sea.letterlib.server;

import android.os.Handler;
import android.os.Looper;

import com.sea.letterlib.UriComponent;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.stream.ChunkedWriteHandler;

class CoreServerInitializer extends ChannelInitializer<SocketChannel> {

    private UriComponent uriComponent;
    private IServerHandlerListener serverListener;
    private Handler handler;

    CoreServerInitializer() {
        handler = new Handler(Looper.getMainLooper());
    }

    void setUriComponent(UriComponent component) {
        uriComponent = component;
    }

    void notifyStartServer() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (serverListener != null) serverListener.onServerStart();
            }
        });
    }

    void notifyCloseServer() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (serverListener != null) serverListener.onServerStop();
            }
        });
    }

    void setServerListener(IServerHandlerListener listener) {
        serverListener = listener;
    }

    /**
     * 每一次请求都是一个新的Handler
     */
    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        p.addLast(new HttpRequestDecoder());
        p.addLast(new HttpResponseEncoder());
        p.addLast(new CoreUploadHandler(uriComponent, serverListener));
        p.addLast(new HttpObjectAggregator(65536));
        p.addLast(new ChunkedWriteHandler());
        p.addLast(new CoreServerHandler(uriComponent, serverListener));
    }
}
