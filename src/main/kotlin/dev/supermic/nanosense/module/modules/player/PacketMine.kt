package dev.supermic.nanosense.module.modules.player

import dev.supermic.nanosense.event.SafeClientEvent
import dev.supermic.nanosense.event.events.*
import dev.supermic.nanosense.event.events.player.InteractEvent
import dev.supermic.nanosense.event.events.player.OnUpdateWalkingPlayerEvent
import dev.supermic.nanosense.event.events.render.Render3DEvent
import dev.supermic.nanosense.event.listener
import dev.supermic.nanosense.event.safeConcurrentListener
import dev.supermic.nanosense.event.safeListener
import dev.supermic.nanosense.manager.managers.HotbarManager
import dev.supermic.nanosense.manager.managers.HotbarManager.spoofHotbar
import dev.supermic.nanosense.manager.managers.HotbarManager.spoofHotbarBypass
import dev.supermic.nanosense.manager.managers.PlayerPacketManager
import dev.supermic.nanosense.manager.managers.PlayerPacketManager.sendPlayerPacket
import dev.supermic.nanosense.module.AbstractModule
import dev.supermic.nanosense.module.Category
import dev.supermic.nanosense.module.Module
import dev.supermic.nanosense.translation.TranslateType
import dev.supermic.nanosense.util.EntityUtils.eyePosition
import dev.supermic.nanosense.util.SwingMode
import dev.supermic.nanosense.util.TickTimer
import dev.supermic.nanosense.util.extension.ceilToInt
import dev.supermic.nanosense.util.extension.fastCeil
import dev.supermic.nanosense.util.extension.sq
import dev.supermic.nanosense.util.extension.synchronized
import dev.supermic.nanosense.util.graphics.ESPRenderer
import dev.supermic.nanosense.util.graphics.Easing
import dev.supermic.nanosense.util.graphics.color.ColorRGB
import dev.supermic.nanosense.util.interfaces.DisplayEnum
import dev.supermic.nanosense.util.inventory.findBestTool
import dev.supermic.nanosense.util.inventory.operation.swapToSlot
import dev.supermic.nanosense.util.inventory.slot.currentHotbarSlot
import dev.supermic.nanosense.util.inventory.slot.hotbarSlots
import dev.supermic.nanosense.util.items.isTool
import dev.supermic.nanosense.util.math.RotationUtils.getRotationTo
import dev.supermic.nanosense.util.math.isInSight
import dev.supermic.nanosense.util.math.scale
import dev.supermic.nanosense.util.math.vector.toVec3dCenter
import dev.supermic.nanosense.util.pause.MainHandPause
import dev.supermic.nanosense.util.pause.withPause
import dev.supermic.nanosense.util.threads.runSafe
import dev.supermic.nanosense.util.world.canBreakBlock
import dev.supermic.nanosense.util.world.getBlock
import dev.supermic.nanosense.util.world.getMiningSide
import dev.supermic.nanosense.util.world.isAir
import net.minecraft.block.state.IBlockState
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.init.Blocks
import net.minecraft.init.Enchantments
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.set

