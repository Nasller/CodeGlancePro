package com.nasller.codeglance.concurrent

class DirtyLock {
    var locked = false
        private set

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

    public fun release() {
        synchronized(this) {
            locked = false
        }
    }

    public fun clean() {
        synchronized(this) {
            dirty = false
        }
    }
}