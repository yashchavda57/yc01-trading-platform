# Implementation Plan — yc01 Trading Platform

This document is the step-by-step build guide for the multi-module trading platform.
Work through one step at a time. Each step has a "verify" section — don't move on until it passes.

---

## Phase 1 — Multi-Module Conversion + user-service + api-gateway

### Step 1 — Convert root `pom.xml` to aggregator parent ✅

**What to do:**
- Add `<packaging>pom</packaging>`
- Remove `<dependencies>` block (each child module declares its own)
- Normalize Java to 21 in `<properties>` and `maven-compiler-plugin`
- Add `<properties>`: `spring.cloud.version`, `jjwt.version=0.12.6`
- Add `<dependencyManagement>` with Spring Cloud BOM
- Add `<modules>` listing all child modules
- Remove source/resource directories from root `<build>` (aggregator has no src/)
- Remove `spring-boot-maven-plugin` from root (each runnable service adds it)
- Remove `maven-jar-plugin` mainClass from root (belongs in order-service)
- Keep `maven-compiler-plugin` and `maven-surefire-plugin` as inherited defaults
- Keep `<profiles>` (dev/prod)

**Verify:** `mvn validate` from root passes with no errors.

---

### Step 2 — Scaffold `shared/common-exceptions`

**What to do:**

Create directory `shared/common-exceptions/` with this structure:
```
shared/common-exceptions/
├── pom.xml
└── src/main/java/com/chavd/yc01/common/exception/
    ├── TradingPlatformException.java
    ├── ResourceNotFoundException.java
    ├── UnauthorizedException.java
    ├── DuplicateResourceException.java
    └── ValidationException.java
```

**`pom.xml`:**
```xml
<parent>
    <groupId>com.chavd.yc01</groupId>
    <artifactId>trading-platform</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
</parent>
<artifactId>common-exceptions</artifactId>
<packaging>jar</packaging>
<dependencies>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```
No `spring-boot-maven-plugin` — this is a plain library jar.

**`TradingPlatformException.java`** (base):
```java
package com.chavd.yc01.common.exception;

public class TradingPlatformException extends RuntimeException {
    private final int statusCode;

    public TradingPlatformException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() { return statusCode; }
}
```

**The four subclasses** extend `TradingPlatformException` with fixed status codes:
- `ResourceNotFoundException` → 404
- `UnauthorizedException` → 401
- `DuplicateResourceException` → 409
- `ValidationException` → 400

Each just calls `super(message, <statusCode>)` from its constructor.

**Verify:** `mvn compile -pl shared/common-exceptions` from root passes.

---

### Step 3 — Scaffold `shared/common-dto`

**What to do:**

```
shared/common-dto/
├── pom.xml
└── src/main/java/com/chavd/yc01/common/dto/
    └── ApiResponse.java
```

**`pom.xml`:** Same parent pattern as common-exceptions. Dependencies: Lombok + `jackson-databind`.

**`ApiResponse.java`** — generic wrapper used by every service's REST endpoints:
```java
package com.chavd.yc01.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private final boolean success;
    private final String message;
    private final T data;
    private final LocalDateTime timestamp;

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .success(true).data(data).timestamp(LocalDateTime.now()).build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false).message(message).timestamp(LocalDateTime.now()).build();
    }
}
```

Kafka event DTOs will be added to this module in Phase 2.

**Verify:** `mvn compile -pl shared/common-dto` passes.

---

### Step 4 — Scaffold `shared/common-security`

**What to do:**

```
shared/common-security/
├── pom.xml
└── src/main/java/com/chavd/yc01/common/security/
    ├── JwtProperties.java
    └── JwtTokenProvider.java
```

**`pom.xml`** dependencies:
- `spring-security-crypto` (for BCrypt — no full Spring Security needed here)
- `io.jsonwebtoken:jjwt-api:${jjwt.version}`
- `io.jsonwebtoken:jjwt-impl:${jjwt.version}` (scope: runtime)
- `io.jsonwebtoken:jjwt-jackson:${jjwt.version}` (scope: runtime)
- Lombok

**`JwtProperties.java`** — value object holding JWT config (services bind this via `@ConfigurationProperties`):
```java
package com.chavd.yc01.common.security;

public class JwtProperties {
    private String secret;
    private long accessTokenExpirationMs;   // 900000 (15 min)
    private long refreshTokenExpirationMs;  // 604800000 (7 days)
    // getters + setters (or use Lombok @Data)
}
```

