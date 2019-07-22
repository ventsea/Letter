package com.sea.letterlib.server;

import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

import static io.netty.buffer.Unpooled.copiedBuffer;

public class CustomResponse {

    private String remoteIp;
    private HttpRequest request;
    private String responseContent;
    private String httpBody;
    private Map<String, String> attribute;

    CustomResponse(String remoteIp, HttpRequest request) {
        this.remoteIp = remoteIp;
        this.request = request;
        this.responseContent = "ok";
    }

    void setHttpBody(String body) {
        this.httpBody = body;
    }

    public String getHttpBody() {
        return httpBody;
    }

    void setAttribute(Map<String, String> map) {
        attribute = map;
    }

    public Map<String, String> getAttribute() {
        return attribute;
    }

    public String getAttributeValue(String key) {
        if (attribute == null) return null;
        return attribute.get(key);
    }

    public String getRemoteIp() {
        return remoteIp;
    }

    public HttpRequest getRequest() {
        return request;
    }

    public void setResponseContent(String responseContent) {
        this.responseContent = responseContent;
        // Convert the response content to a ChannelBuffer.
    }

    String getResponseContent() {
        return responseContent;
    }

    HttpResponse response() {
        ByteBuf buf = copiedBuffer(responseContent, CharsetUtil.UTF_8);

        // Decide whether to close the connection or not.
        boolean close = request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true)
                || request.protocolVersion().equals(HttpVersion.HTTP_1_0)
                && !request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE, true);

        // Build the response object.
        DefaultHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

        if (!close) {
            // There's no need to add 'Content-Length' header
            // if this is the last response.
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
        }
        return response;
    }
}
