package com.wust.dormitory.controller;

import com.wust.dormitory.model.api.ExampleApi;
import com.wust.dormitory.model.dto.ExampleRequest;
import com.wust.dormitory.model.dto.ExampleResponse;
import com.wust.dormitory.service.ExampleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExampleController implements ExampleApi {

    private final ExampleService exampleService;

    public ExampleController(ExampleService exampleService) {
        this.exampleService = exampleService;
    }

    @Override
    public ResponseEntity<ExampleResponse> echoExample(ExampleRequest exampleRequest) {
        return ResponseEntity.ok(exampleService.echo(exampleRequest));
    }
}
