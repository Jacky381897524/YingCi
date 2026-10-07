package cn.yingci.app

import kotlinx.coroutines.delay

// Every automatic turn is gated before and after its pause; never schedule background work.
internal suspend fun chatTurns(allowed:()->Boolean,waiting:()->Unit,pause:suspend ()->Unit={delay(4000)},reply:suspend (Boolean)->Boolean){
    if(!reply(false))return
    repeat(3){
        if(!allowed())return
        waiting()
        pause()
        if(!allowed())return
        if(!reply(true))return
    }
}
