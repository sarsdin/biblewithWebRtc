package future.plugins

import future.SessionManager2
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.time.Duration
import java.util.*

fun Application.configureSockets2() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
//        webSocket("/rtc") { // websocketSession
//            for (frame in incoming) {
//                if (frame is Frame.Text) {
//                    val text = frame.readText()
//                    outgoing.send(Frame.Text("YOU SAID: $text"))
//                    if (text.equals("bye", ignoreCase = true)) {
//                        close(CloseReason(CloseReason.Codes.NORMAL, "Client said BYE"))
//                    }
//                }
//            }
//        }
        webSocket("/rtc") {
            val sessionID = UUID.randomUUID() // 이값을 앱의 id(==email)로 삼아 등록해야겠음.
//            val fm = this.incoming.receive()
//            if (fm is Frame.Text) {
//                fm.readText()
//            }

            try {
                SessionManager2.onSessionStarted(sessionID, this)

                //이후 반복문을 돌리면서 소켓으로부터 들어오는 메시지를 계속적으로 확인함.
                for (frame in incoming) {
//                while(true ) {
//                    incoming.receive()
                    println("뭐야?")
//                    when (val frame = incoming.receive()) {
                    when (frame) {
                        //when에서 frame의 타입에 따라 is를 사용하여 타입 확인 및 자동캐스팅을 진행하고,
                        is Frame.Text -> {
                            //전송받은 frame의 타입이 문자열일 경우
//                            val jOut = JsonParser.parseString(frame.readText())
//                            jOut.addProperty("signalingCommand", "${MessageType.STATE}")
//                            jOut.addProperty("sessionState", "$sessionState")
                            println("뭐야?1")
                            SessionManager2.onMessage(sessionID, frame.readText())
                        }

                        else -> Unit
                    }
                }
                println("Exiting incoming loop, closing session: $sessionID")
                SessionManager2.onSessionClose(sessionID)
            } catch (e: ClosedReceiveChannelException) {
                println("onClose $sessionID")
                SessionManager2.onSessionClose(sessionID)
            } catch (e: Throwable) {
                println("onError $sessionID $e")
                SessionManager2.onSessionClose(sessionID)
            }
        }





    }
}
