package com.igot.cb.pores.dto;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CustomResponseTest {

    @Test
    void testAllArgsConstructorAndGetters() {
        RespParam respParam = new RespParam("res1", "msg1", "err1", "SUCCESS", "errmsg1");
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("key", "value");

        CustomResponse response = new CustomResponse(
                "Test message",
                respParam,
                HttpStatus.OK,
                resultMap
        );

        assertEquals("Test message", response.getMessage());
        assertEquals(respParam, response.getParams());
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(resultMap, response.getResult());
    }

    @Test
    void testNoArgsConstructorAndSetters() {
        CustomResponse response = new CustomResponse();

        RespParam respParam = new RespParam("res2", "msg2", "err2", "FAILED", "errmsg2");
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("anotherKey", "anotherValue");

        response.setMessage("Another message");
        response.setParams(respParam);
        response.setResponseCode(HttpStatus.BAD_REQUEST);
        response.setResult(resultMap);

        assertEquals("Another message", response.getMessage());
        assertEquals(respParam, response.getParams());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(resultMap, response.getResult());
    }

    @Test
    void testGetParamsWhenNull() {
        CustomResponse response = new CustomResponse();
        response.setParams(null);

        RespParam returnedParams = response.getParams();

        assertNotNull(returnedParams);
        // The returnedParams should be a new instance of RespParam
        assertNull(returnedParams.getResmsgid());
        assertNull(returnedParams.getMsgid());
        assertNull(returnedParams.getErr());
        assertNull(returnedParams.getStatus());
        assertNull(returnedParams.getErrmsg());
    }

    @Test
    void testResultDefaultInitialization() {
        CustomResponse response = new CustomResponse();
        assertNotNull(response.getResult());
        assertTrue(response.getResult().isEmpty());
    }
}
