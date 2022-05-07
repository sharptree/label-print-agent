package io.sharptree.maximo.app.label

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.core.joran.spi.JoranException
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.google.gson.Gson
import mu.KotlinLogging
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

private const val applicationName = "Label Print Agent"

fun main(args: Array<String>) =
    Application().subcommands(Monitor(), CreateAPIKey(), RevokeAPIKey(), Encrypt(), Stop(), Version())
        .main(args)

@Suppress("unused", "RemoveEmptyPrimaryConstructor") // this is required by the Apache daemon for linux
class LabelPrintAgent() {
    private var args: Array<String> = emptyArray()

    @Suppress("unused")
    fun init(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
        this.args = args
    }

    @Suppress("unused")
    fun start() {
        val newArgs = mutableListOf<String>()
        newArgs.addAll(this.args)
        newArgs.add("monitor")
        println(newArgs.joinToString())
        main(newArgs.toTypedArray())
    }

    @Suppress("unused")
    fun stop() {
        main(arrayOf("stop"))
    }

    @Suppress("unused")
    fun destroy() {
    }
}

class Application : CliktCommand() {
    private val configFile by option(
        "-c",
        "--config",
        help = "The path to the $applicationName configuration file"
    ).default("./label-print-agent.yaml")

    private val logConfig by option("-l", "--log", help = "The path to the logger configuration file")

    override fun run() {
        // Ensure that at least Java 8 is being used.
        if (javaVersion() < 8) {
            logger.error { "The $applicationName requires Java 8 or higher." }
            exitProcess(1)
        }

        val file = File(configFile)

        if (!file.exists()) {
            logger.error { "The $applicationName configuration file $configFile could not be found." }
            exitProcess(1)
        }

        var logConfigFile: File? = null

        if (logConfig != null && File(logConfig!!).exists()) {
            logConfigFile = File(logConfig!!)
        } else {
            if (File("./logback.xml").exists()) {
                logConfigFile = File("./logback.xml")
            }
        }

        if (logConfigFile != null && logConfigFile.exists()) {
            val context: LoggerContext = LoggerFactory.getILoggerFactory() as LoggerContext

            try {
                val configurator = JoranConfigurator()
                configurator.context = context
                // Call context.reset() to clear any previous configuration, e.g. default
                // configuration. For multi-step configuration, omit calling context.reset().
                context.reset()
                configurator.doConfigure(logConfigFile)
            } catch (je: JoranException) {
                // StatusPrinter will handle this
            }
        }

        try {
            // get the configuration before we inject it, so we can get the validation errors.
            val config = ConfigurationServiceImpl.fromFile(File(configFile)).getConfiguration().validate()

            @Suppress("USELESS_CAST") // The casting is required for Koin to make specific classes generic.
            val modules = module {
                single { config }
                single { MaximoAuthenticator(get()) as Authenticator }
                single { MaximoCookieJar() as CookieJar }
                single { MaximoOkHttpClient(get(), get(), get()).getOkHttpClient() }
            }

            stopKoin()
            startKoin {
                // declare modules
                modules(modules)
            }
        } catch (e: Exception) {
            handleException(e, null)
        }
    }
}

class Stop : CliktCommand(name = "stop", help = "Stop the $applicationName") {
    override fun run() {
        logger.info { "Stopping the $applicationName." }
        exitProcess(0)
    }
}

class Version :
    CliktCommand(name = "version", help = "Print the $applicationName version information to the console") {
    override fun run() {
        println("$applicationName version 1.0.0")
        exitProcess(0)
    }
}

class Encrypt : KoinComponent,
    CliktCommand(name = "encrypt", help = "Encrypt the Maximo user password in the configuration file") {
    override fun run() {
        val config by inject<Configuration>()

        try {
            val configFile: String = when {
                config.resource != null ->
                    throw Exception("The current configuration was obtained via a resource reference and cannot be updated with an encrypted value.")
                config.path != null ->
                    config.path!!.toFile().absolutePath
                config.file != null ->
                    config.file!!
                else ->
                    throw Exception("No configuration file specified")
            }
            encryptConfig(configFile)
            exitProcess(0)
        } catch (e: Exception) {
            handleException(e, config)
        }
    }
}

