/* ==========================================================================
   GuardianVoice — script.js
   Shared API client + per-page logic.

   ASSUMED REST CONTRACT (adjust to match your actual Spring controllers,
   or tell me your real routes and I'll re-point these calls):

     POST   /api/auth/register   { name, email, phone, password }        -> 201 { id, name, email }
     POST   /api/auth/login      { email, password }                     -> 200 { token, user: { id, name, email } }

     GET    /api/contacts                                                -> 200 [ { id, name, phone, relation, primary } ]
     POST   /api/contacts        { name, phone, relation, primary }      -> 201 { id, ... }
     PUT    /api/contacts/{id}   { name, phone, relation, primary }      -> 200 { id, ... }
     DELETE /api/contacts/{id}                                          -> 204

     POST   /api/sos             { lat, lng, message }                   -> 201 { id, status, timestamp }
     PUT    /api/sos/{id}/cancel                                         -> 200 { id, status }
     GET    /api/sos/history                                             -> 200 [ { id, timestamp, status, lat, lng, message } ]

   Auth: token returned from login is stored in localStorage under
   "gv_token" and sent as "Authorization: Bearer <token>" on every
   protected request. Adjust AUTH_HEADER below if your backend expects
   a different scheme (e.g. a custom header or cookie session).
   ========================================================================== */

const API_BASE = '/api';
const TOKEN_KEY = 'gv_token';
const USER_KEY = 'gv_user';

/* ---- storage helpers --------------------------------------------------- */

function getToken() { return localStorage.getItem(TOKEN_KEY); }
function setSession(token, user) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user || {}));
}
function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}
function getUser() {
  try { return JSON.parse(localStorage.getItem(USER_KEY) || '{}'); }
  catch { return {}; }
}

/* ---- API client --------------------------------------------------------- */

async function apiRequest(path, { method = 'GET', body, auth = true } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (auth) {
    const token = getToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
  }

  let res;
  try {
    res = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch (err) {
    const networkErr = new Error('Could not reach the server. Check your connection and try again.');
    networkErr.isNetworkError = true;
    throw networkErr;
  }

  if (res.status === 401) {
    clearSession();
    window.location.href = 'login.html';
    throw new Error('Session expired.');
  }

  if (res.status === 204) return null;

  let data = null;
  try { data = await res.json(); } catch { /* empty body */ }

  if (!res.ok) {
    const message = (data && (data.message || data.error)) || `Request failed (${res.status})`;
    throw new Error(message);
  }
  return data;
}

/* ---- offline support: contact cache + pending-sync queues ---------------- */
/*
   The backend can't be reached when the device has no signal/data, so the
   app keeps a local copy of the contact list (for the SMS fallback) and
   queues anything created while offline (SOS alerts, incident reports) to
   replay automatically the moment connectivity returns.
*/

const CONTACTS_CACHE_KEY = 'gv_contacts_cache';
const PENDING_SOS_KEY = 'gv_pending_sos';
const PENDING_INCIDENTS_KEY = 'gv_pending_incidents';
const INCIDENTS_CACHE_KEY = 'gv_incidents_cache';

function cacheContacts(contacts) {
  try { localStorage.setItem(CONTACTS_CACHE_KEY, JSON.stringify(contacts || [])); } catch { /* ignore */ }
}
function getCachedContacts() {
  try { return JSON.parse(localStorage.getItem(CONTACTS_CACHE_KEY) || '[]'); } catch { return []; }
}

function readQueue(key) {
  try { return JSON.parse(localStorage.getItem(key) || '[]'); } catch { return []; }
}
function writeQueue(key, items) {
  try { localStorage.setItem(key, JSON.stringify(items)); } catch { /* storage full, ignore */ }
}

function queuePendingSos(payload) {
  const items = readQueue(PENDING_SOS_KEY);
  const entry = { localId: `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`, payload, queuedAt: Date.now() };
  items.push(entry);
  writeQueue(PENDING_SOS_KEY, items);
  return entry;
}
function getPendingSos() { return readQueue(PENDING_SOS_KEY); }
function removePendingSos(localId) {
  writeQueue(PENDING_SOS_KEY, getPendingSos().filter((i) => i.localId !== localId));
}

function queuePendingIncident(payload) {
  const items = readQueue(PENDING_INCIDENTS_KEY);
  const entry = { localId: `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`, payload, queuedAt: Date.now() };
  items.push(entry);
  writeQueue(PENDING_INCIDENTS_KEY, items);
  return entry;
}
function getPendingIncidents() { return readQueue(PENDING_INCIDENTS_KEY); }
function removePendingIncident(localId) {
  writeQueue(PENDING_INCIDENTS_KEY, getPendingIncidents().filter((i) => i.localId !== localId));
}

function cacheIncidents(incidents) {
  try { localStorage.setItem(INCIDENTS_CACHE_KEY, JSON.stringify(incidents || [])); } catch { /* ignore */ }
}
function getCachedIncidents() {
  try { return JSON.parse(localStorage.getItem(INCIDENTS_CACHE_KEY) || '[]'); } catch { return []; }
}

/**
 * Replays anything queued while offline. Called on the 'online' event and
 * once at page load if the browser already reports a connection. Silently
 * gives up on individual items that still fail (e.g. token expired) -- they
 * stay queued and are retried on the next sync.
 */
let syncInFlight = false;
async function syncPendingData() {
  if (syncInFlight || !navigator.onLine || !getToken()) return;
  syncInFlight = true;
  try {
    for (const item of getPendingSos()) {
      try {
        await apiRequest('/sos', { method: 'POST', body: item.payload });
        removePendingSos(item.localId);
      } catch (err) {
        if (!err.isNetworkError) removePendingSos(item.localId); // e.g. bad request -- drop, don't loop forever
        else break; // still offline-ish, stop and retry later
      }
    }
    for (const item of getPendingIncidents()) {
      try {
        await apiRequest('/incidents', { method: 'POST', body: item.payload });
        removePendingIncident(item.localId);
      } catch (err) {
        if (!err.isNetworkError) removePendingIncident(item.localId);
        else break;
      }
    }
  } finally {
    syncInFlight = false;
    updateOfflineBanner();
    if (typeof window.__gvRefreshIncidents === 'function') window.__gvRefreshIncidents();
  }
}

/**
 * Small banner shown on every page (#offline-banner) reflecting connection
 * state and any queued-but-unsent alerts/reports.
 */
function updateOfflineBanner() {
  const banner = document.getElementById('offline-banner');
  if (!banner) return;

  const pendingSos = getPendingSos().length;
  const pendingIncidents = getPendingIncidents().length;

  if (!navigator.onLine) {
    banner.textContent = "You're offline. SOS alerts will fall back to SMS, and reports will be saved on this device and sent once you're back online.";
    banner.classList.add('is-error');
    banner.style.display = 'block';
  } else if (pendingSos > 0 || pendingIncidents > 0) {
    const parts = [];
    if (pendingSos > 0) parts.push(`${pendingSos} SOS alert${pendingSos > 1 ? 's' : ''}`);
    if (pendingIncidents > 0) parts.push(`${pendingIncidents} report${pendingIncidents > 1 ? 's' : ''}`);
    banner.textContent = `Back online — syncing ${parts.join(' and ')}…`;
    banner.classList.remove('is-error');
    banner.style.display = 'block';
  } else {
    banner.style.display = 'none';
  }
}

function initOfflineBanner() {
  updateOfflineBanner();
  window.addEventListener('online', () => { updateOfflineBanner(); syncPendingData(); });
  window.addEventListener('offline', updateOfflineBanner);
  if (navigator.onLine) syncPendingData();
}

/* ---- guardian settings (keywords, tracking, listening) ------------------ */

const SETTINGS_KEY = 'gv_settings';
const LAST_POS_KEY = 'gv_last_position';
const STATION_CACHE_KEY = 'gv_station_cache';

const DEFAULT_SETTINGS = {
  sosKeyword: 'guardian help',
  fakeCallKeyword: 'mango',
  callerName: 'Amma',
  listenEnabled: false,
  trackingEnabled: false,
};

function getSettings() {
  try { return { ...DEFAULT_SETTINGS, ...JSON.parse(localStorage.getItem(SETTINGS_KEY) || '{}') }; }
  catch { return { ...DEFAULT_SETTINGS }; }
}
function setSettings(patch) {
  const next = { ...getSettings(), ...patch };
  localStorage.setItem(SETTINGS_KEY, JSON.stringify(next));
  return next;
}

/* ---- location: cached + throttled to save battery/network --------------- */

function saveLastPosition(coords) {
  localStorage.setItem(LAST_POS_KEY, JSON.stringify({
    lat: coords.latitude, lng: coords.longitude, at: Date.now(),
  }));
}
function getLastPosition() {
  try { return JSON.parse(localStorage.getItem(LAST_POS_KEY) || 'null'); }
  catch { return null; }
}

/**
 * Gets a current position, preferring a recent cached fix over a fresh GPS
 * request so the app works reasonably on a weak/slow connection. Falls back
 * to the last known position (however old) if a live fix isn't available.
 */
function getPosition({ maxAgeMs = 60000, timeoutMs = 6000 } = {}) {
  const cached = getLastPosition();
  if (cached && (Date.now() - cached.at) < maxAgeMs) {
    return Promise.resolve(cached);
  }
  if (!navigator.geolocation) return Promise.resolve(cached);
  return new Promise((resolve) => {
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        saveLastPosition(pos.coords);
        resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude, at: Date.now() });
      },
      () => resolve(cached),
      { timeout: timeoutMs, maximumAge: maxAgeMs }
    );
  });
}

