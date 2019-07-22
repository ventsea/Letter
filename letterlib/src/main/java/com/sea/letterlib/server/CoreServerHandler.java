package com.sea.letterlib.server;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.sea.letterlib.UriComponent;
import com.sea.letterlib.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;

import static com.sea.letterlib.server.CoreServer.DIR;
import static com.sea.letterlib.server.CoreServer.FILE;
import static com.sea.letterlib.server.CoreServer.ICON;
import static com.sea.letterlib.server.CoreServer.THUMB_IMG;
import static com.sea.letterlib.server.CoreServer.THUMB_VIDEO;
import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_ACCEPTABLE;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.PARTIAL_CONTENT;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * can not support http upload file the ChannelInitializer can not user HttpObjectAggregator
 */
class CoreServerHandler extends ChannelInboundHandlerAdapter {

    private static final String TAG = CoreServer.TAG;
    private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");
    private static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    private static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    private static final int HTTP_CACHE_SECONDS = 60;

    private UriComponent uriComponent;
    private IServerHandlerListener listener;

    CoreServerHandler(UriComponent uriComponent, IServerHandlerListener listener) {
        this.uriComponent = uriComponent;
        this.listener = listener;
    }

    /**
     * 读取请求
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        String remoteIp = getRemoteIp(ctx);
        Log.e(TAG, remoteIp + " channelRead");
        if ((msg instanceof HttpRequest)) {
            HttpRequest request = (HttpRequest) msg;

            String url = request.uri();
            String decode;
            try {
                decode = URLDecoder.decode(url, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                sendError(ctx, NOT_ACCEPTABLE, e.getMessage());
                return;
            }
            Uri uri = Uri.parse(url);
            List<String> segments = uri.getPathSegments(); //xxx.xxx.xx.xx:8010/file?dir=xxx&name=xxx → file
            if (segments.size() == 1) {
                String s;
                try {
                    s = URLDecoder.decode(segments.get(0), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    sendError(ctx, NOT_ACCEPTABLE, e.getMessage());
                    return;
                }
                if (FILE.equals(s)) {
                    responseFile(ctx, request, uri, remoteIp);
                    return;
                }
                if (THUMB_IMG.equals(s)) {
                    responseThumb(ctx, request, uri, THUMB_IMG);
                    return;
                }
                if (THUMB_VIDEO.equals(s)) {
                    responseThumb(ctx, request, uri, THUMB_VIDEO);
                    return;
                }
                if (ICON.equals(s)) {
                    responseThumb(ctx, request, uri, ICON);
                    return;
                }
            }
            if (uriComponent != null) {
                if (uriComponent.getUri().contains(decode)) {
                    CustomResponse response = new CustomResponse(remoteIp, request);
                    if (listener != null) listener.onServerRead(decode, response);
                    ChannelFuture f = ctx.channel().write(response.response());
                    if (!HttpUtil.isKeepAlive(request)) {
                        f.addListener(ChannelFutureListener.CLOSE);
                    }
                    return;
                }
            }
        }
        sendError(ctx, BAD_REQUEST, "WTF");
    }

    private void responseThumb(ChannelHandlerContext ctx, HttpRequest request, Uri uri, String tag) {
        Log.d(TAG, "responseThumb dir ");
        String dir = uri.getQueryParameter(DIR); //自带decode
        if (checkUrlError(dir)) {
            sendError(ctx, FORBIDDEN, "Check Url Error");
            return;
        }
        File file = new File(dir);
        if (file.isHidden() || !file.exists() || !file.isFile()) {
            sendError(ctx, NOT_FOUND, "404");
            return;
        }
        if (tag.equals(THUMB_IMG)) {
            if (file.length() > 500 * 1024) {
                file = Utils.convertImgThumb(CoreServer.getInstance().getContext(), file.getAbsolutePath());
            }
        }
        if (tag.equals(THUMB_VIDEO)) {
            file = Utils.convertVideoThumb(CoreServer.getInstance().getContext(), file.getAbsolutePath());
        }
        if (tag.equals(ICON)) {
            file = Utils.convertIconThumb(CoreServer.getInstance().getContext(), file.getAbsolutePath());
        }
        if (file == null || !file.exists()) {
            sendError(ctx, NOT_FOUND, "404");
            return;
        }
        RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(file, "r");
        } catch (FileNotFoundException e) {
            sendError(ctx, NOT_FOUND, e.getMessage());
            return;
        }
        try {
            long fileLength = raf.length();
            sendResponse(ctx, request, file, fileLength, OK); //response 200
            ChannelFuture sendFileFuture = ctx.writeAndFlush(new HttpChunkedInput(new ChunkedFile(raf, 0, fileLength, 8192)), ctx.newProgressivePromise());

            // Decide whether to close the connection or not.
            if (!HttpUtil.isKeepAlive(request)) {
                // Close the connection when the whole content is written out.
                sendFileFuture.addListener(ChannelFutureListener.CLOSE);
            }
        } catch (IOException e) {
            sendError(ctx, NOT_FOUND, e.getMessage());
        }
    }

    private void responseFile(ChannelHandlerContext ctx, HttpRequest request, Uri uri, String remoteIp) throws Exception {
        String dir = uri.getQueryParameter(DIR);
        Log.d(TAG, "responseFile dir " + dir);
        if (checkUrlError(dir)) {
            sendError(ctx, FORBIDDEN, "Check Url Error");
            return;
        }
        notifySendStart(remoteIp, uri);
        File file = new File(dir);
        if (file.isHidden() || !file.exists() || !file.isFile()) {
            sendError(ctx, NOT_FOUND, "404");
            notifySendError(remoteIp, uri);
            return;
        }

        RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(file, "r");
        } catch (FileNotFoundException e) {
            sendError(ctx, NOT_FOUND, e.getMessage());
            notifySendError(remoteIp, uri);
            return;
        }
        long fileLength = raf.length();
        long offset = 0;

        String range = request.headers().get(HttpHeaderNames.RANGE);
        Log.d(TAG, "Range : " + range + "， fileLength : " + fileLength);
        if (range != null) {
            range = range.replaceAll("bytes=", "");
            if (range.contains(",")) {
                //not support
                sendError(ctx, FORBIDDEN, "Not Support");
                notifySendError(remoteIp, uri);
                return;
            } else {
                offset = getOffset(range, fileLength);
                fileLength = getLength(range, fileLength);
                if (fileLength == -1 || offset == -1) {
                    sendError(ctx, FORBIDDEN, "Not Support");
                    notifySendError(remoteIp, uri);
                    return;
                }
            }
            sendResponse(ctx, request, file, fileLength, PARTIAL_CONTENT); //response 206
            Log.d(TAG, "response 206");
        } else {
            sendResponse(ctx, request, file, fileLength, OK); //response 200
            Log.d(TAG, "response 200");
        }

        // Write the content.
        ChannelFuture sendFileFuture;
        try {
            sendFileFuture = ctx.writeAndFlush(new HttpChunkedInput(new ChunkedFile(raf, offset, fileLength, 8192)), ctx.newProgressivePromise()); //ChunkedFile 处理了多1的异常？
            // HttpChunkedInput will write the end marker (LastHttpContent) for us.

            if (listener != null) {
                MyChannelProgressiveFutureListener futureListener = new MyChannelProgressiveFutureListener();
                futureListener.setParameter(remoteIp, uri);
                futureListener.setProgressListener(listener);
                sendFileFuture.addListener(futureListener);
            }

            // Decide whether to close the connection or not.
            if (!HttpUtil.isKeepAlive(request)) {
                // Close the connection when the whole content is written out.
                sendFileFuture.addListener(ChannelFutureListener.CLOSE);
            }
        } catch (IOException e) {
            notifySendError(remoteIp, uri);
        }
    }

    private void notifySendStart(final String remoteIp, final Uri uri) {
        if (listener != null) listener.onSendStarted(remoteIp, uri);
    }

    private void notifySendError(final String remoteIp, final Uri uri) {
        if (listener != null) listener.onSendError(remoteIp, uri);
    }

    private static long getOffset(String range, long fileLength) {
        if (range == null) return 0;
        String[] split = range.split("-");
        if (split.length == 2) {
            return Long.valueOf(split[0]);
        } else if (split.length == 1) {
            if (range.indexOf("-") == 0) {
                return fileLength - Long.valueOf(split[0]);
            } else {
                return Long.valueOf(split[0]);
            }
        } else {
            if (range.equals("0--1")) {
                return 0;
            }
            Log.e(TAG, "getOffset 非法请求");
            return -1;
        }
    }

    private static long getLength(String range, long fileLength) {
        if (range == null) return fileLength;
        String[] split = range.split("-");
        if (split.length == 2) {                                                                // xxx-yyy (包括xxx，和yyy之间的字节)
            fileLength = Long.valueOf(split[1]) - Long.valueOf(split[0]) + 1;
        } else if (split.length == 1) {
            if (range.indexOf("-") == 0) {                                                      // -xxx (最后xxx个字节)
                fileLength = Long.valueOf(split[0]);
            } else {                                                                            // xxx- (包括xxx以后的字节)
                fileLength = fileLength - Long.valueOf(split[0]);
            }
        } else {
            if (range.equals("0--1")) {
                return 0;
            }
            Log.e(TAG, "getLength 非法请求");
            return -1;
        }
        return fileLength;
    }

    private static void sendResponse(ChannelHandlerContext ctx, HttpRequest request, File file, long fileLength, HttpResponseStatus status) {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, status);
        setContentLength(response, fileLength);
        setAcceptRanges(response);
        setDisposition(response, file);
        setContentTypeHeader(response, file);
        setDateAndCacheHeaders(response, file);
        if (HttpUtil.isKeepAlive(request)) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        ctx.write(response);
    }

    private static void setContentLength(HttpResponse response, long length) {
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, length);
    }

    private static void setAcceptRanges(HttpResponse response) {
        response.headers().set("Accept-Ranges", "bytes");
    }


    private static void setDisposition(HttpResponse response, File file) {
        String fileName = file.getName();
        try {
            fileName = URLEncoder.encode(file.getName(), "UTF-8");
        } catch (UnsupportedEncodingException ignore) {
        }
        response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=" + fileName);
    }

    /**
     * Sets the content type header for the HTTP Response
     *
     * @param response HTTP response
     * @param file     file to extract content type
     */
    private static void setContentTypeHeader(HttpResponse response, File file) {
        String name = file.getName();
        String substring = file.getName().substring(name.lastIndexOf(".") + 1, name.length()).toLowerCase();
        String extension;
        if (TextUtils.isEmpty(substring)) {
            extension = "application/octet-stream";
        } else {
            extension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(substring);
            if (extension == null) extension = "application/octet-stream";
        }
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, extension);
    }

    /**
     * Sets the Date and Cache headers for the HTTP Response
     *
     * @param response    HTTP response
     * @param fileToCache file to extract content type
     */
    private static void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        // Date header
        Calendar time = new GregorianCalendar();
        response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));

        // Add cache headers
        time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
        response.headers().set(HttpHeaderNames.EXPIRES, dateFormatter.format(time.getTime()));
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
        response.headers().set(HttpHeaderNames.LAST_MODIFIED, dateFormatter.format(new Date(fileToCache.lastModified())));
    }

    private boolean checkUrlError(String dir) {
        if (dir == null) {
            return true;
        }
        return sanitizeUri(dir) == null;
    }

    private String sanitizeUri(String uri) {
        if (uri.isEmpty() || uri.charAt(0) != '/') {
            return null;
        }
        // Convert file separators.
        uri = uri.replace('/', File.separatorChar);

        // Simplistic dumb security check.
        // You will have to do something serious in the production environment.
        if (uri.contains(File.separator + '.') ||
                uri.contains('.' + File.separator) ||
                uri.charAt(0) == '.' || uri.charAt(uri.length() - 1) == '.' || INSECURE_URI.matcher(uri).matches()) {
            return null;
        }
        return uri;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    /**
     * 异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String ip = getRemoteIp(ctx);
        Log.e(TAG, ip + " exceptionCaught", cause);
        ctx.close();
    }

    private String getRemoteIp(ChannelHandlerContext ctx) {
        String s = ctx.channel().remoteAddress().toString();
        return s.substring(1, s.indexOf(":"));
    }

    private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, copiedBuffer("Failure: " + message + "\r\n", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        Log.d(TAG, "warning abnormal...");
    }
}