**`JwtTokenProvider.java`** — pure utility, no Spring beans, no annotations:
```java
package com.chavd.yc01.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtTokenProvider(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = props.getAccessTokenExpirationMs();
        this.refreshTokenExpirationMs = props.getRefreshTokenExpirationMs();
    }

    public String generateAccessToken(String username, String role) {
        // build JWT: subject=username, claim "role"=role, expiry=now+accessTokenExpirationMs, sign with key
    }

    public String generateRefreshToken(String username) {
        // build JWT: subject=username, NO role claim, expiry=now+refreshTokenExpirationMs, sign with key
    }

    public String extractUsername(String token) {
        // parse token, return subject claim
    }

    public String extractRole(String token) {
        // parse token, return "role" claim as string
    }

    public boolean isTokenValid(String token) {
        // parse token — if JwtException or IllegalArgument → return false, else true
        // do NOT query a database here — purely crypto validation
    }

    public Date getExpiry(String token) {
        // parse and return expiration date (used to set Redis TTL)
    }

    private Claims parseAllClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
```

Key things to understand in `JwtTokenProvider`:
- `Keys.hmacShaKeyFor()` derives a proper HMAC-SHA key from your secret bytes
- JJWT 0.12.x API: `Jwts.builder()...signWith(key)...compact()` to generate; `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)` to validate
- The secret must be at least 32 chars for HS256
- No Spring dependency — this class can be unit-tested without loading a context

**Verify:** `mvn compile -pl shared/common-security` passes.

---

### Step 5 — `order-service` stub (preserve existing code)

**What to do:**

Create `order-service/pom.xml`, then move all files from root `src/` → `order-service/src/`.
Rename package `com.chavd.yc01.orderservice` → `com.chavd.yc01.orderservice` in all files.