function haversineKm(lat1, lng1, lat2, lng2) {
  const R = 6371;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLng = (lng2 - lng1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLng / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/**
 * Nearby police stations via the free OpenStreetMap Overpass API (no key
 * needed). Results are cached for a few minutes keyed to a rounded
 * lat/lng so panning around or re-opening the map doesn't re-hit the
 * network every time -- keeps this usable on a slow connection.
 */
async function fetchNearbyPoliceStations(lat, lng, radiusM = 4000) {
  const cacheKey = `${lat.toFixed(2)},${lng.toFixed(2)}`;
  let cache = {};
  try { cache = JSON.parse(localStorage.getItem(STATION_CACHE_KEY) || '{}'); } catch { /* ignore */ }
  const hit = cache[cacheKey];
  if (hit && (Date.now() - hit.at) < 5 * 60 * 1000) {
    return hit.stations;
  }

  const query = `[out:json][timeout:15];node["amenity"="police"](around:${radiusM},${lat},${lng});out;`;
  const url = `https://overpass-api.de/api/interpreter?data=${encodeURIComponent(query)}`;

  let stations = [];
  try {
    const res = await fetch(url);
    if (res.ok) {
      const data = await res.json();
      stations = (data.elements || []).map((el) => ({
        id: el.id,
        name: (el.tags && el.tags.name) || 'Police Station',
        phone: (el.tags && (el.tags.phone || el.tags['contact:phone'])) || null,
        lat: el.lat,
        lng: el.lon,
        distanceKm: haversineKm(lat, lng, el.lat, el.lon),
      })).sort((a, b) => a.distanceKm - b.distanceKm);
    }
  } catch {
    stations = [];
  }

  cache[cacheKey] = { at: Date.now(), stations };
  try { localStorage.setItem(STATION_CACHE_KEY, JSON.stringify(cache)); } catch { /* storage full, ignore */ }
  return stations;
}

/**
 * Nearby police/hospital/transport via the backend's own Overpass-backed
 * endpoint (see NearbyPlacesController) -- this replaced the old
 * client-side-only Overpass call so map.html, and eventually Android, share
 * one source of truth instead of duplicating the lookup logic.
 */
async function fetchNearbyPlaces(lat, lng, types = ['police', 'hospital', 'transport'], radiusM = 4000) {
  try {
    const qs = new URLSearchParams({ lat, lng, types: types.join(','), radius: radiusM }).toString();
    return await apiRequest(`/places/nearby?${qs}`);
  } catch {
    return [];
  }
}

/* ---- offline SMS fallback -------------------------------------------------
   When the API can't be reached (no signal/data), GuardianVoice can't send a
   push/SMS itself -- there's no server to do it. Instead it hands off to the
   phone's own SMS app, pre-filled with an SOS message and the last known
   location, addressed to every cached emergency contact. This needs no
   internet: it's the same as the user manually texting their contacts, just
   pre-filled so it's one tap in a crisis. The user still has to hit send in
   their SMS app -- browsers can't send a text on their own.
   ------------------------------------------------------------------------- */

function isIOS() {
  return /iPad|iPhone|iPod/.test(navigator.userAgent);
}

function buildSosSmsMessage(pos) {
  const loc = pos && pos.lat && pos.lng
    ? `My location: https://maps.google.com/?q=${pos.lat},${pos.lng}`
    : 'My location is unavailable right now.';
  return `SOS from GuardianVoice: I need help. ${loc}`;
}

/**
 * Builds an sms: link addressed to every cached contact. iOS and Android
 * disagree on the separator between the number list and the query string
 * (Android/most browsers: "?body=", iOS Safari: "&body="), so branch on UA.
 */
function buildSmsFallbackLink(contacts, message) {
  const numbers = contacts.map((c) => c.phone).filter(Boolean).join(',');
  const sep = isIOS() ? '&' : '?';
  return `sms:${numbers}${sep}body=${encodeURIComponent(message)}`;
}

/**
 * Opens the device's SMS app pre-filled with an SOS message to every cached
 * contact. Returns true if it had contacts to text, false if there was
 * nobody to fall back to (caller should tell the user to add contacts).
 */
function triggerSmsFallback(pos) {
  const contacts = getCachedContacts();
  if (contacts.length === 0) return false;
  const message = buildSosSmsMessage(pos);
  const link = buildSmsFallbackLink(contacts, message);
  window.location.href = link;
  return true;
}

/* ---- background-ish tracking (foreground only, tab must be open) -------- */

let watchId = null;

function updateTrackingIndicator(active) {
  document.querySelectorAll('[data-tracking-dot]').forEach((el) => {
    el.classList.toggle('live', active);
  });
}

function startTracking() {
  if (!navigator.geolocation || watchId !== null) return;
  watchId = navigator.geolocation.watchPosition(
    (pos) => saveLastPosition(pos.coords),
    () => { /* ignore transient errors, cached position still used */ },
    { enableHighAccuracy: false, maximumAge: 30000, timeout: 20000 }
  );
  updateTrackingIndicator(true);
}
function stopTracking() {
  if (watchId !== null) {
    navigator.geolocation.clearWatch(watchId);
    watchId = null;
  }
  updateTrackingIndicator(false);
}

/* ---- voice keyword listening (foreground only, needs mic permission) ---- */

let recognizer = null;
let recognizerRestartTimer = null;

function initVoiceGuardian() {
  const settings = getSettings();
  const SpeechRecognitionCtor = window.SpeechRecognition || window.webkitSpeechRecognition;

  if (!settings.listenEnabled) { stopVoiceGuardian(); return; }
  if (!SpeechRecognitionCtor) return; // unsupported browser; settings page already warns about this
  if (recognizer) return; // already running

  recognizer = new SpeechRecognitionCtor();
  recognizer.continuous = true;
  recognizer.interimResults = false;
  recognizer.lang = 'en-IN';

  recognizer.onresult = (event) => {
    const s = getSettings();
    const transcript = Array.from(event.results)
      .map((r) => r[0].transcript)
      .join(' ')
      .toLowerCase();

    if (s.fakeCallKeyword && transcript.includes(s.fakeCallKeyword.toLowerCase())) {
      triggerFakeCall();
      return;
    }
    if (s.sosKeyword && transcript.includes(s.sosKeyword.toLowerCase())) {
      triggerVoiceSos();
    }
  };

  recognizer.onerror = () => { /* mic glitches happen; onend below restarts it */ };
  recognizer.onend = () => {
    recognizer = null;
    if (getSettings().listenEnabled) {
      clearTimeout(recognizerRestartTimer);
      recognizerRestartTimer = setTimeout(initVoiceGuardian, 800);
    }
  };

  try { recognizer.start(); updateListeningIndicator(true); }
  catch { recognizer = null; }
}

function stopVoiceGuardian() {
  clearTimeout(recognizerRestartTimer);
  if (recognizer) {
    recognizer.onend = null;
    try { recognizer.stop(); } catch { /* already stopped */ }
    recognizer = null;
  }
  updateListeningIndicator(false);
}

function updateListeningIndicator(active) {
  document.querySelectorAll('[data-listening-dot]').forEach((el) => {
    el.classList.toggle('live', active);
  });
}

function triggerVoiceSos() {
  if (currentPage() === 'sos.html' && typeof window.__gvAutoTriggerSos === 'function') {
    window.__gvAutoTriggerSos();
  } else {
    window.location.href = 'sos.html?auto=1';
  }
}

/* ---- fake call overlay --------------------------------------------------- */

let fakeCallAudioCtx = null;
let fakeCallRingInterval = null;
let fakeCallTimerInterval = null;

function buildFakeCallOverlay() {
  if (document.getElementById('gv-fakecall-overlay')) return;
  const el = document.createElement('div');
  el.id = 'gv-fakecall-overlay';
  el.className = 'fakecall-overlay';
  el.innerHTML = `
    <div class="fakecall-card">
      <div class="fakecall-status" id="gv-fakecall-status">Incoming call</div>
      <div class="fakecall-avatar" id="gv-fakecall-avatar">?</div>
      <div class="fakecall-name" id="gv-fakecall-name">Calling…</div>
      <div class="fakecall-sub" id="gv-fakecall-sub">mobile</div>
      <div class="fakecall-actions" id="gv-fakecall-ringing-actions">
        <button class="fakecall-btn decline" id="gv-fakecall-decline" aria-label="Decline">✕</button>
        <button class="fakecall-btn accept" id="gv-fakecall-accept" aria-label="Accept">✓</button>
      </div>
      <div class="fakecall-actions" id="gv-fakecall-active-actions" style="display:none;">
        <button class="fakecall-btn end" id="gv-fakecall-end" aria-label="End call">✕</button>
      </div>
    </div>`;
  document.body.appendChild(el);

  document.getElementById('gv-fakecall-decline').addEventListener('click', endFakeCall);
  document.getElementById('gv-fakecall-end').addEventListener('click', endFakeCall);
  document.getElementById('gv-fakecall-accept').addEventListener('click', answerFakeCall);
}

function playRingtone() {
  try {
    fakeCallAudioCtx = new (window.AudioContext || window.webkitAudioContext)();
    const ring = () => {
      const ctx = fakeCallAudioCtx;
      if (!ctx) return;
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.frequency.value = 480;
      gain.gain.setValueAtTime(0.0001, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.15, ctx.currentTime + 0.05);
      gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.4);
      osc.connect(gain).connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + 0.45);
    };
    ring();
    fakeCallRingInterval = setInterval(ring, 900);
  } catch { /* audio not available, silent fallback */ }
  if (navigator.vibrate) {
    try { navigator.vibrate([600, 300, 600, 300, 600]); } catch { /* unsupported */ }
  }
}
function stopRingtone() {
  clearInterval(fakeCallRingInterval);
  fakeCallRingInterval = null;
  if (fakeCallAudioCtx) { fakeCallAudioCtx.close(); fakeCallAudioCtx = null; }
  if (navigator.vibrate) { try { navigator.vibrate(0); } catch { /* unsupported */ } }
}

