package dev.supermic.nanosense.setting.settings.impl.primitive

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import dev.supermic.nanosense.setting.settings.MutableNonPrimitive
import dev.supermic.nanosense.setting.settings.MutableSetting
import dev.supermic.nanosense.util.asStringOrNull
import dev.supermic.nanosense.util.extension.next
import java.util.*

class EnumSetting<T : Enum<T>>(
    name: CharSequence,
    value: T,
    visibility: ((() -> Boolean))? = null,
    consumer: (prev: T, input: T) -> T = { _, input -> input },
    description: CharSequence = ""
) : MutableSetting<T>(name, value, visibility, consumer, description), MutableNonPrimitive<T> {

    val enumClass: Class<T> = value.declaringJavaClass
    val enumValues: Array<out T> = enumClass.enumConstants

    fun nextValue() {
        value = value.next()
    }

    override fun setValue(valueIn: String) {
        super<MutableSetting>.setValue(valueIn.uppercase(Locale.ROOT).replace(' ', '_'))
    }

    override fun write(): JsonElement = JsonPrimitive(value.name)

    override fun read(jsonElement: JsonElement) {
        jsonElement.asStringOrNull?.let { element ->
            enumValues.firstOrNull { it.name.equals(element, true) }?.let {
                value = it
            }
        }
    }

}