**`pom.xml`** dependencies:
- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `com.h2database:h2` (runtime)
- Lombok
- `common-exceptions` (internal)
- Add `spring-boot-maven-plugin` here (it's a runnable service)

**Verify:** `mvn compile -pl order-service` passes. `mvn spring-boot:run -pl order-service` starts on port 8083.

---

### Step 6 — Scaffold `user-service`

**What to do:**

```
user-service/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/chavd/yc01/userservice/
    │   │   ├── UserServiceApplication.java
    │   │   ├── controller/
    │   │   │   ├── AuthController.java
    │   │   │   └── UserController.java
    │   │   ├── service/
    │   │   │   ├── AuthService.java
    │   │   │   └── UserDetailsServiceImpl.java
    │   │   ├── repository/
    │   │   │   └── UserRepository.java
    │   │   ├── entity/
    │   │   │   └── User.java
    │   │   ├── enums/
    │   │   │   ├── Role.java
    │   │   │   └── KycStatus.java
    │   │   ├── dto/
    │   │   │   ├── request/RegisterRequest.java
    │   │   │   ├── request/LoginRequest.java
    │   │   │   └── response/AuthResponse.java
    │   │   └── security/
    │   │       ├── JwtAuthenticationFilter.java
    │   │       └── SecurityConfig.java
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/V1__create_users_table.sql
    └── test/java/com/chavd/yc01/userservice/
        └── UserServiceApplicationTests.java
```

**`pom.xml`** dependencies:
```
spring-boot-starter-web
spring-boot-starter-data-jpa
spring-boot-starter-security
spring-boot-starter-validation
spring-boot-starter-data-redis
spring-boot-starter-actuator
org.postgresql:postgresql (runtime)
org.flywaydb:flyway-core
io.jsonwebtoken:jjwt-api:${jjwt.version}
io.jsonwebtoken:jjwt-impl:${jjwt.version} (runtime)
io.jsonwebtoken:jjwt-jackson:${jjwt.version} (runtime)
com.chavd.yc01:common-dto (version from parent dependencyManagement)
com.chavd.yc01:common-security
com.chavd.yc01:common-exceptions
spring-boot-starter-test (test)
spring-security-test (test)
com.h2database:h2 (test)
```

**`User.java`** — implements `UserDetails` so Spring Security can use it directly:
```java
@Entity
@Table(name = "users")
public class User implements UserDetails {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String email;
    @Column(nullable = false)
    private String passwordHash;
    private String firstName;
    private String lastName;
    @Enumerated(EnumType.STRING)
    private Role role;
    @Enumerated(EnumType.STRING)
    private KycStatus kycStatus;
    private boolean enabled;
    private boolean emailVerified;
    @Version private Long version;       // optimistic locking
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // UserDetails interface:
    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return passwordHash; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public boolean isEnabled() { return enabled; }
    // isAccountNonExpired, isAccountNonLocked, isCredentialsNonExpired → return true for now
}
```

**`AuthService.java`** — the heart of this step. Key methods:

`register(RegisterRequest req)`:
1. `if (userRepository.existsByEmail(req.getEmail())) throw new DuplicateResourceException("Email already in use")`
2. Create `User`, set `passwordEncoder.encode(req.getPassword())` as passwordHash
3. Set `role=USER`, `kycStatus=PENDING`, `enabled=true`
4. `userRepository.save(user)`
5. Generate tokens via `jwtTokenProvider`
6. Store refresh token in Redis: `redisTemplate.opsForValue().set("refresh:" + email, refreshToken, 7, TimeUnit.DAYS)`
7. Return `AuthResponse`

`login(LoginRequest req)`:
1. `authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password))` — Spring Security does the password check
2. Load user from repo
3. Generate tokens, store refresh in Redis, return `AuthResponse`

`refreshToken(String token)`:
1. `jwtTokenProvider.isTokenValid(token)` — if false → throw `UnauthorizedException`
2. Extract username: `jwtTokenProvider.extractUsername(token)`
3. Check Redis: `redisTemplate.opsForValue().get("refresh:" + username)` — must match passed token (prevents old tokens after logout)
4. Load user, generate new access token, return `AuthResponse`

`logout(String username)`:
1. `redisTemplate.delete("refresh:" + username)`

**`JwtAuthenticationFilter.java`** — runs on every request before Spring Security's auth:
```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    // 1. Read "Authorization" header
    // 2. If null or doesn't start with "Bearer " → skip (let SecurityConfig handle it)
    // 3. Extract token string
    // 4. jwtTokenProvider.isTokenValid(token) → if false → skip
    // 5. Extract username, load user via userDetailsService
    // 6. Set SecurityContextHolder:
    //    UsernamePasswordAuthenticationToken auth =
    //        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
    //    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request))
    //    SecurityContextHolder.getContext().setAuthentication(auth)
    // 7. chain.doFilter(request, response)
}
```

**`SecurityConfig.java`**:
```java
@Configuration @EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**", "/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean public JwtAuthenticationFilter jwtAuthenticationFilter() { return new JwtAuthenticationFilter(...); }
}
```

**`application.yml`** for user-service:
```yaml
server:
  port: 8081

spring:
  application:
    name: user-service
  datasource:
    url: jdbc:postgresql://localhost:5432/trading_users
    username: ${DB_USERNAME:trading_user}
    password: ${DB_PASSWORD:trading_pass}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        format_sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

app:
  jwt:
    secret: ${JWT_SECRET:dev-secret-must-be-at-least-32-chars!}
    access-token-expiration-ms: 900000
    refresh-token-expiration-ms: 604800000

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

**`V1__create_users_table.sql`**:
```sql
CREATE TABLE users (
    id             BIGSERIAL    PRIMARY KEY,
    email          VARCHAR(255) UNIQUE NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    first_name     VARCHAR(100),
    last_name      VARCHAR(100),
    role           VARCHAR(50)  NOT NULL DEFAULT 'USER',
    kyc_status     VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
```

**Verify:**
1. `mvn compile -pl user-service --also-make` (also compiles shared dependencies)
2. `docker-compose up -d` (Postgres + Redis must be running)
3. Run user-service on :8081
4. `POST localhost:8081/api/v1/auth/register` with JSON body → get back accessToken + refreshToken
5. `POST localhost:8081/api/v1/auth/login` → get tokens
6. `GET localhost:8081/api/v1/users/me` with Bearer token → 200 with user profile
7. `GET localhost:8081/api/v1/users/me` with no token → 403
8. `POST localhost:8081/api/v1/auth/logout` → then try /me → 403

---

### Step 7 — Scaffold `api-gateway`

**What to do:**

```
api-gateway/
├── pom.xml
└── src/main/
    ├── java/com/chavd/yc01/apigateway/
    │   ├── ApiGatewayApplication.java
    │   ├── filter/
    │   │   └── JwtAuthenticationFilter.java   (GlobalFilter, Ordered)
    │   ├── config/
    │   │   ├── GatewayRoutesConfig.java
    │   │   └── RateLimiterConfig.java
    │   └── exception/
    │       └── GatewayExceptionHandler.java
    └── resources/
        └── application.yml
```

**`pom.xml`** dependencies:
```
spring-cloud-starter-gateway       ← reactive, do NOT add spring-boot-starter-web
spring-boot-starter-data-redis-reactive
spring-boot-starter-actuator
com.chavd.yc01:common-security
spring-boot-starter-test (test)
```
Include `spring-boot-maven-plugin` (it's a runnable service).

**`JwtAuthenticationFilter.java`** — reactive (uses `ServerWebExchange`, returns `Mono<Void>`):
```
1. Check request path against public paths list (from application.yml)
   → if public: return chain.filter(exchange)
2. Read Authorization header
   → if missing: return 401 JSON response (write to ServerHttpResponse, complete)
3. Strip "Bearer " prefix
4. jwtTokenProvider.isTokenValid(token)
   → if false: return 401 JSON
5. Extract username and role from token
6. Mutate request — add headers to forward to downstream:
   ServerHttpRequest mutated = exchange.getRequest().mutate()
       .header("X-User-Email", username)
       .header("X-User-Role", role)
       .build();
   return chain.filter(exchange.mutate().request(mutated).build());
```

Important: The gateway does NOT call a database or user-service to validate the JWT —
it only does cryptographic validation. The downstream service trusts `X-User-Email` and `X-User-Role` headers.

**`GatewayRoutesConfig.java`** (Phase 1 — direct URL, no Eureka yet):
```java
@Bean
public RouteLocator routes(RouteLocatorBuilder builder, RateLimiterConfig rateLimiter) {
    return builder.routes()
        .route("user-service-auth", r -> r
            .path("/api/v1/auth/**")
            .uri("http://localhost:8081"))
        .route("user-service-users", r -> r
            .path("/api/v1/users/**")
            .filters(f -> f.filter(rateLimiter.redisRateLimiter()))  // or use RequestRateLimiter filter
            .uri("http://localhost:8081"))
        .build();
}
```

**`application.yml`**:
```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

app:
  jwt:
    secret: ${JWT_SECRET:dev-secret-must-be-at-least-32-chars!}   # MUST match user-service
    access-token-expiration-ms: 900000
    refresh-token-expiration-ms: 604800000
  public-paths:
    - /api/v1/auth/register
    - /api/v1/auth/login
    - /actuator/health

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

**Verify:**
1. `mvn compile -pl api-gateway --also-make` passes
2. Start both user-service (:8081) and api-gateway (:8080)
3. `POST localhost:8080/api/v1/auth/register` → goes through gateway → hits user-service → 201
4. `GET localhost:8080/api/v1/users/me` with valid token → 200
5. `GET localhost:8080/api/v1/users/me` with no token → 401 from gateway (before hitting user-service)
6. Fire 25 rapid requests → 21st+ returns 429 (rate limiting)

---

### Step 8 — `infrastructure/docker-compose.yml`

**What to do:**

```yaml
version: '3.9'

services:
  postgres:
    image: postgres:16-alpine
    container_name: trading-postgres
    environment:
      POSTGRES_DB: trading_users
      POSTGRES_USER: trading_user
      POSTGRES_PASSWORD: trading_pass
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U trading_user -d trading_users"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: trading-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    command: redis-server --appendonly yes
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres-data:
  redis-data:
```

**Verify:** `docker-compose up -d` → both containers healthy (`docker-compose ps`)

---

## Progress Tracker

| Step | Description | Status |
|---|---|---|
| 1 | Convert root `pom.xml` to aggregator | ✅ Done |
| 2 | Scaffold `shared/common-exceptions` | ✅ Done |
| 3 | Scaffold `shared/common-dto` | ✅ Done |
| 4 | Scaffold `shared/common-security` (JwtTokenProvider) | ✅ Done |
| 5 | `order-service` stub (move existing code) | ✅ Done |
| 6 | Scaffold `user-service` (full auth) | ✅ Done |
| 7 | Scaffold `api-gateway` (JWT filter + routing) | ⬜ |
| 8 | `infrastructure/docker-compose.yml` | ⬜ |

---

## Phase 2 Preview (coming after Phase 1 is done)

- `market-data-service`: WebSocket price feed, TimescaleDB, Kafka `market.ticks`
- `order-service`: Full implementation — order state machine, Outbox pattern, Kafka producer
- `matching-engine`: ConcurrentSkipListMap order book, trade execution
- `wallet-service`: Double-entry ledger, fund freeze/release
- Kafka + Zookeeper added to docker-compose
