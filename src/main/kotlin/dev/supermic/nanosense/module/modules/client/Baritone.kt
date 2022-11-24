package dev.supermic.nanosense.module.modules.client

import dev.supermic.nanosense.event.events.baritone.BaritoneSettingsInitEvent
import dev.supermic.nanosense.event.listener
import dev.supermic.nanosense.module.Category
import dev.supermic.nanosense.module.Module
import dev.supermic.nanosense.util.BaritoneUtils

/**
 * Created by Dewy on the 21st of April, 2020
 */
internal object Baritone : Module(
    name = "Baritone",
    category = Category.CLIENT,
    description = "Configures Baritone settings",
    visible = false,
    alwaysEnabled = true
) {
    private val allowBreak = setting("Allow Break", true)
    private val allowSprint = setting("Allow Sprint", true)
    private val allowPlace = setting("Allow Place", true)
    val allowInventory = setting("Allow Inventory", false)
    private val freeLook = setting("Free Look", true)
    private val allowDownwardTunneling = setting("Downward Tunneling", true)
    private val allowParkour = setting("Allow Parkour", true)
    private val allowParkourPlace = setting("Allow Parkour Place", true)
    private val avoidPortals = setting("Avoid Portals", false)
    private val mapArtMode = setting("Map Art Mode", false)
    private val renderGoal = setting("Render Goals", true)
    private val failureTimeout = setting("Fail Timeout", 2, 1..20, 1)
    private val blockReachDistance = setting("Reach Distance", 4.5f, 1.0f..10.0f, 0.5f)

    init {
        settingList.forEach {
            it.listeners.add { sync() }
        }

        listener<BaritoneSettingsInitEvent> {
            sync()
        }
    }

    private fun sync() {
        BaritoneUtils.settings?.let {
            it.chatControl.value = false // enable chatControlAnyway if you want to use it
            it.allowBreak.value = allowBreak.value
            it.allowSprint.value = allowSprint.value
            it.allowPlace.value = allowPlace.value
            it.allowInventory.value = allowInventory.value
            it.freeLook.value = freeLook.value
            it.allowDownward.value = allowDownwardTunneling.value
            it.allowParkour.value = allowParkour.value
            it.allowParkourPlace.value = allowParkourPlace.value
            it.enterPortal.value = !avoidPortals.value
            it.mapArtMode.value = mapArtMode.value
            it.renderGoal.value = renderGoal.value
            it.failureTimeoutMS.value = failureTimeout.value * 1000L
            it.blockReachDistance.value = blockReachDistance.value
        }
    }
}