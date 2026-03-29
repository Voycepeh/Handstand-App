# Feature: Calibration

- Calibration is person-level and stored as `BodyProfileRecord` versions tied to a named `UserProfileRecord`.
- One user profile is active at a time (`user_settings.activeUserProfileId`), and calibration writes update the active user’s body profile history.
- Drill movement profiles remain drill-level; live/upload analysis injects the active body profile at runtime.
- Runtime resolution is centralized in `RuntimeBodyProfileResolver` to prevent drift across live/upload/provider flows.
- Runtime body-profile resolution order:
  1. Active user `BodyProfileRecord` (latest version) from `UserProfileManager`.
  2. Legacy `user_settings.userBodyProfileJson` only as migration fallback when no active body profile exists.
  3. Default body model behavior when neither source is available.
- One-time migration behavior: when an active user has no `BodyProfileRecord` but legacy JSON exists, the app promotes that JSON into `body_profile_records` (version 1) and clears the legacy settings field.
- Session rows persist profile traceability metadata:
  - `userProfileId`
  - `bodyProfileId`
  - `bodyProfileVersion`
  - `usedDefaultBodyModel`
- If the active user has no calibration, analysis continues with default body-model behavior.
