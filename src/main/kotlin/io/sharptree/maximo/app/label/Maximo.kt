package io.sharptree.maximo.app.label

import com.google.gson.Gson
import io.sharptree.maximo.app.label.sse.*
import io.sharptree.maximo.app.label.sse.okhttp.connectToSse
import io.sharptree.maximo.app.label.sse.okhttp.shutdownDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

internal fun sseLoop(client: OkHttpClient, configuration: Configuration) {

    val endPoint = configuration.maximo.sseEndpoint
    val url = "${configuration.maximo.url()}${if (endPoint.startsWith("/")) endPoint else "/$endPoint"}"

    val requestBuilder = Request.Builder()
        .url(url)
        .header("Accept-Language", "en")

    val randomSeed = Random.nextLong()
    var lastProcessedEventId = -1L

    runBlocking {
        withContext(Dispatchers.Default) {
            val eventSource = connectToSse(
                client,
                requestBuilder,
                random = Random(randomSeed),
                logger = object : Logger {
                    override fun onStart() = logger.info { "Monitor started." }
                    override fun onLine(line: Line) = logger.debug { "Line='$line'" }
                    override fun onLineEnding(lineEnding: LineEnding) = logger.debug { "LineEnding='$lineEnding'" }
                    override fun onLinePartial(linePartial: LinePartial) = logger.debug { "LinePartial='$linePartial'" }
                    override fun onEndOfFile() = logger.debug { "EOF 🚫" }
                    override fun onEventEnd() = logger.debug { "EventEnd" }
                    override fun onEventType(type: EventType) = logger.debug { "EventType='$type'" }
                    override fun onEventId(id: EventId) = logger.debug { "EventId='$id'" }
                    override fun onComment(comment: Comment) = logger.debug { "Comment='$comment'" }
                    override fun onInvalidReconnectionTime(value: String) =
                        logger.debug { "InvalidReconnectionTime='$value'" }

                    override fun onInvalidField(field: String) = logger.debug { "InvalidField='$field'" }
                    override fun onAwaitingConnection() = logger.debug { "OnAwaitingConnection" }
                    override fun onRetryDelay(milliseconds: Milliseconds) =
                        logger.debug { "OnRetryDelay='$milliseconds'" }

                    override fun onState(state: EventSourceState<*, *>) = logger.debug { "State='$state'" }
                    override fun onEventData(data: EventData) {
                        logger.debug { "EventData='$data'" }
                    }
                }
            )

            eventSource.consumeAsFlow().collect {
                if (it is EventSourceState.WithEvent) {
                    val lastEvent = it.lastEvent

                    if (lastEvent.type == "print" && lastEvent.lastEventId.toLong() > lastProcessedEventId) {
                        try {
                            val printData = Gson().fromJson(lastEvent.data, PrintData::class.java)
                            logger.info {
                                "Received print event: $printData"
                            }

                            // either the printers are not listed or the printer is configured.
                            if ((configuration.printers == null || configuration.printers?.isEmpty() == true) || configuration.printers?.contains(
                                    printData.printer
                                ) == true
                            ) {
                                val clientSocket = Socket()
                                withContext(Dispatchers.IO) {
                                    try {
                                        clientSocket.connect(
                                            InetSocketAddress(printData.printer, printData.port),
                                            printData.timeout
                                        )
                                        val outToServer = DataOutputStream(clientSocket.getOutputStream())
                                        outToServer.writeBytes(printData.label)
                                        clientSocket.close()
                                    } catch (error: Throwable) {
                                        logger.error { "Error printing label ${error.message}" }
                                    }
                                }
                            } else {
                                logger.info {
                                    "Ignoring print event because printer is not in local list."
                                }
                            }
                        }catch(error:Throwable){
                            logger.error { "An error occurred while processing the label printing: ${error.message}" }
                        }
                        // if there is an error we are still going to move past it so it doesn't get retried.
                        lastProcessedEventId = lastEvent.lastEventId.toLong()
                    }
                }
            }
        }
    }

    client.shutdownDispatcher()
}

data class PrintData(val printer: String, val port: Int, val label: String, val timeout: Int = 5000)