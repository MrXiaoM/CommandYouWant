package top.mrxiaom.commandyouwant.config

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.SimpleCommand
import top.mrxiaom.commandyouwant.CommandYouWant

object CommandReload : SimpleCommand(
    owner = CommandYouWant,
    primaryName = "CommandYouWant",
    secondaryNames = arrayOf("cmd", "cmduw"),
    parentPermission = CommandYouWant.parentPermission
) {
    @Handler
    suspend fun CommandSender.handle(vararg operation: String) {
        if (operation.isNotEmpty() && operation[0].equals("reload", true)) {
            CommandYouWant.reloadConfig()
            sendMessage("配置文件已重载")
        }
    }
}