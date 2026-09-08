package com.liskovsoft.smartyoutubetv2.common.vot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VotProtobufTest {
    @Test
    public void decoderAcceptsProto3FailedResponseWithOmittedDefaultStatus() {
        VotWireWriter writer = new VotWireWriter();
        writer.writeString(9, "server failure");

        VotTranslationResponse failed = VotProtobuf.decodeTranslationResponse(writer.toByteArray());

        assertTrue(failed.parseValid);
        assertFalse(failed.statusPresent);
        assertEquals(VotTranslationResponse.STATUS_FAILED, failed.status);
    }

    @Test
    public void decoderReadsServerProgressAndLivelyMetadata() {
        VotWireWriter writer = new VotWireWriter();
        writer.writeInt32(4, VotTranslationResponse.STATUS_LONG_WAITING);
        writer.writeInt32(5, 125);
        writer.writeBool(10, true);
        writer.writeBool(11, true);
        writer.writeInt32(12, 1);

        VotTranslationResponse response = VotProtobuf.decodeTranslationResponse(writer.toByteArray());

        assertEquals("LONG_WAITING", response.getStatusName());
        assertTrue(response.parseValid);
        assertEquals(125, response.remainingTimeSec);
        assertTrue(response.isLivelyVoice);
        assertTrue(response.allowToTranslateVideo);
        assertEquals(1, response.shouldRetry);
    }

    @Test
    public void decoderRejectsTruncatedLengthDelimitedFieldWithoutCrashing() {
        byte[] malformed = new byte[]{0x0A, 0x7F, 0x01};

        VotTranslationResponse response = VotProtobuf.decodeTranslationResponse(malformed);

        assertFalse(response.parseValid);
    }

    @Test
    public void ordinaryFallbackIsEncodedAsFreshInitialRequest() {
        byte[] request = VotProtobuf.encodeTranslationRequest(
                "https://www.youtube.com/watch?v=test", 60, "en", "ru", true, false);

        assertTrue(containsBytes(request, new byte[]{0x28, 0x01}));
        assertFalse(containsBytes(request, new byte[]{(byte) 0x90, 0x01, 0x01}));
    }

    private boolean containsBytes(byte[] data, byte[] expected) {
        for (int i = 0; i <= data.length - expected.length; i++) {
            boolean match = true;
            for (int j = 0; j < expected.length; j++) {
                if (data[i + j] != expected[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }
}