package dev.supermic.nanosense.command

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import dev.supermic.nanosense.NanoSenseMod
import dev.supermic.nanosense.event.ClientExecuteEvent
import dev.supermic.nanosense.event.IListenerOwner
import dev.supermic.nanosense.module.modules.client.CommandSetting
import dev.supermic.nanosense.util.ClassUtils.instance
import dev.supermic.nanosense.util.text.MessageSendUtils
import dev.supermic.nanosense.util.text.formatValue
import dev.supermic.nanosense.util.threads.defaultScope
import dev.supermic.nanosense.util.threads.onMainThreadSuspend
import java.lang.reflect.Modifier
import kotlin.system.measureTimeMillis

object CommandManager : AbstractCommandManager<ClientExecuteEvent>(),
    dev.supermic.nanosense.AsyncLoader<List<Class<out ClientCommand>>> {
    override var deferred: Deferred<List<Class<out ClientCommand>>>? = null
    val prefix: String get() = CommandSetting.prefix

    override suspend fun preLoad0(): List<Class<out ClientCommand>> {
        val classes = dev.supermic.nanosense.AsyncLoader.classes.await()
        val list: List<Class<*>>

        val time = measureTimeMillis {
            val clazz = ClientCommand::class.java

            list = classes.asSequence()
                .filter { Modifier.isFinal(it.modifiers) }
                .filter { it.name.startsWith("dev.supermic.nanosense.command.commands") }
                .filter { clazz.isAssignableFrom(it) }
                .sortedBy { it.simpleName }
                .toList()
        }

        NanoSenseMod.logger.info("${list.size} commands found, took ${time}ms")

        @Suppress("UNCHECKED_CAST")
        return list as List<Class<out ClientCommand>>
    }

    override suspend fun load0(input: List<Class<out ClientCommand>>) {
        val time = measureTimeMillis {
            for (clazz in input) {
                register(clazz.instance)
            }
        }

        NanoSenseMod.logger.info("${input.size} commands loaded, took ${time}ms")
    }

    override fun register(builder: CommandBuilder<ClientExecuteEvent>): Command<ClientExecuteEvent> {
        synchronized(lockObject) {
            (builder as? IListenerOwner)?.subscribe()
            return super.register(builder)
        }
    }

    override fun unregister(builder: CommandBuilder<ClientExecuteEvent>): Command<ClientExecuteEvent>? {
        synchronized(lockObject) {
            (builder as? IListenerOwner)?.unsubscribe()
            return super.unregister(builder)
        }
    }

    fun runCommand(string: String) {
        defaultScope.launch {
            val args = tryParseArgument(string) ?: return@launch
            NanoSenseMod.logger.debug("Running command with args: [${args.joinToString()}]")

            try {
                try {
                    invoke(ClientExecuteEvent(args))
                } catch (e: CommandNotFoundException) {
                    handleCommandNotFoundException(args.first())
                } catch (e: SubCommandNotFoundException) {
                    handleSubCommandNotFoundException(string, args, e)
                }
            } catch (e: Exception) {
                MessageSendUtils.sendNoSpamChatMessage("Error occurred while running command! (${e.message}), check the log for info!")
                NanoSenseMod.logger.warn("Error occurred while running command!", e)
            }
        }
    }

    fun tryParseArgument(string: String) = try {
        parseArguments(string)
    } catch (e: IllegalArgumentException) {
        MessageSendUtils.sendNoSpamChatMessage(e.message.toString())
        null
    }

    override suspend fun invoke(event: ClientExecuteEvent) {
        val name = event.args.getOrNull(0) ?: throw IllegalArgumentException("Arguments can not be empty!")
        val command = getCommand(name)
        val finalArg = command.finalArgs.firstOrNull { it.checkArgs(event.args) }
            ?: throw SubCommandNotFoundException(event.args, command)

        onMainThreadSuspend {
            runBlocking {
                finalArg.invoke(event)
            }
        }
    }

    private fun handleCommandNotFoundException(command: String) {
        MessageSendUtils.sendNoSpamChatMessage("Unknown command: ${formatValue("$prefix$command")}. " +
            "Run ${formatValue("${prefix}help")} for a list of commands.")
    }

    private suspend fun handleSubCommandNotFoundException(string: String, args: Array<String>, e: SubCommandNotFoundException) {
        val bestCommand = e.command.finalArgs.maxByOrNull { it.countArgs(args) }

        var message = "Invalid syntax: ${formatValue("$prefix$string")}\n"

        if (bestCommand != null) message += "Did you mean ${formatValue("$prefix${bestCommand.printArgHelp()}")}?\n"

        message += "\nRun ${formatValue("${prefix}help ${e.command.name}")} for a list of available arguments."

        MessageSendUtils.sendNoSpamChatMessage(message)
    }

}