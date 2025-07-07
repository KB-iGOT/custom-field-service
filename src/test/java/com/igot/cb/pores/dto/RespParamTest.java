package com.igot.cb.pores.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RespParamTest {

    @Test
    void testAllArgsConstructorAndGetters() {
        RespParam param = new RespParam(
                "resmsgid123",
                "msgid456",
                "error",
                "success",
                "error message"
        );

        assertEquals("resmsgid123", param.getResmsgid());
        assertEquals("msgid456", param.getMsgid());
        assertEquals("error", param.getErr());
        assertEquals("success", param.getStatus());
        assertEquals("error message", param.getErrmsg());
    }

    @Test
    void testNoArgsConstructorAndSetters() {
        RespParam param = new RespParam();

        param.setResmsgid("resmsgid789");
        param.setMsgid("msgid012");
        param.setErr("no error");
        param.setStatus("failed");
        param.setErrmsg("something went wrong");

        assertEquals("resmsgid789", param.getResmsgid());
        assertEquals("msgid012", param.getMsgid());
        assertEquals("no error", param.getErr());
        assertEquals("failed", param.getStatus());
        assertEquals("something went wrong", param.getErrmsg());
    }
}
