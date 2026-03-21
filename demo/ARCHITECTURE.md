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

Contains configuration classes that shape how the application starts and where core settings come from.

Main file:

- `src/main/java/com/dSystems/demo/Config/AppConfig.java`
- `src/main/java/com/dSystems/demo/Config/StorageProperties.java`

What it does:

- defines the Spring Security filter chain
- disables CSRF for this API-style backend
- allows `/auth/**` without login
- requires authentication for everything else
- registers the JWT filter
- registers the authentication provider
- exposes the password encoder and authentication manager

#### Why `AppConfig` exists

Spring Security needs explicit configuration to know:

- which URLs are public
- which URLs require JWT authentication
- how passwords should be encoded
- how user authentication should be performed

Without this class, Spring Security would either use defaults or not behave the way this project needs.

#### Important methods in `AppConfig`

- `securityFilterChain(...)`
  Purpose:
  defines the HTTP security rules for the whole application
- `authenticationProvider()`
  Purpose:
  tells Spring how to validate username/password against stored users
- `passwordEncoder()`
  Purpose:
  ensures passwords are stored and matched using BCrypt
- `authenticationManager(...)`
  Purpose:
  gives the controller access to Spring’s authentication engine during login

#### Why `StorageProperties` exists

The chunk upload flow needs filesystem paths and chunk limits from configuration, not hardcoded strings in controllers.

This class binds values such as:

- `app.tempDir`
- `app.uploadDir`
- `app.maxChunksPerFile`

That makes the storage layer configurable and easier to change later when moving to Docker or another environment.

## `Controller`

Contains the REST endpoints.

Main files:

- `src/main/java/com/dSystems/demo/Controller/AuthController.java`
- `src/main/java/com/dSystems/demo/Controller/FileChunkController.java`
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

#### Why `AuthController` exists

This class is the entry point for authentication requests from the client.

It should stay thin:

- accept HTTP requests
- pass work to Spring Security and other services
- return HTTP responses

It should not contain database or token logic directly.

#### Important methods in `AuthController`

- `register(...)`
  Purpose:
  create a user and store an encoded password
- `login(...)`
  Purpose:
  authenticate credentials and return a JWT token

### `TestController`

This is a sample protected endpoint.

#### `/api/me`

Purpose:

- confirm that JWT authentication is working

What happens:

- request must contain a valid JWT token
- if authentication succeeds, it returns the logged-in username

#### Why `TestController` exists

This controller is not business logic. It exists only as a simple protected endpoint to verify that JWT authentication works correctly end to end.

### `FileChunkController`

This controller handles chunked file upload and rebuild endpoints.

#### `/files/chunks`

Purpose:

- receive one chunk of a file upload

What happens:

- receives the multipart chunk
- receives chunk number, total chunks, and identifier
- passes everything to `FileChunkService`
- returns the result as an HTTP response

#### `/files/rebuild`

Purpose:

- rebuild the final file from previously uploaded chunks

What happens:

- receives the file identifier
- delegates rebuild logic to `FileChunkService`
- returns success or failure message

#### Why `FileChunkController` exists

This class only handles web routing for chunk upload functionality.

The actual logic was intentionally moved out of the controller so:

- the controller stays simple
- the upload logic is reusable
- business logic is easier to test and maintain

#### Important methods in `FileChunkController`

- `uploadChunk(...)`
  Purpose:
  map HTTP chunk upload requests to the chunk service
- `rebuildFile(...)`
  Purpose:
  map rebuild requests to the rebuild service flow

## `Model`

Contains classes that represent structured application data.

Main file:

- `src/main/java/com/dSystems/demo/Model/AppUser.java`
- `src/main/java/com/dSystems/demo/Model/FileChunkMetadata.java`

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

### `FileChunkMetadata`

This model represents the meaning of the metadata file used in chunk uploads.

Fields:

- `fileId`
- `originalFileName`
- `totalChunks`
- `tempDirectory`
- `chunkPaths`

#### Why `FileChunkMetadata` exists

The metadata file on disk needs a structured in-memory representation so code can:

- read metadata cleanly
- inspect chunk locations
- rebuild the original file in the correct order

Without this model, metadata handling would stay as loose key/value strings everywhere.

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

