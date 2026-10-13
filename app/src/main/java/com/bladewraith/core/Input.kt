package com.bladewraith.core

import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * ============================================================
 *  Blade Wraith — Multi-Touch Input System
 *
 *  A thread-safe bridge between the Android UI thread (which
 *  delivers [android.view.MotionEvent]s) and the OpenGL
 *  rendering thread (which drains the input queue once per
 *  frame).
 *
 *  Architecture:
 *    ┌──────────────────┐      ┌──────────────────┐
 *    │   UI Thread      │      │   GL Thread      │
 *    │  (onTouchEvent)  │      │  (onDrawFrame)   │
 *    └────────┬─────────┘      └─────────┬────────┘
 *             │                          │
 *             │ push()                   │ update()
 *             ▼                          ▼
 *         ┌────────────────────────────────────┐
 *         │  ConcurrentLinkedQueue<Event>      │
 *         │  (lock-free, thread-safe)          │
 *         └────────────────────────────────────┘
 *                          │
 *                          ▼
 *              ┌───────────────────────┐
 *              │  HashMap<Int, Pointer>│
 *              │  (GL thread only)     │
 *              └───────────────────────┘
 *
 *  Threading Contract:
 *    • [onDown], [onMove], [onUp], [clear] are called from the
 *      UI thread. They only enqueue events — no shared mutable
 *      state is touched directly.
 *    • [update] is called from the GL thread once per frame.
 *      It drains the queue and updates the pointer map.
 *    • [all], [first], [count], [pointer] are only safe to
 *      call from the GL thread (i.e., during [Game.update]).
 *
 *  Why not use synchronized blocks?
 *    Synchronization on every touch event would introduce
 *    contention between the UI and GL threads, causing
 *    jank. The lock-free [ConcurrentLinkedQueue] avoids this
 *    entirely and is the standard pattern for game input
 *    systems on Android.
 *
 *  References:
 *    • Android Developers — "MotionEvent"
 *      https://developer.android.com/reference/android/view/MotionEvent
 *    • Oracle — "ConcurrentLinkedQueue"
 *      https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentLinkedQueue.html
 *    • Android NDK — "Game input best practices"
 * ============================================================
 */
class Input {

    // ========================================================
    //  Companion — logging tag and event type constants
    // ========================================================
    companion object {
        private const val TAG = "Input"

        /** Event type: a new pointer touched the screen */
        private const val EVENT_DOWN = 0

        /** Event type: an existing pointer moved */
        private const val EVENT_MOVE = 1

        /** Event type: a pointer was lifted */
        private const val EVENT_UP = 2
    }

    // ========================================================
    //  Event — immutable, value-type payload
    // ========================================================

    /**
     * A single input event queued by the UI thread.
     *
     * Using a private data class keeps the queue typed and
     * avoids boxing of primitive fields on the hot path.
     */
    private class Event(
        @JvmField val type: Int,
        @JvmField val id: Int,
        @JvmField val x: Float,
        @JvmField val y: Float
    )

    // ========================================================
    //  Pointer — mutable state of a single finger
    // ========================================================

    /**
     * The current state of a single active pointer (finger).
     *
     * Pointers are created on [EVENT_DOWN] and destroyed on
     * [EVENT_UP]. Their [x] and [y] are updated on [EVENT_MOVE].
     *
     * All fields are mutable because the GL thread updates them
     * directly during [update]. There is no need for atomicity
     * because only the GL thread ever reads or writes them.
     */
    class Pointer internal constructor(
        @JvmField var x: Float,
        @JvmField var y: Float
    ) {
        /**
         * True on the frame the pointer first touched down.
         * Reset to false at the start of the next [update].
         */
        @JvmField var justDown: Boolean = true

        /**
         * True on the frame the pointer lifted.
         * The pointer is removed from the active map at the
         * end of the frame, but remains visible here for the
         * duration of the frame so game code can react.
         */
        @JvmField var justUp: Boolean = false

        /**
         * The pointer's previous frame position.
         * Used by game code for delta calculations (e.g., swipe
         * detection, drag velocities).
         */
        @JvmField var prevX: Float = x

        @JvmField var prevY: Float = y
    }

    // ========================================================
    //  Shared State
    // ========================================================

