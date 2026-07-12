package com.chavd.yc01.userservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {

    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private String kycStatus;
    private boolean emailVerified;
    private LocalDateTime createdAt;

}
