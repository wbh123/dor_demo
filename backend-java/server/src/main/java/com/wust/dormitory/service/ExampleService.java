package com.wust.dormitory.service;

import com.wust.dormitory.model.dto.ExampleRequest;
import com.wust.dormitory.model.dto.ExampleResponse;

public interface ExampleService {

    ExampleResponse echo(ExampleRequest request);
}
