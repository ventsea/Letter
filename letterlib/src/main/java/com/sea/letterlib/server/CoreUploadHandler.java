package com.sea.letterlib.server;

import android.net.Uri;
import android.util.Log;

import com.sea.letterlib.UriComponent;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.CharsetUtil;

import static com.sea.letterlib.server.CoreServer.FN;
import static com.sea.letterlib.server.CoreServer.UPLOAD;
import static com.sea.letterlib.server.CoreServer.UPLOAD_PATH;
import static io.netty.buffer.Unpooled.copiedBuffer;

class CoreUploadHandler extends SimpleChannelInboundHandler<HttpObject> {

    private static final String TAG = CoreServer.TAG;
    private static final HttpDataFactory factory = new DefaultHttpDataFactory(true);
    private HttpPostRequestDecoder decoder;
    private HttpRequest request;
    private HttpData partialContent;
    private String postUrl;
    private String segment;
    private ByteBuf byteBuf;
    private Map<String, String> attrMap;

    private UriComponent uriComponent;
    private IServerHandlerListener listener;

    CoreUploadHandler(UriComponent component, IServerHandlerListener listener) {
        this.uriComponent = component;
        this.listener = listener;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject httpObject) {
        if (httpObject instanceof HttpRequest) {
            request = (HttpRequest) httpObject;
            String url = request.uri();


            Uri uri = Uri.parse(url);
            List<String> segments = uri.getPathSegments();
            if (segments.size() == 1) {
                try {
                    segment = URLDecoder.decode(segments.get(0), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    writeResponse(ctx.channel(), e.getMessage());
                    ctx.channel().close();
                    return;
                }

                try {
                    postUrl = URLDecoder.decode(url, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    writeResponse(ctx.channel(), e.getMessage());
                    ctx.channel().close();
                    return;
                }

                if ((UPLOAD.equals(segment) || (uriComponent.getUri().contains(postUrl)) && request.method().equals(HttpMethod.POST))) {
                    Log.d(TAG, "Upload Read " + postUrl);
                    try {
                        decoder = new HttpPostRequestDecoder(factory, request);
                    } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
                        writeResponse(ctx.channel(), e.getMessage()); //请 encode http body
                        ctx.channel().close();
                        return;
                    }
                }
            }
        }
        if (decoder != null) {
            if (httpObject instanceof HttpContent) {
                // New chunk is received
                HttpContent httpContent = (HttpContent) httpObject;
                //before offer
                if (!UPLOAD.equals(segment)) {
                    readHttpBody(httpContent);
                }

                try {
                    decoder.offer(httpContent);
                } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
                    writeResponse(ctx.channel(), e.getMessage());
                    ctx.channel().close();
                    return;
                }

                readHttpDataChunkByChunk();
                if (httpContent instanceof LastHttpContent) {
                    CustomResponse response = new CustomResponse(getRemoteIp(ctx), request);
                    //缺陷：body（json） 和 multipart(表单) 可能会存在重复数据，因为byteBuf 既有可能是json ，也可能是表单，还没想到什么办法 仅保存json而忽略表单，或者直接保存表单
                    if (byteBuf != null) { //存在httpContent
                        try {
                            if (attrMap == null) { //是否存在表单
                                //body
                                response.setHttpBody(URLDecoder.decode(byteBuf.toString(CharsetUtil.UTF_8), "UTF-8"));
                            } else {
                                //multipart
                                response.setAttribute(attrMap);
                            }
                        } catch (UnsupportedEncodingException ignore) {
                        }
                        if (listener != null) listener.onServerRead(postUrl, response);
                        byteBuf.clear();
                        byteBuf = null;
                        attrMap = null;
                    }
                    writeResponse(ctx.channel(), response.getResponseContent());
                    reset();
                }
            }
            return;
        }
        ctx.fireChannelRead(httpObject);
    }

    private void readHttpBody(HttpContent httpContent) {
        ByteBuf content = httpContent.content();
        if (content.isReadable()) {
            if (byteBuf == null) {
                byteBuf = Unpooled.buffer(0, Integer.MAX_VALUE);//Unpooled.directBuffer();
            }
            byteBuf.writeBytes(content.copy());
        }
    }

