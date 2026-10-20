# GradPilot

A daily planner for graduates juggling a job hunt, casual shifts, study and life admin. One Today screen, trackers for anything you need to follow, and reminders that arrive on your phone.

> Status: in development (Week 2 of 8 done). See the [roadmap](docs/ROADMAP.md).

## Why

Many graduates run two lives at once: a survival job with changing shifts, and a career push of applications, study and research. Progress slips because nothing lives in one place and nothing nudges you each day. GradPilot fixes that.

## Features (version 1)

- **Today screen:** next shift and leave-home time, today's one task, countdowns, and everything due today.
- **Daily check-in:** wake time, meals, self-care and bedtime.
- **Trackers with +:** create a tracker of any type.
  - Checklist, Pipeline, Progress, Log, Countdown, Schedule
  - Templates: Work shifts, Job hunt, Driving licence, Study plan, Research project, Big date
- **Notifications:** morning plan and evening wind-down, plus per-item reminders.
- **Login and sync** between phone and laptop.

Planned for version 2: automatic job application tracking from email, LeetCode and calendar connections, and a Python insights service.

## Tech stack

| Layer | Choice |
| --- | --- |
| Backend | Java 21, Spring Boot 3, Spring Security |
| Database | PostgreSQL, Flyway migrations |
| Frontend | React, TypeScript, Vite, installable web app (PWA) |
| Notifications | Web Push (VAPID) from a scheduled job |
| Testing | JUnit 5, Mockito, Testcontainers, Vitest |
| DevOps | Docker, GitHub Actions |

## Project structure

```
gradpilot/
├── backend/            Spring Boot API
├── docs/               Roadmap and design notes
├── docker-compose.yml  Local PostgreSQL
└── .github/workflows/  Continuous integration
```

## API

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| GET | `/api/health` | Public | Check the API is running |
| POST | `/api/auth/signup` | Public | Create an account, returns a token |
| POST | `/api/auth/login` | Public | Log in, returns a token |
| GET | `/api/me` | Bearer token | The logged-in user's profile |

Example:

```bash
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"at-least-8-chars","timezone":"Australia/Melbourne"}'
```

Errors come back as JSON [problem details](https://www.rfc-editor.org/rfc/rfc9457) with a `fields` map for validation errors.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/gradpilot` | Database connection |
| `DB_USERNAME` / `DB_PASSWORD` | `gradpilot` | Database login |
| `JWT_SECRET` | Development value | Token signing key, at least 32 bytes. Always set in production |
| `JWT_TTL` | `7d` | How long a login lasts |

## Run it locally

Requirements: Java 21, Maven 3.9+, Docker.

```bash
# 1. Start PostgreSQL
docker compose up -d

# 2. Run the API
cd backend
mvn spring-boot:run

# 3. Check it is alive
curl http://localhost:8080/api/health
```

Run the tests:

```bash
cd backend
mvn test
```

## Author

Nithyashree KP · [GitHub](https://github.com/nithyash2) · [Portfolio](https://nithyash2.netlify.app)

## Licence

MIT
