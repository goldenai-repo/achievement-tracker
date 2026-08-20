import type { User } from "firebase/auth";

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8000";

export type CatalogItem = {
  id: string;
  kind: string;
  code: string;
  name: string;
  nameAscii: string | null;
  parentId: string | null;
};

export type Checkin = {
  id: string;
  entityId: string;
  entity: CatalogItem | null;
  visitedAt: string;
  note: string | null;
  latitude: number | null;
  longitude: number | null;
  createdAt: string;
};

export type Summary = {
  checkinCount: number;
  uniqueUnlockCount: number;
  byKind: Record<string, number>;
};

async function apiRequest<T>(user: User, path: string, init: RequestInit = {}): Promise<T> {
  const token = await user.getIdToken();
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      ...init.headers,
    },
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.detail ?? `API request failed (${response.status})`);
  }
  return response.json();
}

export function getMe(user: User) {
  return apiRequest<Record<string, string | null>>(user, "/v1/me");
}

export function getCatalog(
  user: User,
  params: { kind: "country" | "admin1"; q?: string; parentId?: string; limit?: number },
) {
  const query = new URLSearchParams({ kind: params.kind, limit: String(params.limit ?? 25) });
  if (params.q) query.set("q", params.q);
  if (params.parentId) query.set("parent_id", params.parentId);
  return apiRequest<CatalogItem[]>(user, `/v1/catalog?${query.toString()}`);
}

export function getCheckins(user: User, limit = 50) {
  return apiRequest<Checkin[]>(user, `/v1/checkins?limit=${limit}`);
}

export function createCheckin(
  user: User,
  payload: { entity_id: string; visited_at: string; note?: string },
) {
  return apiRequest<Checkin>(user, "/v1/checkins", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function getSummary(user: User) {
  return apiRequest<Summary>(user, "/v1/summary");
}
