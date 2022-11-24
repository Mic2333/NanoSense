package dev.supermic.nanosense.command.commands

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dev.supermic.nanosense.command.ClientCommand
import dev.supermic.nanosense.translation.I18N_LOCAL_DIR
import dev.supermic.nanosense.translation.TranslationManager
import dev.supermic.nanosense.util.text.NoSpamMessage
import dev.supermic.nanosense.util.threads.defaultScope

object TranslationCommand : ClientCommand(
    name = "translation",
    alias = arrayOf("i18n")
) {
    init {
        literal("dump") {
            execute {
                defaultScope.launch(Dispatchers.Default) {
                    TranslationManager.dump()
                    NoSpamMessage.sendMessage(TranslationCommand, "Dumped root lang to $I18N_LOCAL_DIR")
                }
            }
        }

        literal("reload") {
            execute {
                defaultScope.launch(Dispatchers.IO) {
                    TranslationManager.reload()
                    NoSpamMessage.sendMessage(TranslationCommand, "Reloaded translations")
                }
            }
        }

        literal("update") {
            string("language") {
                execute {
                    defaultScope.launch(Dispatchers.IO) {
                        TranslationManager.update()
                        NoSpamMessage.sendMessage(TranslationCommand, "Updated translation")
                    }
                }
            }
        }
    }
}