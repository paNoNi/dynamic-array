import kotlinx.atomicfu.*

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E {
        checkIndex(index)
        return this.core.value.array[index].value!!.value.value
    }

    override fun put(index: Int, element: E) {
        checkIndex(index)
        while (true) {
            val newVal = Value(element, false)
            val curVal = this.core.value.array[index].value
            if (curVal!!.state.value) continue
            if (this.core.value.array[index].compareAndSet(expect = curVal, update = newVal))
                break
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            if (!increaseCapacity()) continue
            val newVal = Value(element, false)
            val size = this.core.value.size.value
            if (this.core.value.capacity > size &&
                this.core.value.array[size].compareAndSet(expect = null, update = newVal) &&
                this.core.value.size.compareAndSet(expect = size, update = size + 1))
                break
        }
    }

    override val size: Int get() = core.value.size.value

    private fun increaseCapacity(): Boolean {
        val core = this.core.value
        val size = core.size.value

        if (size < core.capacity) {
            return true
        }
        if (core.isFilled.compareAndSet(expect = false, update = true)) {
            val newCore = Core<E>(core.capacity * DOUBLE_CAPACITY)
            newCore.size.compareAndSet(0, size)

            var i = 0
            do {
                val value = core.array[i].value
                if (core.array[i].compareAndSet(expect = value, update = Value(value!!.value.value, true))) {
                    newCore.array[i].getAndSet(value)
                    ++i
                }
            } while (i < size)

            this.core.compareAndSet(expect = core, update = newCore)
            return true
        } else {
            return false
        }
    }

    private fun checkIndex(index: Int) {
        if (index >= this.size)
            throw IllegalArgumentException()
    }
}


private class Value<E>(nodeValue: E, nodeState: Boolean) {

    val value: AtomicRef<E>
    val state: AtomicBoolean

    init {
        value = atomic(nodeValue)
        state = atomic(nodeState)
    }
}

private class Core<E>(
    val capacity: Int
) {
    val array: AtomicArray<Value<E>?> = atomicArrayOfNulls(capacity)
    val size: AtomicInt = atomic(0)
    val isFilled: AtomicBoolean = atomic(false)

}

private const val DOUBLE_CAPACITY = 2
private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