function triggerFakeCall() {
  buildFakeCallOverlay();
  const settings = getSettings();
  const name = settings.callerName || 'Amma';
  document.getElementById('gv-fakecall-name').textContent = name;
  document.getElementById('gv-fakecall-avatar').textContent = initials(name);
  document.getElementById('gv-fakecall-sub').textContent = 'mobile';
  document.getElementById('gv-fakecall-status').textContent = 'Incoming call';
  document.getElementById('gv-fakecall-ringing-actions').style.display = 'flex';
  document.getElementById('gv-fakecall-active-actions').style.display = 'none';
  document.getElementById('gv-fakecall-overlay').classList.add('open');
  playRingtone();
}

function answerFakeCall() {
  stopRingtone();
  document.getElementById('gv-fakecall-status').textContent = '00:00';
  document.getElementById('gv-fakecall-sub').textContent = 'On call';
  document.getElementById('gv-fakecall-ringing-actions').style.display = 'none';
  document.getElementById('gv-fakecall-active-actions').style.display = 'flex';

  let seconds = 0;
  fakeCallTimerInterval = setInterval(() => {
    seconds += 1;
    const m = String(Math.floor(seconds / 60)).padStart(2, '0');
    const s = String(seconds % 60).padStart(2, '0');
    const statusEl = document.getElementById('gv-fakecall-status');
    if (statusEl) statusEl.textContent = `${m}:${s}`;
  }, 1000);
}

