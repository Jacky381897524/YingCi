package cn.yingci.app

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.content.pm.ServiceInfo
import kotlinx.coroutines.*

class GenerationService:Service() {
    companion object {internal val running=kotlinx.coroutines.flow.MutableStateFlow(false)}
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var job:Job?=null
    private val store get()=GenerationStore.get(this)
    private fun notification(text:String,ongoing:Boolean):Notification {
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("generation","图片生成",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("generation",true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,"generation").setSmallIcon(android.R.drawable.ic_menu_gallery).setContentTitle("映词").setContentText(text).setContentIntent(open).setOngoing(ongoing).setAutoCancel(!ongoing).build()
    }
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        try{startForeground(102,notification("正在生成图片",true),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)}catch(e:Exception){scope.launch{store.interrupt();stopSelf()};return START_NOT_STICKY}
        if(job?.isActive==true)return START_NOT_STICKY
        running.value=true
        job=scope.launch{
            try{
                store.initialize()
                val client=GenerationClient()
                var failed=false
                while(isActive){
                    val item=store.next()?:break
                    try{
                        val saved=withContext(Dispatchers.IO){Repository(this@GenerationService).preferences()}
                        require(ApiEndpoint.images(saved.baseUrl)==ApiEndpoint.images(item.baseUrl)){"服务地址已改变，请从新服务重新发起任务"}
                        val p=saved.copy(imageModel=item.model)
                        val files=item.inputs.map{store.file(item.id,it)}
                        val draft=GenerationDraft(prompt=item.prompt,references=files,ratio=item.ratio,quality=item.quality,format=item.format)
                        val bytes=client.generate(p,draft,files);store.finish(item,bytes)
                    }catch(e:CancellationException){throw e}catch(e:Exception){failed=true;store.fail(item,e.message?:"生成未完成，请手动重试")}
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                if(android.os.Build.VERSION.SDK_INT<33||checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED){
                    getSystemService(NotificationManager::class.java).notify(103,notification(if(failed)"生成任务已结束，请查看结果与失败记录"else"图片已保存到生成记录",false))
                }
            }finally{withContext(NonCancellable){store.interrupt()};running.value=false;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
        }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId:Int,fgsType:Int){job?.cancel();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}
