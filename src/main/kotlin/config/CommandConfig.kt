package top.mrxiaom.commandyouwant.config

import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.command.CommandManager
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CommandSender.Companion.asCommandSender
import net.mamoe.mirai.console.data.*
import net.mamoe.mirai.console.permission.Permission
import net.mamoe.mirai.console.permission.PermissionId
import net.mamoe.mirai.console.permission.PermissionService
import net.mamoe.mirai.console.plugin.id
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.message.data.*
import top.mrxiaom.commandyouwant.CommandYouWant
import top.mrxiaom.commandyouwant.split
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

class CommandConfig(
    fileName: String,
) : ReadOnlyPluginConfig("commands/$fileName") {
    val keywordParsed by lazy {
        parseCommandArgument(keyword)
    }
    val actionsParsed by lazy {
        parseActions(actions)
    }
    var permissionId: PermissionId? = null
    var permissionRegistered: Permission? = null

    /**
     * 注册权限
     */
    fun registerPermission() {
        if (permission.isNotEmpty()) {
            if (PermissionService.INSTANCE.getRegisteredPermissions().any { it.id == permissionId }) return
            permissionRegistered = PermissionService.INSTANCE.register(
                PermissionId(CommandYouWant.id, "command.$permission").also { permissionId = it },
                permissionDescription,
                CommandYouWant.permissionCommand
            )
        }
    }
    @ValueName("event-mode")
    @ValueDescription("是否使用事件模式\n有些插件并不是标准地注册命令，故需要伪造发送消息事件让插件响应\n警告: 这可能会干扰聊天记录类的插件")
    val eventMode by value(false)
    @ValueName("perm")
    @ValueDescription("触发命令所需权限 \n(留空为不注册权限，若不注册权限\n将无法限定该命令可在何处使用)\n权限注册后无法注销")
    val permission by value("cssxsh.novelai")
    @ValueName("perm-desc")
    @ValueDescription("权限描述")
    val permissionDescription by value("生成一张图片")
    @ValueName("deny-tips")
    @ValueDescription("权限不足提示，留空不提示")
    val denyTips by value("")
    @ValueName("keyword")
    @ValueDescription("触发命令的关键词规则")
    val keyword by value("{at:bot}画画 {next}")
    @ValueName("keyword-block")
    @ValueDescription("各个关键词的屏蔽规则")
    val keywordBlocks by value(mapOf(
        1 to listOf("#")
    ))
    @ValueName("keyword-block-tips")
    @ValueDescription("触发屏蔽规则提示，留空不提示")
    val keywordBlockTips by value("你输入的参数不合规")
    @ValueName("actions")
    @ValueDescription("执行命令")
    val actions by value(listOf("/nai {1} #seed=114514 #step=3 #width=512 #height=512"))
    @ValueName("is-action-check-perm")
    @ValueDescription("在执行命令时，是否检查权限，若不检查将忽略权限强制执行")
    val checkPerm by value(true)

    // 兼容无法保存 ReadOnly 配置的老版本
    @OptIn(ConsoleExperimentalApi::class)
    private lateinit var owner_: PluginDataHolder
    @OptIn(ConsoleExperimentalApi::class)
    private lateinit var storage_: PluginDataStorage
    @OptIn(ConsoleExperimentalApi::class)
    override fun onInit(owner: PluginDataHolder, storage: PluginDataStorage) {
        owner_ = owner
        storage_ = storage
    }
    @OptIn(ConsoleExperimentalApi::class)
    private fun save() {
        kotlin.runCatching {
            storage_.store(owner_, this)
        }.onFailure { e ->
            CommandYouWant.logger.error(e)
        }
    }
}

