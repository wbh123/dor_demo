package com.wust.dormitory.service.impl;

import com.wust.dormitory.model.dto.ExampleRequest;
import com.wust.dormitory.model.dto.ExampleResponse;
import com.wust.dormitory.service.ExampleService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ExampleServiceImpl implements ExampleService {

    @Override
    public ExampleResponse echo(ExampleRequest request) {
        if (request == null || isBlank(request.getCode()) || isBlank(request.getName())) {
            ExampleResponse response = new ExampleResponse();
            response.setRequestId(UUID.randomUUID().toString());
            response.setStatus(ExampleResponse.StatusEnum.REJECTED);
            return response;
        }

        ExampleResponse response = new ExampleResponse();
        response.setRequestId(UUID.randomUUID().toString());
        response.setStatus(ExampleResponse.StatusEnum.ACCEPTED);
        response.setCode(request.getCode());
        response.setName(request.getName());
        response.setDescription(request.getDescription());
        return response;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