    private void readHttpDataChunkByChunk() {
        try {
            while (decoder.hasNext()) {
                Log.d(TAG, "Decoder has Next");
                InterfaceHttpData data = decoder.next();
                if (data != null) {
                    // check if current HttpData is a FileUpload and previously set as partial
                    if (partialContent == data) {
                        partialContent = null;
                    }
                    try {
                        // new value
                        writeHttpData(data);
                    } finally {
                        Log.d(TAG, "WriteHttpData Finally");
                        data.release();
                    }
                }
            }
            Log.d(TAG, "http data decoder not Next");
            // Check partial decoding for a FileUpload
            InterfaceHttpData data = decoder.currentPartialHttpData();
            if (data != null) {
                StringBuilder builder = new StringBuilder();
                if (partialContent == null) {
                    partialContent = (HttpData) data;
                    if (partialContent instanceof FileUpload) {
                        builder.append("Start FileUpload: ").append(((FileUpload) partialContent).getFilename()).append(" ");
                    } else {
                        builder.append("Start Attribute: ").append(partialContent.getName()).append(" ");
                    }
//                    builder.append("(DefinedSize: ").append(partialContent.definedLength()).append(")");
                }
                if (partialContent.definedLength() > 0) {
                    builder.append(" ").append(partialContent.length() * 100 / partialContent.definedLength())
                            .append("% ");
                } else {
                    builder.append(" ").append(partialContent.length()).append(" ");
                }
                Log.d(TAG, builder.toString());
            }
            //此处可能是可以获取http body content 的，若可以，则 成员变量byteBuf 可以移除
        } catch (HttpPostRequestDecoder.EndOfDataDecoderException e) {
            // end
            Log.d(TAG, "End of content chunk by chunk");
        }
    }

    private void writeHttpData(InterfaceHttpData data) {
        if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
            Attribute attribute = (Attribute) data;
            String value;
            try {
                value = attribute.getValue();
            } catch (IOException e1) {
                // Error while reading data from File, only print name and error
                Log.d(TAG, "Attribute name: " + attribute.getName() + " getValue Error ");
                return;
            }
            if (attrMap == null) attrMap = new HashMap<>();
            attrMap.put(attribute.getName(), value);
            decoder.removeHttpDataFromClean(attribute);
        } else {
            if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.FileUpload) {
                FileUpload fileUpload = (FileUpload) data;
                if (fileUpload.isCompleted()) {
                    try {
                        Log.d(TAG, "file upload - " + data);
                        Log.d(TAG, "file name - " + fileUpload.getFilename() + ", length - " + fileUpload.length());
                        Log.d(TAG, "file isInMemory - " + fileUpload.isInMemory());
                        Log.d(TAG, "getFile.getName - " + fileUpload.getFile().getName());//这个好像没什么卵用

                        File dest = new File(UPLOAD_PATH, getFileName(fileUpload.getFilename()));
                        fileUpload.renameTo(dest);
                        decoder.removeHttpDataFromClean(fileUpload);
                        Log.d(TAG, "file upload over .");
                    } catch (Exception e) {
                        Log.d(TAG, "file Upload isCompleted error : " + e.getMessage());
                    }
                } else {
                    Log.d(TAG, "file to be continued but should not!");
                }
            } else {
                Log.d(TAG, "internalAttribute ???");
            }
        }
    }

    private String getFileName(String normal) {
        String dir = Uri.parse(request.uri()).getQueryParameter(FN);
        try {
            return URLDecoder.decode(dir, "UTF-8");
        } catch (Exception e) {
            return normal;
        }
    }

    private void reset() {
        request = null;
        // destroy the decoder to release all resources
        decoder.destroy();
        decoder = null;
    }

    private void writeResponse(Channel channel, String message) {
        Log.d(TAG, "upload writeResponse " + message);
        // Convert the response content to a ChannelBuffer.
        ByteBuf buf = copiedBuffer(message, CharsetUtil.UTF_8);

        // Decide whether to close the connection or not.
        boolean close = request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true)
                || request.protocolVersion().equals(HttpVersion.HTTP_1_0)
                && !request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE, true);

        // Build the response object.
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

        if (!close) {
            // There's no need to add 'Content-Length' header
            // if this is the last response.
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
        }

        // Write the response.
        ChannelFuture future = channel.writeAndFlush(response);
        // Close the connection after the write operation is done if necessary.
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private String getRemoteIp(ChannelHandlerContext ctx) {
        String s = ctx.channel().remoteAddress().toString();
        return s.substring(1, s.indexOf(":"));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (decoder != null) {
            decoder.cleanFiles();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Log.e(TAG, "upload exceptionCaught", cause);
        ctx.channel().close();
    }
}
