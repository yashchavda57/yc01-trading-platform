package com.chavd.yc01.userservice.service;

import com.chavd.yc01.common.exception.DuplicateResourceException;
import com.chavd.yc01.common.exception.UnauthorizedException;
import com.chavd.yc01.common.security.JwtTokenProvider;
import com.chavd.yc01.userservice.dto.request.LoginRequest;
import com.chavd.yc01.userservice.dto.request.RegisterRequest;
import com.chavd.yc01.userservice.dto.response.AuthResponse;
import com.chavd.yc01.userservice.entity.User;
import com.chavd.yc01.userservice.enums.KycStatus;
import com.chavd.yc01.userservice.enums.Role;
import com.chavd.yc01.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final AuthenticationManager authenticationManager;

    private static final String REFRESH_TOKEN_PREFIX = "refresh:";

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(Role.USER)
                .kycStatus(KycStatus.PENDING)
                .enabled(true)
                .emailVerified(false)
                .build();

        userRepository.save(user);

        return generateAndStoreTokens(user.getEmail(), user.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                request.getEmail(),request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid Credentials"));

        return generateAndStoreTokens(user.getEmail(), user.getRole().name());
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.isTokenValid(refreshToken)){
            throw new UnauthorizedException("Invalid Refresh Token");
        }

        String email = jwtTokenProvider.extractUsername(refreshToken);
        String storedToken = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX+email);

        if (storedToken == null || !storedToken.equals(refreshToken)){
            throw new UnauthorizedException("Refresh Token has been revoked");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        return generateAndStoreTokens(email,user.getRole().name());
    }

    public void logout(String email) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX+email);
    }


    private AuthResponse generateAndStoreTokens(String email, String role) {
        String accessToken = jwtTokenProvider.generateAccessToken(email, role);
        String refreshToken = jwtTokenProvider.generateRefreshToken(email);

        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + email,
                refreshToken,
                7,
                TimeUnit.DAYS
        );

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900L)
                .build();

    }



}
