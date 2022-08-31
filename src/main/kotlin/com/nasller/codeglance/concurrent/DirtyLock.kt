package com.nasller.codeglance.concurrent

class DirtyLock {
    private var locked = false

    var dirty = false
        private set

    fun acquire() : Boolean {
        synchronized(this) {
            if (locked) {
                // Someone else already grabbed the lock, we are dirty now
                dirty = true
                return false
            }

            locked = true
            return true
        }
    }

    fun release() {
        synchronized(this) {
            locked = false
        }
    }

    fun clean() {
        synchronized(this) {
            dirty = false
        }
    }
}