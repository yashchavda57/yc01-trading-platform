package com.chavd.yc01.common.security;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JwtProperties {

    private String secret;
    private long accessTokenExpirationMs;
    private long refreshTokenExpirationMs;

}