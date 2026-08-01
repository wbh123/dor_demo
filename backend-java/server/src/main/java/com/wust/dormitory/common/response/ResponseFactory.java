package com.wust.dormitory.common.response;

import com.wust.dormitory.model.dto.ErrorDetail;
import com.wust.dormitory.model.dto.ErrorResponse;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.model.dto.VoidSuccessResponse;
import org.slf4j.MDC;

import java.util.Date;
import java.util.List;
import java.util.Map;

public final class ResponseFactory {
    private ResponseFactory() {
    }

    public static ObjectSuccessResponse object(Map<String, Object> data) {
        ObjectSuccessResponse response = new ObjectSuccessResponse();
        applySuccess(response);
        response.setData(data);
        response.setError(null);
        return response;
    }

    public static ListSuccessResponse list(List<Map<String, Object>> data) {
        ListSuccessResponse response = new ListSuccessResponse();
        applySuccess(response);
        response.setData(data);
        response.setError(null);
        return response;
    }

    public static VoidSuccessResponse empty() {
        VoidSuccessResponse response = new VoidSuccessResponse();
        applySuccess(response);
        response.setData(null);
        response.setError(null);
        return response;
    }

    public static ErrorResponse error(String code, String message) {
        ErrorDetail error = new ErrorDetail();
        error.setCode(code);
        error.setMessage(message);
        ErrorResponse response = new ErrorResponse();
        response.setSuccess(false);
        response.setRequestId(requestId());
        response.setTimestamp(new Date());
        response.setData(null);
        response.setError(error);
        return response;
    }

    private static void applySuccess(ObjectSuccessResponse response) {
        response.setSuccess(true);
        response.setRequestId(requestId());
        response.setTimestamp(new Date());
    }

    private static void applySuccess(ListSuccessResponse response) {
        response.setSuccess(true);
        response.setRequestId(requestId());
        response.setTimestamp(new Date());
    }

    private static void applySuccess(VoidSuccessResponse response) {
        response.setSuccess(true);
        response.setRequestId(requestId());
        response.setTimestamp(new Date());
    }

    private static String requestId() {
        String requestId = MDC.get("requestId");
        return requestId == null ? "unknown" : requestId;
    }
}
