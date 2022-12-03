package top.mrxiaom.commandyouwant

import net.mamoe.mirai.console.command.CommandManager
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandSender.Companion.toCommandSender
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.permission.Permission
import net.mamoe.mirai.console.permission.PermissionId
import net.mamoe.mirai.console.permission.PermissionService
import net.mamoe.mirai.console.permission.PermissionService.Companion.testPermission
import net.mamoe.mirai.console.plugin.id
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.EventPriority
import net.mamoe.mirai.event.broadcast
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageMetadata
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.MiraiInternalApi
import net.mamoe.mirai.utils.info
import top.mrxiaom.commandyouwant.config.CommandConfig
import top.mrxiaom.commandyouwant.config.CommandReload
import java.io.File

object CommandYouWant : KotlinPlugin(
    JvmPluginDescription(
        id = "top.mrxiaom.commandyouwant",
        name = "CommandYouWant",
        version = "0.1.2",
    ) {
        author("MrXiaoM")

        dependsOn("xyz.cssxsh.mirai.plugin.mirai-economy-core", true)
    }
) {
    lateinit var permissionCommand: Permission
    val commandList = mutableListOf<CommandConfig>()
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
        CommandReload.register()
        logger.info { "Plugin loaded" }
    }

    @OptIn(ExperimentalCommandDescriptors::class, ConsoleExperimentalApi::class)
    private suspend fun processCommand(sender: CommandSenderOnMessage<MessageEvent>, originalMessage: MessageChain) {
        val message = originalMessage.filterNot { it is MessageMetadata }.mapIndexed { _, single ->
            if (single is PlainText) return@mapIndexed PlainText(single.content.trim().replace(Regex(" +"), " "))
            single
        }
        val group = if (sender.subject is Group) sender.subject as Group else null
        // 不收控制台命令
        val user = sender.user ?: return
        val source = sender.fromEvent.source
        for (cmd in commandList) {
            val args = cmd.keywordParsed.findArguments(sender, message)
            if (args.isEmpty()) continue
            if (cmd.permissionRegistered?.testPermission(sender) == false) {
                if (cmd.denyTips.isNotEmpty()) {
                    sender.sendMessage(cmd.denyTips)
                }
                break
            }
            var blocked = false
            for ((index, list) in cmd.keywordBlocks) {
                val s = args.getOrNull(index)
                if (s != null && list.any { s.contentToString().contains(it) }) {
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

            if (!cmd.costMoney(group, user, source) ) return

            cmd.actionsParsed.forEach {
                val command = it.parse(args)
                logger.verbose("refer to $command")
                if (cmd.eventMode) {
                    // 重构事件并广播
                    sender.fromEvent.rebuildMessageEvent(command).broadcast()
                } else {
                    // 执行命令
                    CommandManager.executeCommand(sender, command, cmd.checkPerm)
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
                it.keywordParsed
                it.actionsParsed
            })
        }
    }
}

@OptIn(MiraiInternalApi::class)
fun MessageEvent.rebuildMessageEvent(newMessage: MessageChain): MessageEvent {
    return when (this) {
        is FriendMessageEvent -> FriendMessageEvent(sender, source.plus(newMessage), time)
        is GroupMessageEvent -> GroupMessageEvent(senderName, permission, sender, source.plus(newMessage), time)
        is GroupTempMessageEvent -> GroupTempMessageEvent(sender, source.plus(newMessage), time)
        is StrangerMessageEvent -> StrangerMessageEvent(sender, source.plus(newMessage), time)
        is OtherClientMessageEvent -> OtherClientMessageEvent(client, source.plus(newMessage), time)
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
