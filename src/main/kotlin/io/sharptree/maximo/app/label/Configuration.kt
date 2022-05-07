package io.sharptree.maximo.app.label

import com.google.common.net.InetAddresses
import com.google.common.net.InternetDomainName
import com.sksamuel.hoplite.ConfigException
import com.sksamuel.hoplite.ConfigLoader
import kotlinx.serialization.SerialName
import mu.KotlinLogging
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}

interface ConfigurationService {
    fun getConfiguration(): Configuration
}

@Suppress("SpellCheckingInspection")
internal class ConfigurationServiceImpl private constructor(
    val resource: String? = null,
    val path: Path? = null,
    val file: File? = null
) : ConfigurationService {

    override fun getConfiguration(): Configuration {
        val config = when {
            resource != null ->
                try {
                    ConfigLoader()
                        .loadConfigOrThrow(resource)
                } catch (ce: ConfigException) {
                    ConfigLoader()
                        .loadConfigOrThrow<Configuration>(
                            Paths.get(resource)
                        )
                }
            path != null ->
                ConfigLoader().loadConfigOrThrow(path)
            file != null ->
                ConfigLoader().loadConfigOrThrow(Paths.get(file.toURI()))
            else ->
                throw Exception("No configuration file specified")
        }
        config.path = path
        config.file = file?.absolutePath
        config.resource = resource

        val password = config.maximo.password
        if (password != null) {
            if (password.startsWith("{encrypted}")) {
                config.maximo.password = decryptPassword(password.substring(password.indexOf("}") + 1))
            }
        }


        return config.validate()
    }

    companion object {
        @Suppress("unused")
        fun fromResource(resource: String): ConfigurationService {
            return ConfigurationServiceImpl(resource = resource)
        }

        @Suppress("unused")
        fun fromPath(path: Path): ConfigurationService {
            return ConfigurationServiceImpl(path = path)
        }

        fun fromFile(file: File): ConfigurationService {
            if (!file.exists()) {
                throw FileNotFoundException("The configuration file ${file.absolutePath} could not be found.")
            }
            return ConfigurationServiceImpl(file = file)
        }
    }


}

@SerialName("configuration")
data class Configuration(
    val maximo: MaximoConfiguration,var printers: List<String>?, var path: Path?, var resource: String?, var file: String?
) {
    @Throws(ConfigurationException::class)
    fun validate(): Configuration {
        maximo.validate()
        return this
    }
}

@SerialName("maximo")
data class MaximoConfiguration(
    var username: String?,
    var password: String? = null,
    var host: String,
    var port: Int = 443,
    var secure: Boolean = true,
    val authType: String = "maxauth",
    val context: String = "maximo",
    val apikey: String? = null,
    val allowInsecure:Boolean = false,
    val sseEndpoint :String = "/labeldispatch"
) {

    @Throws(ConfigurationException::class)
    fun validate() {
        if (apikey.isNullOrBlank() && (username.isNullOrBlank() || password.isNullOrBlank())) {
            throw ConfigurationException("The configuration requires an API key or a user name and password.")
        }
        if (host.isBlank()) {
            throw ConfigurationException("The configuration requires a host name or IP.")
        }

        if(host.startsWith("http")){
            throw ConfigurationException("The host name should not start include the protocol http:// or https://")
        }

        @Suppress("UnstableApiUsage")
        if(!InternetDomainName.isValid(host) && !InetAddresses.isInetAddress(host)){
            throw ConfigurationException("The configured host is not a valid host or IP address.")
        }

        if (port < 1 || port > 65535) {
            throw ConfigurationException("The Maximo port number must be between 1 and 65535, typically 80 for HTTP or 443 for HTTPS.")
        }

        if (authType.isBlank()) {
            throw ConfigurationException("The authType is required and must be either oidc, maxauth, basic or form.")
        } else if (authType != "oidc" && authType != "maxauth" && authType != "basic" && authType != "form") {
            throw ConfigurationException("The authType) $authType is not valid, only 'oidc', 'maxauth', 'basic' or 'form' are valid.")
        }
    }

    fun url(): String {
        return (if (secure) "https://" else "http://") + if (host.endsWith("/")) host.substring(
            0,
            host.length - 1
        ) else host + (if (secure) (if (port == 443) "" else ":$port") else (if (port == 80) "" else ":$port")) + "/$context"
    }
}

fun decryptPassword(password: String): String {
    check(File(KEY_FILE).exists()) { "The encryption key $KEY_FILE was not found." }

    val ck = File(KEY_FILE).readBytes()
    val iv = ck.copyOfRange(0, 16)
    val key = ck.copyOfRange(16, 48)

    val keySpec = SecretKeySpec(key, "AES")
    val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
    cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
    return String(cipher.doFinal(Base64.getDecoder().decode(password)))

}

private const val KEY_FILE = "./.password.key"
fun encryptConfig(path: String) {
    val contents = StringBuilder()

    File(path).readLines().forEach {
        if (it.contains("password") && !it.trim().startsWith("#")) {
            if (!it.contains("{encrypted}")) {
                val password = encryptPassword(it.substring(it.indexOf(":") + 1).trim().replace("\"", ""))
                contents.append(it.substring(0, it.indexOf(":") + 1)).append(" \"{encrypted}$password\"\n")
            } else {
                logger.warn { "The configuration file $path is already encrypted." }
                contents.append(it).append("\n")
            }
        } else {
            contents.append(it).append("\n")
        }
    }

    File(path).writeText(contents.toString())

    logger.info { "Successfully encrypted the Maximo user password in the $path configuration file." }
}


fun encryptPassword(password: String): String {
    //if the key doesn't exist then create it
    if (!File(KEY_FILE).exists()) {
        File(KEY_FILE).writeBytes(
            IvParameterSpec(SecureRandom().generateSeed(16)).iv.plus(
                SecureRandom.getInstanceStrong().generateSeed(32)
            )
        )
        File(KEY_FILE).makeOwnerAccessOnly()
    }
    val ck = File(KEY_FILE).readBytes()
    val iv = ck.copyOfRange(0, 16)
    val key = ck.copyOfRange(16, 48)

    val skeySpec = SecretKeySpec(key, "AES")
    val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
    cipher.init(Cipher.ENCRYPT_MODE, skeySpec, IvParameterSpec(iv))
    return Base64.getEncoder().encodeToString(cipher.doFinal(password.toByteArray()))
}

fun File.makeOwnerAccessOnly() {
    if (System.getProperty("os.name").startsWith("windows", true)) {
        Files.setAttribute(toPath(), "dos:hidden", true)
    } else {
        Files.setPosixFilePermissions(toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
    }
}

class ConfigurationException(message: String?) : Exception(message)