internal object PacketMine : Module(
    name = "PacketMine",
    alias = arrayOf("InstantMine"),
    category = Category.PLAYER,
    description = "Break block with packet",
    modulePriority = 200
) {
    private val miningMode by setting("Mining Mode", MiningMode.NORMAL_RETRY)
    private val swapMode by setting("Swap Mode", SwapMode.SPOOF)
    private val preSwapDelay by setting("Pre Swap Delay", 1, 0..20, 1, { swapMode == SwapMode.SWAP })
    private val postSwapDelay by setting("Post Swap Delay", 1, 0..20, 1, { swapMode == SwapMode.SWAP })
    private val rotation by setting("Rotation", false)
    private val rotateTime by setting("Rotate Time", 100, 0..1000, 10, ::rotation)
    private val startPacketOnClick by setting("Start Packet On Click", true)
    private val endPacketOnBreak by setting("End Packet On Break", true)
    private val startPacketAfterBreak by setting("Start Packet After Break", false, { miningMode.continous })
    private val endPacketAfterBreak by setting("End Packet After Break", false, { miningMode.continous })
    private val spamPackets0 = setting("Spam Packets", true)
    private val spamPackets by spamPackets0
    val noSwing by setting("No Swing", false)
    private val noAnimation by setting("No Animation", false)
    private val swingMode by setting("Swing Mode", SwingMode.CLIENT)
    private val packetDelay by setting("Packet Delay", 100, 0..1000, 5)
    private val breakTimeMultiplier by setting("Break Time Multiplier", 0.8f, 0.5f..2.0f, 0.01f)
    private val breakTimeBias by setting("Break Time Bias", 0, -5000..5000, 50)
    private val range by setting("Range", 8.0f, 0.0f..10.0f, 0.25f)

    private val clickTimer = TickTimer()
    private val renderer = ESPRenderer().apply { aFilled = 31; aOutline = 233 }
    private val packetTimer = TickTimer()
    private var miningInfo0: MiningInfo? = null
    private var breakConfirm: Pair<BlockPos, Long>? = null
    val miningInfo: IMiningInfo? get() = miningInfo0

    private val miningQueue = HashMap<AbstractModule, MiningTask>().synchronized()

    private val swapInfo = AtomicReference<SwapInfo>(null)
    private var tickCount = 0

    private enum class SwapMode(override val displayName: String) : DisplayEnum {
        OFF("Off"),
        SWAP("Swap"),
        SPOOF("Spoof"),
        SPOOF_BYPASS("Spoof Bypass")
    }

    private enum class MiningMode(override val displayName: CharSequence, val continous: Boolean) : DisplayEnum {
        NORMAL_ONCE(TranslateType.SPECIFIC key "Normal Once", false),
        NORMAL_RETRY(TranslateType.SPECIFIC key "Normal Retry", false),
        CONTINUOUS_NORMAL(TranslateType.SPECIFIC key "Continuous Normal", true),
        CONTINUOUS_INSTANT(TranslateType.SPECIFIC key "Continuous Instant", true),
    }

    override fun isActive(): Boolean {
        return isEnabled && miningInfo0 != null
    }

    init {
        onDisable {
            miningQueue.clear()
            reset()
        }

        listener<ConnectionEvent.Disconnect> {
            miningQueue.clear()
            reset()
        }

        listener<InputEvent.Mouse> {
            if (it.button == 0 && it.state && mc.currentScreen == null) {
                if (!clickTimer.tickAndReset(250L)) reset(this)
            }
        }

        listener<Render3DEvent> {
            miningInfo0?.let {
                val multiplier = Easing.OUT_CUBIC.inc(Easing.toDelta(it.startTime, it.length))
                val box = AxisAlignedBB(it.pos).scale(multiplier.toDouble())
                val color = if (it.isAir) ColorRGB(32, 255, 32) else ColorRGB(255, 32, 32)

                renderer.add(box, color)
                renderer.render(true)
            }
        }

        safeListener<OnUpdateWalkingPlayerEvent.Post> {
            val temp = swapInfo.get()
            if (temp != null && tickCount - temp.swapTick >= preSwapDelay + postSwapDelay + 1) {
                if (HotbarManager.serverSideHotbar == temp.swapSlot) {
                    swapToSlot(temp.prevSlot)
                }
                swapInfo.compareAndSet(temp, null)
            }
            tickCount++
        }

        safeListener<PacketEvent.Receive> { event ->
            val miningInfo = miningInfo0 ?: return@safeListener

            if (event.packet is SPacketBlockChange && event.packet.blockPosition == miningInfo.pos) {
                val newBlockState = event.packet.blockState
                val current = world.getBlock(miningInfo.pos)
                val new = newBlockState.block

                if (new != Blocks.AIR) {
                    breakConfirm = null
                    if (new != current) {
                        miningInfo.isAir = false

                        if (miningMode == MiningMode.CONTINUOUS_INSTANT) {
                            finishMining(newBlockState, miningInfo)
                        } else if (miningMode == MiningMode.CONTINUOUS_NORMAL) {
                            miningInfo.length = calcBreakTime(miningInfo.pos)
                            if (System.currentTimeMillis() < miningInfo.endTime) {
                                reset(PacketMine)
                                mineBlock(PacketMine, miningInfo.pos, Int.MAX_VALUE)
                            }
                        }
                    }
                } else if (new != current) {
                    breakConfirm = miningInfo.pos to System.currentTimeMillis() + 50L
                }
            }
        }

        safeListener<OnUpdateWalkingPlayerEvent.Pre> {
            if (rotation) {
                miningInfo0?.let {
                    if (!it.isAir
                        && (miningMode == MiningMode.CONTINUOUS_INSTANT && it.mined
                            || it.endTime - System.currentTimeMillis() <= rotateTime)) {
                        sendPlayerPacket {
                            rotate(getRotationTo(it.pos.toVec3dCenter()))
                        }
                    }
                }
            }
        }

        listener<InteractEvent.Block.LeftClick> {
            mineBlock(PacketMine, it.pos, Int.MAX_VALUE)
            if (miningInfo0?.pos == it.pos) it.cancel()
        }

        listener<InteractEvent.Block.Damage> {
            mineBlock(PacketMine, it.pos, Int.MAX_VALUE)
            if (it.pos == miningInfo0?.pos) it.cancel()
        }

        safeConcurrentListener<TickEvent.Post> {
            updateMining()
        }

        safeListener<RunGameLoopEvent.Tick> {
            val miningInfo = miningInfo0 ?: return@safeListener
            val breakConfirm = breakConfirm

            if (breakConfirm != null && System.currentTimeMillis() < breakConfirm.second) {
                miningInfo.isAir = true
                miningInfo.mined = true

                if (!miningMode.continous) {
                    reset()
                    miningQueue.remove(miningInfo.module)
                } else if (miningMode.continous) {
                    if (miningMode == MiningMode.CONTINUOUS_NORMAL) {
                        miningInfo.startTime = System.currentTimeMillis()
                        miningInfo.length
                    }
                    if (startPacketAfterBreak) {
                        connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, miningInfo.pos, miningInfo.side))
                    }
                    if (endPacketAfterBreak) {
                        connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, miningInfo.pos, miningInfo.side))
                    }
                }

                this@PacketMine.breakConfirm = null
            }

            if (player.getDistanceSqToCenter(miningInfo.pos) > range.sq) {
                reset()
                return@safeListener
            }

            val blockState = world.getBlockState(miningInfo.pos)
            miningInfo.isAir = blockState.block == Blocks.AIR

            if (isFinished(miningInfo, blockState) && checkRotation(miningInfo)) {
                if (packetTimer.tick(packetDelay)) {
                    if (finishMining(blockState, miningInfo)) {
                        if (miningMode == MiningMode.NORMAL_ONCE) reset(miningInfo.module)
                    }
                }
            } else if (spamPackets) {
                if (packetTimer.tick(packetDelay)) {
                    sendMiningPacket(miningInfo, false)
                }
            } else {
                packetTimer.reset(-114514)
            }
        }
    }

    private fun SafeClientEvent.finishMining(blockState: IBlockState, miningInfo: MiningInfo): Boolean {
        var result = false

        when (swapMode) {
            SwapMode.OFF -> {
                // do nothing
            }
            SwapMode.SWAP -> {
                findBestTool(blockState)?.let {
                    MainHandPause.withPause(PacketMine, 150L) {
                        val temp = swapInfo.get()
                        if (temp == null || player.currentHotbarSlot != it) {
                            swapInfo.set(SwapInfo(HotbarManager.serverSideHotbar, it.hotbarSlot, tickCount))
                            swapToSlot(it)
                        } else if (tickCount - temp.swapTick >= preSwapDelay) {
                            mc.playerController.updateController()
                            sendMiningPacket(miningInfo, true)
                            result = true
                        }
                    }
                }
            }
            SwapMode.SPOOF -> {
                findBestTool(blockState)?.let {
                    spoofHotbar(it) {
                        sendMiningPacket(miningInfo, true)
                    }
                    result = true
                }
            }
            SwapMode.SPOOF_BYPASS -> {
                findBestTool(blockState)?.let {
                    spoofHotbarBypass(it) {
                        sendMiningPacket(miningInfo, true)
                    }
                    result = true
                }
            }
        }

        return result
    }

    fun mineBlock(module: AbstractModule, pos: BlockPos, priority: Int) {
        runSafe {
            val prev = miningQueue[module]
            if (prev == null || prev.pos != pos || prev.priority != priority) {
                miningQueue[module] = MiningTask(module, pos, priority)
            }
            updateMining()
        }
    }

    fun reset(module: AbstractModule) {
        runSafe {
            miningQueue.remove(module)
            updateMining()
        }
    }

    private fun SafeClientEvent.updateMining() {
        var maxPriorityTask: MiningTask? = null

        synchronized(miningQueue) {
            if (miningQueue.isEmpty()) return@synchronized
            val sorted = miningQueue.values.toTypedArray()
            sorted.sortBy { it.priority }

            var index = sorted.size - 1
            maxPriorityTask = sorted[index]

            while (maxPriorityTask != null && index > 0
                && (maxPriorityTask!!.owner.isDisabled || !miningMode.continous && world.isAir(maxPriorityTask!!.pos))) {
                miningQueue.remove(maxPriorityTask!!.owner)
                maxPriorityTask = sorted[--index]
            }
        }

        maxPriorityTask?.let {
            mineBlock(it)
        } ?: run {
            reset()
        }
    }

    private fun SafeClientEvent.mineBlock(task: MiningTask) {
        if (world.canBreakBlock(task.pos) && task.pos != miningInfo0?.pos) {
            if (player.getDistanceSqToCenter(task.pos) > range * range) return

            val breakTime = calcBreakTime(task.pos)
            if (breakTime == -1) return

            reset()
            val side = getMiningSide(task.pos) ?: run {
                val vector = player.eyePosition.subtract(task.pos.x + 0.5, task.pos.y + 0.5, task.pos.z + 0.5)
                EnumFacing.getFacingFromVector(vector.x.toFloat(), vector.y.toFloat(), vector.z.toFloat())
            }
            miningInfo0 = MiningInfo(task.owner, task.pos, side, (breakTime * breakTimeMultiplier + breakTimeBias).ceilToInt())
            packetTimer.reset(-69420)

            if (startPacketOnClick) connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, task.pos, side))
            connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, task.pos, side))

            if (noAnimation) connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, task.pos, side))
            if (!noSwing) swingMode.swingHand(this, EnumHand.MAIN_HAND)
        }
    }

    private fun SafeClientEvent.checkRotation(miningInfo: MiningInfo): Boolean {
        val eyeHeight = player.getEyeHeight().toDouble()
        return !rotation || AxisAlignedBB(miningInfo.pos).isInSight(PlayerPacketManager.position.add(0.0, eyeHeight, 0.0), rotation = PlayerPacketManager.rotation) != null
    }

    private fun isFinished(miningInfo: MiningInfo, blockState: IBlockState): Boolean {
        return (!miningInfo.isAir || blockState.block != Blocks.AIR)
            && (miningMode == MiningMode.CONTINUOUS_INSTANT && miningInfo.mined || System.currentTimeMillis() > miningInfo.endTime)
    }

    private fun SafeClientEvent.calcBreakTime(pos: BlockPos): Int {
        val blockState = world.getBlockState(pos)

        val hardness = blockState.getBlockHardness(world, pos)
        val breakSpeed = getBreakSpeed(blockState)
        if (breakSpeed == -1.0f) {
            return -1
        }

        val relativeDamage = breakSpeed / hardness / 30.0f
        val ticks = (0.7f / relativeDamage).fastCeil()

        return ticks * 50
    }

    private fun SafeClientEvent.getBreakSpeed(blockState: IBlockState): Float {
        var maxSpeed = 1.0f

        for (slot in player.hotbarSlots) {
            val stack = slot.stack

            if (stack.isEmpty || !stack.item.isTool) {
                continue
            } else {
                var speed = stack.getDestroySpeed(blockState)

                if (speed <= 1.0f) {
                    continue
                } else {
                    val efficiency = EnchantmentHelper.getEnchantmentLevel(Enchantments.EFFICIENCY, stack)
                    if (efficiency > 0) {
                        speed += efficiency * efficiency + 1.0f
                    }
                }

                if (speed > maxSpeed) {
                    maxSpeed = speed
                }
            }
        }

        return maxSpeed
    }

    private fun SafeClientEvent.sendMiningPacket(miningInfo: MiningInfo, end: Boolean) {
        if (endPacketOnBreak && end) connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, miningInfo.pos, miningInfo.side))
        if (noAnimation && !miningInfo.mined) connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, miningInfo.pos, miningInfo.side))
        packetTimer.reset()
        if (end) swingMode.swingHand(this, EnumHand.MAIN_HAND)
    }

    private fun reset() {
        packetTimer.reset(-69420)
        miningInfo0?.let {
            miningInfo0 = null
        }
    }

    private class SwapInfo(val prevSlot: Int, val swapSlot: Int, val swapTick: Int)

    private class MiningTask(val owner: AbstractModule, val pos: BlockPos, val priority: Int)

    interface IMiningInfo {
        val pos: BlockPos
        val side: EnumFacing
    }

    private class MiningInfo(
        val module: AbstractModule,
        override val pos: BlockPos,
        override val side: EnumFacing,
        var length: Int
    ) : IMiningInfo {
        var startTime = System.currentTimeMillis()
        val endTime get() = startTime + length
        var isAir = false
        var mined = false
    }
}