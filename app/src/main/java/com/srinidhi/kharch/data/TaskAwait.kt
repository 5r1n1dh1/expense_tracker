package com.srinidhi.kharch.data

import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Suspends until a Play services [Task] completes.
 *
 * Written by hand rather than pulled in from `kotlinx-coroutines-play-services`: that
 * artifact is not in the version catalogue (plan §11.2 lists exactly what the app depends
 * on), and adding a dependency to bridge one callback type is not a trade worth making
 * when the bridge is ten lines.
 *
 * The cancellation contract is honest: a cancelled coroutine cancels the Task, and a
 * failed Task resumes with the exception so the caller can classify it rather than
 * swallowing it.
 */
suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { failure -> continuation.resumeWithException(failure) }
    addOnCanceledListener { continuation.cancel() }
}
