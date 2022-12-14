package dev.supermic.nanosense.gui.hudgui

import dev.supermic.nanosense.event.events.InputEvent
import dev.supermic.nanosense.event.events.render.Render2DEvent
import dev.supermic.nanosense.event.listener
import dev.supermic.nanosense.gui.AbstractNanoGui
import dev.supermic.nanosense.gui.clickgui.NanoClickGui
import dev.supermic.nanosense.gui.hudgui.component.HudButton
import dev.supermic.nanosense.gui.hudgui.window.HudSettingWindow
import dev.supermic.nanosense.gui.rgui.Component
import dev.supermic.nanosense.gui.rgui.windows.ListWindow
import dev.supermic.nanosense.module.modules.client.GuiSetting
import dev.supermic.nanosense.module.modules.client.Hud
import dev.supermic.nanosense.module.modules.client.HudEditor
import dev.supermic.nanosense.util.extension.remove
import dev.supermic.nanosense.util.extension.rootName
import dev.supermic.nanosense.util.graphics.GlStateUtils
import dev.supermic.nanosense.util.math.vector.Vec2f
import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11.*
import java.util.*

object NanoHudGui : AbstractNanoGui<HudSettingWindow, AbstractHudElement>() {

    override val alwaysTicking = true
    private val hudWindows = EnumMap<AbstractHudElement.Category, ListWindow>(AbstractHudElement.Category::class.java)

    init {
        var posX = 0.0f
        var posY = 0.0f
        val screenWidth = NanoClickGui.mc.displayWidth / GuiSetting.scaleFactorFloat

        for (category in AbstractHudElement.Category.values()) {
            val window = ListWindow(category.displayName, posX, 0.0f, 90.0f, 300.0f, Component.SettingGroup.HUD_GUI)
            windowList.add(window)
            hudWindows[category] = window

            posX += 90.0f

            if (posX > screenWidth) {
                posX = 0.0f
                posY += 100.0f
            }
        }

        listener<InputEvent.Keyboard> {
            if (!it.state || it.key == Keyboard.KEY_NONE || Keyboard.isKeyDown(Keyboard.KEY_F3)) return@listener

            for (child in windowList) {
                if (child !is AbstractHudElement) continue
                if (!child.bind.isDown(it.key)) continue
                child.visible = !child.visible
            }
        }
    }

    internal fun register(hudElement: AbstractHudElement) {
        val button = HudButton(hudElement)
        hudWindows[hudElement.category]!!.children.add(button)
        windowList.add(hudElement)
    }

    internal fun unregister(hudElement: AbstractHudElement) {
        hudWindows[hudElement.category]!!.children.removeIf { it is HudButton && it.hudElement == hudElement }
        windowList.remove(hudElement)
    }

    override fun onGuiClosed() {
        super.onGuiClosed()
        setHudButtonVisibility { true }
    }

    override fun newSettingWindow(element: AbstractHudElement, mousePos: Vec2f): HudSettingWindow {
        return HudSettingWindow(element, mousePos.x, mousePos.y)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (keyCode == Keyboard.KEY_ESCAPE || HudEditor.bind.value.isDown(keyCode) && !searching && settingWindow?.listeningChild == null) {
            HudEditor.disable()
        } else {
            super.keyTyped(typedChar, keyCode)

            val string = typedString.remove(" ")

            if (string.isNotEmpty()) {
                setHudButtonVisibility { hudButton ->
                    hudButton.hudElement.name.contains(string, true)
                        || hudButton.hudElement.alias.any { it.contains(string, true) }
                }
            } else {
                setHudButtonVisibility { true }
            }
        }
    }

    private fun setHudButtonVisibility(function: (HudButton) -> Boolean) {
        windowList.filterIsInstance<ListWindow>().forEach {
            for (child in it.children) {
                if (child !is HudButton) continue
                child.visible = function(child)
            }
        }
    }

    init {
        listener<Render2DEvent.NanoSense>(-1000) {
            if (mc == null || mc.world == null || mc.player == null || mc.currentScreen == this) return@listener

            if (Hud.isEnabled) {
                GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE)

                for (window in windowList) {
                    if (window !is AbstractHudElement || !window.visible) continue
                    mc.profiler.startSection(window.rootName)
                    renderHudElement(window)
                    mc.profiler.endSection()
                }

                GlStateUtils.depth(true)
            }
        }
    }

    private fun renderHudElement(window: AbstractHudElement) {
        glPushMatrix()
        glTranslatef(window.renderPosX, window.renderPosY, 0.0f)

        glScalef(window.scale, window.scale, window.scale)
        window.renderHud()

        glPopMatrix()
    }

}