package com.chavd.yc01.userservice.controller;

import com.chavd.yc01.common.dto.ApiResponse;
import com.chavd.yc01.userservice.service.AuthService;
import com.chavd.yc01.userservice.dto.request.LoginRequest;
import com.chavd.yc01.userservice.dto.request.RegisterRequest;
import com.chavd.yc01.userservice.dto.response.AuthResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login (@Valid @RequestBody LoginRequest request) {
        return ResponseEntity
                .ok(ApiResponse.ok(authService.login(request)));
    }

    @PostMapping("refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@RequestHeader("Refresh-Token") String refreshToken) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refreshToken(refreshToken)));
    }

    @PostMapping("logout")
    public ResponseEntity<ApiResponse<Void>> logout(Principal principal){
        authService.logout(principal.getName());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

}
