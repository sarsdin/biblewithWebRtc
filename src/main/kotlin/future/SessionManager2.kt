package future

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
object SessionManager2 {

    private val sessionManagerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val clients = mutableMapOf<UUID, DefaultWebSocketServerSession>()

    //세션에 2명의 peer가 없으면 Impossible임.
    private var sessionState: WebRTCSessionState = WebRTCSessionState.Impossible

    /**
     * 소켓으로 클라이언트가 제일 처음 연결되면 실행되는 메소드.
     */
    fun onSessionStarted(sessionId: UUID, session: DefaultWebSocketServerSession) {
        //세션이 시작되면 먼저 코루틴 하나 실행하고, mutex로 동시접근을 막아 중복 에러를 방지한다.
        sessionManagerScope.launch {
            println("뭐야?")
            mutex.withLock {
                //클라이언트 맵의 사이즈가 1개 이상 존재하면,
                if (clients.size > 1) {
                    println("뭐야? 3")
                    //이번에는 취소불가능한 또다른 코루틴을 생성하여
                    sessionManagerScope.launch(NonCancellable) {//기존의 코드전용임: 접속한 클라가 2명일때, 다른 클라가 접속하면 소켓연결을 끊어버림.
                        //해당 클라이언트에게 논쓰레드세이프한 Frame객체를 전송한다.
                        //Close()는 웹소켓세션이 닫혔다는 것을 알리는 용도로 전달. Raw 웹세션이 아니라면..
                        // 보통 Frame은 관리될 필요가 없다고 함.
                        // 아무튼, close하는 이유는 위에서 맵의 사이즈를 2명으로 제한하고 싶기 때문임.
                        // 결국 접속 유저를 2명으로 제한하고 싶은 것이고, 다자간 연결을 계층적으로 관리하고 싶다면,
                        // 저 맵자체를 최상위로하고, 맵의 value로 vector등의 리스트를 이용해 클라이언트의 정보를 담은 객체
                        //를 넣어주는 방식으로 관리해야함.
                        session.send(Frame.Close()) // only two peers are supported
                        //다른 코루틴에서 소켓끊는 작업을 하게두고, 이전 코루틴은 종료(return)해버림.
                    }
                    return@launch
                }
                //소켓을 끊음과 동시에 현재 들어온 클라이언트의 uuid를 키로 값에 소켓 객체를 등록함..
                //이때 clients Map의 키값으로 UUID는 고유
                clients[sessionId] = session
                session.send("Added as a client: $sessionId")
                if (clients.size > 1) {
                    sessionState = WebRTCSessionState.Ready
                }
                notifyAboutStateUpdate()
            }
        }
    }


    /**
     * 클라이언트 소켓에서 서버로 전달되어져온 명령 메시지에 따라 해당 작업을 수행함.
     */
    fun onMessage(sessionId: UUID, message: String) {
        when {
            message.startsWith(MessageType.STATE.toString(), true) -> handleState(sessionId)
            //OFFER일 때 message에는 SDP정보를 담은 String이 있음.
            message.startsWith(MessageType.OFFER.toString(), true) -> handleOffer(sessionId, message)
            message.startsWith(MessageType.ANSWER.toString(), true) -> handleAnswer(sessionId, message)

            //ICE 관련된 메시지는 중간 중간 옴
            message.startsWith(MessageType.ICE.toString(), true) -> handleIce(sessionId, message)
        }
    }

    /**
     * STATE 상태를 전달하기 하지만, 어떤 전달할 명령을 담고 있진 않음.
     * 대신, Impossible 이라는 상태를 보냄. 클라이언트에게 아직 2명이상의 연결을 할 상황이 안된다는 것을 알려주는 것임.
     */
    private fun handleState(sessionId: UUID) {
        sessionManagerScope.launch {
            println("현재 MessageType.STATE의 상태값: ${MessageType.STATE}, sessionId: $sessionId")
            clients[sessionId]?.send("${MessageType.STATE} $sessionState")
        }
    }


