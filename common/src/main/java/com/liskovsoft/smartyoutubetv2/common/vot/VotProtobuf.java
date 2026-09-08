package com.liskovsoft.smartyoutubetv2.common.vot;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class VotProtobuf {
    private VotProtobuf() {
    }

    public static byte[] encodeTranslationRequest(
            String url,
            double duration,
            String requestLang,
            String responseLang,
            boolean firstRequest,
            boolean useLivelyVoice
    ) {
        VotWireWriter w = new VotWireWriter();
        w.writeString(3, url);
        w.writeBool(5, firstRequest);
        w.writeDouble(6, duration);
        w.writeInt32(7, 1);
        w.writeString(8, requestLang);
        w.writeBool(9, false);
        w.writeInt32(10, 0);
        w.writeString(14, responseLang);
        w.writeInt32(15, 1);
        w.writeInt32(16, 2);
        w.writeBool(18, useLivelyVoice);
        return w.toByteArray();
    }

    public static byte[] encodeSessionRequest(String uuid, String module) {
        VotWireWriter w = new VotWireWriter();
        w.writeString(1, uuid);
        w.writeString(2, module);
        return w.toByteArray();
    }

    public static byte[] encodeTranslationAudioRequest(String url, String translationId, String fileId) {
        VotWireWriter audioInfo = new VotWireWriter();
        audioInfo.writeString(1, fileId);
        audioInfo.writeBytes(2, new byte[0]);

        VotWireWriter w = new VotWireWriter();
        w.writeString(1, translationId);
        w.writeString(2, url);
        w.writeEmbedded(6, audioInfo.toByteArray());
        return w.toByteArray();
    }

    public static VotTranslationResponse decodeTranslationResponse(byte[] data) {
        ParseResult parsed = parseFields(data);
        Map<Integer, Object> fields = parsed.fields;
        VotTranslationResponse r = new VotTranslationResponse();
        r.parseValid = parsed.valid;
        r.url = (String) fields.get(1);
        Object status = fields.get(4);
        r.statusPresent = status instanceof Integer;
        r.status = status instanceof Integer ? (Integer) status : 0;
        Object remaining = fields.get(5);
        r.remainingTimeSec = remaining instanceof Integer ? (Integer) remaining : 0;
        r.translationId = (String) fields.get(7);
        r.message = (String) fields.get(9);
        Object lively = fields.get(10);
        r.isLivelyVoice = lively instanceof Integer && (Integer) lively != 0;
        Object allowed = fields.get(11);
        r.allowToTranslateVideo = allowed instanceof Integer && (Integer) allowed != 0;
        Object retry = fields.get(12);
        r.shouldRetry = retry instanceof Integer ? (Integer) retry : 0;
        return r;
    }

    public static VotSession decodeSessionResponse(byte[] data) {
        ParseResult parsed = parseFields(data);
        Map<Integer, Object> fields = parsed.fields;
        VotSession s = new VotSession();
        s.parseValid = parsed.valid;
        s.secretKey = (String) fields.get(1);
        Object expires = fields.get(2);
        s.expiresSec = expires instanceof Integer ? (Integer) expires : 3600;
        return s;
    }

    private static ParseResult parseFields(byte[] data) {
        Map<Integer, Object> result = new HashMap<>();
        int pos = 0;
        while (pos < data.length) {
            int[] tag = readVarint(data, pos);
            if (tag[0] < 0) {
                return new ParseResult(result, false);
            }
            pos = tag[1];
            int fieldNumber = tag[0] >>> 3;
            int wireType = tag[0] & 0x7;

            switch (wireType) {
                case 0: {
                    int[] val = readVarint(data, pos);
                    if (val[0] < 0) {
                        return new ParseResult(result, false);
                    }
                    pos = val[1];
                    result.put(fieldNumber, val[0]);
                    break;
                }
                case 1: {
                    if (pos + 8 > data.length) {
                        return new ParseResult(result, false);
                    }
                    pos += 8;
                    break;
                }
                case 2: {
                    int[] len = readVarint(data, pos);
                    if (len[0] < 0) {
                        return new ParseResult(result, false);
                    }
                    pos = len[1];
                    int length = len[0];
                    if (length < 0 || pos + length > data.length) {
                        return new ParseResult(result, false);
                    }
                    byte[] chunk = new byte[length];
                    System.arraycopy(data, pos, chunk, 0, length);
                    pos += length;
                    if (isUtf8String(chunk)) {
                        result.put(fieldNumber, new String(chunk, StandardCharsets.UTF_8));
                    }
                    break;
                }
                case 5: {
                    if (pos + 4 > data.length) {
                        return new ParseResult(result, false);
                    }
                    pos += 4;
                    break;
                }
                default:
                    return new ParseResult(result, false);
            }
        }
        return new ParseResult(result, true);
    }

    private static final class ParseResult {
        final Map<Integer, Object> fields;
        final boolean valid;

        ParseResult(Map<Integer, Object> fields, boolean valid) {
            this.fields = fields;
            this.valid = valid;
        }
    }

    private static boolean isUtf8String(byte[] chunk) {
        for (byte b : chunk) {
            if (b < 0x09) {
                return false;
            }
        }
        return true;
    }

    private static int[] readVarint(byte[] data, int pos) {
        int result = 0;
        int shift = 0;
        while (pos < data.length) {
            int b = data[pos++] & 0xFF;
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return new int[]{result, pos};
            }
            shift += 7;
            if (shift > 35) {
                return new int[]{-1, pos};
            }
        }
        return new int[]{-1, pos};
    }
}
