package com.aipurchase.app

class Counter(initialValue: Int = 0) {
    var value: Int = initialValue
        private set

    fun increment(): Int = ++value
}
