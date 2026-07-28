import type { User } from "firebase/auth";

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8000";

export async function getMe(user: User): Promise<Record<string, string | null>> {
  const token = await user.getIdToken();
  const response = await fetch(`${apiBaseUrl}/v1/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.detail ?? `API request failed (${response.status})`);
  }
  return response.json();
}
