package com.igot.cb.pores.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class ProjectUtilTest {

    @Test
    void testCreateDefaultResponse() {
        String api = "testApi";

        ApiResponse response = ProjectUtil.createDefaultResponse(api);

        assertNotNull(response);
        assertEquals(api, response.getId());
        assertEquals(Constants.API_VERSION_1, response.getVer());
        assertNotNull(response.getParams());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertNotNull(response.getParams().getMsgId());
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getTs());
    }

    @Test
    void testReturnErrorMsg() {
        ApiResponse response = ProjectUtil.createDefaultResponse("testApi");

        String error = "SomeError";
        HttpStatus statusCode = HttpStatus.BAD_REQUEST;
        String statusMessage = "Failed";

        ApiResponse updatedResponse = ProjectUtil.returnErrorMsg(error, statusCode, response, statusMessage);

        assertSame(response, updatedResponse); // should be the same object
        assertEquals(error, updatedResponse.getParams().getErr());
        assertEquals(statusCode, updatedResponse.getResponseCode());
        assertEquals(statusMessage, updatedResponse.getMessage());
    }
}
