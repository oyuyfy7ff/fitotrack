package de.tadris.fitness.steps

class TimeConsolidator(
    val consolidationTime: Long,
    val onMinuteConsolidated: (steps: Int) -> Unit,
) {

    private var pendingSteps = 0
    private var lastFlush = System.currentTimeMillis()

    fun receiveSteps(steps: Int){
        pendingSteps += steps
        if(System.currentTimeMillis() - lastFlush > consolidationTime){
            flush()
        }
    }

    fun flush(){
        if (pendingSteps == 0) return
        onMinuteConsolidated(pendingSteps)
        pendingSteps = 0
        lastFlush = System.currentTimeMillis()
    }

}