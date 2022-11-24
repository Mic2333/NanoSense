package dev.supermic.nanosense.module.modules.misc

import dev.supermic.nanosense.event.events.PacketEvent
import dev.supermic.nanosense.event.listener
import dev.supermic.nanosense.module.Category
import dev.supermic.nanosense.module.Module
import dev.supermic.nanosense.util.text.MessageSendUtils
import net.minecraft.item.ItemWrittenBook
import net.minecraft.network.play.client.CPacketClickWindow

/**
 * @author IronException
 * Used with permission from ForgeHax
 * https://github.com/fr1kin/ForgeHax/blob/bb522f8/src/main/java/com/matt/forgehax/mods/AntiBookKick.java
 * Permission (and ForgeHax is MIT licensed):
 * https://discordapp.com/channels/573954110454366214/634010802403409931/693919755647844352
 */
internal object AntiBookKick : Module(
    name = "AntiBookKick",
    category = Category.MISC,
    description = "Prevents being kicked by clicking on books",
    visible = false
) {
    init {
        listener<PacketEvent.PostSend> {
            if (it.packet !is CPacketClickWindow) return@listener
            if (it.packet.clickedItem.item !is ItemWrittenBook) return@listener

            MessageSendUtils.sendNoSpamWarningMessage(chatName
                + " Don't click the book \""
                + it.packet.clickedItem.displayName
                + "\", shift click it instead!")
            mc.player.openContainer.slotClick(it.packet.slotId, it.packet.usedButton, it.packet.clickType, mc.player)
        }
    }
}