    /**
     * Thread-safe queue of pending events.
     *
     * The UI thread enqueues; the GL thread drains. The queue
     * is lock-free and uses a Michael-Scott algorithm, which
     * is safe for one producer and one consumer.
     */
    private val eventQueue: ConcurrentLinkedQueue<Event> = ConcurrentLinkedQueue()

    /**
     * Map of currently active pointers, keyed by pointer ID.
     *
     * ONLY accessed from the GL thread (during [update] and
     * the read methods called from [Game.update]).
     */
    private val activePointers: HashMap<Int, Pointer> = HashMap(8)

    /**
     * Pointers that were lifted this frame.
     *
     * We keep them in a separate map for one frame so game code
     * can see the "justUp" state, then discard them.
     */
    private val releasedPointers: HashMap<Int, Pointer> = HashMap(4)

    /**
     * Reusable snapshot list for [all] to avoid allocating a
     * new collection every frame.
     */
    private val snapshot: ArrayList<Pointer> = ArrayList(8)

    /**
     * Monotonic counter for dropped events (diagnostics only).
     */
    private val droppedEvents: AtomicInteger = AtomicInteger(0)

    // ========================================================
    //  UI-Thread API — called from onTouchEvent
    // ========================================================

    /**
     * Enqueues a pointer-down event.
     *
     * @param id The pointer ID from [MotionEvent.getPointerId]
     * @param x  The screen-space X coordinate
     * @param y  The screen-space Y coordinate (top-left origin)
     */
    fun onDown(id: Int, x: Float, y: Float) {
        eventQueue.offer(Event(EVENT_DOWN, id, x, y))
    }

    /**
     * Enqueues a pointer-move event.
     *
     * Called for every active pointer on every ACTION_MOVE.
     * The GL thread will process all of them in FIFO order,
     * ending with the most recent position for each pointer.
     */
    fun onMove(id: Int, x: Float, y: Float) {
        eventQueue.offer(Event(EVENT_MOVE, id, x, y))
    }

    /**
     * Enqueues a pointer-up event.
     *
     * @param id The pointer ID that was released
     */
    fun onUp(id: Int) {
        eventQueue.offer(Event(EVENT_UP, id, 0f, 0f))
    }

    /**
     * Clears all pending events and active pointers.
     *
     * Called on ACTION_CANCEL and when the Activity is
     * paused/resumed. This prevents phantom input states
     * (e.g., a button "stuck" in the pressed state).
     *
     * Safe to call from the UI thread.
     */
    fun clear() {
        eventQueue.clear()
        // The active pointer map is NOT touched here, because
        // it is only safe to mutate from the GL thread. Instead,
        // we enqueue a "release all" marker that the GL thread
        // will process on its next update.
        //
        // However, since we cannot know the IDs of active
        // pointers from the UI thread without reading the map
        // (which is not thread-safe), we use a sentinel event.
        eventQueue.offer(Event(EVENT_RELEASE_ALL, -1, 0f, 0f))
    }

    // ========================================================
    //  GL-Thread API — called from Game.update
    // ========================================================

