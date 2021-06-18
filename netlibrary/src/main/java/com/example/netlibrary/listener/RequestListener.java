package com.example.netlibrary.listener;

import okhttp3.Headers;

public interface RequestListener {
    void responseListener(Headers headers, int status  , String url,String response);
    void exceptionListener(String url,String error);
}
