package com.sea.letterlib;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

public class UriComponent {

    private List<String> REQ_URI = new ArrayList<>();

    public void addUri(String uri) {
        if (TextUtils.isEmpty(uri)) return;
        REQ_URI.add(uri.trim());
    }

    public List<String> getUri() {
        return REQ_URI;
    }
}
