package dev.supermic.nanosense.setting

import dev.supermic.nanosense.NanoSenseMod
import dev.supermic.nanosense.setting.configs.IConfig
import dev.supermic.nanosense.util.collections.NameableSet

internal object ConfigManager {
    private val configSet = NameableSet<IConfig>()

    init {
        register(GuiConfig)
        register(ModuleConfig)
    }

    fun loadAll(): Boolean {
        var success = load(GenericConfig) // Generic config must be loaded first

        configSet.forEach {
            success = load(it) || success
        }

        return success
    }

    fun load(config: IConfig): Boolean {
        return try {
            config.load()
            NanoSenseMod.logger.info("${config.name} config loaded")
            true
        } catch (e: Exception) {
            NanoSenseMod.logger.error("Failed to load ${config.name} config", e)
            false
        }
    }

    fun saveAll(): Boolean {
        var success = save(GenericConfig) // Generic config must be loaded first

        configSet.forEach {
            success = save(it) || success
        }

        return success
    }

    fun save(config: IConfig): Boolean {
        return try {
            config.save()
            NanoSenseMod.logger.info("${config.name} config saved")
            true
        } catch (e: Exception) {
            NanoSenseMod.logger.error("Failed to save ${config.name} config!", e)
            false
        }
    }

    fun register(config: IConfig) {
        configSet.add(config)
    }

    fun unregister(config: IConfig) {
        configSet.remove(config)
    }
}