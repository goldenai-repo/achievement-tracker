import { onAuthStateChanged, type User } from "firebase/auth";
import { auth, firebaseConfigured, login, logout, register } from "./firebase";
import { getMe } from "./api";
import "./styles.css";

const root = document.querySelector<HTMLDivElement>("#app")!;

function renderAuth(message = "") {
  root.innerHTML = `
    <section class="card auth-card">
      <p class="eyebrow">Achievement Tracker</p>
      <h1>Keep the moments worth remembering.</h1>
      <p class="muted">Sign in to start building your achievement map.</p>
      <form id="auth-form">
        <label>Email<input name="email" type="email" autocomplete="email" required /></label>
        <label>Password<input name="password" type="password" autocomplete="current-password" minlength="6" required /></label>
        <div class="actions">
          <button type="submit" data-action="login">Log in</button>
          <button type="button" class="secondary" data-action="register">Create account</button>
        </div>
      </form>
      <p class="message" role="status">${message}</p>
      ${!firebaseConfigured ? '<p class="warning">Firebase is not configured. Copy <code>.env.example</code> to <code>.env.local</code> and add your Firebase Web config.</p>' : ""}
    </section>`;

  const form = document.querySelector<HTMLFormElement>("#auth-form")!;
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = new FormData(form);
    await submitAuth(String(data.get("email")), String(data.get("password")), "login");
  });
  document.querySelector<HTMLButtonElement>('[data-action="register"]')!.addEventListener("click", async () => {
    const data = new FormData(form);
    await submitAuth(String(data.get("email")), String(data.get("password")), "register");
  });
}

async function submitAuth(email: string, password: string, action: "login" | "register") {
  const message = document.querySelector<HTMLParagraphElement>(".message");
  if (message) message.textContent = "Working...";
  try {
    if (action === "login") await login(email, password);
    else await register(email, password);
  } catch (error) {
    if (message) message.textContent = error instanceof Error ? error.message : "Authentication failed";
  }
}

async function renderHome(user: User) {
  root.innerHTML = `
    <main class="dashboard-shell">
      <header class="topbar">
        <div>
          <p class="eyebrow">Achievement Tracker</p>
          <h1>Build your map of meaningful places.</h1>
        </div>
        <div class="account-actions">
          <span class="user-email">${user.email ?? "Authenticated user"}</span>
          <button id="logout" class="secondary">Log out</button>
        </div>
      </header>

      <p class="message" id="api-message">Checking your profile with the API...</p>

      <section class="summary-grid" aria-label="Achievement summary">
        <article class="card summary-card"><span>Total check-ins</span><strong id="summary-checkins">—</strong></article>
        <article class="card summary-card"><span>Unique unlocks</span><strong id="summary-unlocks">—</strong></article>
        <article class="card summary-card"><span>Countries</span><strong id="summary-countries">—</strong></article>
        <article class="card summary-card"><span>Admin 1 regions</span><strong id="summary-admin1">—</strong></article>
      </section>

      <section class="dashboard-grid">
        <article class="card">
          <p class="eyebrow">New achievement</p>
          <h2>Record a place worth remembering.</h2>
          <p class="muted">Search for a country or first-level administrative region, then add the day you visited.</p>
          <div class="placeholder-panel" id="checkin-panel">Catalog search and check-in form coming next.</div>
        </article>
        <article class="card">
          <p class="eyebrow">Your history</p>
          <h2>Recent check-ins</h2>
          <div class="placeholder-panel" id="history-panel">Your recent check-ins will appear here.</div>
        </article>
      </section>
    </main>`;
  document.querySelector<HTMLButtonElement>("#logout")!.addEventListener("click", logout);
  try {
    const me = await getMe(user);
    document.querySelector<HTMLParagraphElement>("#api-message")!.textContent =
      `Backend verified UID ${me.uid}. Your profile is ready.`;
  } catch (error) {
    document.querySelector<HTMLParagraphElement>("#api-message")!.textContent =
      error instanceof Error ? error.message : "Backend verification failed";
  }
}

if (auth) onAuthStateChanged(auth, (user) => (user ? renderHome(user) : renderAuth()));
else renderAuth();
