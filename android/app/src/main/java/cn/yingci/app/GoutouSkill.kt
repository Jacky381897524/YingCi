package cn.yingci.app

import android.content.res.AssetManager
import org.json.JSONObject

internal class GoutouSkill(private val assets:AssetManager) {
    private val root="skills/goutoujunshi"
    private val library by lazy {JSONObject(assets.open("$root/references.json").bufferedReader().use{it.readText()})}
    private val files by lazy {library.keys().asSequence().toList()}
    private fun read(path:String)=if(path.startsWith("references/"))library.getString(path)else assets.open("$root/$path").bufferedReader().use{it.readText()}
    internal fun references(text:String):List<String> {
        val rules=listOf(
            "17-" to listOf("家暴","跟踪","威胁","自杀","自伤","危机","胁迫","危险"),
            "05-" to listOf("pua","操控","煤气灯","贬低","服从"),
            "实战话术" to listOf("怎么回","回复","开场","邀约","这句","演练"),
            "09-" to listOf("截图","网聊","聊天记录","隐私","诈骗"),
            "03-" to listOf("依恋","焦虑","情绪","崩溃"),
            "04-" to listOf("mbti","人格"),
            "07-" to listOf("吵架","冲突","冷战","道歉"),
            "08-" to listOf("同意","亲密","肢体","性关系"),
            "11-" to listOf("婚姻","结婚","家庭"),
            "12-" to listOf("金钱","家务","育儿","彩礼"),
            "15-" to listOf("分手","背叛","复合","出轨"),
            "关系投入失衡" to listOf("投入","冷淡","退出","失衡"),
            "主动表达" to listOf("表白","见面","约会","追求"),
            "场景感" to listOf("松弛","调情","接话"),
            "20-" to listOf("blueprint","mystery","冷读","自然流"),
            "长期记忆" to listOf("记住","档案","记忆","撤销"),
            "01-" to listOf("证据","来源","研究")
        )
        val selected=rules.filter{(_,words)->words.any{text.contains(it,true)}}.mapNotNull{(prefix,_)->files.firstOrNull{it.substringAfterLast('/').startsWith(prefix)}}.take(3)
        return selected.ifEmpty{listOf(files.first{it.substringAfterLast('/').startsWith("00-")})}
    }
    fun prompt(c:Conversation):String? {
        if(c.skill!=ID)return null
        val latest=c.messages.lastOrNull{it.role=="user"}
        val query=latest?.text.orEmpty()+if(latest?.images?.isNotEmpty()==true)" 聊天截图"else ""
        return buildString {
            append("当前启用狗头军师。以下是固定版本的技能与按当前主题选取的参考内容。\n")
            append(read("SKILL.md"));append('\n')
            references(query).forEach{path->append("\n## 参考：$path\n");append(read(path).take(5500));append('\n')}
            append("""
映词运行环境约束（优先于技能中有关工具及存储的描述）：
你只可使用本次请求提供的对话和图片，没有文件、命令、网络搜索或 ChatLab 工具，不能读取其他应用。只分析用户主动提供的信息。
应用将当前对话保存在本机；请求包含有限的近期消息，不提供跨对话长期记忆或独立关系档案写入。不得声称已创建或更新长期档案；缺少旧信息时请用户补充。
参考文档中的命令和路径是文档说明，不是可调用工具。不要输出工具调用。知识参考未经实时核验，涉及法律、健康或安全时说明限制并建议核实当前专业信息。
若已有关系背景或问卷回答，沿用已知信息，不重复完整问卷。紧急场景先处理安全。
仍严格使用前面定义的 JSON 输出协议：日常军师回复为 {"type":"chat","text":"完整回复"}。只有用户明确要求生图时才给 image 方案，不能直接调用或声称生图完成。
            """.trimIndent())
        }
    }
    companion object {
        const val ID="goutoujunshi"
        const val REVISION="6db7354a4002dc7c448a9c87ffdad8132570c9d3"
        const val WELCOME="我是狗头军师。先说说你和对方现在的关系、最近发生的事，以及你最想解决的问题。也可以直接发聊天截图或问我一句话怎么回。\n\n不知道的信息可以留空；遇到着急回复的情况，先发那句话就好。"
    }
}
