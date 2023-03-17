package future.plugins

import User
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import future.SessionManager.clients
import future.SessionWork
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.time.Duration

fun Application.configureSockets() {
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
        //들어오는 소켓요청에 대해 하나의 (작업단위)독립된 세션을 만듦. 요청하는 각각의 클라이언트가 각 세션단위를 가짐.
        //특정 명령까지의 작업(ICE)이 끝나거나 소켓의 연결이 끊키면, 반복루프가 종료되고 자연히 세션이 닫힘.
        webSocket("/rtc") {
//            val sessionID = UUID.randomUUID() // 이값을 앱의 id(==email)로 삼아 등록해야겠음.
            var sessionID = ""
            var user: User? = null
            var jInit: JsonObject? = null
            val sessionWork = SessionWork()


            try {
                //처음 접속할때 유저 정보를 검사하고 추가해주는 절차를 가짐.
//                println("[디버깅] 0")
//                val socket = incoming
//                println("[디버깅] 01")
////                val fm = this.incoming.receive()
//                val fm = socket.receive()
//                println("[디버깅] 02")
//                if (fm is Frame.Text) {
//                println("[디버깅] 03")
//                    jInit = JsonParser.parseString(fm.readText()) as JsonObject?
//                    //시그널링클라이언트의 init시 등록한 속성(command)이 있는지, 그속성의 값이 ws_init인지 확인.
//                    if (jInit != null && jInit.has("command") && jInit["command"].asString == "ws_init") {
//                        println("[INIT] 처음 접속시 실행. 클라존재유무검사 실행.")
//                        sessionID = jInit.asJsonObject["id"].asString
//                        //아이디 중복검사 후 등록해야됨.
//                        user = sessionWork.클라존재유무검사(jInit, this)
//                        println("[INIT] sessionID: $sessionID, clients.size: ${clients.size},   $user")
//                    }else{
//                        println("[INIT] jInit Failed..ㅠㅠ")
//                    }
//                }



//                println("[디버깅] 1")
                //이후 반복문을 돌리면서 소켓으로부터 들어오는 메시지를 계속적으로 확인함.
                for (frame in incoming) {
//                while (true) {
//                    var frame = incoming.receive()
//                    var frame =  incoming.receiveCatching().getOrNull() ?: break
//                    println("[디버깅] 2")
                    //frame의 타입에 따라 is를 사용하여 타입 확인 및 자동캐스팅을 진행하고,
                    if (frame is Frame.Text){ //전송받은 frame의 타입이 문자열일 경우
                        val tmp = frame.readText()
//                        println("[디버깅] 3 tmp: $tmp")
                        val jin = JsonParser.parseString(tmp).asJsonObject
//                        println("[디버깅] 4")
                        val command = jin["command"].asString
//                        println("[디버깅] 5 $command")

                        when (command) {
                            "ws_init" -> {
                                println("[INIT] 처음 접속시 실행. 클라존재유무검사 실행.")
                                sessionID = jin.asJsonObject["id"].asString
                                //아이디 중복검사 후 등록해야됨.
                                user = sessionWork.클라존재유무검사(jin, this)

                                println("[INIT] sessionID: $sessionID, clients.size: ${clients.size},   $user")
                            }
                            "방만들기" -> {
                                //방장이 될 클라가 방만들기 command로 요청한 경우.
                                //rooms map에 room 객체를 만들어 추가해야함.
                                //room객체에는 jin에 있는 정보(title, pwd, roomid(useremail), groupid)등이 있어야함.
//                                sessionWork.onMessage(sessionID, jin)
                                println("sessionWork.방만들기() 실행. user: $user")
                                sessionWork.방만들기(user!!, jin)

                            }
                            "방접속" -> {
                                // 다른 클라가 방접속 command로 요청한 경우.
                                // jin안의 클릭한 방정보(방장아이디)를 이용해 rooms객체에서 있는지 확인하고,
                                // 거기에 이 유저(user변수)를 추가.
                                // room객체를 찾아서 들어가면, 그것의 sessionState 값을 ready로 바꿔줘야할듯
//                                sessionWork.onMessage(sessionID, jin)
                                if (user != null) {
                                    println("sessionWork.방접속시실행() 실행. user: $user")
                                    sessionWork.방접속시실행(user, jin)
                                } else{
                                    println("[방접속] user 객체가 없음.")
                                }

                            }
                            "signalingCommand" -> {
                                //클라의 sendCommand() 메소드에 의한 메시지를 처리. OFFER, ANSWER, ICE 처리.
                                sessionWork.onMessage(user!!, jin)
                            }

                        }

//                        println("반복 루프 마지막...")
                    } else{
                        println("frame is not Text Type...")
                    }
//                    println("[디버깅] 6")
                }

                println("Exiting incoming loop, closing session: $sessionID")
                sessionWork.onSessionClose(sessionID)
            } catch (e: ClosedReceiveChannelException) {
                println("onClose $sessionID")
                println("onClose $e")
                sessionWork.onSessionClose(sessionID)
            } catch (e: Throwable) {
                println("onError $sessionID $e")
                sessionWork.onSessionClose(sessionID)
            }
        }




    }
}
