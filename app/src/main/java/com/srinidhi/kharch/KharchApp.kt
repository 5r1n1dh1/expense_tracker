package com.srinidhi.kharch

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Kharch — manual-entry expense tracker with a shared Google-account ledger.
 *
 * P1 (§18.1): the app sends nothing anywhere except Firestore sync to the user's own
 * Firebase project, and the optional user-initiated update check. No Crashlytics, no
 * Analytics, no Performance Monitoring.
 */
@HiltAndroidApp
class KharchApp : Application()