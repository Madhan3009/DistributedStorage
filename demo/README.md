# JWT Auth Backend

This project is a Spring Boot backend with:

- JWT-based authentication
- user registration and login
- PostgreSQL as the main database
- JPA/Hibernate persistence

## What Works

- `POST /auth/register` creates a new user
- `POST /auth/login` validates credentials and returns a JWT token
- `GET /api/me` is a protected endpoint that returns the logged-in username

## Tech Stack

- Java 21
- Spring Boot 4
- Spring Security
- Spring Data JPA
- PostgreSQL
- JJWT

## Run Requirements

You need:

- Java 21
- Maven wrapper from the project
- PostgreSQL running locally or reachable from the app

## Database Configuration

The app reads these environment variables:

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`

Current defaults in the app are:

- host: `localhost`
- port: `5432`
- database: `demo_db`
- username: `postgres`

## Example Local Setup

```bash
export JAVA_HOME=/snap/intellij-idea-community/733/jbr
export PATH=$JAVA_HOME/bin:$PATH

export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=user_db
export DB_USERNAME=postgres
export DB_PASSWORD="Secret"
```

Then run:

```bash
./mvnw spring-boot:run
```

## API Usage

Register a user:

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","email":"user1@example.com","password":"secret123"}'
```

Login:

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"secret123"}'
```

Use the returned token:

```bash
curl http://localhost:8080/api/me \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

## Testing

Run tests with:

```bash
./mvnw test
```

Tests use an isolated H2 in-memory database under the `test` profile, so they do not depend on your PostgreSQL data.

## Main Files

- `src/main/java/com/dSystems/demo/Controller/AuthController.java`
- `src/main/java/com/dSystems/demo/Config/AppConfig.java`
- `src/main/java/com/dSystems/demo/Security/JWTAuthenticationFilter.java`
- `src/main/java/com/dSystems/demo/Security/JWTHelper.java`
- `src/main/resources/application.properties`

## Next Recommended Steps

- add role-based authorization
- add refresh tokens if needed
- add frontend integration after backend APIs are stable