function endFakeCall() {
  stopRingtone();
  clearInterval(fakeCallTimerInterval);
  fakeCallTimerInterval = null;
  const overlay = document.getElementById('gv-fakecall-overlay');
  if (overlay) overlay.classList.remove('open');
}

/* ---- boot the always-on guardian features on protected pages ------------ */

function initGuardianAlwaysOn() {
  buildFakeCallOverlay();
  const settings = getSettings();
  if (settings.trackingEnabled) startTracking();
  if (settings.listenEnabled) initVoiceGuardian();

  document.querySelectorAll('[data-fake-call-btn]').forEach((btn) => {
    btn.addEventListener('click', () => triggerFakeCall());
  });
}

/* ---- auth guards -------------------------------------------------------- */

const PROTECTED_PAGES = ['dashboard.html', 'sos.html', 'contacts.html', 'history.html', 'map.html', 'settings.html', 'incident.html'];
const AUTH_PAGES = ['login.html', 'register.html'];

function currentPage() {
  return window.location.pathname.split('/').pop() || 'login.html';
}

function guardPage() {
  const page = currentPage();
  const loggedIn = Boolean(getToken());
  if (PROTECTED_PAGES.includes(page) && !loggedIn) {
    window.location.href = 'login.html';
  }
  if (AUTH_PAGES.includes(page) && loggedIn) {
    window.location.href = 'dashboard.html';
  }
}

/* ---- shared UI wiring ----------------------------------------------------- */

function wireTopbar() {
  const page = currentPage();
  document.querySelectorAll('.topbar-nav a, .mobile-nav a').forEach((a) => {
    if (a.getAttribute('href') === page) a.classList.add('active');
  });
  const nameEl = document.querySelector('[data-user-name]');
  if (nameEl) nameEl.textContent = getUser().name || 'Account';

  const logoutBtn = document.querySelector('[data-logout]');
  if (logoutBtn) {
    logoutBtn.addEventListener('click', () => {
      clearSession();
      window.location.href = 'login.html';
    });
  }
}

function showMsg(el, text, type = 'error') {
  if (!el) return;
  el.textContent = text;
  el.className = `form-msg ${type}`;
}

function initials(name) {
  return (name || '?')
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0].toUpperCase())
    .join('');
}

function formatTimestamp(iso) {
  try {
    const d = new Date(iso);
    return d.toLocaleString(undefined, {
      month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit',
    });
  } catch { return iso; }
}

/* ---- login.html ------------------------------------------------------- */

function initLoginPage() {
  const form = document.getElementById('login-form');
  if (!form) return;
  const msg = document.getElementById('login-msg');

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const submitBtn = form.querySelector('button[type="submit"]');
    submitBtn.disabled = true;
    msg.className = 'form-msg';

    const email = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value;

    try {
      const data = await apiRequest('/auth/login', {
        method: 'POST',
        auth: false,
        body: { email, password },
      });
      setSession(data.token, data.user);
      window.location.href = 'dashboard.html';
    } catch (err) {
      showMsg(msg, err.message || 'Could not sign in. Check your details and try again.');
    } finally {
      submitBtn.disabled = false;
    }
  });
}

/* ---- register.html ----------------------------------------------------- */

function initRegisterPage() {
  const form = document.getElementById('register-form');
  if (!form) return;
  const msg = document.getElementById('register-msg');

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const submitBtn = form.querySelector('button[type="submit"]');
    const password = document.getElementById('password').value;
    const confirm = document.getElementById('confirm-password').value;

    if (password !== confirm) {
      showMsg(msg, 'Passwords do not match.');
      return;
    }

    submitBtn.disabled = true;
    msg.className = 'form-msg';

    const body = {
      name: document.getElementById('name').value.trim(),
      email: document.getElementById('email').value.trim(),
      phone: document.getElementById('phone').value.trim(),
      password,
    };

    try {
      await apiRequest('/auth/register', { method: 'POST', auth: false, body });
      showMsg(msg, 'Account created. Redirecting to sign in…', 'success');
      setTimeout(() => { window.location.href = 'login.html'; }, 1200);
    } catch (err) {
      showMsg(msg, err.message || 'Could not create your account.');
      submitBtn.disabled = false;
    }
  });
}