Important methods:

- `generateToken(...)`
  Purpose:
  create a signed JWT for a successfully authenticated user
- `getUsernameFromToken(...)`
  Purpose:
  read the username stored inside the JWT
- `validateToken(...)`
  Purpose:
  confirm that the token matches the user and is not expired

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

Important method:

- `doFilterInternal(...)`
  Purpose:
  inspect each request, extract JWT if present, validate it, and set authentication in the security context

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

## `Service`

Contains business logic that should not live inside controllers.

Main files:

- `src/main/java/com/dSystems/demo/Service/FileChunkService.java`
- `src/main/java/com/dSystems/demo/Service/FileChunkMetadataService.java`

### `FileChunkService`

Purpose:

- handle chunk upload workflow
- validate upload rules
- save chunk files
- delegate metadata persistence
- trigger rebuild requests

#### Why `FileChunkService` exists

Chunk handling is business logic, not controller logic.

This service exists so the controller does not have to know:

- how chunks are validated
- where chunks are stored
- how many chunks are allowed
- how metadata is updated
- how rebuild requests are processed

#### Important methods in `FileChunkService`

- `uploadChunk(...)`
  Purpose:
  validate and store a chunk, update metadata, and report upload progress
- `rebuildFile(...)`
  Purpose:
  locate the metadata for an identifier and rebuild the final file using metadata service

### `FileChunkMetadataService`

Purpose:

- own metadata file handling completely
- make metadata useful for rebuild operations

#### Why `FileChunkMetadataService` exists

Metadata logic was separated because it is its own concern.

This service is responsible for:

- deciding where metadata lives
- updating metadata after each stored chunk
- reading metadata back into a structured object
- rebuilding a file from metadata

That separation makes the code cleaner because:

- chunk upload service handles upload workflow
- metadata service handles manifest/rebuild workflow

#### Important methods in `FileChunkMetadataService`

- `metadataPath(...)`
  Purpose:
  return the standard path of the metadata file for a chunk directory
- `updateMetadata(...)`
  Purpose:
  write or update the metadata file whenever a chunk is stored
- `readMetadata(...)`
  Purpose:
  load the metadata file into `FileChunkMetadata`
- `rebuildFile(...)`
  Purpose:
  reassemble the final file by reading chunk paths in order from metadata

## Configuration Files

### `src/main/resources/application.properties`

This file contains runtime configuration for:

- PostgreSQL connection
- JPA/Hibernate settings
- JWT secret
- JWT expiration
- temp directory for chunk storage
- upload directory for rebuilt files
- max chunks allowed per file

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

### Chunk Upload Flow

1. Client sends one chunk to `/files/chunks`
2. `FileChunkController` receives request parameters
3. controller delegates to `FileChunkService`
4. `FileChunkService` validates:
   - file presence
   - filename safety
   - identifier format
   - chunk numbering
   - exact 3-chunk rule
5. chunk file is stored under `temp/<identifier>/chunk-N.part`
6. `FileChunkMetadataService` updates `metadata.properties`
7. service counts stored chunks
8. response says either:
   - chunk accepted
   - or all chunks received

### Metadata Update Flow

1. a chunk is saved
2. metadata service opens or creates `metadata.properties`
3. it stores:
   - file id
   - original file name
   - total chunk count
   - temp directory
   - each chunk path
4. metadata file becomes the source of truth for reconstruction

### Rebuild Flow

1. Client calls `/files/rebuild?identifier=<id>`
2. controller delegates to `FileChunkService`
3. service locates `temp/<identifier>/metadata.properties`
4. metadata service reads the metadata
5. metadata service verifies chunk entries exist
6. chunk files are read in order `0 -> totalChunks - 1`
7. final file is written to the configured upload directory
8. response returns rebuilt file path

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
- chunk upload controller to service delegation
- metadata file generation for chunks
- rebuild logic based on metadata

## Current Limitations

These are not implemented yet:

- refresh token flow
- forgot password / reset password
- email verification
- role-based endpoint restrictions beyond basic stored role field
- frontend integration
- automatic client-side file splitting
- hex conversion for stored chunk content

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
- chunk uploads are stored under a controlled temp directory
- metadata is stored separately and can drive rebuild logic
