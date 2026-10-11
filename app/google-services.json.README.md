# Firebase → Android → `google-services.json`
#
# D3: this file IS committed (it is not a secret — access is gated by package name
# and signing SHA). Committing it keeps local and CI builds byte-identical.
#
# The file currently in this directory is a PLACEHOLDER so the project compiles and CI
# is green before a real Firebase project exists. It contains NO real credentials.
#
# ## When you create the Firebase project (one-time, Phase 0/2)
#
# 1. https://console.firebase.google.com → create a project.
# 2. **Stay on the Spark (free) plan. Do NOT enable billing.** (D2)
#    - Do NOT create a Cloud Storage bucket — it has required a linked billing account
#      since 3 Feb 2026 even at zero usage.
#    - Do NOT enable Blaze, Cloud Functions, App Check/Play Integrity, or Firestore TTL.
# 3. Add an Android app with package name:
#        com.srinidhi.kharch
#    and optionally a second app for com.srinidhi.kharch.debug
#    (D11: the package name is registered in Firebase and is effectively permanent).
# 4. Download the real google-services.json and **replace** the placeholder in
#    `app/google-services.json`.
# 5. Add your debug and release SHA-1 fingerprints under Project Settings → Your apps
#    (needed for Google Sign-In).
# 6. Authentication → Sign-in method → enable **Google**.
# 7. Create a Firestore database in your region. The free tier covers exactly one
#    database per project.
# 8. Deploy rules + indexes (Phase 3):
#        firebase deploy --only firestore:rules,firestore:indexes
#
# ## Verify the Spark plan stays at ₹0
# The app uses ONLY: firebase-auth, firebase-firestore, firebase-common.
# It deliberately does NOT depend on Crashlytics / Analytics / Performance Monitoring
# (decision P1). A CI guard (`build.yml` → "No telemetry dependencies") fails the build
# if a telemetry SDK is ever added.