/* ---- dashboard.html ----------------------------------------------------- */

async function initDashboardPage() {
  const grid = document.getElementById('stat-grid');
  if (!grid) return;

  const user = getUser();
  const greetEl = document.getElementById('greeting');
  if (greetEl) greetEl.textContent = `Hi ${user.name || 'there'}, you're protected.`;

  try {
    const [contacts, history] = await Promise.all([
      apiRequest('/contacts'),
      apiRequest('/sos/history'),
    ]);
    cacheContacts(contacts);

    document.getElementById('stat-contacts').textContent = contacts.length;
    document.getElementById('stat-alerts').textContent = history.length;

    const last = history[0];
    document.getElementById('stat-last').textContent = last ? formatTimestamp(last.timestamp) : '—';

    const recentList = document.getElementById('recent-activity');
    if (recentList) {
      recentList.innerHTML = '';
      if (history.length === 0) {
        recentList.innerHTML = `
          <div class="empty-state">
            <h3>No activity yet</h3>
            <p>Your alert history will show up here once you use GuardianVoice.</p>
          </div>`;
      } else {
        history.slice(0, 4).forEach((item) => {
          recentList.insertAdjacentHTML('beforeend', renderHistoryRow(item));
        });
      }
    }
  } catch (err) {
    console.error(err);
  }
}

/* ---- sos.html ------------------------------------------------------------ */

function initSosPage() {
  const holdBtn = document.getElementById('hold-button');
  if (!holdBtn) return;

  const HOLD_MS = 1800;
  const statusLine = document.getElementById('sos-status-line');
  const sentPanel = document.getElementById('sos-sent-panel');
  const cancelBtn = document.getElementById('cancel-sos');

  let holdTimer = null;
  let progressRaf = null;
  let startedAt = 0;
  let currentSosId = null;
  let autoCancelTimer = null;

  function setProgress(pct) {
    holdBtn.style.setProperty('--progress', String(pct));
  }

  function stepProgress() {
    const elapsed = performance.now() - startedAt;
    const pct = Math.min(100, (elapsed / HOLD_MS) * 100);
    setProgress(pct);
    if (pct < 100) {
      progressRaf = requestAnimationFrame(stepProgress);
    }
  }

  function startHold() {
    if (holdBtn.classList.contains('sent')) return;
    holdBtn.classList.add('holding');
    startedAt = performance.now();
    progressRaf = requestAnimationFrame(stepProgress);
    holdTimer = setTimeout(triggerSos, HOLD_MS);
  }

  function cancelHold() {
    holdBtn.classList.remove('holding');
    clearTimeout(holdTimer);
    cancelAnimationFrame(progressRaf);
    setProgress(0);
  }

  async function triggerSos() {
    cancelAnimationFrame(progressRaf);
    setProgress(100);
    holdBtn.classList.remove('holding');
    holdBtn.classList.add('sent');
    statusLine.innerHTML = 'Sending alert to your emergency contacts…';

    const pos = await getPosition();
    let station = null;
    if (pos && navigator.onLine) {
      try {
        const stations = await fetchNearbyPoliceStations(pos.lat, pos.lng);
        station = stations[0] || null;
      } catch { station = null; }
    }

    const sosPayload = {
      lat: pos ? pos.lat : null,
      lng: pos ? pos.lng : null,
      message: 'SOS triggered from GuardianVoice app',
      nearestStationName: station ? station.name : null,
      nearestStationPhone: station ? station.phone : null,
      stationLat: station ? station.lat : null,
      stationLng: station ? station.lng : null,
    };

    const stationLine = document.getElementById('sos-station-line');

    // Offline (or the server just can't be reached): skip straight to the
    // SMS fallback and queue the alert so it still lands in history once
    // connectivity returns.
    if (!navigator.onLine) {
      handleOfflineSos(sosPayload, pos);
      return;
    }

    try {
      const data = await apiRequest('/sos', { method: 'POST', body: sosPayload });

      currentSosId = data && data.id;
      document.getElementById('sos-sent-heading').textContent = 'Your location was shared';
      document.getElementById('sos-sent-sub').textContent = 'Your primary contacts have been notified with your last known location.';
      statusLine.innerHTML = '<strong>Alert sent.</strong> Your contacts have been notified.';

      if (stationLine) {
        if (station) {
          stationLine.innerHTML = `Nearest station: <strong>${escapeHtml(station.name)}</strong>` +
            (station.phone
              ? ` — <a href="tel:${escapeHtml(station.phone)}">call ${escapeHtml(station.phone)}</a>`
              : ' (no listed phone number — dial 112 for police)');
          stationLine.style.display = 'block';
        } else {
          stationLine.style.display = 'none';
        }
      }
      sentPanel.style.display = 'block';
    } catch (err) {
      if (err.isNetworkError) {
        handleOfflineSos(sosPayload, pos);
        return;
      }
      statusLine.textContent = err.message || 'Could not send the alert. Try again.';
      holdBtn.classList.remove('sent');
      setProgress(0);
    }
  }

  /**
   * Can't reach the backend -- queue the alert for later sync and fall back
   * to opening the device's SMS app addressed to cached contacts.
   */
  function handleOfflineSos(sosPayload, pos) {
    queuePendingSos(sosPayload);
    updateOfflineBanner();

    const sentSms = triggerSmsFallback(pos);
    document.getElementById('sos-sent-heading').textContent = 'Offline — falling back to SMS';
    document.getElementById('sos-sent-sub').textContent = sentSms
      ? "We couldn't reach the server, so your SMS app should now be open with an SOS message ready to send to your contacts. It will also sync to your account once you're back online."
      : "We couldn't reach the server, and you don't have any cached contacts to text. It will sync to your account once you're back online. Add contacts while you have signal so this fallback works.";
    statusLine.innerHTML = "<strong>You're offline.</strong> Queued for sync — use the SMS button below if it didn't open automatically.";
    const stationLine = document.getElementById('sos-station-line');
    if (stationLine) stationLine.style.display = 'none';
    sentPanel.style.display = 'block';
  }

  holdBtn.addEventListener('pointerdown', startHold);
  holdBtn.addEventListener('pointerup', cancelHold);
  holdBtn.addEventListener('pointerleave', cancelHold);

  const smsFallbackBtn = document.getElementById('sms-fallback-btn');
  if (smsFallbackBtn) {
    smsFallbackBtn.addEventListener('click', async () => {
      const pos = getLastPosition();
      const sent = triggerSmsFallback(pos);
      if (!sent) {
        alert('No emergency contacts are cached on this device yet. Add contacts while you have signal so this works offline.');
      }
    });
  }

  if (cancelBtn) {
    cancelBtn.addEventListener('click', async () => {
      clearInterval(autoCancelTimer);
      try {
        if (currentSosId) await apiRequest(`/sos/${currentSosId}/cancel`, { method: 'PUT' });
      } catch (err) {
        console.error(err);
      }
      holdBtn.classList.remove('sent');
      setProgress(0);
      statusLine.innerHTML = 'Hold the button for two seconds to alert your contacts.';
      sentPanel.style.display = 'none';
      currentSosId = null;
    });
  }

  // Voice-keyword trigger: fires the alert immediately (no hold), but gives
  // a short window to cancel in case the keyword was picked up by mistake.
  window.__gvAutoTriggerSos = () => {
    if (holdBtn.classList.contains('sent')) return;
    statusLine.textContent = 'Voice keyword detected — sending in 3s. Tap Cancel to stop.';
    sentPanel.style.display = 'none';
    let secondsLeft = 3;
    autoCancelTimer = setInterval(() => {
      secondsLeft -= 1;
      if (secondsLeft <= 0) {
        clearInterval(autoCancelTimer);
        triggerSos();
      } else {
        statusLine.textContent = `Voice keyword detected — sending in ${secondsLeft}s. Tap Cancel to stop.`;
      }
    }, 1000);
    sentPanel.style.display = 'block';
    document.getElementById('sos-station-line').style.display = 'none';
  };

  if (new URLSearchParams(window.location.search).get('auto') === '1') {
    window.__gvAutoTriggerSos();
  }
}

