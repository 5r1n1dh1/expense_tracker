# Firebase → Android → `google-services.json`

Wired to the Spark (free) plan project **`expense-tracker-1013e`**
(`project_number = 492411597447`). This file IS committed (it is not a secret —
access is gated by package name + signing SHA, decision D3). Committing it
keeps local and CI builds byte-identical.

Bundled OAuth setup:

- Both Android clients are present, each with the **debug keystore**
  certificate hash (`c61af53d…`):

      com.srinidhi.kharch        (release package)
      com.srinidhi.kharch.debug  (debug package — every CI build + emulator test)

- The shared **type-3 Web SDK configuration** client drives Credential Manager:

      492411597447-b0g3i00cletno0dupt91uti4pcihue2t.apps.googleusercontent.com

  `app/src/main/res/values/strings.xml` keeps the same value in
  `default_web_client_id` — the main source set wins over the plugin-generated
  resource, so the two MUST be updated together.

## What is still missing before sign-in works on the RELEASE build

The Play/release `expense_tracker.apk` is signed by the release keystore
(`android/KEYSTORE_BASE64` CI secret). Its SHA-1 is NOT yet registered on the
`com.srinidhi.kharch` Firebase app — the downloaded config carries only the
debug certificate hash. To make Google sign-in work on installed release builds:

1. Get the release keystore fingerprints (decode the secret locally, or read
   them from any release APK's signature — no keystore needed for this):

       apksigner verify --print-certs expense_tracker.apk

   or, with the keystore:

       keytool -list -v -keystore release.jks -storepass <password>

2. Firebase console → Project settings → Your apps → `com.srinidhi.kharch`
   → add the release SHA-1 and SHA-256.
3. Re-download `google-services.json` and replace this file (the debug and
   `.debug` clients stay identical until the debug keystore changes).
4. Update `app/src/main/res/values/strings.xml` again **only if** the Web
   client ID changed (it normally does not).

Until step 2–3 are done the debug variant signs in; the release APK is
correctly built but sign-in there fails with a developer error on the
`default_web_client_id` / certificate mismatch.