    /**
     * 해당 peer로부터 OFFER 메시지를 받으면 진행.
     */
    private fun handleOffer(sessionId: UUID, message: String) {
        //현재는 2인만으로 서버에서 제한되있기 때문에 이런 조건이 걸렸다.
        // 차후에는 sessionState 값자체를 하나의 전체그룹 객체에 담아서 검사해야할것 같다.
        // 생각 - 전체그룹객체(Map이 좋을듯)에 들어갈 값(내용):
        //          단일그룹 클래스 - sessionState변수 , 그룹내 peer소켓을 담은 리스트

        //      처음 단일그룹을 만들때는 uuid+방만든사람id를 이용해 key값으로 정함.
        //      그리고 단일그룹 객체내 sessionState객체의 상태값을 대기중으로 바꾸고 방을 유지함.
        if (sessionState != WebRTCSessionState.Ready) {
            error("Session should be in Ready state to handle offer")
        }
        sessionState = WebRTCSessionState.Creating
        println("$sessionId 로부터 OFFER 메시지옴. 상태값으로 WebRTCSessionState.Creating 전달. message: $message")
        //서버 clients맵에 속한 소켓 전부에게 바뀐 상태를 전달함.
        notifyAboutStateUpdate()
        // OFFER보낸 클라이언트(sessionID)가 아닌 클라들을 clients Map내에서 찾고,
        // 그모음을 새로운 맵으로 생성함. 그리고, 그 맵의 가장 처음 pair를 가져옴.
        val clientToSendOffer = clients.filterKeys { it != sessionId }.values.first()
        //filterKeys로 반환된 뉴맵은 기존 맵의 iterator(반복순서)를 유지함.
        // (현재 2인..연결제한이라 이런식의 로직을 이용함.)
        // OFFER보낸 클라이언트 외에 남는 클라이언트는 first()로 가져온 클라이언트만 남음.
        // 그 클라이언트 소켓에 'OFFER 보낸 클라이언트'가 동봉한 SDP 문자열 정보를 그대로 전달함.
        clientToSendOffer.send(message)
    }

    private fun handleAnswer(sessionId: UUID, message: String) {
        if (sessionState != WebRTCSessionState.Creating) {
            error("Session should be in Creating state to handle answer")
        }
        println("$sessionId 로부터 ANSWER 메시지옴. message: $message")
        val clientToSendAnswer = clients.filterKeys { it != sessionId }.values.first()
        clientToSendAnswer.send(message)
        sessionState = WebRTCSessionState.Active
        notifyAboutStateUpdate()
    }

    /**
     * ICE command에 대해서는 sessionState 값의 변화가 없음.
     *
     * 서버에서는 ICE 관련 메시지를 클라이언트들에게 알려만 줌.
     */
    private fun handleIce(sessionId: UUID, message: String) {
        println("$sessionId 로부터 ICE 관련 메시지 옴 message: $message")
        val clientToSendIce = clients.filterKeys { it != sessionId }.values.first()
        clientToSendIce.send(message)
    }

    fun onSessionClose(sessionId: UUID) {
        sessionManagerScope.launch {
            mutex.withLock {
                clients.remove(sessionId)
                sessionState = WebRTCSessionState.Impossible
                notifyAboutStateUpdate()
            }
        }
    }


    /**
     * 서버의 세션 현상태를 설명하는 값임. (메시지타입은 클라이언트의 행동단계를 전달하는 것이고.)
     * 서버안의 이 상태값에 따라 클라의 Ui상태값을 변화시켜 그에 맞는 Ui를 따르게 해줌.
     */
    enum class WebRTCSessionState {
        Active, // Offer and Answer messages has been sent
        Creating, // Creating session, offer has been sent
        Ready, // Both clients available and ready to initiate session
        Impossible // We have less than two clients
    }

    /**
     * 메시지 타입을 보면 STATE, OFFER, ANSWER, ICE 의 4가지 유형만 있는데, 이것은
     * 시그널링 서버의 역할을 한정지어 보여주는 것이다.
     * 즉, ICE 상태까지만 시그널링 서버로 peer들을 맺어주면, 시그널링 서버의 역할은 끝나는 것.
     * 그후에는 클라이언트의 WebRtc 패키지인 peerConnection 객체를 이용해 비디오 & 오디오
     * 트랙을 주고 받게 된다.
     */
    enum class MessageType {
        STATE,
        OFFER,
        ANSWER,
        ICE
    }

    /**
     * 클라이언트 맵에 있는 소켓으로 연결된 각 클라에게 메시지 타입으로 STATE 에 해당하는 상태 값을 전달해줌.
     * 예로, 맨처음에는 onSessionStarted()가 시작되어 STATE Impossible 을 보내지만,
     * 2명이 되자마자 STATE Ready 를 현재 소켓 맵에 연결되어 있는 '모든 클라이언트'(아직2명)에게 보낸다.
     */
    private fun notifyAboutStateUpdate() {
        clients.forEach { (_, client) ->
            client.send("${MessageType.STATE} $sessionState")
        }
    }

    private fun DefaultWebSocketServerSession.send(message: String) {
        sessionManagerScope.launch {
            this@send.send(Frame.Text(message))
        }
    }
}