/* ---- contacts.html ------------------------------------------------------- */

function renderContactRow(c) {
  return `
    <div class="list-item" data-id="${c.id}">
      <div class="list-item-main">
        <div class="avatar">${initials(c.name)}</div>
        <div class="list-item-text">
          <div class="name">${escapeHtml(c.name)} ${c.primary ? '<span class="badge badge-primary">Primary</span>' : ''}</div>
          <div class="meta">${escapeHtml(c.phone)} · ${escapeHtml(c.relation || 'Contact')}</div>
        </div>
      </div>
      <button class="icon-btn" data-delete-contact="${c.id}" aria-label="Remove contact">✕</button>
    </div>`;
}

function escapeHtml(str) {
  return String(str ?? '').replace(/[&<>"']/g, (m) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[m]));
}

async function initContactsPage() {
  const list = document.getElementById('contacts-list');
  if (!list) return;
  const form = document.getElementById('contact-form');
  const msg = document.getElementById('contact-msg');

  async function loadContacts() {
    list.innerHTML = '<p class="sub">Loading contacts…</p>';
    try {
      const contacts = await apiRequest('/contacts');
      cacheContacts(contacts);
      if (contacts.length === 0) {
        list.innerHTML = `
          <div class="empty-state">
            <h3>No trusted contacts yet</h3>
            <p>Add the people you want notified when you send an alert.</p>
          </div>`;
        return;
      }
      list.innerHTML = contacts.map(renderContactRow).join('');
    } catch (err) {
      const cached = getCachedContacts();
      if (err.isNetworkError && cached.length > 0) {
        list.innerHTML = `<p class="sub mb-24">Offline — showing your last saved list.</p>` + cached.map(renderContactRow).join('');
      } else {
        list.innerHTML = `<p class="sub">${escapeHtml(err.message)}</p>`;
      }
    }
  }

  list.addEventListener('click', async (e) => {
    const btn = e.target.closest('[data-delete-contact]');
    if (!btn) return;
    const id = btn.getAttribute('data-delete-contact');
    if (!confirm('Remove this contact?')) return;
    try {
      await apiRequest(`/contacts/${id}`, { method: 'DELETE' });
      loadContacts();
    } catch (err) {
      alert(err.message);
    }
  });

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const submitBtn = form.querySelector('button[type="submit"]');
    submitBtn.disabled = true;

    const body = {
      name: document.getElementById('contact-name').value.trim(),
      phone: document.getElementById('contact-phone').value.trim(),
      relation: document.getElementById('contact-relation').value.trim(),
      primary: document.getElementById('contact-primary').checked,
    };

    try {
      await apiRequest('/contacts', { method: 'POST', body });
      form.reset();
      msg.className = 'form-msg';
      loadContacts();
    } catch (err) {
      showMsg(msg, err.message || 'Could not add this contact.');
    } finally {
      submitBtn.disabled = false;
    }
  });

  loadContacts();
}

/* ---- history.html -------------------------------------------------------- */

function renderHistoryRow(item) {
  const status = (item.status || 'sent').toLowerCase();
  const badgeClass = status === 'cancelled' ? 'badge-cancelled' : status === 'pending' ? 'badge-pending' : 'badge-sent';
  const location = (item.lat && item.lng) ? `${item.lat.toFixed(3)}, ${item.lng.toFixed(3)}` : 'Location unavailable';
  return `
    <div class="history-item status-${status}">
      <div class="list-item" style="border:none; box-shadow:none; padding:14px 0;">
        <div class="list-item-main">
          <div class="list-item-text">
            <div class="name">${formatTimestamp(item.timestamp)}</div>
            <div class="meta">${escapeHtml(location)}</div>
          </div>
        </div>
        <span class="badge ${badgeClass}">${escapeHtml(item.status || 'Sent')}</span>
      </div>
    </div>`;
}

async function initHistoryPage() {
  const list = document.getElementById('history-list');
  if (!list) return;

  try {
    const history = await apiRequest('/sos/history');
    if (history.length === 0) {
      list.innerHTML = `
        <div class="empty-state">
          <h3>No alerts logged</h3>
          <p>Every SOS you send will appear here with its time and status.</p>
        </div>`;
      return;
    }
    list.innerHTML = history.map(renderHistoryRow).join('');
  } catch (err) {
    list.innerHTML = `<p class="sub">${escapeHtml(err.message)}</p>`;
  }
}

