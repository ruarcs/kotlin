class Host {
    operator fun plusAssign(x: Int) {}

    fun test1() {
        this += 1
    }
}

fun Host.test2() {
    this += 1
}