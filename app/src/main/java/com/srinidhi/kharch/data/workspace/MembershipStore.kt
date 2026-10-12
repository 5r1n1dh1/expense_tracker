package com.srinidhi.kharch.data.workspace

import com.srinidhi.kharch.data.remote.dto.WorkspaceDto

/**
 * The transactional half of workspace membership (plan §6.2, decision D1).
 *
 * This is the **only** part of the shared ledger that has to be atomic. Two devices linking a
 * second account at the same moment would otherwise read the same `memberUids`, append their
 * own uid, and one write would win — silently dropping the other member, with no error
 * anywhere. So the member list is changed on the server inside a Firestore transaction, and
 * this interface is the seam that makes it testable without a real project (plan §13.2, cases
 * 6, 7 and 8).
 *
 * Implemented by [com.srinidhi.kharch.data.remote.FirestoreRemoteDataSource], which is also
 * the single place in the app that knows the workspace document's field names.
 */
interface MembershipStore {

    /** The workspace document, or `null` when no ledger exists yet. */
    suspend fun readWorkspace(workspaceId: String): WorkspaceDto?

    /** Creates the document, or updates it if it already exists (an idempotent upsert). */
    suspend fun createWorkspace(document: WorkspaceDto)

    /** Appends [uid] to the member list atomically. */
    suspend fun addMember(workspaceId: String, uid: String)

    /** Removes [uid] from the member list atomically. Throws when it would empty the ledger. */
    suspend fun removeMember(workspaceId: String, uid: String)
}