/* ---- incident.html --------------------------------------------------------- */

const INCIDENT_CATEGORY_LABELS = {
  HARASSMENT: 'Harassment',
  THEFT: 'Theft',
  ASSAULT: 'Assault',
  SUSPICIOUS_ACTIVITY: 'Suspicious activity',
  ACCIDENT: 'Accident',
  UNSAFE_AREA: 'Unsafe area',
  OTHER: 'Other',
};

const INCIDENT_STATUS_BADGE = {
  SUBMITTED: 'badge-submitted',
  UNDER_REVIEW: 'badge-review',
  RESOLVED: 'badge-resolved',
};

function renderIncidentRow(item) {
  const pending = Boolean(item.pending);
  const category = INCIDENT_CATEGORY_LABELS[item.category] || item.category || 'Other';
  const status = pending ? 'Saved offline' : (item.status || 'Submitted');
  const badgeClass = pending ? 'badge-offline-pending' : (INCIDENT_STATUS_BADGE[item.status] || 'badge-submitted');
  const when = item.createdAt ? formatTimestamp(item.createdAt) : (item.occurredAt ? formatTimestamp(item.occurredAt) : '');
  const location = item.address || ((item.lat && item.lng) ? `${item.lat.toFixed(3)}, ${item.lng.toFixed(3)}` : null);

  return `
    <div class="list-item" style="align-items:flex-start;">
      <div class="list-item-main" style="flex-direction:column; align-items:flex-start; gap:4px;">
        <div class="name">${escapeHtml(item.title)}</div>
        <div class="meta">${escapeHtml(category)}${when ? ' · ' + escapeHtml(when) : ''}${location ? ' · ' + escapeHtml(location) : ''}</div>
        <div class="sub" style="font-size:13px; margin-top:2px;">${escapeHtml(item.description || '')}</div>
      </div>
      <span class="badge ${badgeClass}">${escapeHtml(status)}</span>
    </div>`;
}

async function initIncidentPage() {
  const list = document.getElementById('incident-list');
  if (!list) return;

  const form = document.getElementById('incident-form');
  const msg = document.getElementById('incident-msg');
  const locationBtn = document.getElementById('incident-use-location');
  const locationStatus = document.getElementById('incident-location-status');
  let attachedPos = null;

  async function renderList() {
    let serverItems = [];
    let offline = false;
    try {
      serverItems = await apiRequest('/incidents');
      cacheIncidents(serverItems);
    } catch (err) {
      offline = Boolean(err.isNetworkError);
      serverItems = getCachedIncidents();
    }

    const pendingItems = getPendingIncidents().map((q) => ({ ...q.payload, pending: true, createdAt: new Date(q.queuedAt).toISOString() }));
    const combined = [...pendingItems, ...serverItems];

    if (combined.length === 0) {
      list.innerHTML = `
        <div class="empty-state">
          <h3>No reports yet</h3>
          <p>Anything you log here stays with your account, even things that weren't urgent enough for an SOS.</p>
        </div>`;
      return;
    }

    const prefix = offline ? `<p class="sub mb-24">Offline — showing your last saved reports.</p>` : '';
    list.innerHTML = prefix + combined.map(renderIncidentRow).join('');
  }
  window.__gvRefreshIncidents = renderList;

  if (locationBtn) {
    locationBtn.addEventListener('click', async () => {
      locationStatus.textContent = 'Locating…';
      const pos = await getPosition();
      if (pos) {
        attachedPos = pos;
        locationStatus.textContent = `Attached: ${pos.lat.toFixed(4)}, ${pos.lng.toFixed(4)}`;
      } else {
        locationStatus.textContent = 'Could not get your location.';
      }
    });
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const submitBtn = form.querySelector('button[type="submit"]');
    submitBtn.disabled = true;

    const occurredRaw = document.getElementById('incident-occurred').value;
    const payload = {
      title: document.getElementById('incident-title').value.trim(),
      category: document.getElementById('incident-category').value,
      description: document.getElementById('incident-description').value.trim(),
      lat: attachedPos ? attachedPos.lat : null,
      lng: attachedPos ? attachedPos.lng : null,
      address: document.getElementById('incident-address').value.trim() || null,
      occurredAt: occurredRaw ? new Date(occurredRaw).toISOString() : null,
      syncedFromOffline: false,
    };

    try {
      if (!navigator.onLine) throw Object.assign(new Error('offline'), { isNetworkError: true });
      await apiRequest('/incidents', { method: 'POST', body: payload });
      form.reset();
      attachedPos = null;
      locationStatus.textContent = '';
      showMsg(msg, 'Report submitted.', 'success');
      renderList();
    } catch (err) {
      if (err.isNetworkError) {
        queuePendingIncident({ ...payload, syncedFromOffline: true });
        updateOfflineBanner();
        form.reset();
        attachedPos = null;
        locationStatus.textContent = '';
        showMsg(msg, "Saved on this device — you're offline. It'll submit automatically once you're back online.", 'success');
        renderList();
      } else {
        showMsg(msg, err.message || 'Could not submit this report.');
      }
    } finally {
      submitBtn.disabled = false;
    }
  });

  renderList();
}

/* ---- map.html -------------------------------------------------------------- */

const PLACE_ICON = { police: '🚓', hospital: '🏥', transport: '🚌' };
const PLACE_EMPTY_LABEL = {
  police: 'No police stations found nearby.',
  hospital: 'No hospitals found nearby.',
  transport: 'No bus or train stops found nearby.',
};

function renderPlaceRow(p) {
  const distance = `${p.distanceKm.toFixed(1)} km away`;
  const callLink = p.phone
    ? `<a href="tel:${escapeHtml(p.phone)}">${escapeHtml(p.phone)}</a>`
    : '<span class="sub">No phone listed</span>';
  const directions = `https://www.openstreetmap.org/directions?to=${p.lat}%2C${p.lng}`;
  return `
    <div class="list-item">
      <div class="list-item-main">
        <div class="avatar">${PLACE_ICON[p.category] || '📍'}</div>
        <div class="list-item-text">
          <div class="name">${escapeHtml(p.name)}</div>
          <div class="meta">${distance} · ${callLink}</div>
        </div>
      </div>
      <a class="icon-btn" href="${directions}" target="_blank" rel="noopener" aria-label="Directions">↗</a>
    </div>`;
}

