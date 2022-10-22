package top.mrxiaom.commandyouwant

import kotlinx.coroutines.launch
import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.command.CommandManager
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.registeredCommands
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregisterAll
import net.mamoe.mirai.console.command.CommandSender
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
import net.mamoe.mirai.event.EventPriority
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageMetadata
import net.mamoe.mirai.message.data.MessageSource
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.BotConfiguration
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.info
import top.mrxiaom.commandyouwant.config.CommandConfig
import java.io.File
import java.io.FilenameFilter

object CommandYouWant : KotlinPlugin(
    JvmPluginDescription(
        id = "top.mrxiaom.commandyouwant",
        name = "CommandYouWant",
        version = "0.1.0",
    ) {
        author("MrXiaoM")
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
        reloadConfig()
        globalEventChannel().subscribeAlways<MessageEvent>(
            priority = EventPriority.MONITOR
        ) {
            val sender = toCommandSender()
            processCommand(sender, message, source)
        }
        logger.info { "Plugin loaded" }
    }

    @OptIn(ExperimentalCommandDescriptors::class, ConsoleExperimentalApi::class)
    private suspend fun processCommand(sender: CommandSenderOnMessage<MessageEvent>, originalMessage: MessageChain, source: MessageSource) {
        val message = originalMessage.filterNot { it is MessageMetadata }.mapIndexed{ _, single ->
            if (single is PlainText) return@mapIndexed PlainText(single.content.trim().replace(Regex(" +"), " "))
            single
        }
        for (cmd in commandList) {
            val args = cmd.keywordParsed.parse(sender, message)
            if (args.isEmpty()) continue
            if (cmd.permissionRegistered?.testPermission(sender) == false) {
                if (cmd.denyTips.isNotEmpty()) {
                    sender.sendMessage(cmd.denyTips)
                }
                break
            }
            cmd.actionsParsed.forEach {
                val command = it.parse(args)
                CommandManager.executeCommand(sender, command, cmd.checkPerm)
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

/**
 * 分隔字符串
 * @param input 需要分隔的字符串
 * @param transform 转换器，返回 null 时不添加该项到结果
 */
fun <T> Regex.split(
    input: CharSequence,
    transform : (s : String, isMatched: Boolean) -> T?
) : List<T> {
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
