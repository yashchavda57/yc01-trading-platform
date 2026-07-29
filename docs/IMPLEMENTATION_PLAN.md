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
| 7 | Scaffold `api-gateway` (JWT filter + routing) | ✅ Done |
| 8 | `infrastructure/docker-compose.yml` | ✅ Done |

---

## Phase 1 Summary — What Was Built and Why

Phase 1 converted a single-module project into a Maven multi-module monorepo and delivered a working
auth path through an API gateway: `register` → `login` → authenticated request → `refresh` → `logout`,
backed by Postgres + Redis in Docker. Below is what each step covered and the concepts behind it —
useful as an interview map: "walk me through your auth flow" should trace directly through this table.

| Step | What it covered | Core concepts to be able to explain |
|---|---|---|
| 1 — Aggregator `pom.xml` | Converted root POM to `packaging=pom`, added `<dependencyManagement>` with the Spring Cloud BOM, declared `<modules>` | **Maven multi-module builds**: parent POM has no source, just governs versions and child module list. `dependencyManagement` vs `dependencies` — the former pins versions without pulling the artifact in; children opt in. Why a BOM (Bill of Materials) matters for keeping Spring Cloud + Spring Boot versions compatible across every module. |
| 2 — `common-exceptions` | Shared exception hierarchy: `TradingPlatformException` base with a `statusCode`, four subclasses (`ResourceNotFoundException` 404, `UnauthorizedException` 401, `DuplicateResourceException` 409, `ValidationException` 400) | **Centralized error semantics**: every service throws the same exception types so a `@ControllerAdvice` can map exception → HTTP status in one place instead of every controller doing manual status codes. Plain library JAR (no `spring-boot-maven-plugin`) — the distinction between a runnable Spring Boot app and a shared dependency JAR. |
| 3 — `common-dto` | `ApiResponse<T>` generic response envelope with `success`, `message`, `data`, `timestamp` | **Consistent API contract** across all microservices — clients always parse the same envelope shape regardless of which service answered. `@JsonInclude(NON_NULL)` to omit empty fields from JSON. Static factory methods (`ok()`, `error()`) over public constructors — controls what states are constructible. |
| 4 — `common-security` (`JwtTokenProvider`) | Pure-Java JWT issuing/parsing utility, no Spring dependency | **JWT mechanics**: HMAC-SHA signing (`Keys.hmacShaKeyFor`), access vs refresh token asymmetry (refresh has no `role` claim — narrower privilege), `isTokenValid` does *cryptographic* validation only (no DB hit) so it's fast and stateless. Why this class is framework-agnostic: unit-testable without a Spring context, reusable from both `user-service` and `api-gateway` (gateway needs to validate tokens without calling the DB). |
| 5 — `order-service` stub | Moved pre-existing root code into its own module | **Module extraction** without behavior change — proving the aggregator restructure didn't break anything before adding new services on top of it. |
| 6 — `user-service` (full auth) | Registration, login, refresh, logout, `/users/me`; Postgres + Flyway + Redis + Spring Security | **Password hashing**: BCrypt (cost factor 12) — one-way, salted, slow-by-design to resist brute force. **Stateless auth**: `SessionCreationPolicy.STATELESS` + JWT means no server-side session store for the access token itself. **Refresh token revocation**: refresh tokens are *also* stored in Redis (`refresh:{email}` key) precisely because JWTs can't be invalidated once issued — logout deletes the Redis key, and `refreshToken()` checks the presented token against Redis so a stolen/old refresh token stops working after logout. **Custom filter chain**: `JwtAuthenticationFilter extends OncePerRequestFilter`, registered `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)` — runs once per request, populates `SecurityContextHolder` before Spring Security's own auth filter would look for credentials. **Optimistic locking** (`@Version` on `User`) vs pessimistic (`SELECT FOR UPDATE`, used later in wallet-service) — different concurrency strategies for different contention profiles. **Flyway**: versioned, forward-only migrations (`V1__create_users_table.sql`) as the source of truth for schema, `ddl-auto: validate` so Hibernate never auto-generates DDL in a real environment. |
| 7 — `api-gateway` | Reactive Spring Cloud Gateway, JWT filter, Redis rate limiting, routing | **Reactive vs servlet stack**: gateway uses WebFlux (`Mono<Void>`, non-blocking `ServerWebExchange`) because a gateway multiplexes many concurrent long-lived connections — blocking threads-per-request doesn't scale here. **Edge authentication pattern**: the gateway validates the JWT signature/expiry only, then forwards trust via `X-User-Email` / `X-User-Role` headers — downstream services trust the gateway rather than re-validating, which is why the gateway must be the only public entry point. **Token-bucket rate limiting** via Redis — distributed rate limiting works across multiple gateway instances because the bucket state lives in Redis, not in-process. |
| 8 — `docker-compose.yml` | Postgres + Redis containers with healthchecks | **Healthchecks as a proxy, not a guarantee**: `pg_isready` checks "is the server accepting connections," not "can my actual app user authenticate with the right permissions" — a wrong `-U` in the healthcheck can still report `healthy` while masking a real credential mismatch. This is why we deliberately traced the difference between "container is healthy" and "verified `trading_user` can actually connect and own the migrated schema" via `psql`. |

