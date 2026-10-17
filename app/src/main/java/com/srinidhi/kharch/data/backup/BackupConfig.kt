package com.srinidhi.kharch.data.backup

/**
 * Backup policy for the Kharch ledger (P5, plan §18.4).
 *
 * Android Auto Backup is opted out: a financial ledger must never be silently uploaded to
 * Google's backup servers. Users back up explicitly through Settings → Data → Export.
 *
 * `allowBackup="false"` is set in `AndroidManifest.xml`, and both `data-extraction-rules.xml`
 * variants (cloud-backup and device-transfer) exclude every domain below, because some OEMs
 * ignore `allowBackup` on its own.
 */
object BackupConfig {
    /** Mirrors `android:allowBackup` in the manifest. */
    const val ALLOW_BACKUP: Boolean = false

    /** Manifest string form, for tooling that reads the attribute as text. */
    const val ALLOW_BACKUP_MANIFEST_VALUE: String = "false"

    /** The cloud-backup variant excludes every domain, including the database. */
    const val CLOUD_BACKUP_EXCLUDES_ALL: Boolean = true

    /** The device-transfer variant excludes every domain, including the database. */
    const val DEVICE_TRANSFER_EXCLUDES_ALL: Boolean = true

    /** Resource name (without extension) of the Android 12+ extraction rules. */
    const val DATA_EXTRACTION_RULES_RESOURCE: String = "data_extraction_rules"

    /** Resource name (without extension) of the pre-Android-12 full-backup rules. */
    const val BACKUP_RULES_RESOURCE: String = "backup_rules"
}