    /**
     * Processes all pending events and updates the pointer map.
     *
     * MUST be called from the GL thread at the beginning of
     * each frame, before any game logic that reads input.
     *
     * Order of operations:
     *   1. Clear the justDown/justUp flags from last frame.
     *   2. Save previous positions for delta calculations.
     *   3. Drain the event queue and apply each event.
     *   4. Remove pointers that were released.
     */
    fun update() {
        // ----------------------------------------------------
        //  Step 1: Reset per-frame flags
        // ----------------------------------------------------
        for (p in activePointers.values) {
            p.justDown = false
            p.justUp = false
        }
        releasedPointers.clear()

        // ----------------------------------------------------
        //  Step 2: Save previous positions
        //  This lets game code compute per-frame deltas.
        // ----------------------------------------------------
        for (p in activePointers.values) {
            p.prevX = p.x
            p.prevY = p.y
        }

        // ----------------------------------------------------
        //  Step 3: Drain the queue
        // ----------------------------------------------------
        while (true) {
            val ev = eventQueue.poll() ?: break
            when (ev.type) {

                EVENT_DOWN -> {
                    // If the pointer was already active (should
                    // not happen, but defensive), update instead
                    // of creating a duplicate.
                    val existing = activePointers[ev.id]
                    if (existing != null) {
                        existing.x = ev.x
                        existing.y = ev.y
                    } else {
                        val p = Pointer(ev.x, ev.y)
                        p.prevX = ev.x
                        p.prevY = ev.y
                        p.justDown = true
                        activePointers[ev.id] = p
                    }
                }

                EVENT_MOVE -> {
                    val p = activePointers[ev.id]
                    if (p != null) {
                        p.x = ev.x
                        p.y = ev.y
                    }
                    // If the pointer is not active, ignore the
                    // move. This can happen if a move arrives
                    // before its corresponding down (rare, but
                    // possible under heavy load).
                }

                EVENT_UP -> {
                    val p = activePointers.remove(ev.id)
                    if (p != null) {
                        p.justUp = true
                        releasedPointers[ev.id] = p
                    }
                }

                EVENT_RELEASE_ALL -> {
                    // Release every active pointer.
                    for ((id, p) in activePointers) {
                        p.justUp = true
                        releasedPointers[id] = p
                    }
                    activePointers.clear()
                }

                else -> {
                    // Unknown event type — log once and skip.
                    Log.w(TAG, "Unknown event type: ${ev.type}")
                }
            }
        }

        // ----------------------------------------------------
        //  Step 4: Guard against queue overflow
        //  If the UI thread produced more than MAX_PENDING
        //  events in a single frame (e.g., during a lag spike),
        //  we drop the oldest ones to prevent a backlog that
        //  would take many frames to drain.
        // ----------------------------------------------------
        if (eventQueue.size > MAX_PENDING_EVENTS) {
            val dropped = eventQueue.size - MAX_PENDING_EVENTS
            var i = 0
            while (i < dropped) {
                eventQueue.poll() ?: break
                droppedEvents.incrementAndGet()
                i++
            }
            Log.w(TAG, "Dropped $dropped stale input events")
        }
    }

    // ========================================================
    //  GL-Thread Query API — read-only access to pointers
    // ========================================================

    /**
     * Returns the number of currently active pointers.
     */
    fun count(): Int = activePointers.size

    /**
     * Returns the first active pointer, or null if none.
     *
     * "First" here means "first in HashMap iteration order",
     * which is not guaranteed to be stable. For deterministic
     * behavior, callers should key on pointer ID.
     */
    fun first(): Pointer? {
        val it = activePointers.values.iterator()
        return if (it.hasNext()) it.next() else null
    }

    /**
     * Returns the pointer with the given ID, or null if it is
     * not currently active.
     */
    fun pointer(id: Int): Pointer? = activePointers[id]

    /**
     * Returns a read-only snapshot of all active pointers.
     *
     * The returned collection is a stable snapshot, but the
     * [Pointer] objects inside it are the live instances.
     * Do not store this collection across frames.
     */
    fun all(): List<Pointer> {
        snapshot.clear()
        snapshot.addAll(activePointers.values)
        // Also include released pointers so game code can react
        // to "justUp" states this frame.
        for (p in releasedPointers.values) {
            if (!snapshot.contains(p)) snapshot.add(p)
        }
        return snapshot
    }

    /**
     * Returns true if there is at least one active pointer.
     */
    fun isAnyActive(): Boolean = activePointers.isNotEmpty()

    /**
     * Returns the number of input events dropped due to
     * overflow since the app started. For diagnostics only.
     */
    fun droppedEventCount(): Int = droppedEvents.get()

    // ========================================================
    //  Constants
    // ========================================================

    /**
     * Maximum number of events allowed in the queue at once.
     * If exceeded, the oldest events are dropped. This
     * prevents memory growth during sustained lag spikes.
     *
     * 256 events is enough for ~40 simultaneous pointers
     * moving at 60 Hz for 100 ms — far beyond any realistic
     * user input.
     */
    private val MAX_PENDING_EVENTS = 256

    // ========================================================
    //  Sentinel Event Type
    //  Declared after use for clarity.
    // ========================================================
    private companion object Sentinels {
        const val EVENT_RELEASE_ALL = 3
    }
}
