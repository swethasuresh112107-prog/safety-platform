# GuardianVoice

A personal safety app: sign in, add trusted contacts, and send a location-tagged
SOS alert with a two-second hold of a button. Built as a Spring Boot REST API
with a vanilla HTML/CSS/JS frontend.

## Guardian features (map, keywords, fake call, tracking)

- **Map** (`map.html`) — shows your current position and the nearest police
  stations, sourced free from OpenStreetMap's Overpass API (no key needed).
  Results are cached client-side for a few minutes so re-opening the map
  doesn't hammer the network on a slow connection.
- **Voice keywords** (`settings.html`) — set a spoken word that triggers SOS
  and a separate one that starts a fake call, using the browser's
  Web Speech API. **This only works while the tab is open and the
  microphone is granted** — Chrome/Edge support it, Firefox does not, and no
  browser can listen while the phone is locked or the app is closed. A voice
  SOS trigger fires after a 3-second on-screen countdown you can cancel, to
  guard against a false pickup.
- **Fake call** — a full-screen simulated incoming call (ringtone via Web
  Audio, vibration, accept/decline, live call timer) you can start manually
  from the dashboard/SOS page or with the fake-call keyword.
- **Nearest-station notification** — when SOS fires, the app looks up the
  nearest station and sends its name/phone with the alert. **There is no
  public API for a private app to auto-dispatch police** — `SosService`
  logs it the same way it logs contact notifications (see "Notifications
  are stubbed" below), and the UI surfaces a one-tap `tel:` link to the
  station's number if OpenStreetMap has one listed.
- **Location tracking** — a toggle that keeps your last-known position fresh
  via `watchPosition` while the tab is open, so SOS/map lookups don't wait
  on a slow GPS fix. This is *not* a true background service: it stops when
  the tab closes or the phone locks. A real always-on tracker needs a native
  Android/iOS app with a foreground location service.

## Offline SMS fallback

If the app can't reach the backend — `apiRequest` fails or the browser
reports `navigator.onLine === false` — SOS no longer just shows an error.
Instead (`script.js`, "offline SMS fallback" section):

1. The alert is queued in `localStorage` (`gv_pending_sos`) so it still
   lands in your history once you're back online — a background `sync`
   loop retries it automatically on the `online` event.
2. The app opens the phone's own SMS app via an `sms:` link, addressed to
   every contact cached the last time `/contacts` was reachable
   (`gv_contacts_cache`), pre-filled with an SOS message and a Google Maps
   link to the last known GPS fix. The user still has to hit send — a
   browser can't dispatch a text on its own, and there's no server in the
   loop to fail. A manual **"Send SOS via SMS instead"** button on the SOS
   page triggers the same flow on demand (e.g. you know you're offline and
   don't want to wait for the hold-button attempt to time out).
3. Every page shows a small offline/sync banner (`#offline-banner`) so it's
   obvious when you're offline and whether anything is still queued.

This is a *browser-app* fallback, not a native background SMS sender —
it needs the tab open and the user to tap "send" in their own SMS app.
A true silent background SMS (no user tap, works with the screen off)
needs a native Android app with `SmsManager` and the `SEND_SMS`
permission, which a web app cannot get.

## Incident reporting

A lighter-weight companion to SOS for things worth logging that weren't
urgent enough for an emergency alert (`incident.html`, `/api/incidents`):
title, category (harassment, theft, assault, suspicious activity, accident,
unsafe area, other), free-text description, optional location/address, and
when it happened. Reports created while offline are queued the same way as
SOS alerts (`gv_pending_incidents`) and synced automatically once the
connection returns; the list view merges cached + queued + server data so
you can still see your own reports offline.



```
GuardianVoice/
  frontend/     static pages (source of truth for the UI)
  backend/      Spring Boot API (Java 17, Maven)
  database/     schema.sql -- reference schema for MySQL
```

## How it fits together

The backend serves the API under `/api/**`. A copy of the frontend also lives
inside `backend/src/main/resources/static/`, so running the backend alone
serves the whole app (frontend + API) from one origin at `http://localhost:8080` --
no CORS setup needed. If you edit `frontend/`, re-copy those files into
`backend/src/main/resources/static/` before your next build (see below).

## Running it locally

**Requirements:** Java 17+, Maven 3.9+ (or use the included wrapper if you add one).

```bash
cd backend
mvn spring-boot:run
```

The app starts on `http://localhost:8080` using an in-memory H2 database --
no setup required, but data resets on every restart. Open
`http://localhost:8080/login.html` to use it.

### Switching to MySQL

1. Create the database: `mysql -u root -p < database/schema.sql`
2. In `backend/src/main/resources/application.properties`, comment out the H2
   block and uncomment the MySQL block, filling in your credentials.
3. Restart the app -- Hibernate will use the existing tables (or create/adjust
   them if you leave `ddl-auto=update`).

### Keeping frontend/ and the served copy in sync

```bash
cp frontend/*.html frontend/*.css frontend/*.js backend/src/main/resources/static/
```

## API reference

All routes are prefixed `/api`. Protected routes require
`Authorization: Bearer <token>`, using the token returned from login.

| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/auth/register` | `{ name, email, phone, password }` | 201, returns the created user |
| POST | `/auth/login` | `{ email, password }` | 200, returns `{ token, user }` |
| GET | `/contacts` | -- | list of the caller's contacts |
| POST | `/contacts` | `{ name, phone, relation, primary }` | 201 |
| PUT | `/contacts/{id}` | `{ name, phone, relation, primary }` | 200 |
| DELETE | `/contacts/{id}` | -- | 204 |
| POST | `/sos` | `{ lat, lng, message, nearestStationName?, nearestStationPhone?, stationLat?, stationLng? }` | 201, creates + "notifies" contacts and nearest station (logged; see below) |
| PUT | `/sos/{id}/cancel` | -- | 200, marks the alert cancelled |
| GET | `/sos/history` | -- | list of the caller's past alerts, most recent first |
| POST | `/incidents` | `{ title, category, description, lat?, lng?, address?, occurredAt?, syncedFromOffline? }` | 201, creates an incident report |
| GET | `/incidents` | -- | list of the caller's reports, most recent first |
| GET | `/incidents/{id}` | -- | a single report |
| DELETE | `/incidents/{id}` | -- | 204 |

Errors come back as `{ "message": "..." }` with an appropriate HTTP status.

### Notifications are stubbed

`SosService.notifyContacts()` currently logs who *would* be notified instead
of sending a real SMS/push/email. Wire in a provider (Twilio, SES, Firebase
Cloud Messaging, etc.) there before relying on this for real alerts.

## Security notes before deploying

- Set `JWT_SECRET` as a real environment variable -- don't ship the default
  in `application.properties`.
- The CORS config in `SecurityConfig` currently allows any origin, which is
  convenient for development. Once the frontend is served from the same
  origin as the API (as it is by default here), tighten this to your actual
  domain.
- Passwords are hashed with BCrypt before storage; the raw password is never
  persisted.

## Uploading to GitHub

See the walkthrough in the accompanying chat message, or in short:

```bash
cd GuardianVoice
git init
git add .
git commit -m "Initial commit: GuardianVoice frontend + backend"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo>.git
git push -u origin main
```
