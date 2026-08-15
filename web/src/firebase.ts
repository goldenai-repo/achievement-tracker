import { getApps, initializeApp } from "firebase/app";
import {
  createUserWithEmailAndPassword,
  getAuth,
  signInWithEmailAndPassword,
  signOut,
  type User,
} from "firebase/auth";

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
};

export const firebaseConfigured = Object.values(firebaseConfig).every(Boolean);
const app = firebaseConfigured
  ? getApps()[0] ?? initializeApp(firebaseConfig)
  : undefined;
export const auth = app ? getAuth(app) : undefined;

export async function register(email: string, password: string): Promise<User> {
  if (!auth) throw new Error("Firebase is not configured. Copy web/.env.example to web/.env.local.");
  const credential = await createUserWithEmailAndPassword(auth, email, password);
  return credential.user;
}

export async function login(email: string, password: string): Promise<User> {
  if (!auth) throw new Error("Firebase is not configured. Copy web/.env.example to web/.env.local.");
  const credential = await signInWithEmailAndPassword(auth, email, password);
  return credential.user;
}

export async function logout(): Promise<void> {
  if (auth) await signOut(auth);
}