async function initMapPage() {
  const mapEl = document.getElementById('gv-map');
  if (!mapEl) return;

  const msgEl = document.getElementById('map-msg');
  const listEls = {
    police: document.getElementById('police-list'),
    hospital: document.getElementById('hospital-list'),
    transport: document.getElementById('transport-list'),
  };
  const refreshBtn = document.getElementById('map-refresh');

  let leafletMap = null;
  let userMarker = null;
  let placeMarkers = [];

  function showMsg(text) {
    if (!text) { msgEl.style.display = 'none'; return; }
    msgEl.textContent = text;
    msgEl.style.display = 'block';
  }

  function renderSection(category, places) {
    const el = listEls[category];
    if (!el) return;
    if (places.length === 0) {
      el.innerHTML = `<p class="sub">${PLACE_EMPTY_LABEL[category]}</p>`;
      return;
    }
    el.innerHTML = places.slice(0, 10).map(renderPlaceRow).join('');
  }

  async function load() {
    Object.values(listEls).forEach((el) => { if (el) el.innerHTML = '<p class="sub">Finding your location…</p>'; });
    showMsg('');

    const pos = await getPosition({ maxAgeMs: 15000 });
    if (!pos) {
      showMsg('Could not get your location. Check location permission and try again.');
      Object.values(listEls).forEach((el) => { if (el) el.innerHTML = ''; });
      return;
    }

    if (!leafletMap && window.L) {
      leafletMap = L.map('gv-map').setView([pos.lat, pos.lng], 14);
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; OpenStreetMap contributors',
        maxZoom: 19,
      }).addTo(leafletMap);
    } else if (leafletMap) {
      leafletMap.setView([pos.lat, pos.lng], 14);
    }

    if (leafletMap) {
      if (userMarker) userMarker.remove();
      userMarker = L.marker([pos.lat, pos.lng]).addTo(leafletMap).bindPopup('You are here');
    }

    Object.values(listEls).forEach((el) => { if (el) el.innerHTML = '<p class="sub">Looking up nearby places…</p>'; });

    let places = await fetchNearbyPlaces(pos.lat, pos.lng);
    // Backend unreachable (offline, or old server without /api/places/nearby yet):
    // fall back to the direct client-side police lookup so that at least still works.
    if (places.length === 0) {
      const stations = await fetchNearbyPoliceStations(pos.lat, pos.lng);
      places = stations.map((s) => ({ ...s, category: 'police' }));
    }

    placeMarkers.forEach((m) => m.remove());
    placeMarkers = [];

    const byCategory = { police: [], hospital: [], transport: [] };
    places.forEach((p) => { if (byCategory[p.category]) byCategory[p.category].push(p); });

    const shown = [];
    for (const category of ['police', 'hospital', 'transport']) {
      renderSection(category, byCategory[category]);
      shown.push(...byCategory[category].slice(0, 10));
    }

    if (leafletMap) {
      shown.forEach((p) => {
        const marker = L.marker([p.lat, p.lng]).addTo(leafletMap)
          .bindPopup(`${PLACE_ICON[p.category] || ''} ${escapeHtml(p.name)}`);
        placeMarkers.push(marker);
      });
    }
  }

  refreshBtn.addEventListener('click', load);
  load();
}

/* ---- settings.html ---------------------------------------------------------- */

function initSettingsPage() {
  const form = document.getElementById('settings-form');
  if (!form) return;

  const warning = document.getElementById('settings-support-warning');
  const supportsSpeech = Boolean(window.SpeechRecognition || window.webkitSpeechRecognition);
  if (!supportsSpeech) {
    warning.textContent = 'Voice keywords aren\u2019t supported in this browser. Try Chrome or Edge, and use the on-screen buttons instead.';
    warning.style.display = 'block';
  }

  const settings = getSettings();
  document.getElementById('setting-sos-keyword').value = settings.sosKeyword;
  document.getElementById('setting-fakecall-keyword').value = settings.fakeCallKeyword;
  document.getElementById('setting-caller-name').value = settings.callerName;
  document.getElementById('setting-listen-enabled').checked = settings.listenEnabled;
  document.getElementById('setting-tracking-enabled').checked = settings.trackingEnabled;

  const msg = document.getElementById('settings-msg');

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const wantsListen = document.getElementById('setting-listen-enabled').checked;

    const baseSettings = {
      sosKeyword: document.getElementById('setting-sos-keyword').value.trim() || DEFAULT_SETTINGS.sosKeyword,
      fakeCallKeyword: document.getElementById('setting-fakecall-keyword').value.trim() || DEFAULT_SETTINGS.fakeCallKeyword,
      callerName: document.getElementById('setting-caller-name').value.trim() || DEFAULT_SETTINGS.callerName,
      trackingEnabled: document.getElementById('setting-tracking-enabled').checked,
    };

    const apply = (listenEnabled, note, type = 'success') => {
      setSettings({ ...baseSettings, listenEnabled });

      if (getSettings().trackingEnabled) startTracking(); else stopTracking();
      stopVoiceGuardian();
      if (getSettings().listenEnabled) initVoiceGuardian();

      showMsg(msg, note || 'Settings saved.', type);
    };

    if (wantsListen && supportsSpeech) {
      navigator.mediaDevices?.getUserMedia?.({ audio: true })
        .then(() => apply(true))
        .catch(() => {
          document.getElementById('setting-listen-enabled').checked = false;
          apply(false, 'Microphone access was denied, so voice listening stayed off. Other settings were saved.', 'error');
        });
    } else {
      apply(wantsListen && supportsSpeech);
    }
  });
}

/* ---- bootstrap ------------------------------------------------------------ */

document.addEventListener('DOMContentLoaded', () => {
  guardPage();
  wireTopbar();
  initOfflineBanner();
  initLoginPage();
  initRegisterPage();
  initDashboardPage();
  initSosPage();
  initContactsPage();
  initHistoryPage();
  initIncidentPage();
  initMapPage();
  initSettingsPage();

  if (PROTECTED_PAGES.includes(currentPage()) && getToken()) {
    initGuardianAlwaysOn();
  }
});
