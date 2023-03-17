package future

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import future.plugins.*

fun main() {
    embeddedServer(Netty, port = 8585, host = "0.0.0.0", module = Application::module)
            .start(wait = true)
}

fun Application.module() {
//    configureSockets()
    configureSockets2()
    configureMonitoring()
    configureRouting()
}