val regex0 = Regex("\\{[A-Za-z0-9_:-]+}")
private fun parseCommandArgument(s: String) : CommandArgumentsDefector = CommandArgumentsDefector(regex0.split(s) { text, isMatched ->
    if (!isMatched) return@split CommandArgumentPlainText(text.trim())
    fun checkSpecial(): ICommandArgument? {
        val special = text.substring(1, text.length - 1)
        if (special == "arg") return CommandArgument()
        if (special == "next") return CommandArgumentNext()
        if (special == "at") return CommandArgumentAtAny()
        if (special.startsWith("at:")) {
            val target = special.substring(3)
            if (target == "bot") return CommandArgumentAtBot()
            return CommandArgumentAt(target.toLongOrNull() ?: return null)
        }
        if (special == "img") return CommandArgumentImage()
        if (special == "face") return CommandArgumentFaceAny()
        return null
    }

    val result = checkSpecial()
    if (result == null) CommandYouWant.logger.warning("无法解析 “$s” 的语句 “$text”")
    return@split result
})

class CommandArgumentsDefector(
    val args: List<ICommandArgument>
){
    fun parse(sender: CommandSender, message: List<SingleMessage>): List<SingleMessage> {
        val msg = message.toMutableList()
        val argsResult = mutableListOf<SingleMessage>()
        for (arg in args) {
            val single = arg.check(sender, msg) ?: return listOf()
            if (single is PlainText && single.content.isEmpty()) continue
            argsResult.add(single)
        }
        return argsResult
    }
}

interface ICommandArgument {
    fun check(sender: CommandSender, msg: MutableList<SingleMessage>): SingleMessage?
}

class CommandArgumentPlainText(val content: String): ICommandArgument{
    override fun check(sender: CommandSender, msg: MutableList<SingleMessage>): SingleMessage? {
        val m = msg.firstOrNull() ?: return null
        if (m is PlainText && m.content.startsWith(content)) {
            msg[0] = PlainText(m.content.substring(content.length))
            return PlainText("")
        }
        return null
    }
}
class CommandArgument: ICommandArgument {
    override fun check(sender: CommandSender, msg: MutableList<SingleMessage>): SingleMessage? {
        val m = msg.firstOrNull() ?: return null
        if (m is PlainText) {
            val arg = if(m.content.contains(" ")) m.content.substringBefore(" ") else m.content
            return PlainText(arg)
        }
        return null
    }
}
class CommandArgumentNext: ICommandArgument {
    override fun check(sender: CommandSender, msg: MutableList<SingleMessage>): SingleMessage? {
        return msg.firstOrNull()
    }
}
class CommandArgumentFaceAny: CommandArgumentTypeCheck(Face::class)
class CommandArgumentImage: CommandArgumentTypeCheck(Image::class)
class CommandArgumentAtAny: CommandArgumentTypeCheck(At::class)
class CommandArgumentAtBot: ICommandArgument {
    override fun check(sender: CommandSender, msg: MutableList<SingleMessage>): SingleMessage? {
        val m = msg.firstOrNull() ?: return null
        if (m is At && m.target == sender.bot?.id) return m
        return null
    }
}
class CommandArgumentAt(val target: Long): ICommandArgument {
    override fun check(sender: CommandSender, msg: MutableList<SingleMessage>): SingleMessage? {
        val m = msg.firstOrNull() ?: return null
        if (m is At && m.target == target) return m
        return null
    }
}

abstract class CommandArgumentTypeCheck(
    private val type: KClass<out SingleMessage>
) : ICommandArgument {
    override fun check(sender: CommandSender, msg: MutableList<SingleMessage>): SingleMessage? {
        val m = msg.firstOrNull() ?: return null
        if (m::class.isSuperclassOf(type)) return m
        return null
    }
}

val regex1 = Regex("\\{[0-9]+}")
private fun parseActions(s: List<String>) : List<ActionArgumentsReplacement> = s.map { parseAction(it) }
private fun parseAction(s: String) : ActionArgumentsReplacement {
    return ActionArgumentsReplacement(regex1.split(s) { text, isMatched ->
        return@split ActionArgument(text, isMatched)
    })
}
class ActionArgument(
    val text: String,
    val isArgument: Boolean
)
class ActionArgumentsReplacement(
    val action: List<ActionArgument>
) {
    fun parse(args: List<SingleMessage>): MessageChain = action.map {
        if (!it.isArgument) return@map PlainText(it.text)
        val index = it.text.substring(1, it.text.length - 1).toIntOrNull() ?: return@map PlainText(it.text)
        if (index < 0 || index >= args.size) return@map PlainText(it.text)
        return@map args[index]
    }.toMessageChain()
}