**End-to-end flow you should be able to whiteboard:** `POST /api/v1/auth/register` → gateway (public path, skips JWT filter) → `user-service` hashes password with BCrypt, inserts row via Flyway-migrated schema, issues access+refresh JWT, stores refresh token in Redis → client calls `GET /api/v1/users/me` with `Bearer <token>` → gateway's reactive filter validates signature/expiry, injects `X-User-Email`/`X-User-Role` headers → `user-service`'s own `JwtAuthenticationFilter` (defense in depth — it re-validates too, doesn't blindly trust the gateway header) sets `SecurityContextHolder` → controller returns profile.

---

## Phase 1.5 — Service Discovery + Centralized Config (bridge before Phase 2)

**Why this exists between Phase 1 and Phase 2:** `api-gateway` currently hardcodes
`http://localhost:8081` for `user-service` in `GatewayRoutesConfig`. That's fine for one service on
one machine. Phase 2 adds `market-data-service`, `order-service` (full), `matching-engine`, and
`wallet-service` — four more hardcoded URLs waiting to happen, plus config duplication (JWT secret,
Redis host) across every `application.yml`. Doing this now means every Phase 2 service is built
*correctly* from day one instead of retrofitted later.

**Concept — Config Server (Externalized Configuration pattern):**
Instead of each service bundling its own `application.yml` values, a `config-server` module serves
config to every other service on startup from one source of truth (a Git repo, in the classic setup).
Problem it solves: today, if you rotate `JWT_SECRET`, you must edit it in both `user-service` and
`api-gateway` and keep them in sync manually. Centralizing removes that duplication and gives you
per-environment profiles (`application-dev.yml`, `application-prod.yml`) without rebuilding services.

**Concept — Service Discovery (Eureka):**
A **client-server** registry pattern. `service-discovery` runs the Eureka *server*; every other
service is a Eureka *client* — it self-registers on startup (instance ID, host, port) and sends
periodic heartbeats to stay listed. Consumers (like `api-gateway`) no longer hardcode
`http://localhost:8081` — they ask "where is `user-service`?" and get back a live list of instances,
enabling client-side load balancing across multiple instances of the same service. Interview point:
Eureka is **AP, not CP** (CAP theorem) — on network partition it keeps serving its last-known
registry rather than refusing to answer, because a stale-but-available registry is more useful in a
live trading system than a strongly-consistent one that's temporarily down.

### Step 9 — Scaffold `config-server`

**Concept recap before building:** this is a standalone Spring Boot app annotated
`@EnableConfigServer`. It reads config files (named `{service-name}.yml`, e.g. `user-service.yml`)
from a backing repo and serves them over HTTP; other services become **config clients** that fetch
their config from it at startup, before their own `application.yml` even fully applies.

**What to do:**
```
config-server/
├── pom.xml
└── src/main/
    ├── java/com/chavd/yc01/configserver/ConfigServerApplication.java
    └── resources/
        ├── application.yml
        └── config-repo/               ← native/local backend to start (no Git needed yet)
            ├── user-service.yml
            ├── api-gateway.yml
            └── order-service.yml
```

**`pom.xml`** dependency: `spring-cloud-config-server`. Include `spring-boot-maven-plugin` (runnable).

**`ConfigServerApplication.java`**: `@SpringBootApplication @EnableConfigServer`.

**`application.yml`** (config-server itself, port convention: `8888`):
```yaml
server:
  port: 8888

spring:
  application:
    name: config-server
  cloud:
    config:
      server:
        native:
          search-locations: classpath:/config-repo
  profiles:
    active: native   # native = read from local filesystem/classpath; swap for 'git' later
```

Move the service-specific values (JWT secret, Redis host, DB URL) out of each service's
`application.yml` into `config-repo/{service-name}.yml` — each service keeps only its `port`,
`spring.application.name`, and the `spring.config.import` pointer (added in Step 11).

**Verify:** start `config-server`, hit `http://localhost:8888/user-service/default` — should return
JSON with the properties you moved into `config-repo/user-service.yml`.

---

### Step 10 — Scaffold `service-discovery` (Eureka server)

**Concept recap before building:** this module *is* the registry — it holds no business logic, just
the Eureka server. Self-preservation mode (Eureka's default) is worth understanding: if it stops
receiving enough heartbeats network-wide, it assumes a network partition (not that every client died)
and stops evicting instances — a safety behavior appropriate for local dev, but worth tuning down
awareness of in a real deployment discussion.

**What to do:**
```
service-discovery/
├── pom.xml
└── src/main/
    ├── java/com/chavd/yc01/servicediscovery/ServiceDiscoveryApplication.java
    └── resources/application.yml
```

**`pom.xml`** dependency: `spring-cloud-starter-netflix-eureka-server`.

**`ServiceDiscoveryApplication.java`**: `@SpringBootApplication @EnableEurekaServer`.

**`application.yml`** (port convention: `8761`, the Eureka default):
```yaml
server:
  port: 8761

spring:
  application:
    name: service-discovery

eureka:
  client:
    register-with-eureka: false   # the server doesn't register with itself
    fetch-registry: false
  server:
    enable-self-preservation: true
```

**Verify:** start `service-discovery`, open `http://localhost:8761` in a browser — the Eureka
dashboard should load with "No instances available" (nothing's registered yet — that's Step 11).

---

### Step 11 — Retrofit `user-service`, `api-gateway`, `order-service` as Eureka + Config clients

**Concept recap before building:** each service adds `spring-cloud-starter-netflix-eureka-client` (to
register itself) and `spring-cloud-starter-config` (to fetch config from `config-server`). The
gateway's routing then changes from a hardcoded IP to a **logical service name**:
`uri: "lb://USER-SERVICE"` — `lb://` tells Spring Cloud LoadBalancer to resolve `USER-SERVICE` via
Eureka at request time and pick an instance, instead of a fixed `http://localhost:8081`.

**What to do, per service:**
1. Add `spring-cloud-starter-netflix-eureka-client` + `spring-cloud-starter-config` to each `pom.xml`.
2. Trim each service's `application.yml` to just `server.port`, `spring.application.name`, and:
   ```yaml
   spring:
     config:
       import: "configserver:http://localhost:8888"
   eureka:
     client:
       service-url:
         defaultZone: http://localhost:8761/eureka
   ```
3. In `api-gateway`'s `GatewayRoutesConfig`, change:
   ```java
   .uri("http://localhost:8081")
   ```
   to:
   ```java
   .uri("lb://USER-SERVICE")   // matches user-service's spring.application.name, case-insensitive
   ```
4. Add `@EnableDiscoveryClient` is implicit with the starter on the classpath (no annotation needed
   in recent Spring Cloud versions) — confirm via the Eureka dashboard instead of relying on an
   annotation being present.

**Verify:**
1. Start in order: `config-server` → `service-discovery` → `user-service` → `api-gateway`.
2. Open `http://localhost:8761` — both `USER-SERVICE` and `API-GATEWAY` should appear as registered
   instances (status `UP`).
3. Repeat the Step 7 gateway verification (`register` → `login` → `/users/me`) — should work
   identically, but now routed via Eureka lookup instead of a hardcoded URL.
4. Kill `user-service`, wait ~30s, check the Eureka dashboard — it should show the instance as
   removed (or `DOWN` if self-preservation kicked in) rather than the gateway silently still trying
   the dead address forever.

---

## Progress Tracker (Phase 1.5)

| Step | Description | Status |
|---|---|---|
| 9 | Scaffold `config-server` | ✅ Done |
| 10 | Scaffold `service-discovery` (Eureka server) | ✅ Done |
| 11 | Retrofit `user-service`, `api-gateway`, `order-service` as Eureka + Config clients | ✅ Done |

---

## Phase 2 Preview (coming after Phase 1 is done)

- `market-data-service`: WebSocket price feed, TimescaleDB, Kafka `market.ticks`
- `order-service`: Full implementation — order state machine, Outbox pattern, Kafka producer
- `matching-engine`: ConcurrentSkipListMap order book, trade execution
- `wallet-service`: Double-entry ledger, fund freeze/release
- Kafka + Zookeeper added to docker-compose
