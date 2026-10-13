package com.bladewraith.core

import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class Input {

    private companion object {
        const val TAG = "Input"
        const val EVENT_DOWN = 0
        const val EVENT_MOVE = 1
        const val EVENT_UP = 2
        const val EVENT_RELEASE_ALL = 3
        const val MAX_PENDING_EVENTS = 256
    }

    private class Event(
        @JvmField val type: Int,
        @JvmField val id: Int,
        @JvmField val x: Float,
        @JvmField val y: Float
    )

    class Pointer internal constructor(
        @JvmField var x: Float,
        @JvmField var y: Float
    ) {
        @JvmField var justDown: Boolean = true
        @JvmField var justUp: Boolean = false
        @JvmField var prevX: Float = x
        @JvmField var prevY: Float = y
    }

    private val eventQueue: ConcurrentLinkedQueue<Event> = ConcurrentLinkedQueue()
    private val activePointers: HashMap<Int, Pointer> = HashMap(8)
    private val releasedPointers: HashMap<Int, Pointer> = HashMap(4)
    private val snapshot: ArrayList<Pointer> = ArrayList(8)
    private val droppedEvents: AtomicInteger = AtomicInteger(0)

    fun onDown(id: Int, x: Float, y: Float) {
        eventQueue.offer(Event(EVENT_DOWN, id, x, y))
    }

    fun onMove(id: Int, x: Float, y: Float) {
        eventQueue.offer(Event(EVENT_MOVE, id, x, y))
    }

    fun onUp(id: Int) {
        eventQueue.offer(Event(EVENT_UP, id, 0f, 0f))
    }

    fun clear() {
        eventQueue.clear()
        eventQueue.offer(Event(EVENT_RELEASE_ALL, -1, 0f, 0f))
    }

    fun update() {
        for (p in activePointers.values) {
            p.justDown = false
            p.justUp = false
        }
        releasedPointers.clear()

        for (p in activePointers.values) {
            p.prevX = p.x
            p.prevY = p.y
        }

        while (true) {
            val ev = eventQueue.poll() ?: break
            when (ev.type) {
                EVENT_DOWN -> {
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
                }
                EVENT_UP -> {
                    val p = activePointers.remove(ev.id)
                    if (p != null) {
                        p.justUp = true
                        releasedPointers[ev.id] = p
                    }
                }
                EVENT_RELEASE_ALL -> {
                    for ((id, p) in activePointers) {
                        p.justUp = true
                        releasedPointers[id] = p
                    }
                    activePointers.clear()
                }
            }
        }

        if (eventQueue.size > MAX_PENDING_EVENTS) {
            val dropped = eventQueue.size - MAX_PENDING_EVENTS
            var i = 0
            while (i < dropped) {
                eventQueue.poll() ?: break
                droppedEvents.incrementAndGet()
                i++
            }
        }
    }

    fun count(): Int = activePointers.size

    fun first(): Pointer? {
        val it = activePointers.values.iterator()
        return if (it.hasNext()) it.next() else null
    }

    fun pointer(id: Int): Pointer? = activePointers[id]

    fun all(): List<Pointer> {
        snapshot.clear()
        snapshot.addAll(activePointers.values)
        for (p in releasedPointers.values) {
            if (!snapshot.contains(p)) snapshot.add(p)
        }
        return snapshot
    }

    fun isAnyActive(): Boolean = activePointers.isNotEmpty()
    fun droppedEventCount(): Int = droppedEvents.get()
}
