package dev.supermic.nanosense.event.events.player

import dev.supermic.nanosense.event.Cancellable
import dev.supermic.nanosense.event.Event
import dev.supermic.nanosense.event.EventBus
import dev.supermic.nanosense.event.EventPosting
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos

sealed class InteractEvent : Cancellable(), Event {
    sealed class Block(
        val pos: BlockPos,
        val side: EnumFacing,
        val hand: EnumHand
    ) : InteractEvent() {
        class LeftClick(pos: BlockPos, side: EnumFacing) : Block(pos, side, EnumHand.MAIN_HAND), EventPosting by Companion {
            companion object : EventBus()
        }

        class RightClick(pos: BlockPos, side: EnumFacing) : Block(pos, side, EnumHand.MAIN_HAND), EventPosting by Companion {
            companion object : EventBus()
        }

        class Damage(pos: BlockPos, side: EnumFacing) : Block(pos, side, EnumHand.MAIN_HAND), EventPosting by Companion {
            companion object : EventBus()
        }
    }

    sealed class Item(
        val hand: EnumHand
    ) : InteractEvent() {
        class RightClick(hand: EnumHand) : Item(hand), EventPosting by Companion {
            companion object : EventBus()
        }
    }
}