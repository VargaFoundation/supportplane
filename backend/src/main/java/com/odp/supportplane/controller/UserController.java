package com.odp.supportplane.controller;

import com.odp.supportplane.dto.request.CreateUserRequest;
import com.odp.supportplane.dto.response.UserResponse;
import com.odp.supportplane.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UserResponse>> listUsers() {
        List<UserResponse> users = userService.getUsers().stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        return userService.findById(id)
                .map(u -> ResponseEntity.ok(UserResponse.from(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        var user = userService.create(
                request.getEmail(), request.getFullName(),
                request.getPassword(), request.getRole());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id,
                                                     @RequestBody Map<String, Object> body) {
        var user = userService.update(id,
                (String) body.get("fullName"),
                (String) body.get("role"),
                body.containsKey("active") ? (Boolean) body.get("active") : null);
        return ResponseEntity.ok(UserResponse.from(user));
    }
}
