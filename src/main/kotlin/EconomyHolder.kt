package top.mrxiaom.commandyouwant

import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.utils.MiraiLogger
import xyz.cssxsh.mirai.economy.EconomyService
import xyz.cssxsh.mirai.economy.economy

object EconomyHolder {
    val logger = MiraiLogger.Factory.create(this::class)
    val hasEconomyCorePlugin by lazy {
        try {
            EconomyService
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun costMoney(user: User, currencyName: String, money: Double): Boolean {
        if (!hasEconomyCorePlugin) return true
        val currency = EconomyService.basket[currencyName] ?: return false.also {
            logger.warning("货币种类 `$currencyName` 不存在")
        }
        return user.economy {
            val account = service.account(user)
            if (account[currency] < money) return@economy false
            account -= (currency to money)
            return@economy true
        }
    }

    /**
     * 检查并扣除金钱
     * @return 是否可进行下一步操作
     */
    fun costMoney(
        group: Group,
        user: User,
        currencyName: String,
        money: Double
    ) : Boolean {
        if (!hasEconomyCorePlugin) return true
        val currency = EconomyService.basket[currencyName] ?: return false.also {
            logger.warning("货币种类 `$currencyName` 不存在")
        }
        return (group as Contact).economy {
            val account = service.account(user)
            if (account[currency] < money) return@economy false
            account -= (currency to money)
            return@economy true
        }
    }
}