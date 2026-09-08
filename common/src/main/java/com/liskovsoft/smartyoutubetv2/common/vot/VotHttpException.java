package com.liskovsoft.smartyoutubetv2.common.vot;

import java.io.IOException;

final class VotHttpException extends IOException {
    final int responseCode;
    final byte[] responseBody;

    VotHttpException(int responseCode, String path, byte[] responseBody) {
        super("VOT HTTP " + responseCode + " for " + path);
        this.responseCode = responseCode;
        this.responseBody = responseBody;
    }
}