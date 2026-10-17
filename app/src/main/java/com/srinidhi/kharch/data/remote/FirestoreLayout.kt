@file:Suppress("ImportOrdering", "NoUnusedImports", "NoWildcardImports", "LongParameterList", "CyclomaticComplexMethod", "MaxLineLength", "UnusedPrivateMember", "ReturnCount", "TooManyFunctions")
package com.srinidhi.kharch.data.remote

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Firestore layout for the one shared ledger (plan §6.5, decision D1).
 *
 * ```
 * workspaces/{wid}                  { name, memberUids[], createdAt, schemaVersion }
 * workspaces/{wid}/meta             { lastPulledAt, lastPushAt, lastSweepAt, lastSyncError }
 * workspaces/{wid}/expenses/{id}    { …, authorUid }
 * workspaces/{wid}/categories/{id}  { … }
 * ```
 *
 * **No live listeners anywhere in this data source** (plan §6.8 / decision D2). Every read is
 * a one-shot `get()` against a watermark, and every write is an explicit batched `set()`. A
 * snapshot listener is cheaper to *write* and dramatically more expensive to *run*: it bills
 * a read for every document that changes, forever, on every device, which on the Spark free
 * tier is the fastest possible way to spend a day's quota. The rule is stated here so nobody
 * "improves" this class by adding one.
 */
object FirestoreLayout {

    /** Root collection. There is normally exactly one document in it, keyed by [wid]. */
    fun workspaces(firestore: FirebaseFirestore) = firestore.collection(COLLECTION_WORKSPACES)

    fun workspace(firestore: FirebaseFirestore, wid: String): DocumentReference =
        workspaces(firestore).document(wid)

    fun meta(firestore: FirebaseFirestore, wid: String): DocumentReference =
        workspace(firestore, wid).collection(COLLECTION_META).document(DOC_META)

    fun expenses(firestore: FirebaseFirestore, wid: String) =
        workspace(firestore, wid).collection(COLLECTION_EXPENSES)

    fun expense(firestore: FirebaseFirestore, wid: String, id: String): DocumentReference =
        expenses(firestore, wid).document(id)

    fun categories(firestore: FirebaseFirestore, wid: String) =
        workspace(firestore, wid).collection(COLLECTION_CATEGORIES)

    fun category(firestore: FirebaseFirestore, wid: String, id: String): DocumentReference =
        categories(firestore, wid).document(id)

    const val COLLECTION_WORKSPACES: String = "workspaces"

    const val COLLECTION_META: String = "meta"

    const val COLLECTION_EXPENSES: String = "expenses"

    const val COLLECTION_CATEGORIES: String = "categories"

    const val DOC_META: String = "sync"

    // Field names. Written out rather than derived so a name change is one obvious edit.
    const val FIELD_ID: String = "id"
    const val FIELD_NAME: String = "name"
    const val FIELD_MEMBER_UIDS: String = "memberUids"
    const val FIELD_CREATED_AT: String = "createdAt"
    const val FIELD_SCHEMA_VERSION: String = "schemaVersion"
    const val FIELD_AMOUNT_PAISE: String = "amountPaise"
    const val FIELD_KIND: String = "kind"
    const val FIELD_CURRENCY_CODE: String = "currencyCode"
    const val FIELD_CATEGORY_ID: String = "categoryId"
    const val FIELD_DATE_EPOCH_DAY: String = "dateEpochDay"
    const val FIELD_NOTE: String = "note"
    const val FIELD_LINKED_EXPENSE_ID: String = "linkedExpenseId"
    const val FIELD_UPDATED_AT: String = "updatedAt"
    const val FIELD_DELETED_AT: String = "deletedAt"
    const val FIELD_SYNC_STATE: String = "syncState"
    const val FIELD_AUTHOR_UID: String = "authorUid"
    const val FIELD_ICON_KEY: String = "iconKey"
    const val FIELD_COLOR_TOKEN: String = "colorToken"
    const val FIELD_SORT_ORDER: String = "sortOrder"
    const val FIELD_IS_SYSTEM: String = "isSystem"
    const val FIELD_IS_HIDDEN: String = "isHidden"
    const val FIELD_LAST_PULLED_AT: String = "lastPulledAt"
    const val FIELD_LAST_PUSH_AT: String = "lastPushAt"
    const val FIELD_LAST_SWEEP_AT: String = "lastSweepAt"
    const val FIELD_LAST_SYNC_ERROR: String = "lastSyncError"
    const val FIELD_LAST_CATEGORY_PUSH_AT: String = "lastCategoryPushAt"
}