class Monitor : KoinComponent,
    CliktCommand(
        name = "monitor",
        help = "Connect the $applicationName to Maximo and monitor for label print events"
    ) {

    override fun run() {
        val config by inject<Configuration>()
        val okhttp by inject<OkHttpClient>()

        try {

            sseLoop(okhttp, config)

            exitProcess(0)
        } catch (e: Exception) {
            handleException(e, config)
        }
    }
}

class CreateAPIKey : KoinComponent,
    CliktCommand(
        name = "create-apikey",
        help = "Create or replace an API key for the configured user. API key is printed to the terminal and must be manually configured"
    ) {

    override fun run() {

        val config by inject<Configuration>()
        val client by inject<OkHttpClient>()

        try {
            val createTokenUrl = config.maximo.url() + "/oslc/apitoken/create"

            val request: Request = Request.Builder()
                .url(createTokenUrl)
                .post("{\"expiration\":-1}".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .build()

            val body = client.newCall(request).execute().body

            if (body != null) {
                val apikey = Gson().fromJson(body.string(), ApiKey::class.java)

                if (config.maximo.username == null) {
                    logger.info("Api key ${config.maximo.apikey} replaced, copy and add the new key to the configuration under the maximo heading:\n\tapikey: \"${apikey.apikey}\"")
                } else {
                    logger.info("Api key for ${config.maximo.username} created, copy and add to the configuration under the maximo heading:\n    apikey: \"${apikey.apikey}\"\nRemove the password value if configured.")
                }
                body.close()
            }
            exitProcess(0)

        } catch (e: Exception) {
            handleException(e, config)
        }
    }

    data class ApiKey(val apikey: String)
}


class RevokeAPIKey : KoinComponent, CliktCommand(name = "revoke-apikey", help = "Revoke the API key") {

    override fun run() {
        val config by inject<Configuration>()
        val client by inject<OkHttpClient>()
        try {
            val createTokenUrl = config.maximo.url() + "/oslc/apitoken/revoke"

            val request: Request = Request.Builder()
                .url(createTokenUrl)
                .post("{\"apikey\":\"${config.maximo.apikey}\",\"expires\":-1}".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .build()

            client.newCall(request).execute().body?.close()
            logger.info("API key ${config.maximo.apikey} successfully revoked.")
            exitProcess(0)
        } catch (e: Exception) {
            handleException(e, config)
        }
    }
}


fun handleException(e: Exception, config: Configuration?) {

    when (e) {
        is SSLHandshakeException -> {
            if (e.message?.contains("PKIX path") == true) {
                logger.error { "The server certificated is signed by an unknown Certificate Authority (CA), such as a self signed cert. Ensure the certificate's CA is in the JVM keystore.\n Stack overflow discussion: https://stackoverflow.com/questions/11617210/how-to-properly-import-a-selfsigned-certificate-into-java-keystore-that-is-avail" }
            } else {
                logger.error("${e.message}")
            }
        }
        is SSLException -> {
            if (e.message == "Unsupported or unrecognized SSL message") {
                logger.error { "Received an unexpected SSL response, you may have specified a secure connection (https) for an unsecured port (http)." }
            } else {
                logger.error("${e.message}")
            }
        }
        is FileNotFoundException -> {
            logger.debug { e }
            logger.error("Error loading the configuration file: ${e.message}")
        }
        is SocketTimeoutException -> {
            if (config != null) {
                logger.error("Error connecting to ${config.maximo.host}, connection timed out.")
            } else {
                logger.error("${e.message}")
            }
        }
        is ConfigurationException -> {
            logger.error { "${e.message}" }
        }
        else -> {
            logger.debug { e }
            logger.error("${e.message}")
        }
    }

    exitProcess(1)
}


private fun javaVersion(): Int {
    var version = System.getProperty("java.version")
    if (version.startsWith("1.")) {
        version = version.substring(2, 3)
    } else {
        val dot = version.indexOf(".")
        if (dot != -1) {
            version = version.substring(0, dot)
        }
    }
    return version.toInt()
}
