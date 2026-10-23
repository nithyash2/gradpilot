# Deploying GradPilot

GradPilot runs on three free services:

| Part | Service | Why |
| --- | --- | --- |
| Database | [Supabase](https://supabase.com) | Free PostgreSQL that does not expire, and stays awake while the app is used |
| API | [Render](https://render.com) | Runs the Docker image from `backend/Dockerfile` |
| Web app | [Netlify](https://www.netlify.com) | Hosts the built React app with HTTPS, which iPhone notifications need |

Allow about 30 minutes the first time. Do the steps in order, because each one needs a value from the step before.

## 1. Create the database (Supabase)

1. Sign up at supabase.com and create a new project. Pick the **Sydney** region and save the database password somewhere safe.
2. Open **Connect** at the top of the project, and choose the **Session pooler** connection. Render can only reach this one.
3. From that connection string, note three values:
   - **DB_URL:** `jdbc:postgresql://<host>:5432/postgres?sslmode=require`, using the host from the string (for example `aws-0-ap-southeast-2.pooler.supabase.com`)
   - **DB_USERNAME:** the user in the string, for example `postgres.abcdefghijkl`
   - **DB_PASSWORD:** the password you saved

The API creates all the tables itself on first start, using the Flyway migrations.

## 2. Make notification keys

On any computer with Node installed, run:

```bash
npx web-push generate-vapid-keys
```

Keep the **public key** and **private key** it prints. The private key is a secret: never commit it.

## 3. Start the API (Render)

1. Sign up at render.com with your GitHub account.
2. Choose **New > Blueprint** and pick the `gradpilot` repository. Render reads `render.yaml`.
3. Fill in the values it asks for:

   | Variable | Value |
   | --- | --- |
   | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | From step 1 |
   | `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` | From step 2 |
   | `VAPID_SUBJECT` | `mailto:` followed by your email |
   | `CORS_ORIGINS` | Leave as `http://localhost:5173` for now; you update it in step 5 |

4. Deploy. The first build takes about five minutes. When it finishes, open `https://<your-service>.onrender.com/api/health`. It should say `"status":"ok"`.

## 4. Publish the web app (Netlify)

1. Sign up at netlify.com with GitHub and choose **Add new site > Import an existing project**, then pick `gradpilot`.
2. Netlify reads `netlify.toml`, so the build settings fill themselves in.
3. Under **Environment variables**, add `VITE_API_URL` with your Render address, for example `https://gradpilot-api.onrender.com`.
4. Deploy, then rename the site under **Site configuration** if you like (for example `gradpilot.netlify.app`).

## 5. Connect the two

1. In Render, set `CORS_ORIGINS` to your Netlify address, for example `https://gradpilot.netlify.app`, and save. Render restarts the API.
2. In GitHub, open the repository's **Settings > Secrets and variables > Actions > Variables** and add `API_URL` with your Render address. The **Keep API awake** workflow then calls the API every 10 minutes, so the free service does not fall asleep and miss reminders.

## 6. Install on iPhone

1. Open your Netlify address in **Safari**.
2. Tap **Share**, then **Add to Home Screen**, then **Add**.
3. Open GradPilot from the Home Screen, sign up, then go to **Settings > Turn on notifications** and allow them.
4. Tap **Send a test**. A notification should arrive within a few seconds.

Notifications need iOS 16.4 or later.

## Checking it is healthy

- `GET /api/health` returns `ok`.
- The Render logs show `Sent ... daily digests and ... reminders` around your morning and evening times.
- The **Keep API awake** workflow runs green in the Actions tab.

## Costs and limits

Everything above is on free plans. The limits, checked in October 2026:

- **Render free:** 750 hours a month, which covers one service running all month; the service sleeps after 15 minutes without traffic, which the keep-awake workflow prevents.
- **Supabase free:** a 500 MB database; projects pause after a week with no activity, which does not happen while the reminder job runs.

Free plans change, so check each provider's pricing page before relying on these numbers.
