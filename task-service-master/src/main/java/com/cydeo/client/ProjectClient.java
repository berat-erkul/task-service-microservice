package com.cydeo.client;

import com.cydeo.dto.Response.ProjectResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "project-service", url = "http://localhost:8082/api/v1/project")
public interface ProjectClient {

    @GetMapping("/checks/{projectCode}")
    ResponseEntity<ProjectResponse> checkByProjectCode(@PathVariable ("projectCode") String projectCode);

    @GetMapping("/read/manager/{projectCode}")
    ResponseEntity<ProjectResponse> getManagerByProject (@PathVariable ("projectCode") String projectCode);
}
