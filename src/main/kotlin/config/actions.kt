package top.mrxiaom.commandyouwant.config

import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.SingleMessage
import net.mamoe.mirai.message.data.toMessageChain
import top.mrxiaom.commandyouwant.CommandYouWant
import top.mrxiaom.commandyouwant.split


val regex1 = Regex("\\{[0-9]+}\\??")
internal fun parseActions(s: List<String>): List<ActionArgumentsReplacement> = s.map { parseAction(it) }
internal fun parseAction(s: String): ActionArgumentsReplacement {
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
