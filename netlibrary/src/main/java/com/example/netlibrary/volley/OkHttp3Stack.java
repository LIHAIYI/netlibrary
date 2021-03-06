
package com.example.netlibrary.volley;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.HttpStack;
import com.example.netlibrary.BPConfig;
import com.example.netlibrary.utils.SSLUtil;


import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;

import java.io.IOException;
import java.net.Proxy;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * <b>Project:</b> com.yong.volleyok <br>
 * <b>Create Date:</b> 2016/4/22 <br>
 * <b>Author:</b> qingyong <br>
 * <b>Description:</b> 使用OkHttp作为Volley的请求的实现 <br>
 */
public class OkHttp3Stack implements HttpStack {

    private final OkHttpClient mClient;
    BPConfig config;

    public OkHttp3Stack(BPConfig config) {
        this.config=config;
        OkHttpClient.Builder builder = new OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(new RedirectInterceptor())
                .hostnameVerifier( SSLUtil.getInstance().getHostnameVerifier())
                .sslSocketFactory(SSLUtil.getInstance().getSSLSocketFactory(),SSLUtil.getInstance().getTrustManager())

                .readTimeout(15, TimeUnit.SECONDS);
        if(config.interceptorList!=null){
            for(int i=0;i<config.interceptorList.size();i++){
                builder.addInterceptor(config.interceptorList.get(i));
            }
        }
        if(config.banProxy){
            builder.proxy(Proxy.NO_PROXY);
        }
        mClient=builder.build();

    }

    private static HttpEntity entityFromOkHttpResponse(Response response) throws IOException {
        BasicHttpEntity entity = new BasicHttpEntity();
        ResponseBody body = response.body();

        entity.setContent(body.byteStream());
        entity.setContentLength(body.contentLength());
        entity.setContentEncoding(response.header("Content-Encoding"));

        if (body.contentType() != null) {
            entity.setContentType(body.contentType().type());
        }
        return entity;
    }

    @SuppressWarnings("deprecation")
    private static void setConnectionParametersForRequest
            (okhttp3.Request.Builder builder, Request<?> request)
            throws IOException, AuthFailureError {
        switch (request.getMethod()) {
            case Request.Method.DEPRECATED_GET_OR_POST:
                byte[] postBody = request.getPostBody();
                if (postBody != null) {
                    builder.post(RequestBody.create
                            (MediaType.parse(request.getPostBodyContentType()), postBody));
                }
                break;

            case Request.Method.GET:
                builder.get();
                break;

            case Request.Method.DELETE:
                builder.delete();
                break;

            case Request.Method.POST:
                builder.post(createRequestBody(request));
                break;

            case Request.Method.PUT:
                builder.put(createRequestBody(request));
                break;

            case Request.Method.HEAD:
                builder.head();
                break;

            case Request.Method.OPTIONS:
                builder.method("OPTIONS", createRequestBody(request));
                break;

            case Request.Method.TRACE:
                builder.method("TRACE", null);
                break;

            case Request.Method.PATCH:
                builder.patch(createRequestBody(request));
                break;

            default:
                throw new IllegalStateException("Unknown method type.");
        }
    }

    private static RequestBody createRequestBody(Request request) throws AuthFailureError {
        final byte[] body = request.getBody();
        if (body == null) return RequestBody.create(MediaType.parse("application/json;charset=utf-8"),new byte[1024]);
        return RequestBody.create(MediaType.parse(request.getBodyContentType()), body);
    }

    private static ProtocolVersion parseProtocol(final Protocol protocol) {
        switch (protocol) {
            case HTTP_1_0:
                return new ProtocolVersion("HTTP", 1, 0);
            case HTTP_1_1:
                return new ProtocolVersion("HTTP", 1, 1);
            case SPDY_3:
                return new ProtocolVersion("SPDY", 3, 1);
            case HTTP_2:
                return new ProtocolVersion("HTTP", 2, 0);
        }

        throw new IllegalAccessError("Unkwown protocol");
    }

    @Override
    public HttpResponse performRequest(Request<?> request, Map<String, String> additionalHeaders)
            throws IOException, AuthFailureError {
        int timeoutMs = request.getTimeoutMs();
//        OkHttpClient client = mClient.newBuilder()
//                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
//                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
//                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
//                .build();

//请求构造器
        okhttp3.Request.Builder okHttpRequestBuilder = new okhttp3.Request.Builder();

        Map<String, String> headers = request.getHeaders();

        if(headers!=null){
            for (final String name : headers.keySet()) {
                okHttpRequestBuilder.addHeader(name, headers.get(name));
            }
        }


        for (final String name : additionalHeaders.keySet()) {
            okHttpRequestBuilder.addHeader(name, additionalHeaders.get(name));
        }

        setConnectionParametersForRequest(okHttpRequestBuilder, request);

        // 请求
        okhttp3.Request okhttp3Request = okHttpRequestBuilder.
                url(request.getUrl()).
                build();

        // 响应
        Response okHttpResponse = mClient.newCall(okhttp3Request).execute();

        StatusLine responseStatus = new BasicStatusLine
                (
                        parseProtocol(okHttpResponse.protocol()),
                        okHttpResponse.code(),
                        okHttpResponse.message()
                );

        BasicHttpResponse response = new BasicHttpResponse(responseStatus);

        response.setEntity(entityFromOkHttpResponse(okHttpResponse));

        Headers responseHeaders = okHttpResponse.headers();
        for (int i = 0, len = responseHeaders.size(); i < len; i++) {
            final String name = responseHeaders.name(i), value = responseHeaders.value(i);
            if (name != null) {
                response.addHeader(new BasicHeader(name, value));
            }
        }
        if(config!=null&&config.onResponseListener!=null){
            config.onResponseListener.responseListener(responseHeaders,okHttpResponse.code(),request.getUrl(),null);
        }
        return response;
    }
}