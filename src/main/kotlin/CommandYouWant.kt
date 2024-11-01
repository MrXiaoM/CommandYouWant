package top.mrxiaom.commandyouwant

import net.mamoe.mirai.console.command.CommandManager
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CommandSender.Companion.toCommandSender
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.command.resolve.CommandCallInterceptor
import net.mamoe.mirai.console.command.resolve.InterceptResult
import net.mamoe.mirai.console.command.resolve.InterceptedReason
import net.mamoe.mirai.console.extension.PluginComponentStorage
import net.mamoe.mirai.console.extensions.CommandCallInterceptorProvider
import net.mamoe.mirai.console.permission.Permission
import net.mamoe.mirai.console.permission.PermissionId
import net.mamoe.mirai.console.permission.PermissionService
import net.mamoe.mirai.console.permission.PermissionService.Companion.testPermission
import net.mamoe.mirai.console.plugin.id
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.EventPriority
import net.mamoe.mirai.event.broadcast
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.info
import top.mrxiaom.commandyouwant.config.ActionPrefix.*
import top.mrxiaom.commandyouwant.config.CommandConfig
import top.mrxiaom.commandyouwant.command.Commands
import java.io.File

object CommandYouWant : KotlinPlugin(
    JvmPluginDescription(
        id = "top.mrxiaom.commandyouwant",
        name = "CommandYouWant",
        version = "0.2.1",
    ) {
        author("MrXiaoM")

        dependsOn("xyz.cssxsh.mirai.plugin.mirai-economy-core", true)
    }
) {
    lateinit var permissionCommand: Permission
    val commandList = mutableListOf<CommandConfig>()
    @OptIn(ExperimentalCommandDescriptors::class)
    override fun PluginComponentStorage.onLoad() {
        contributeCommandCallParser(object: CommandCallInterceptorProvider {
            override val instance: CommandCallInterceptor by lazy {
                object: CommandCallInterceptor {
                    override fun interceptBeforeCall(
                        message: Message,
                        caller: CommandSender,
                    ): InterceptResult<Message>? {
                        if (message is MessageChain && message.metadataList().any {
                            it is MetaNoCommand
                        }) {
                            return InterceptResult(InterceptedReason("已传到消息事件，不作为指令执行"))
                        }
                        return null
                    }
                }
            }
        })
    }
    override fun onEnable() {
        permissionCommand = PermissionService.INSTANCE.register(
            PermissionId(id, "command"),
            "重定向命令",
            parentPermission
        )

        logger.info(when (EconomyHolder.hasEconomyCorePlugin) {
            true -> "已安装经济插件"
            false -> "未安装经济插件"
        })

        reloadConfig()
        globalEventChannel().subscribeAlways<MessageEvent>(
            priority = EventPriority.MONITOR
        ) {
            val sender = toCommandSender()
            processCommand(sender, message)
        }
        Commands.register()
        logger.info { "Plugin loaded" }
    }

    @OptIn(ExperimentalCommandDescriptors::class)
    private suspend fun processCommand(sender: CommandSenderOnMessage<MessageEvent>, originalMessage: MessageChain) {
        // 不收控制台命令
        val user = sender.user ?: return
        val group = sender.subject as? Group
        val event = sender.fromEvent
        val source = event.source
        val message = originalMessage.filterNot { it is MessageMetadata }.map { single ->
            if (single is PlainText) {
                val s = single.content.trim() // 移除多空格
                    .replace(Regex(" +"), " ")
                return@map PlainText(s)
            } else {
                return@map single
            }
        }
        // 基本变量
        val replacement = mapOf(
            "botId" to event.bot.id,
            "subjectId" to event.subject.id,
            "groupId" to if (event is GroupMessageEvent) event.group.id else "",
            "friendId" to if (event is FriendMessageEvent) event.friend.id else "",
            "senderId" to event.sender.id,
            "quote" to QuoteReply(event.source),
            "at" to if(event is GroupMessageEvent) At(event.sender) else ""
        ).entries.associate {
            val value = it.value
            it.key.lowercase() to if (value is SingleMessage) value else PlainText(value.toString())
        }
        for (cmd in commandList) {
            val args = cmd.findArguments(sender, message) ?: continue
            if (cmd.permissionRegistered?.testPermission(sender) == false) {
                if (cmd.denyTips.isNotEmpty()) {
                    sender.sendMessage(cmd.denyTips)
                }
                break
            }
            var blocked = false
            for ((index, list) in cmd.keywordBlocks) {
                val s = args.getOrNull(index)?.contentToString() ?: continue
                if (list.any(s::contains)) {
                    blocked = true
                    break
                }
            }
            if (blocked) {
                if (cmd.keywordBlockTips.isNotEmpty()) {
                    sender.sendMessage(cmd.keywordBlockTips)
                }
                return
            }

            if (!cmd.costMoney(group, user, source)) return

            cmd.actionsParsed.forEach {
                val prefix = it.prefix
                val command = it.parse(args, replacement)
                logger.verbose("refer to $prefix:$command")
                when (prefix) {
                    // 执行命令
                    CMD -> CommandManager.executeCommand(sender, command, cmd.checkPerm)
                    // 控制台命令
                    CONSOLE -> CommandManager.executeCommand(ConsoleCommandSender, command, cmd.checkPerm)
                    // 重构事件并广播
                    MSG -> sender.fromEvent.rebuildMessageEvent(command).broadcast()
                    // 发送消息
                    SEND -> sender.sendMessage(command)
                    // 默认执行方式
                    else -> CommandManager.executeCommand(sender, command, cmd.checkPerm)
                }
            }
            break
        }
    }

    fun reloadConfig() {
        commandList.clear()
        val dir = File(configFolder, "commands")
        if (!dir.exists()) {
            dir.mkdirs()
            CommandConfig("sample").save()
        }
        for (file in dir.listFiles { _, name -> name.endsWith(".yml") } ?: emptyArray()) {
            commandList.add(CommandConfig(file.nameWithoutExtension).also {
                it.reload()
                it.registerPermission()
                it.keywordsParsed
                it.actionsParsed
            })
        }
    }
}

