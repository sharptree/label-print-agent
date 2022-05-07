package io.sharptree.maximo.app.label

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.encode
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.nio.charset.Charset
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.*


@Suppress("unused")
interface OKHttpClientService {
    fun getOkHttpClient(): OkHttpClient
}


class MaximoOkHttpClient(
    private val cookieJar: CookieJar,
    private val authenticator: Authenticator,
    private val config: Configuration
) {
    fun getOkHttpClient(): OkHttpClient {
        if (!config.maximo.allowInsecure) {
            return OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .authenticator(authenticator)
                .addNetworkInterceptor(MASAuthenticationInterceptor())
                .applySseDefaults()
                .build()
        } else {
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            return OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0])
                .hostnameVerifier(PermissiveHostVerifier)
                .cookieJar(cookieJar)
                .authenticator(authenticator)
                .addNetworkInterceptor(MASAuthenticationInterceptor())
                .applySseDefaults()
                .build()
        }
    }
}


class MASAuthenticationInterceptor : Interceptor, KoinComponent {

    private var isOidcRequest: Boolean = false
    private var authAttempted: Boolean = false
    private val configuration by inject<Configuration>()

    private val encodedCreds = let {
        val usernameAndPassword: String = configuration.maximo.username + ":" + configuration.maximo.password
        usernameAndPassword.encode(Charset.forName("UTF-8")).base64()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (isOidcRequest && !authAttempted) {
            val resp = chain.proceed(chain.request().newBuilder().apply {
                header("Authorization", "Basic $encodedCreds")
            }.build())
            return resp
        } else {
            val resp = chain.proceed(chain.request())
            if (resp.isRedirect) {
                val state = resp.headers("Set-Cookie").filter {
                    (Cookie.parse(resp.request.url, it)?.name?.startsWith("WASOidcState") == true)
                }

                if (state.isNotEmpty()) {
                    val targetCookieName = "WASReqURLOidc${
                        Cookie.parse(
                            resp.request.url,
                            state[0]
                        )?.name?.substring("WASOidcState".length)
                    }"

                    val req = resp.headers("Set-Cookie").filter {
                        (Cookie.parse(resp.request.url, it)?.name?.startsWith(targetCookieName) == true)
                    }

                    isOidcRequest = req.isNotEmpty()

                }
            }

            return resp
        }
    }
}

class MaximoAuthenticator(private val configuration: Configuration) : Authenticator {

    private var invalidated = false

    private val encodedCreds = let {
        val usernameAndPassword: String = configuration.maximo.username + ":" + configuration.maximo.password
        usernameAndPassword.encode(Charset.forName("UTF-8")).base64()
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        val origRequest = response.request

        @Suppress("unused")
        return when {
            origRequest.header("Authorization") != null || origRequest.header("MAXAUTH") != null -> {
                invalidated = true
                null // Give up; We've already failed to authenticate.
            }
            invalidated ->
                null // Give up; We've already failed to authenticate.
            else ->
                // handle apikeys by getting the JSESSIONID
                if (configuration.maximo.apikey != null) {
                    origRequest.newBuilder().apply {
                        url(
                            origRequest.url.scheme + "://" + origRequest.url
                                .host + ":" + origRequest.url.port + "/" + origRequest.url
                                .pathSegments[0] + "/oslc/login?apikey=${configuration.maximo.apikey}&lean=1"
                        )
                    }
                } else {
                    // Build a new request from the old request, only with an extra header for auth
                    origRequest.newBuilder().apply {
                        when (configuration.maximo.authType.lowercase(Locale.getDefault())) {
                            "basic" ->
                                header("Authorization", "Basic $encodedCreds")
                            "maxauth" ->
                                header("MAXAUTH", encodedCreds)
                            "form" -> {
                                url(
                                    origRequest.url.scheme + "://" + origRequest.url
                                        .host + ":" + origRequest.url.port + "/" + origRequest.url
                                        .pathSegments[0] + "/oslc/j_security_check"
                                )
                                post(
                                    "j_username=${configuration.maximo.username}&j_password=${configuration.maximo.password}"
                                        .toRequestBody("application/x-www-form-urlencoded".toMediaType())
                                )
                            }
                            else ->
                                header("MAXAUTH", encodedCreds)
                        }
                    }
                }.build()
        }
    }
}

class MaximoCookieJar : CookieJar {
    private val cookies = mutableMapOf<String, Cookie>()

    override fun loadForRequest(url: HttpUrl): MutableList<Cookie> {
        return cookies.filter { appliesToRequestUri(url, it.value) }.values.toMutableList()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.filter {
            !(it.expiresAt <= 0 || it.expiresAt < System.currentTimeMillis() || it.value.isBlank())
        }.forEach {
            this.cookies[it.name] = it
        }
    }

    private fun appliesToRequestUri(url: HttpUrl, cookie: Cookie): Boolean {
        if (cookie.expiresAt < System.currentTimeMillis()) {
            return false
        }
        if (cookie.domain.isBlank() && !url.host.endsWith(cookie.domain)) {
            return false
        }
        if (!("/" + url.pathSegments.joinToString(separator = "/")).startsWith(cookie.path)) {
            return false
        }

        if (cookie.secure && !url.isHttps) {
            return false
        }

        return true
    }

}

fun OkHttpClient.Builder.applySseDefaults(): OkHttpClient.Builder = this
    .readTimeout(0, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)


private val trustAllCerts = arrayOf<X509TrustManager>(
    object : X509TrustManager {
        @Throws(CertificateException::class)
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        }

        @Throws(CertificateException::class)
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return arrayOf()
        }
    }
)

private object PermissiveHostVerifier : HostnameVerifier {
    override fun verify(hostname: String?, session: SSLSession?): Boolean {
        return true
    }
}

