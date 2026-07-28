import { onAuthStateChanged, type User } from "firebase/auth";
import { auth, firebaseConfigured, login, logout, register } from "./firebase";
import { getCatalog, getMe, type CatalogItem } from "./api";
import "./styles.css";

const root = document.querySelector<HTMLDivElement>("#app")!;
let selectedDimension: "country" | "admin1" = "country";
let selectedParent: CatalogItem | null = null;
let selectedEntity: CatalogItem | null = null;

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (character) => {
    const entities: Record<string, string> = {
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#039;",
    };
    return entities[character];
  });
}

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

function renderCatalogResults(results: CatalogItem[]) {
  const container = document.querySelector<HTMLDivElement>("#catalog-results");
  if (!container) return;
  if (results.length === 0) {
    container.innerHTML = '<p class="muted">No matching places found.</p>';
    return;
  }
  container.innerHTML = results
    .map(
      (item) => `
        <button class="result-item" type="button" data-catalog-id="${escapeHtml(item.id)}">
          <span>${escapeHtml(item.name)}</span>
          <small>${escapeHtml(item.code)}</small>
        </button>`,
    )
    .join("");
  container.querySelectorAll<HTMLButtonElement>("[data-catalog-id]").forEach((button) => {
    button.addEventListener("click", () => {
      const item = results.find((result) => result.id === button.dataset.catalogId);
      if (!item) return;
      selectedEntity = item;
      const selected = document.querySelector<HTMLParagraphElement>("#selected-entity");
      if (selected) {
        selected.innerHTML = `Selected: <strong>${escapeHtml(item.name)}</strong> <small>${escapeHtml(item.code)}</small>`;
      }
    });
  });
}

async function searchCatalog(user: User) {
  const status = document.querySelector<HTMLParagraphElement>("#catalog-status");
  const query = document.querySelector<HTMLInputElement>("#catalog-search")?.value.trim() ?? "";
  if (selectedDimension === "admin1" && !selectedParent) {
    if (status) status.textContent = "Choose a country before searching its regions.";
    renderCatalogResults([]);
    return;
  }
  if (status) status.textContent = "Searching...";
  try {
    const results = await getCatalog(user, {
      kind: selectedDimension,
      q: query,
      parentId: selectedParent?.id,
      limit: 25,
    });
    renderCatalogResults(results);
    if (status) status.textContent = `${results.length} result${results.length === 1 ? "" : "s"}`;
  } catch (error) {
    if (status) status.textContent = error instanceof Error ? error.message : "Catalog search failed";
    renderCatalogResults([]);
  }
}

async function loadParentCountries(user: User) {
  const select = document.querySelector<HTMLSelectElement>("#parent-country");
  if (!select) return;
  try {
    const countries = await getCatalog(user, { kind: "country", limit: 500 });
    select.innerHTML = '<option value="">Choose a country</option>';
    countries.forEach((country) => {
      const option = document.createElement("option");
      option.value = country.id;
      option.textContent = country.name;
      select.appendChild(option);
    });
  } catch (error) {
    select.innerHTML = '<option value="">Unable to load countries</option>';
    const status = document.querySelector<HTMLParagraphElement>("#catalog-status");
    if (status) status.textContent = error instanceof Error ? error.message : "Unable to load countries";
  }
}

function setupCatalogSearch(user: User) {
  const dimension = document.querySelector<HTMLSelectElement>("#catalog-kind")!;
  const parent = document.querySelector<HTMLSelectElement>("#parent-country")!;
  const search = document.querySelector<HTMLInputElement>("#catalog-search")!;
  const parentField = document.querySelector<HTMLLabelElement>("#parent-country-field")!;

  const updateDimension = async () => {
    selectedDimension = dimension.value as "country" | "admin1";
    selectedParent = null;
    selectedEntity = null;
    parent.value = "";
    search.value = "";
    parentField.hidden = selectedDimension !== "admin1";
    document.querySelector<HTMLParagraphElement>("#selected-entity")!.textContent = "No place selected";
    if (selectedDimension === "admin1") await loadParentCountries(user);
    await searchCatalog(user);
  };

  dimension.addEventListener("change", updateDimension);
  parent.addEventListener("change", async () => {
    const selectedOption = parent.options[parent.selectedIndex];
    selectedParent = parent.value
      ? {
          id: parent.value,
          kind: "country",
          code: parent.value.replace("country:", ""),
          name: selectedOption.textContent ?? parent.value,
          nameAscii: selectedOption.textContent,
          parentId: null,
        }
      : null;
    selectedEntity = null;
    document.querySelector<HTMLParagraphElement>("#selected-entity")!.textContent = "No place selected";
    await searchCatalog(user);
  });
  let searchTimer: number | undefined;
  search.addEventListener("input", () => {
    window.clearTimeout(searchTimer);
    searchTimer = window.setTimeout(() => void searchCatalog(user), 250);
  });
  void searchCatalog(user);
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
          <div id="checkin-panel">
            <label>What are you checking in?
              <select id="catalog-kind">
                <option value="country">Country</option>
                <option value="admin1">Admin 1 region</option>
              </select>
            </label>
            <label id="parent-country-field" hidden>Country
              <select id="parent-country"><option value="">Choose a country</option></select>
            </label>
            <label>Search places
              <input id="catalog-search" type="search" placeholder="Try California or United States" autocomplete="off" />
            </label>
            <p class="message" id="catalog-status">Loading places...</p>
            <div id="catalog-results" class="result-list"></div>
            <p id="selected-entity" class="selected-entity">No place selected</p>
          </div>
        </article>
        <article class="card">
          <p class="eyebrow">Your history</p>
          <h2>Recent check-ins</h2>
          <div class="placeholder-panel" id="history-panel">Your recent check-ins will appear here.</div>
        </article>
      </section>
    </main>`;
  document.querySelector<HTMLButtonElement>("#logout")!.addEventListener("click", logout);
  setupCatalogSearch(user);
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
