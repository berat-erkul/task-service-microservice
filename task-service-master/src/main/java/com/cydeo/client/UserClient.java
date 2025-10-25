package com.cydeo.client;

import com.cydeo.dto.responses.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", url = "http://localhost:8081/api/v1/user")
public interface UserClient {

    @GetMapping("/check/{username}")
    ResponseEntity<UserResponse> checkByUsername(@PathVariable ("username") String username);

}
