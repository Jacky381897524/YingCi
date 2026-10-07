package cn.yingci.app

import java.io.File
import java.util.Locale

internal data class ImageCapability(val provider:String, val references:Boolean, val ratios:List<String>, val qualities:List<String>, val formats:List<String>)
internal object BigModelImage {
    val documentedModels=listOf("glm-image","cogview-4-250304","cogview-4","cogview-3-flash")
    private val glmSizes=mapOf("1:1" to "1280x1280","3:2" to "1568x1056","2:3" to "1056x1568","4:3" to "1472x1088","3:4" to "1088x1472","16:9" to "1728x960","9:16" to "960x1728")
    private val cogviewSizes=mapOf("1:1" to "1024x1024","7:4" to "1344x768","4:7" to "768x1344","4:3" to "1152x864","3:4" to "864x1152","2:1" to "1440x720","1:2" to "720x1440")
    fun isHost(baseUrl:String)=runCatching{ApiEndpoint.images(baseUrl).host=="open.bigmodel.cn"}.getOrDefault(false)
    fun candidates(returned:List<String>)=(documentedModels+returned.filter{it.startsWith("glm-image",true)||it.startsWith("cogview-",true)}).distinct()
    fun isTextModel(model:String)=model.startsWith("glm-",true)&&!model.startsWith("glm-image",true)
    fun size(model:String,ratio:String)=(if(model.equals("glm-image",true))glmSizes else cogviewSizes)[ratio]
    fun ratios(model:String)=(if(model.equals("glm-image",true))glmSizes else cogviewSizes).keys.toList()
}
internal fun imageCapability(p:Preferences):ImageCapability {
    val host=runCatching{ApiEndpoint.images(p.baseUrl).host}.getOrDefault("")
    val model=p.imageModel.lowercase(Locale.ROOT).replace(' ','-')
    return when {
        host=="open.bigmodel.cn" -> ImageCapability("bigmodel",false,BigModelImage.ratios(model),if(model=="glm-image")listOf("hd")else listOf("standard","hd"),emptyList())
        host=="apihub.agnes-ai.com" && model in listOf("agnes-image-2.1-flash","agnes-image-2.5-flash") -> ImageCapability("agnes",true,listOf("1:1","3:4","4:3","16:9","9:16","2:3","3:2","21:9"),listOf("1K","2K","3K","4K"),listOf("url","b64_json"))
        model=="gpt-image-1" -> ImageCapability("gpt",true,listOf("自动","1:1","2:3","3:2"),listOf("auto","low","medium","high"),listOf("png","jpeg","webp"))
        model=="dall-e-3" -> ImageCapability("dalle3",false,listOf("1:1","7:4","4:7"),listOf("standard","hd"),emptyList())
        else -> ImageCapability("compatible",false,emptyList(),emptyList(),emptyList())
    }
}
internal data class GenerationDraft(val prompt:String="",val references:List<File> = emptyList(),val separate:Boolean=false,val ratio:String="1:1",val quality:String="1K",val count:Int=1,val format:String="url") {
    fun batches():List<List<File>> {
        require(count in 1..4 && references.size<=6){"最多6张参考图，每组最多生成4张"}
        val groups=if(separate&&references.size>1)references.map{listOf(it)}else listOf(references)
        return groups.flatMap{group->List(count){group}}
    }
}
internal data class GenerationRecord(val id:String,val prompt:String,val model:String,val baseUrl:String,val ratio:String,val quality:String,val format:String,val inputs:List<String>,val created:Long,val status:String="queued",val image:String?=null,val error:String="",val conversationId:String?=null) {
    val pending get()=status=="queued"||status=="running"
    val label get()=when(status){"queued"->"等待生成";"running"->"正在生成";"success"->"已完成";"failed"->"生成失败";"unknown"->"结果待确认";else->"已停止"}
}
internal const val CollectionNameLimit=30
private fun singleLineName(value:String)=value.none{it=='\n'||it=='\r'||it=='\u0085'||it=='\u2028'||it=='\u2029'}
internal fun validNewName(value:String)=value.isNotBlank()&&singleLineName(value)&&value.codePointCount(0,value.length)<=CollectionNameLimit
internal fun acceptNameInput(value:String,previous:String):Boolean {
    val count=value.codePointCount(0,value.length)
    return singleLineName(value)&&(count<=CollectionNameLimit||count<previous.codePointCount(0,previous.length))
}
