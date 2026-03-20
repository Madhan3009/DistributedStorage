# Backend Flow And Code Explanation

This file explains how the backend is organized, what was implemented, and how the authentication flow works internally.

## What Was Implemented

The project was changed from a hardcoded in-memory login setup to a real JWT authentication system backed by a database.

Earlier, authentication was limited to fixed users inside configuration. That approach was removed and replaced with:

- user registration stored in the database
- user login using username and password
- JWT token generation after successful login
- JWT token validation on protected requests
- PostgreSQL as the main runtime database
- isolated H2 configuration only for tests

## High-Level Flow

The backend flow is:

1. A user sends register data to `/auth/register`
2. The backend validates the request
3. The password is encoded with BCrypt
4. The user is saved in the database
5. The user sends credentials to `/auth/login`
6. Spring Security authenticates the credentials
7. A JWT token is generated and returned
8. The client sends that token in `Authorization: Bearer <token>`
9. A JWT filter reads the token on every protected request
10. If valid, Spring Security marks the request as authenticated
11. Protected controllers can then access the logged-in user

## Package Overview

### `Config`

Contains application security configuration.

Main file:

- `src/main/java/com/dSystems/demo/Config/AppConfig.java`

What it does:

- defines the Spring Security filter chain
- disables CSRF for this API-style backend
- allows `/auth/**` without login
- requires authentication for everything else
- registers the JWT filter
- registers the authentication provider
- exposes the password encoder and authentication manager

## `Controller`

Contains the REST endpoints.

Main files:

- `src/main/java/com/dSystems/demo/Controller/AuthController.java`
- `src/main/java/com/dSystems/demo/Controller/TestController.java`

### `AuthController`

This controller handles authentication-related endpoints.

#### `/auth/register`

Purpose:

- create a new user account

What happens:

- receives username, email, and password
- checks whether username already exists
- checks whether email already exists
- encodes the password using BCrypt
- saves the user in the database

#### `/auth/login`

Purpose:

- verify credentials and generate a JWT token

What happens:

- receives username and password
- calls Spring Security authentication manager
- loads the matching user
- generates a JWT token using `JWTHelper`
- returns token and username

### `TestController`

This is a sample protected endpoint.

#### `/api/me`

Purpose:

- confirm that JWT authentication is working

What happens:

- request must contain a valid JWT token
- if authentication succeeds, it returns the logged-in username

## `Model`

Contains the database entity.

Main file:

- `src/main/java/com/dSystems/demo/Model/AppUser.java`

This entity represents a user in the database.

Fields:

- `id`
- `username`
- `email`
- `password`
- `role`
- `enabled`

### Why this model exists

Spring Security needs real user data from a persistent source. This entity stores that data so future users can log in, not just hardcoded names.

## `Repository`

Contains database access logic.

Main file:

- `src/main/java/com/dSystems/demo/Repository/AppUserRepository.java`

This repository provides:

- search by username or email
- username existence check
- email existence check

It is used by both registration and login flows.

## `Payload`

Contains request and response DTOs.

Main files:

- `src/main/java/com/dSystems/demo/Payload/RegisterRequest.java`
- `src/main/java/com/dSystems/demo/Payload/AuthRequest.java`
- `src/main/java/com/dSystems/demo/Payload/AuthResponse.java`

Why these exist:

- to keep API request/response objects separate from entity classes
- to validate incoming input cleanly
- to avoid exposing database objects directly

## `Security`

Contains JWT and user authentication logic.

Main files:

- `src/main/java/com/dSystems/demo/Security/CustomUserDetailsService.java`
- `src/main/java/com/dSystems/demo/Security/JWTAuthenticationFilter.java`
- `src/main/java/com/dSystems/demo/Security/JWTHelper.java`
- `src/main/java/com/dSystems/demo/Security/JWTAthenticationEntryPoint.java`

### `CustomUserDetailsService`

Purpose:

- tell Spring Security how to load a user

What it does:

- receives username or email
- fetches the user from the database
- converts that user into Spring Security `UserDetails`

This is what makes authentication dynamic for all users.

### `JWTHelper`

Purpose:

- create and validate JWT tokens

What it does:

- reads JWT secret and expiration from configuration
- creates tokens during login
- extracts username from token
- checks whether token is expired
- validates token against the authenticated user

### `JWTAuthenticationFilter`

Purpose:

- inspect every request for a JWT token

What it does:

- reads the `Authorization` header
- checks for `Bearer <token>`
- extracts username from token
- loads user details from the database
- validates the token
- sets authentication into Spring Security context

This is the core piece that makes protected endpoints work.

### `JWTAthenticationEntryPoint`

Purpose:

- return unauthorized response when access fails

What it does:

- sends `401 Unauthorized` for invalid or missing authentication

## `Exception`

Main file:

- `src/main/java/com/dSystems/demo/Exception/GlobalExceptionHandler.java`

Purpose:

- return cleaner validation errors for bad request payloads

If required fields are missing or invalid, this class formats the response instead of returning a generic error.

## Configuration Files

### `src/main/resources/application.properties`

This file contains runtime configuration for:

- PostgreSQL connection
- JPA/Hibernate settings
- JWT secret
- JWT expiration

The database values are driven by environment variables:

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`

### `src/test/resources/application-test.properties`

This file is only for tests.

It uses H2 in-memory database so tests:

- do not depend on local PostgreSQL state
- do not fail because of existing real users
- stay repeatable

## Request Flow In Detail

### Register Flow

1. Client sends JSON to `/auth/register`
2. `AuthController` receives `RegisterRequest`
3. validation checks run
4. repository checks duplicate username/email
5. password is encrypted using `PasswordEncoder`
6. a new `AppUser` is created
7. user is saved through `AppUserRepository`
8. response returns success message

### Login Flow

1. Client sends JSON to `/auth/login`
2. `AuthController` receives `AuthRequest`
3. `AuthenticationManager` checks the credentials
4. `CustomUserDetailsService` loads the user from database
5. if credentials are valid, `JWTHelper` creates a token
6. token is returned in `AuthResponse`

### Protected Request Flow

1. Client sends request to `/api/me`
2. Client includes `Authorization: Bearer <token>`
3. `JWTAuthenticationFilter` reads the token
4. `JWTHelper` extracts username
5. `CustomUserDetailsService` loads user
6. token validity is checked
7. authentication is placed in security context
8. controller method runs as authenticated user

## Why Tests Use H2 But Runtime Uses PostgreSQL

Runtime uses PostgreSQL because that is the real application database.

Tests use H2 because:

- tests should not depend on your local database being populated correctly
- tests should not fail because a user already exists
- tests should run quickly and consistently

This is normal and intentional.

## What Is Already Verified

The following are already working:

- register endpoint
- login endpoint
- JWT generation
- JWT validation
- protected endpoint access
- PostgreSQL configuration for runtime
- isolated H2 configuration for tests
- Maven test suite passing

## Current Limitations

These are not implemented yet:

- refresh token flow
- forgot password / reset password
- email verification
- role-based endpoint restrictions beyond basic stored role field
- frontend integration

## Recommended Next Steps

Best next backend improvements:

1. Add role-based authorization using `ROLE_USER` and `ROLE_ADMIN`
2. Add more protected business endpoints
3. Improve response format consistency
4. Add frontend integration after backend API is frozen

## Short Summary

The backend is no longer using fixed users.

It now works like a normal authentication system:

- users register into the database
- users log in with stored credentials
- JWT tokens are issued on login
- tokens are validated for protected routes
- PostgreSQL stores the real user data
