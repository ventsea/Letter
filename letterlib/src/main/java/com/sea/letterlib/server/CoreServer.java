package com.sea.letterlib.server;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.sea.letterlib.UriComponent;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class CoreServer {

    public static final String TAG = "Letter";

    public static final String DIR = "dir";
    public static final String FN = "fn";

    public static final String THUMB_IMG = "thumb_img";// 192.168.43.119:8010/thumb_img?dir=xxx
    public static final String THUMB_VIDEO = "thumb_video";// 192.168.43.119:8010/thumb_video?dir=xxx
    public static final String ICON = "icon";// 192.168.43.119:8010/icon?dir=xxx
    public static final String FILE = "file";// 192.168.43.119:8010/file?dir=xxx
    public static final String UPLOAD = "upload";// 192.168.43.119:8010/upload?fn=xxx.jpg
    public static final String UPLOAD_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();

    @SuppressLint("StaticFieldLeak")
    private static CoreServer INSTANCE;

    private Channel channel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    private CoreServerInitializer initializer;
    private Context context;

    private boolean isRunning;

    private CoreServer() {
    }

    public static CoreServer getInstance() {
        if (INSTANCE == null) {
            synchronized (CoreServer.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CoreServer();
                }
            }
        }
        return INSTANCE;
    }

    public void initServer(Context context, UriComponent uriComponent) {
        this.context = context.getApplicationContext();
        if (initializer == null) {
            initializer = new CoreServerInitializer();
        }
        initializer.setUriComponent(uriComponent);
    }

    public void startServer(final int port) {
        if (initializer == null) throw new RuntimeException("plz init first");

        if (isRunning) {
            Log.e(TAG, "server are running");
            return;
        }

        isRunning = true;
        MyExecutors.newFixedThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                bossGroup = new NioEventLoopGroup(1);
                workerGroup = new NioEventLoopGroup();
                try {
                    ServerBootstrap b = new ServerBootstrap();
                    b.group(bossGroup, workerGroup)
                            .channel(NioServerSocketChannel.class)
                            .handler(new LoggingHandler(LogLevel.INFO))
                            .childHandler(initializer);

                    channel = b.bind(port).sync().channel();
                    Log.d(TAG, "************" + "\r\nstart server on port :" + port + "\r\n" + "************");
                    initializer.notifyStartServer();
                    channel.closeFuture().sync();
                } catch (Exception e) {
                    Log.e(TAG, "start server error", e);
                } finally {
                    destroy();
                    isRunning = false;
                    Log.d(TAG, "server are destroy");
                }
            }
        });
    }

    public void setListener(IServerHandlerListener serverListener) {
        if (initializer == null) throw new RuntimeException("plz init first");
        initializer.setServerListener(serverListener);
    }

    public void closeServer() {
        MyExecutors.newFixedThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                destroy();
            }
        });
    }

    public Context getContext() {
        return context;
    }

    private void destroy() {
        synchronized (CoreServer.class) {
            try {
                if (channel != null) {
                    channel.close();
                    Log.d(TAG, "server channel close");
                    channel = null;
                }

                // Shut down all event loops to terminate all threads.
                if (bossGroup != null) {
                    bossGroup.shutdownGracefully();
                    bossGroup = null;
                }

                if (workerGroup != null) {
                    workerGroup.shutdownGracefully();
                    workerGroup = null;
                }

            } catch (Exception e) {
                Log.e(TAG, "server destroy error", e);
            } finally {
                if (initializer != null) {
                    initializer.notifyCloseServer();
                    initializer = null;
                }
            }
        }
    }
}
