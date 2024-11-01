package top.mrxiaom.commandyouwant.config

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.data.*
import net.mamoe.mirai.console.permission.Permission
import net.mamoe.mirai.console.permission.PermissionId
import net.mamoe.mirai.console.permission.PermissionService
import net.mamoe.mirai.console.plugin.id
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.message.data.*
import top.mrxiaom.commandyouwant.CommandYouWant
import top.mrxiaom.commandyouwant.EconomyHolder
import top.mrxiaom.commandyouwant.EconomyHolder.CostResult.*
import top.mrxiaom.commandyouwant.split
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

class CommandConfig(
    private val fileName: String,
) : ReadOnlyPluginConfig("commands/$fileName") {
    companion object{
        val registeredPerm = mutableMapOf<String, Permission>()
    }
    val keywordsParsed by lazy {
        mutableListOf(parseCommandArgument(keyword)).plus(
            keywordsList.map { parseCommandArgument(it) }
        )
    }
    val actionsParsed by lazy {
        parseActions(actions)
    }
    var permissionRegistered: Permission? = null

    /**
     * 匹配命令
     * @param sender 命令发送者
     * @param message 发送的消息
     * @return 命令匹配时返回参数列表，不匹配时返回空列表
     */
    fun findArguments(sender: CommandSender, message: List<SingleMessage>): List<SingleMessage>? =
        keywordsParsed.firstNotNullOfOrNull {
            it.findArguments(sender, message)
        }

    /**
     * 注册权限
     */
    fun registerPermission() {
        try {
            if (permission.isEmpty()) return
            permissionRegistered = registeredPerm.getOrElse(permission) {
                PermissionService.INSTANCE.register(
                    PermissionId(CommandYouWant.id, "command.$permission"),
                    permissionDescription,
                    CommandYouWant.permissionCommand
                ).also { registeredPerm[permission] = it }
            }
        } catch (t: Throwable) {
            CommandYouWant.logger.error("注册 $fileName.yml 的权限时出错", t)
        }
    }

    @ValueName("perm")
    @ValueDescription("触发命令所需权限 \n(留空为不注册权限，若不注册权限\n将无法限定该命令可在何处使用)\n权限注册后无法注销\n如要修改，重启生效")
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

    @ValueName("keywords-list")
    @ValueDescription("触发命令的关键词规则别名列表。请保持参数数量与主规则一致并避免与其他命令的规则冲突")
    val keywordsList by value(listOf<String>())

    @ValueName("keyword-block")
    @ValueDescription("各个关键词的屏蔽规则")
    val keywordBlocks by value(
        mapOf(
            1 to listOf("#")
        )
    )

    @ValueName("keyword-block-tips")
    @ValueDescription("触发屏蔽规则提示，留空不提示")
    val keywordBlockTips by value("你输入的参数不合规")

    @ValueName("actions")
    @ValueDescription("执行命令")
    val actions by value(listOf("/nai {1} #seed=114514 #step=3 #width=512 #height=512"))

    @ValueName("is-action-check-perm")
    @ValueDescription("在执行命令时，是否检查权限，若不检查将忽略权限强制执行")
    val checkPerm by value(true)

    @ValueName("cost-money-currency")
    @ValueDescription("执行命令所需金钱的货币类型\n" +
            "留空为不花费金钱\n" +
            "该功能需要安装 mirai-economy-core 插件生效")
    val costMoneyCurrency by value("mirai-coin")

    @ValueName("cost-money")
    @ValueDescription("执行命令所需金钱")
    val costMoney by value(10.0)

    @ValueName("cost-money-global")
    @ValueDescription("是否从全局上下文扣除金钱\n" +
            "若关闭该项，将在用户执行命令所在群的上下文扣除金钱\n" +
            "私聊执行命令将强制使用全局上下文")
    val costMoneyGlobal by value(false)

    @ValueName("cost-money-not-enough")
    @ValueDescription("执行命令金钱不足提醒\n" +
            "\$at 为 @ 发送者，\$quote 为回复发送者，\$cost 为需要花费的金钱")
    val costMoneyNotEnough by value("\$quote你没有足够的 Mirai 币 (\$cost) 来执行该命令!")

    suspend fun costMoney(
        group: Group?,
        user: User,
        source: MessageSource
    ): Boolean = when(
        if (costMoneyGlobal || group == null) EconomyHolder.costMoney(user, costMoneyCurrency, costMoney)
        else EconomyHolder.costMoney(group, user, costMoneyCurrency, costMoney)
    ) {
        NO_CURRENCY -> false.also { EconomyHolder.logger.warning("货币种类 `$costMoneyCurrency` 不存在") }
        NOT_ENOUGH -> false.also {
            (group ?: user).sendMessage(buildMessageChain {
                if (costMoneyNotEnough.contains("\$quote")) add(QuoteReply(source))
                addAll(Regex("\\\$at").split<SingleMessage>(
                    costMoneyNotEnough
                        .replace("\$cost", costMoney.toString())
                        .replace("\$quote", "")
                ) { s, isMatched ->
                    if (isMatched) At(user.id) else PlainText(s)
                })
            })
        }
        else -> true
    }

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
private fun parseCommandArgument(s: String): CommandArgumentsDefector =
    CommandArgumentsDefector(regex0.split(s) { text, isMatched ->
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
) {
    fun findArguments(sender: CommandSender, message: List<SingleMessage>): List<SingleMessage>? {
        val msg = message.toMutableList()
        val argsResult = mutableListOf<SingleMessage>()
        for (arg in args) {
            // 如果有一个参数不符合条件，换下一个命令
            val single = arg.check(sender, msg) ?: return null
            if (single is PlainText && single.content.isEmpty()) {
                continue
            }
            argsResult.add(single)
        }
        return argsResult
    }
}

interface ICommandArgument {
    fun check(sender: CommandSender, msg: MutableList<SingleMessage>): SingleMessage?
}

/**
 * 固定格式文字
 */
class CommandArgumentPlainText(val content: String) : ICommandArgument {
    override fun check(sender: CommandSender, msg: MutableList<SingleMessage>): SingleMessage? {
        val m = msg.firstOrNull() ?: return null
        if (m is PlainText && m.content.startsWith(content)) {
            // 吃掉挖出来的字符串
            msg[0] = PlainText(m.content.substring(content.length))
            if (msg[0].content.isEmpty()) {
                // 吃空之后移除
                msg.removeFirstOrNull()
            }
            return PlainText("")
        }
        return null
    }

    override fun toString(): String = content
}

/**
 * 空格间隔参数
 */
class CommandArgument : ICommandArgument {
    override fun check(sender: CommandSender, msg: MutableList<SingleMessage>): SingleMessage? {
        val m = msg.firstOrNull() ?: return null
        if (m is PlainText) {
            val arg = if (m.content.contains(" ")) m.content.substringBefore(" ") else m.content
            // 吃掉挖出来的字符串
            msg[0] = PlainText(m.content.substring(arg.length))
            if (msg[0].content.isEmpty()) {
                // 吃空之后移除
                msg.removeFirstOrNull()
            }
            return PlainText(arg)
        }
        return null
    }

    override fun toString(): String = "<文字>"
}

/**
 * 泛消息类型参数
 */
class CommandArgumentNext : ICommandArgument {
    override fun check(sender: CommandSender, msg: MutableList<SingleMessage>): SingleMessage? {
        val m = msg.firstOrNull() ?: return null
        // 吃掉挖出来的消息类型
        msg.removeFirstOrNull()
        return m
    }

    override fun toString(): String = "<文字>"
}

class CommandArgumentFaceAny : CommandArgumentTypeCheck(Face::class){
    override fun toString(): String = "<表情>"
}
class CommandArgumentImage : CommandArgumentTypeCheck(Image::class) {
    override fun toString(): String = "<图片>"
}
class CommandArgumentAtAny : CommandArgumentTypeCheck(At::class) {
    override fun toString(): String = "<@任意群员>"
}
class CommandArgumentAtBot : ICommandArgument {
    override fun check(sender: CommandSender, msg: MutableList<SingleMessage>): SingleMessage? {
        val m = msg.firstOrNull() ?: return null
        if (m is At && m.target == sender.bot?.id) {
            // 吃掉挖出来的At消息
            msg.removeFirstOrNull()
            return m
        }
        return null
    }

    override fun toString(): String = "<@机器人>"
}

class CommandArgumentAt(val target: Long) : ICommandArgument {
    override fun check(sender: CommandSender, msg: MutableList<SingleMessage>): SingleMessage? {
        val m = msg.firstOrNull() ?: return null
        if (m is At && m.target == target) {
            // 吃掉挖出来的At消息
            msg.removeFirstOrNull()
            return m
        }
        return null
    }

    override fun toString(): String = "<@特定人:$target>"
}

abstract class CommandArgumentTypeCheck(
    private val type: KClass<out SingleMessage>
) : ICommandArgument {
    override fun check(sender: CommandSender, msg: MutableList<SingleMessage>): SingleMessage? {
        val m = msg.firstOrNull() ?: return null
        if (m::class.isSuperclassOf(type).not()) return null
        // 吃掉挖出来的指定类型消息
        msg.removeFirstOrNull()
        return m
    }
}

val regex1 = Regex("\\{[0-9]+}\\??")
private fun parseActions(s: List<String>): List<ActionArgumentsReplacement> = s.map { parseAction(it) }
private fun parseAction(s: String): ActionArgumentsReplacement {
    val prefix = ActionPrefix.values().firstOrNull { s.startsWith(it.prefix) }
    val command = prefix?.let { s.removePrefix(it.prefix) } ?: s
    return ActionArgumentsReplacement(prefix, regex1.split(command) { text, isMatched ->
        if (isMatched){
            return@split ActionArgument(text.removeSuffix("?"), true, text.endsWith("?"))
        }
        return@split ActionArgument(text, false, false)
    })
}
enum class ActionPrefix(val text: String) {
    CMD("cmd"), CONSOLE("console"), MSG("msg"), SEND("send");
    val prefix: String = "$text:"
}
class ActionArgument(
    val text: String,
    val isArgument: Boolean,
    val isNullable: Boolean
)

class ActionArgumentsReplacement(
    val prefix: ActionPrefix?,
    val action: List<ActionArgument>
) {
    fun parse(args: List<SingleMessage>, replacement: Map<String, SingleMessage>): MessageChain {
        CommandYouWant.logger.verbose(action.joinToString(", ") { (if (it.isArgument) "*" else "") + "\"" + it.text + "\"" })
        CommandYouWant.logger.verbose(args.joinToString(", ") { "\"" + it.toString() + "\"" })
        return action.map {
            if (!it.isArgument) return@map PlainText(it.text)
            val text = it.text.removeSurrounding("{","}")
            val index = text.toIntOrNull() ?: return@map replacement[text.lowercase()] ?: PlainText(text)
            if (!it.isNullable && (index < 0 || index >= args.size))
                throw IndexOutOfBoundsException("参数索引 {$index} 超出范围 [0, ${args.size})，请检查你的配置文件")
            return@map args[index]
        }.toMessageChain()
    }
}
