# Roadmap

## Version 1: done

### Set up
- [x] Repository, README, licence, editor config
- [x] Spring Boot skeleton with a health endpoint and test
- [x] PostgreSQL in Docker Compose, Flyway migrations
- [x] GitHub Actions CI for the backend and frontend

### Accounts
- [x] `users` table
- [x] Sign up and log in with JWT (Spring Security)
- [x] Profile, timezone and reminder times
- [x] Integration tests with Testcontainers

### Trackers
- [x] `trackers` and `items` tables, with type-specific data in JSONB
- [x] Endpoints for trackers and items, private to each user
- [x] Seven templates: Work shifts, Job hunt, Driving licence, Practice log, Study plan, Research project, Big date
- [x] Rules per type: pipeline stages, shift dates and times, countdown dates

### Today and check-in
- [x] Today API: next shift and leave time, items due, countdowns, check-in, seven-day streak
- [x] Daily check-in saved per day

### Notifications
- [x] Web Push encryption and VAPID signing, built on the JDK
- [x] Device subscriptions limited to real push services
- [x] Morning plan, evening wind-down and "time to leave" shift reminders
- [x] End-to-end tests against a fake push service

### Web app
- [x] React and TypeScript app with a screen for each tracker type
- [x] Installable on iPhone with a service worker and manifest
- [x] Light and dark themes

### Shipping
- [x] Production Docker image
- [x] Render, Netlify and Supabase configuration
- [x] Keep-awake workflow so reminders never miss
- [x] Deployment guide
- [ ] Deploy and add the live link to the README

## Version 2

- [ ] Automatic job application tracking from confirmation emails (Outlook first)
- [ ] Add shifts from a roster screenshot
- [ ] LeetCode progress from a username
- [ ] Google Calendar sync for schedule trackers
- [ ] Python FastAPI insights service, for example "you study more on days you wake before 7:30"
- [ ] Weekly review and streaks
- [ ] Meal planner with a shopping list
