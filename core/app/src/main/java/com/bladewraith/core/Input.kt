package com.bladewraith.core

import java.util.concurrent.ConcurrentLinkedQueue

class Input {

    private data class Ev(val type: Int, val id: Int, val x: Float, val y: Float)

    private val queue = ConcurrentLinkedQueue<Ev>()

    class Pointer {
        var x = 0f
        var y = 0f
        var justDown = false
        var justUp = false
    }

    private val pointers = HashMap<Int, Pointer>()
    private val pending = HashMap<Int, Pointer>()

    fun onDown(id: Int, x: Float, y: Float) = queue.add(Ev(0, id, x, y))
    fun onMove(id: Int, x: Float, y: Float) = queue.add(Ev(1, id, x, y))
    fun onUp(id: Int) = queue.add(Ev(2, id, 0f, 0f))
    fun clear() { queue.clear(); pending.clear(); for (p in pointers.values) p.justUp = true; }

    fun update() {
        for (p in pointers.values) { p.justDown = false; p.justUp = false }
        while (true) {
            val e = queue.poll() ?: break
            when (e.type) {
                0 -> {
                    val p = Pointer().also { it.x = e.x; it.y = e.y; it.justDown = true }
                    pointers[e.id] = p
                }
                1 -> pointers[e.id]?.let { it.x = e.x; it.y = e.y }
                2 -> pointers.remove(e.id)?.let {
                    it.justUp = true
                    pending[e.id] = it
                }
            }
        }
        // نگه‌داشتن پوینترهای justUp برای یک فریم
        if (pending.isNotEmpty()) {
            for ((id, p) in pending) {
                if (pointers.containsKey(id)) continue
                // در فریم بعد حذف می‌شوند — اینجا فقط justUp برای این فریم باقی می‌ماند
            }
            pending.clear()
        }
    }

    fun pointer(id: Int): Pointer? = pointers[id]
    fun all(): Collection<Pointer> = pointers.values
    fun first(): Pointer? = pointers.values.firstOrNull()
    fun count(): Int = pointers.size
}
