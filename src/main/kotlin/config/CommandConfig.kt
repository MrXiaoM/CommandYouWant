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
    @ValueDescription("触发命令所需权限 \n(留空为不注册权限，若不注册权限\n将无法限定该命令可在何处使用)\n权限注册后无法注销\n如要修改，重启生效\n最终真正注册到mirai-console里的权限为 top.mrxiaom.commandyouwant:command.你输入的值")
    val permission by value("")

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
    val actions by value(listOf("cmd:/nai {1} #seed=114514 #step=3 #width=512 #height=512"))

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
