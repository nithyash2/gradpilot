# Roadmap

Eight weeks to a live version 1. Each week ends with working code pushed to `main`.

Progress: Weeks 1 and 2 done.

## Week 1: Set up
- [x] Repository, README, licence, editor config
- [x] Spring Boot skeleton with a health endpoint and test
- [x] PostgreSQL in Docker Compose, Flyway baseline migration
- [x] GitHub Actions CI for the backend

## Week 2: Login
- [x] `users` table migration
- [x] Sign up and log in endpoints with JWT (Spring Security)
- [x] Integration tests with Testcontainers

## Week 3: Trackers backend
- [ ] `trackers` and `items` tables (type-specific fields in JSONB)
- [ ] CRUD endpoints for trackers and items
- [ ] Templates as seed data: Work shifts, Job hunt, Driving licence, Study plan, Research project, Big date

## Week 4: Frontend basics
- [ ] React + TypeScript app with Vite
- [ ] Log in screen and Trackers screen connected to the API

## Week 5: Tracker types
- [ ] Detail views: Checklist, Pipeline, Progress, Log, Countdown, Schedule
- [ ] "+ New tracker" flow with templates

## Week 6: Today and check-in
- [ ] Today screen pulling from all trackers
- [ ] Daily check-in saved per day

## Week 7: Notifications
- [ ] Installable on iPhone (web manifest + service worker)
- [ ] Store push subscriptions; morning and evening messages from a scheduled job
- [ ] Leave-home reminders for shifts

## Week 8: Ship it
- [ ] Frontend CI
- [ ] Deploy frontend to Netlify and backend to a free host
- [ ] Screenshots, demo video, README polish

## Version 2
- Automatic job application tracking from email (Outlook first)
- Add shifts from a roster screenshot
- LeetCode, GitHub and Google Calendar connections
- Python FastAPI insights service
- Weekly review and streaks
