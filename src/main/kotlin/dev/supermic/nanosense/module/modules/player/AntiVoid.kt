package dev.supermic.nanosense.module.modules.player

import dev.supermic.nanosense.event.events.player.PlayerMoveEvent
import dev.supermic.nanosense.event.safeListener
import dev.supermic.nanosense.module.Category
import dev.supermic.nanosense.module.Module
import dev.supermic.nanosense.util.world.getGroundLevel
import net.minecraft.util.math.Vec3d

internal object AntiVoid : Module(
    name = "AntiVoid",
    description = "AntiVoid for hypixel",
    category = Category.PLAYER
) {
    private val fallDistance by setting("Fall Distance", 3.0f, 1.0f..10.0f, 0.5f)
    private val yOffset by setting("Y Offset", 0.2f, 0.0f..3.0f, 0.1f)

    private var lastPos: Vec3d? = null

    init {
        onDisable {
            lastPos = null
        }

        safeListener<PlayerMoveEvent.Post> {
            if (world.getGroundLevel(player) != -1.0) {
                if (player.onGround) {
                    lastPos = player.positionVector
                }
            } else if (player.fallDistance > fallDistance) {
                lastPos?.let {
                    player.setPosition(it.x, it.y + yOffset, it.z)
                    player.motionX = 0.0
                    player.motionY = 0.0
                    player.motionZ = 0.0
                }
            }
        }
    }
}