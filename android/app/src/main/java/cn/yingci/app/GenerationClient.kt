package cn.yingci.app

import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class GenerationClient(private val client:OkHttpClient=OkHttpClient.Builder().connectTimeout(30,TimeUnit.SECONDS).readTimeout(360,TimeUnit.SECONDS).callTimeout(390,TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()) {
    internal fun buildRequest(p:Preferences,draft:GenerationDraft,files:List<File>):Request {
        require(p.apiKey.isNotBlank()&&p.imageModel.isNotBlank()){ "请先配置生图模型及 API Key" }
        require(draft.prompt.isNotBlank()&&draft.prompt.length<=32000){"提示词不能为空，且最多32000字"}
        val cap=imageCapability(p)
        require(cap.provider!="bigmodel"||!BigModelImage.isTextModel(p.imageModel.trim())){"${p.imageModel.trim()} 是对话/识图模型，请选择 glm-image 或 CogView 生图模型"}
        require(files.isEmpty()||cap.references){"此模型的参考图接口尚未适配，未发送请求；请选择已适配的 Agnes Image 2.1/2.5 Flash 或 gpt-image-1"}
        require(files.size<=6&&files.all{it.isFile&&it.length() in 1..(20L*1024*1024)}){"参考图片无效或过大"}
        val size=when(cap.provider){"bigmodel"->BigModelImage.size(p.imageModel.trim(),draft.ratio)?:"1024x1024";"gpt"->mapOf("1:1" to "1024x1024","2:3" to "1024x1536","3:2" to "1536x1024")[draft.ratio]?:"auto";"dalle3"->mapOf("7:4" to "1792x1024","4:7" to "1024x1792")[draft.ratio]?:"1024x1024";else->draft.quality}
        if(cap.ratios.isNotEmpty())require(draft.ratio in cap.ratios){"模型不支持所选比例"}
        if(cap.qualities.isNotEmpty())require(draft.quality in cap.qualities){"模型不支持所选清晰度"}
        if(cap.formats.isNotEmpty())require(draft.format in cap.formats){"模型不支持所选返回格式"}
        var endpoint=ApiEndpoint.images(p.baseUrl)
        val body:RequestBody=if(cap.provider=="gpt"&&files.isNotEmpty()) {
            endpoint=endpoint.newBuilder().encodedPath(endpoint.encodedPath.removeSuffix("generations")+"edits").build()
            MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("model",p.imageModel.trim()).addFormDataPart("prompt",draft.prompt)
                .addFormDataPart("n","1").addFormDataPart("size",size).addFormDataPart("quality",draft.quality).addFormDataPart("output_format",draft.format).apply{
                    files.forEach{addFormDataPart("image[]",it.name,it.asRequestBody("image/webp".toMediaType()))}
                }.build()
        }else {
            val json=JSONObject().put("model",p.imageModel.trim()).put("prompt",draft.prompt)
            if(cap.provider!="bigmodel")json.put("n",1)
            when(cap.provider){
                "bigmodel"->json.put("size",size).put("quality",draft.quality)
                "agnes"->{
                    json.put("size",draft.quality).put("ratio",draft.ratio)
                    val extra=JSONObject().put("response_format",draft.format)
                    if(files.isNotEmpty())extra.put("image",JSONArray(files.map{"data:image/webp;base64,"+Base64.encodeToString(it.readBytes(),Base64.NO_WRAP)}))
                    else if(draft.format=="b64_json")json.put("return_base64",true)
                    json.put("extra_body",extra)
                }
                "gpt"->json.put("size",size).put("quality",draft.quality).put("output_format",draft.format)
                "dalle3"->json.put("size",size).put("quality",draft.quality)
            }
            json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        }
        // One-shot bodies also prevent OkHttp's HTTP follow-up retries of generation POSTs.
        val once=object:RequestBody(){override fun contentType()=body.contentType();override fun contentLength()=body.contentLength();override fun isOneShot()=true;override fun writeTo(sink:BufferedSink)=body.writeTo(sink)}
        return Request.Builder().url(endpoint).header("Authorization","Bearer ${p.apiKey.trim()}").header("Accept","application/json").post(once).build()
    }
    private suspend fun fetch(request:Request,limit:Int,key:String=""):ByteArray=suspendCancellableCoroutine{c->
        val call=client.newCall(request);c.invokeOnCancellation{call.cancel()}
        call.enqueue(object:Callback{
            override fun onFailure(call:Call,e:IOException){if(c.isActive)c.resumeWithException(IOException("连接中断或超时。服务端可能仍在处理，请先核对服务商记录，避免重复计费。"))}
            override fun onResponse(call:Call,response:Response){
                val result=runCatching{response.use{r->
                    if(!r.isSuccessful)throw ServiceError.from(r,key)
                    val out=ByteArrayOutputStream();val input=r.body?.byteStream()?:throw IOException("服务返回空内容")
                    val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=limit){"图片响应超过本机大小限制"};out.write(buffer,0,n)};out.toByteArray()
                }}
                if(c.isActive)result.fold({c.resume(it)},{c.resumeWithException(it)})
            }
        })
    }
    suspend fun generate(p:Preferences,draft:GenerationDraft,files:List<File>):ByteArray=withContext(Dispatchers.IO){
        val json=JSONObject(String(fetch(buildRequest(p,draft,files),48*1024*1024,p.apiKey),Charsets.UTF_8))
        val data=json.optJSONArray("data")?:throw IOException("服务没有返回图片，未重新提交请求")
        require(data.length()==1){"服务返回的图片数量与单张请求不符，请核对服务商记录"}
        val item=data.getJSONObject(0)
        val encoded=(item.opt("b64_json") as? String).orEmpty()
        val bytes=if(encoded.isNotBlank())Base64.decode(encoded.substringAfter("base64,",encoded),Base64.DEFAULT)else{
            val url=java.net.URI(item.optString("url"))
            require(url.scheme=="https"&&!url.host.isNullOrBlank()&&url.userInfo==null){"服务返回无效图片地址"}
            // Never forward credentials to a generated asset's URL.
            fetch(Request.Builder().url(url.toString()).get().build(),32*1024*1024)
        }
        validateImage(bytes);bytes
    }
    companion object {
        fun validateImage(bytes:ByteArray):String {
            require(bytes.size<=32*1024*1024){"生成图片过大"}
            val info=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,info)
            require(info.outWidth in 1..16384&&info.outHeight in 1..16384&&info.outWidth.toLong()*info.outHeight<=40_000_000){"生成结果不是有效图片，或尺寸超过限制"}
            val options=BitmapFactory.Options().apply{inSampleSize=1;while(maxOf(info.outWidth,info.outHeight)/inSampleSize>512)inSampleSize*=2}
            (BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)?:throw IOException("生成图片无法解码")).recycle()
            return when(info.outMimeType){"image/png"->"png";"image/jpeg"->"jpg";"image/webp"->"webp";else->throw IOException("不支持的图片格式")}
        }
    }
}