fun MessageEvent.rebuildMessageEvent(newMessage: MessageChain): MessageEvent {
    return when (this) {
        is FriendMessageEvent -> FriendMessageEvent(sender, source.plus(newMessage).plus(MetaNoCommand), time)
        is GroupMessageEvent -> GroupMessageEvent(senderName, permission, sender, source.plus(newMessage).plus(MetaNoCommand), time)
        is GroupTempMessageEvent -> GroupTempMessageEvent(sender, source.plus(newMessage).plus(MetaNoCommand), time)
        is StrangerMessageEvent -> StrangerMessageEvent(sender, source.plus(newMessage).plus(MetaNoCommand), time)
        is OtherClientMessageEvent -> OtherClientMessageEvent(client, source.plus(newMessage).plus(MetaNoCommand), time)
        else -> throw IllegalArgumentException("Unsupported MessageEvent")
    }
}

/**
 * 分隔字符串
 * @param input 需要分隔的字符串
 * @param transform 转换器，返回 null 时不添加该项到结果
 */
fun <T> Regex.split(
    input: CharSequence,
    transform: (s: String, isMatched: Boolean) -> T?
): List<T> {
    val list = mutableListOf<T>()
    var index = 0
    for (result in findAll(input)) {
        val first = result.range.first
        val last = result.range.last
        if (first > index) {
            val value = transform(input.substring(index, first), false)
            if (value != null) list.add(value)
        }
        val value = transform(input.substring(first, last + 1), true)
        if (value != null) list.add(value)
        index = last + 1
    }
    if (index < input.length) {
        val value = transform(input.substring(index), false)
        if (value != null) list.add(value)
    }
    return list
}
