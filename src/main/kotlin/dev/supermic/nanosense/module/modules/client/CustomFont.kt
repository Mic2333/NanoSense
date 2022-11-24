package dev.supermic.nanosense.module.modules.client

import dev.supermic.nanosense.event.events.TickEvent
import dev.supermic.nanosense.event.listener
import dev.supermic.nanosense.module.Category
import dev.supermic.nanosense.module.Module
import dev.supermic.nanosense.util.TimeUnit
import dev.supermic.nanosense.util.delegate.AsyncCachedValue
import dev.supermic.nanosense.util.extension.fastCeil
import dev.supermic.nanosense.util.graphics.font.GlyphCache
import dev.supermic.nanosense.util.graphics.font.renderer.MainFontRenderer
import dev.supermic.nanosense.util.threads.onMainThread
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.util.*

internal object CustomFont : Module(
    name = "CustomFont",
    description = "Use the better font instead of the stupid Minecraft font",
    visible = false,
    category = Category.CLIENT,
    alwaysEnabled = true
) {
    private const val DEFAULT_FONT_NAME = "Lexend Deca"

    val fontName = setting("Font", DEFAULT_FONT_NAME, consumer = { prev: String, value ->
        getMatchingFontName(value) ?: getMatchingFontName(prev) ?: DEFAULT_FONT_NAME
    })
    val overrideMinecraft by setting("Override Minecraft", true)
    private val sizeSetting = setting("Size", 1.0f, 0.5f..2.0f, 0.05f)
    private val charGapSetting = setting("Char Gap", 0.0f, -10f..10f, 0.5f)
    private val lineSpaceSetting = setting("Line Space", 0.0f, -10f..10f, 0.05f)
    private val baselineOffsetSetting = setting("Baseline Offset", 0.0f, -10.0f..10.0f, 0.05f)
    private val lodBiasSetting = setting("Lod Bias", 0.0f, -10.0f..10.0f, 0.05f)

    val isDefaultFont get() = fontName.value.equals(DEFAULT_FONT_NAME, true)
    val size get() = sizeSetting.value * 0.1425f
    val charGap get() = charGapSetting.value * 0.5f - 2.05f
    val lineSpace get() = size * (lineSpaceSetting.value * 0.05f + 0.77f)
    val lodBias get() = lodBiasSetting.value * 0.25f - 0.5375f
    val baselineOffset get() = baselineOffsetSetting.value * 2.0f - 8.0f

    init {
        listener<TickEvent.Post>(true) {
            mc.fontRenderer.FONT_HEIGHT = if (overrideMinecraft) {
                MainFontRenderer.getHeight().fastCeil()
            } else {
                9
            }
        }
    }

    /** Available fonts on the system */
    val availableFonts: Map<String, String> by AsyncCachedValue(5L, TimeUnit.SECONDS) {
        HashMap<String, String>().apply {
            val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()

            environment.availableFontFamilyNames.forEach {
                this[it.lowercase(Locale.ROOT)] = it
            }

            environment.allFonts.forEach {
                val family = it.family
                if (family != Font.DIALOG) {
                    this[it.name.lowercase(Locale.ROOT)] = family
                }
            }
        }
    }

    private fun getMatchingFontName(name: String): String? {
        return if (name.equals(DEFAULT_FONT_NAME, true)) DEFAULT_FONT_NAME
        else availableFonts[name.lowercase(Locale.ROOT)]
    }

    init {
        fontName.valueListeners.add { prev, _ ->
            GlyphCache.delete(Font(prev, Font.PLAIN, 64))
            onMainThread {
                MainFontRenderer.reloadFonts()
            }
        }
    }
}