# Glimpse — Messaging App

A clean, lightweight messaging app built with **Kotlin + Jetpack Compose** and **Supabase**, featuring a crystal liquid glassmorphism UI.

---

## Features

- **Auth** — Email & password sign up / sign in
- **Profile Setup** — Nickname, auto-generated username (`nickname + 5 random digits`), visibility (public/private)
- **Messages Tab** — Conversation list sorted by newest, real-time updates
- **Chat Screen**
  - Real-time message delivery via Supabase Realtime
  - Typing indicator
  - Message status: Sent → Delivered → Seen
  - React to messages (long-press)
  - Reply to messages
  - Delete messages
- **Explore Tab** — Grid of public users, preview profiles, start conversations
- **Profile Tab** — View profile, sign out
- **Push Notifications** — Background foreground service, notifications in the system drawer when a new message arrives

---

## Setup

### 1. Supabase Schema

Run the SQL in `supabase_schema.sql` in your Supabase project's **SQL Editor**.

This creates:
- `profiles` table
- `conversations` table
- `messages` table
- `typing_status` table
- Row-Level Security policies for all tables
- Realtime enabled on messages, conversations, and typing_status

### 2. Build

Open the project in **Android Studio Hedgehog (2023.1.1) or later**.

```
./gradlew assembleDebug
```

Min SDK: 26 (Android 8.0)  
Target SDK: 35

### 3. Supabase Config

Already pre-configured in `SupabaseClient.kt`:
```kotlin
supabaseUrl = "https://ratyoralhhbtqamyjglf.supabase.co"
supabaseKey = "sb_publishable_sW-_hInNqPu4uUEJv8QxTA_83eQHyTX"
```

---

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| Backend | Supabase (Auth, Postgrest, Realtime, Storage) |
| HTTP | Ktor Android |
| Images | Coil |
| Coroutines | Kotlinx Coroutines |

---

## Design

Crystal liquid glassmorphism:
- Dark deep-space background with radial ambient glows
- Semi-transparent frosted glass cards with gradient borders
- Smooth color-coded message bubbles
- Floating pill-style bottom navigation bar
