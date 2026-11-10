# Firebase → Android → `google-services.json`

# D3: this file IS committed (it is not a secret — access is gated by package
# name and signing SHA). Committing it keeps local and CI builds byte-identical.
#
# Wired to the Spark (free) plan project:
#   project_id     = expense-tracker-1013e
#   project_number = 492411597447
#   clients        = com.srinidhi.kharch (release), com.srinidhi.kharch.debug (debug)
#
# The debug client is required: without it `com.google.gms.google-services`
# fails with "No matching client found for package name" — every CI build and
# an emulator test uses the debug variant (applicationIdSuffix = ".debug").

## What is still missing before sign-in / sync work end to end

The downloaded `google-services.json` has an empty `oauth_client` list, so
Google sign-in and the stored token cannot mint credentials yet. To finish:

1. Firebase console → Authentication → Sign-in method → enable **Google**.
2. Firebase console → Project settings → Your apps → both apps → add:
   - Debug keystore SHA-1:   C6:1A:F5:3D:0B:B6:14:29:BA:3E:18:56:A8:F8:78:92:77:68:A7:5C
     Debug keystore SHA-256: 96:65:2A:23:CE:55:5D:6A:E1:B7:24:42:CE:C9:1D:46:EC:D1:DA:50:9B:5A:7F:60:18:F6:1D:67:0E:19:F4:DA
   - Release keystore SHA-1 and SHA-256 (the release keystore lives only in the
     CI secrets — keep its fingerprint registered on the same Google app).
3. Re-download `google-services.json` — it will now contain `oauth_client`
   entries (client_type 3 = web SDK configuration client).
4. Create the Firestore database (single free database per project) and deploy
   rules + indexes:
       firebase deploy --only firestore:rules,firestore:indexes
5. Update `app/src/main/res/values/strings.xml` `default_web_client_id` with the
   **Web SDK configuration Web client ID** (Authentication → Sign-in method →
   Google → Web SDK configuration). Update `strings.xml` and
   `google-services.json` **together** — the main source set's `strings.xml`
   wins over the plugin-generated resource.
6. Re-build via the normal CI release flow on a new tag.

Until steps 1–5 are done the app launches and the ledger works fully offline,
but Google sign-in and Firestore sync cannot complete.
