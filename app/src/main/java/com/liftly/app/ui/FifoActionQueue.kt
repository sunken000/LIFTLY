package com.liftly.app.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Executes every accepted action once and in insertion order without suspending the caller. */
internal class FifoActionQueue<T>(
    scope: CoroutineScope,
    private val process: suspend (T) -> Unit,
    private val onFailure: (T, Throwable) -> Unit,
) {
    private val channel = Channel<QueueEntry<T>>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (entry in channel) {
                when (entry) {
                    is QueueEntry.Action -> runCatching { process(entry.value) }
                        .onFailure { error -> onFailure(entry.value, error) }

                    is QueueEntry.Barrier -> entry.completion.complete(Unit)
                }
            }
        }
    }

    fun tryEnqueue(item: T): Boolean = channel.trySend(QueueEntry.Action(item)).isSuccess

    /** Suspends until every action accepted before this call has finished processing. */
    suspend fun awaitIdle() {
        val completion = CompletableDeferred<Unit>()
        check(channel.trySend(QueueEntry.Barrier(completion)).isSuccess) {
            "A fila de alterações da série já foi encerrada."
        }
        completion.await()
    }

    fun close() {
        channel.close()
    }
}

private sealed interface QueueEntry<out T> {
    data class Action<T>(val value: T) : QueueEntry<T>
    data class Barrier(val completion: CompletableDeferred<Unit>) : QueueEntry<Nothing>
}
