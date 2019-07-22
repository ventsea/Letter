package com.sea.letter;

import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.sea.letterlib.UriComponent;
import com.sea.letterlib.server.CoreServer;
import com.sea.letterlib.server.CustomResponse;
import com.sea.letterlib.server.IServerHandlerListener;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = CoreServer.TAG;
    private static final int PORT = 8087;
    private static final String INDEX = "/index";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        UriComponent uriComponent = new UriComponent();
        uriComponent.addUri(INDEX);
        CoreServer.getInstance().initServer(getApplicationContext(), uriComponent);
        CoreServer.getInstance().setListener(new IServerHandlerListener() {
            @Override
            public void onServerRead(String url, CustomResponse response) {
                Log.d(TAG, "url : " + url);
                response.setResponseContent("hello client, i'm server");
            }

            @Override
            public void onServerStart() {

            }

            @Override
            public void onServerStop() {

            }

            @Override
            public void onSendStarted(String remoteAddress, Uri uri) {

            }

            @Override
            public void onSendProgress(String remoteAddress, Uri uri, long progress, long total) {

            }

            @Override
            public void onSendCompleted(String remoteAddress, Uri uri) {

            }

            @Override
            public void onSendError(String remoteAddress, Uri uri) {

            }
        });
    }

    public void start(View view) {
        CoreServer.getInstance().startServer(PORT);
    }

    public void request(View view) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                request();
            }
        }).start();
    }

    private void request() {
        HttpURLConnection httpURLConnection = null;
        InputStream is = null;
        final StringBuilder sb = new StringBuilder();
        sb.append("response : ");
        try {
            URL url = new URL("http://127.0.0.1:" + PORT + INDEX);
            httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setConnectTimeout(5 * 1000);
            httpURLConnection.setReadTimeout(5 * 1000);
            httpURLConnection.connect();

            if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                is = httpURLConnection.getInputStream();
                byte[] bytes = new byte[1024];
                int i;
                while ((i = is.read(bytes)) != -1) {
                    sb.append(new String(bytes, 0, i, StandardCharsets.UTF_8));
                }
                is.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), sb.toString(), Toast.LENGTH_SHORT).show();
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        CoreServer.getInstance().closeServer();
    }
}
