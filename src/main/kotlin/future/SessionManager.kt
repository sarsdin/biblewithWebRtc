package future

import Room
import User
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import future.SessionManager.clients
import future.SessionManager.rooms
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Originally written by Artem Bagritsevich.
 *
 * https://github.com/artem-bagritsevich/WebRTCKtorSignalingServerExample
 */
object SessionManager {

    val sessionManagerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val mutex = Mutex()

    //방제목을 방만드는 유저아이디로 할지?

    //group map 안에 clients 소켓과 sessionState 및 유저들 정보를 담는 객체가 있어야함.
    //즉, 어떤 객체 == group의( 소켓들 + sessionState변수 + userInfoList
    val rooms = mutableMapOf<String, Room>()

    /**
     * 웹소켓으로 접속한 모든 클라이언트의 정보를 가지고 있는 맵
     */
    val clients = mutableMapOf<String, User>()

    //세션에 2명의 peer가 없으면 Impossible임.
    // Room 객체의 sessionState변수로 대체. 각 방의 세션상태를 확인해야함.
//    private var sessionState: WebRTCSessionState = WebRTCSessionState.Impossible

}



class SessionWork {

    val sessionManagerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val mutex = Mutex()
    /**
     * 방만들기 또는 방접속시실행 메소드에서 만들거나 찾은 방장의 방을 멤버변수로 맵핑해준다.
     * 현재 유저의 소켓이 살아있는동안 접속하거나 만드는 방에 대한 접근을 제공해주는 용도임.
     * 이것을 이용해서 각 유저(클라이언트)는 ICE에 관한 메소드를 처리하거나, 접속을 끊을때
     * 해당 방에서 제거될 수 있음.
     */
    var mRoom : Room = Room()
//    lateinit var mRoom : Room

    fun 방만들기(user: User, jin: JsonObject) {
        sessionManagerScope.launch {
//            mutex.withLock {
//            }
            rooms[user.id] = Room(
                roomId = user.id,
                groupId = jin["groupId"].asString,
                title = jin["title"].asString,
                pwd = jin["pwd"].asString //pwd는 받는 클라쪽에서 null체크해야함. 방접속시에도 pwd 유무에따른 elseif문 추가해야함.
            ).apply { users.add(user) }

            //클래스의 멤버 변수에도 방금 만든 방 객체를 할당해준다.
            mRoom = rooms[user.id]!!

            //방목록에서 방장의 모임아이디와 같은 방들을 뽑음.
            val filteredRoomL = rooms.filter {
                it.value.groupId == jin["groupId"].asString
            }.run {
                val jarry = JsonArray()
                forEach{
                    val jo = JsonObject()
                    jo.addProperty("roomId", it.value.roomId)
                    jo.addProperty("title", it.value.title)
                    jo.addProperty("groupId", it.value.groupId)
                    jo.addProperty("size", it.value.size)
                    jo.addProperty("pwd", it.value.pwd)
                    jo.addProperty("sessionState", "${it.value.sessionState}")
                    jo.addProperty("usersCount", it.value.users.size)
                    jarry.add(jo)
                }
                return@run jarry
            }
            //뽑은 방목록을 json스트링으로 바꿈
//            val jo = Gson().toJson(filteredRoomL)
            val jOut = JsonObject().apply {
                addProperty("command", "방만들기")
                add("roomList", filteredRoomL)
            }
            //websocket으로 접속중인 모든 클라이언트 중 방장과 같은 모임에 접속해 있는 클라이언트들을 뽑은뒤
            //그 클라이언트들에 위에서 변환한 방목록 json객체를 보냄.(클라쪽에서 받을때는 형식이 어떻게 보내지는지 확인해야함. 맵변환이라
            //JsonObject인지 다른 형식인지 확인해봐야함)
            mutex.withLock {

                clients.filter {
                    it.value.groupId == jin["groupId"].asString
                }.forEach {
                    it.value.socket.send(Frame.Text(jOut.toString()))
                }
            }

        }
    }

    /**
     * 클라이언트가 방접속시 처음 실행되는 메소드.
     */
    fun 방접속시실행(user: User, jin: JsonObject) {
        //세션이 시작되면 먼저 코루틴 하나 실행하고, mutex로 동시접근을 막아 중복 에러를 방지한다.
        // 기존은 이랬지만...
        // 다자간코드에서는 방만들고, 다른 클라가 그 방에 접속시 이 과정을 거치는게 맞을듯.
        sessionManagerScope.launch {
            mutex.withLock {
                //클라이언트 맵의 사이즈가 1개 이상 존재하면,
                // 다자간코드에서는 방장이 만든 방에 접속시, clients.size대신 설정한 인원수를 넣어줌.
                val 방장아이디 = jin["makerId"].asString
                println("방장아이디: $방장아이디")
                //방의 존재유무확인 후 찾아서 그 방에 현재 접속한 유저를 유저리스트에 넣어줌.
                if (rooms.containsKey(방장아이디)){
                    val room = rooms.get(방장아이디)!!
                    //클래스의 멤버 변수에도 찾은 방 객체를 할당해준다.
                    mRoom = room
                    println("방장방: $mRoom")
                    //방에서 설정된 인원수와 현재 그방에 접속한 클라이언트의 수가 같으면 방인원수 초과 메시지를 보내야함.
                    if (room.size == room.users.size){
                        println("방인원수 초과.")
                        sessionManagerScope.launch(NonCancellable) {
                            user.socket.send(Frame.Text(JsonObject().run {
                                addProperty("command", "방접속")
                                addProperty("content", "방인원수 초과.")
                                toString()
                            }))
                        }

                    } else {
                        //방에 접속한 유저를 넣어줌.

                        println("방에 접속한 유저를 넣어줌.")
                        room.users.add(user)
//                        user.socket.send("Added as a client: $user")
                        //0명이면 비디오트랙만들어서 시작하는게 안될려나? 생각해볼문제.
                        //방의 접속유저가 2명이상일때 Ready로 만듦.
                        if (room.users.size > 1) {
                            room.sessionState = WebRTCSessionState.Ready
                        }
                        //현재 'room'의 sessionState를 전달해야함. 이 메소드에 room객체 전달해야할듯?
                        //그리고, 그걸 해당 방에 소속한 peer들에 sessionState를 전해야함.
                        notifyAboutStateUpdate(room)

                        //여기까지 진행되면 최소 2명이 확보되고, Ready상태가 됌. 이때, 클라방내부에서 방장이 OFFER명령을 게시하는 버튼등을
                        //눌러줘야함..(임시로) 아니면, 위의 sessionState값을 Ready말고 바로 방접속해서 2명이상되버리면 Creating으로 바꿔도
                        //될듯?



                        //밑은 (임시로) << 부분대신 수락/요청 시스템 도입시 바뀌는 사항들.
                        //접속 요청하면 방장은 접속요청에 대한 수락리스트에서 수락/거부해야함. 그동안 요청자는 방목록화면에서 초대창을 띄워두고
                        //접속중이라는 대기서클러뷰를 봐야함.취소하기버튼도 있어야함. 취소하기시 소켓으로 취소명령을 보내 방의 수락리스트에서
                        //자신의 아이디로 요청한 요청객체를 없애야함. 그리고, 방장클라에게 취소에 대한 소켓명령을 보내야함. 그러면, 수락리스트에서
                        //제거됨. 수락리스트는 방내에서 따로 리스트를 띄워보여줘야할건데, 컴포즈에서는 어떤 컴포넌트가 있는지 알아봐야함.
                        //방장이 수락을 누르면 방장클라에서 offer명령을 서버로 보냄. offer명령을 보낼때는 json객체에 요청한사람에대한 id등 정보
                        //를 포함해야함. 그리고, 서버에서 offer대상에 대해 찾고, answer명령을 보내면 요청자의 클라에서 받아서 자신의 sdp를 담은
                        //answer를 서버로 보냄. 그렇게 서로 주고받는 중간중간 클라<->서버간 ice명령을 주고받음(둘다에게 보냄)
                    }

                } else {
                    //방이 없으면 취소불가인 코루틴으로 방이 없다는 메시지를 보냄.
                    println("방없음.")
                    sessionManagerScope.launch(NonCancellable) {
                        user.socket.send(Frame.Text(JsonObject().run {
                            addProperty("command", "방접속")
                            addProperty("content", "방없음.")
                            toString()
                        }))
                    }
                    return@launch
                }


//                if (clients.size > 1) { //기존의 코드전용임: 접속한 클라가 2명일때, 다른 클라가 접속하면 소켓연결을 끊어버림.
//                    //이번에는 취소불가능한 또다른 코루틴을 생성하여
//                    sessionManagerScope.launch(NonCancellable) {
//                        //해당 클라이언트에게 논쓰레드세이프한 Frame객체를 전송한다.
//                        //Close()는 웹소켓세션이 닫혔다는 것을 알리는 용도로 전달. Raw 웹세션이 아니라면..
//                        // 보통 Frame은 관리될 필요가 없다고 함.
//                        // 아무튼, close하는 이유는 위에서 맵의 사이즈를 2명으로 제한하고 싶기 때문임.
//                        // 결국 접속 유저를 2명으로 제한하고 싶은 것이고, 다자간 연결을 계층적으로 관리하고 싶다면,
//                        // 저 맵자체를 최상위로하고, 맵의 value로 vector등의 리스트를 이용해 클라이언트의 정보를 담은 객체
//                        //를 넣어주는 방식으로 관리해야함.
//                        user.socket.send(Frame.Close()) // only two peers are supported
//                        //다른 코루틴에서 소켓끊는 작업을 하게두고, 이전 코루틴은 종료(return)해버림.
//                    }
//                    return@launch
//                }


                //소켓을 끊음과 동시에 현재 들어온 클라이언트의 uuid를 키로 값에 소켓 객체를 등록함..
                //이때 clients Map의 키값으로 UUID는 고유
                //다자간 코드에서는 clients말고, rooms의 room객체의 userList에 넣어줘야할듯.
//                clients[sessionId] = user.socket
//                //마찬가지로 room객체의 users 리스트 전부에게 send 해줘야함.
//                session.send("Added as a client: $sessionId")
//                //0명이면 비디오트랙만들어서 시작하는게 안될려나? 생각해볼문제.
//                if (clients.size > 1) {
//                    sessionState = WebRTCSessionState.Ready
//                }
//                //현재 'room'의 sessionState를 전달해야함. 이 메소드에 room객체 전달해야할듯?
//                //그리고, 그걸 해당 방에 소속한 peer들에 sessionState를 전해야함.
//                notifyAboutStateUpdate()
            }
        }
    }


    /**
     * 클라이언트 소켓에서 서버로 전달되어져온 명령 메시지에 따라 해당 작업을 수행함.
     */
    fun onMessage(sessionId: User, message: JsonObject) {
        val signalingCommand = message.get("signalingCommand").asString


        when {
//            signalingCommand.startsWith(MessageType.STATE.toString(), true) -> handleState(sessionId) //안쓰는것

            //OFFER, ANSWER, ICE일 때 message["sdp"]에는 SDP정보를 담은 String이 있음.
            signalingCommand.startsWith(MessageType.OFFER.toString(), true) -> handleOffer(sessionId, message)
            signalingCommand.startsWith(MessageType.ANSWER.toString(), true) -> handleAnswer(sessionId, message)

            //ICE 관련된 메시지는 중간 중간 옴
            signalingCommand.startsWith(MessageType.ICE.toString(), true) -> handleIce(sessionId, message)
        }
    }

    /**
     * STATE 상태를 전달하기 하지만, 어떤 전달할 명령을 담고 있진 않음.
     * 대신, Impossible 이라는 상태를 보냄. 클라이언트에게 아직 2명이상의 연결을 할 상황이 안된다는 것을 알려주는 것임.
     */
//    private fun handleState(sessionId: User) {
//        val jOut = JsonObject()
//        jOut.addProperty("command", "signalingCommand")
//        jOut.addProperty("signalingCommand", "${MessageType.STATE}")
////        jOut.addProperty("sessionState", "$sessionState")
//        sessionManagerScope.launch {
//            println("현재 MessageType.STATE의 상태값: ${MessageType.STATE}, sessionId: $sessionId")
////            clients[sessionId]?.send("${MessageType.STATE} $sessionState")
//            sessionId.socket.send("$jOut")
//        }
//    }


    /**
     * 해당 peer로부터 OFFER 메시지를 받으면 진행.
     */
    private fun handleOffer(sessionId: User, message: JsonObject) {
        val sdp = message["sdp"].asString

        val jOut = JsonObject()
        jOut.addProperty("command", "signalingCommand")
        jOut.addProperty("signalingCommand", "${MessageType.STATE}")
        jOut.addProperty("sessionState", "${mRoom.sessionState}")

        //현재는 2인만으로 서버에서 제한되있기 때문에 이런 조건이 걸렸다.
        // 차후에는 sessionState 값자체를 하나의 전체그룹 객체에 담아서 검사해야할것 같다.
        // 생각 - 전체그룹객체(Map이 좋을듯)에 들어갈 값(내용):
        //          단일그룹 클래스 - sessionState변수 , 그룹내 peer소켓을 담은 리스트

        //      처음 단일그룹을 만들때는 uuid+방만든사람id를 이용해 key값으로 정함.
        //      그리고 단일그룹 객체내 sessionState객체의 상태값을 대기중으로 바꾸고 방을 유지함.
        if (mRoom.sessionState != WebRTCSessionState.Ready) {
            error("Session should be in Ready state to handle offer")
        }
        mRoom.sessionState = WebRTCSessionState.Creating
        println("$sessionId 로부터 OFFER 메시지옴. 상태값으로 WebRTCSessionState.Creating 전달. message: $message")
        //서버 clients맵에 속한 소켓 전부에게 바뀐 상태를 전달함.
        notifyAboutStateUpdate(mRoom)
        // OFFER보낸 클라이언트(sessionID)가 아닌 클라들을 clients Map내에서 찾고,
        // 그 모움을 새로운 맵으로 생성함. 그리고, 그 맵의 가장 처음 pair를 가져옴.
//        val clientToSendOffer = clients.filterKeys { it != sessionId }.values.first()
        //filterKeys로 반환된 뉴맵은 기존 맵의 iterator(반복순서)를 유지함.
        // (현재 2인..연결제한이라 이런식의 로직을 이용함.)
        // OFFER보낸 클라이언트 외에 남는 클라이언트는 first()로 가져온 클라이언트만 남음.
        // 그 클라이언트 소켓에 'OFFER 보낸 클라이언트'가 동봉한 SDP 문자열 정보를 그대로 전달함.


        //새코드: Offer보낸 클라를 제외한 방의 모든 클라에 Offer보낸 클라의 sdp를 전달.
        //todo 의문: 이렇게 모든 클라에 전달하면, 각 클라에 spd가 인원수 제곱으로 전달되고,
        // 그에따라 addIceCandidate()메소드가 너무 많이 실행되지 않나? 그러면 문제 생기지 않을까??
        mRoom.users.filter { it.id != sessionId.id }.forEach {
            it.socket.send(sdp)
        }
    }

    /**
     * Answer 보낸 클라이언트의 sdp를 타 클라이언트에 전달.
     */
    private fun handleAnswer(sessionId: User, message: JsonObject) {
        val sdp = message["sdp"].asString

        if (mRoom.sessionState != WebRTCSessionState.Creating) {
            error("Session should be in Creating state to handle answer")
        }
        println("$sessionId 로부터 ANSWER 메시지옴. message: $message")

        //새코드: Answer보낸 클라를 제외한 방의 모든 클라에 Answer보낸 클라의 sdp를 전달.
        mRoom.users.filter { it.id != sessionId.id }.forEach {
            it.socket.send(sdp)
        }

        mRoom.sessionState = WebRTCSessionState.Active
        notifyAboutStateUpdate(mRoom)
    }

    /**
     * ICE command에 대해서는 sessionState 값의 변화가 없음.
     *
     * 서버에서는 ICE 관련 메시지를 클라이언트들에게 알려만 줌.
     */
    private fun handleIce(sessionId: User, message: JsonObject) {
        val sdp = message["sdp"].asString

        println("$sessionId 로부터 ICE 관련 메시지 옴 message: $message")

        //새코드: 각 ICE 과정(OFFER, ANSWER)에서 실행되는 onIcecandidate()의 실행결과 웹소켓을 통해
        //이곳까지 전달됨.
        mRoom.users.filter { it.id != sessionId.id }.forEach {
            it.socket.send(sdp)
        }
    }

    fun onSessionClose(sessionId: String) {
        sessionManagerScope.launch {
            mutex.withLock {
                clients.remove(sessionId)
                mRoom.sessionState = WebRTCSessionState.Impossible
                notifyAboutStateUpdate(mRoom)
            }
        }
    }




    /**
     * 클라이언트 맵에 있는 소켓으로 연결된 각 클라에게 메시지 타입으로 STATE 에 해당하는 상태 값을 전달해줌.
     * 예로, 맨처음에는 onSessionStarted()가 시작되어 STATE Impossible 을 보내지만,
     * 2명이 되자마자 STATE Ready 를 현재 소켓 맵에 연결되어 있는 '모든 클라이언트'(아직2명)에게 보낸다.
     */
//    private fun notifyAboutStateUpdate() {
//        val jOut = JsonObject()
//        jOut.addProperty("command", "signalingCommand")
//        jOut.addProperty("signalingCommand", "${MessageType.STATE}")
//        jOut.addProperty("sessionState", "$sessionState")
//        clients.forEach { (_, client) ->
//            client.send("$jOut")
//        }
//    }

    private fun notifyAboutStateUpdate(room: Room) {
        val jOut = JsonObject()
        jOut.addProperty("command", "signalingCommand")
        jOut.addProperty("signalingCommand", "${MessageType.STATE}")
        jOut.addProperty("sessionState", "${room.sessionState}")
        room.users.forEach {
            it.socket.send("$jOut")
        }
    }

    private fun DefaultWebSocketServerSession.send(message: String) {
        sessionManagerScope.launch {
            this@send.send(Frame.Text(message))
        }
    }

    fun 클라존재유무검사(jin: JsonObject, socket: DefaultWebSocketServerSession) :User {
        println("[클라존재유무검사] jin: $jin")
        val user_email = jin["id"].asString
        if (clients.containsKey(user_email)) {
            val user = User(
                id = user_email,
                nick = jin["nick"].asString,
                groupId = jin["groupId"].asString,
                socket = socket
            )
            clients[user_email] = user
            //여기 방목록을 클라에 보내는 로직있어야함.
//            방목록전달(user)
            return user

        } else {
            // 같은 아이디 없음. 추가함.
            // 굳이 else로 나눈이유: 차후 강종되거나 끊킬때 clients에서 접속되어있는 User객체를 삭제시킬 로직추가대비.
            val user = User(
                id = user_email,
                nick = jin["nick"].asString,
                groupId = jin["groupId"].asString,
                socket = socket
            )
            clients[user_email] = user

            방목록전달(user)
            return user
        }
    }

    private fun 방목록전달(user: User) {
        //방목록에서 user의 모임아이디와 같은 방들을 뽑음.
        val filteredRoomL = rooms.filter {
            it.value.groupId == user.groupId
        }/*.run {
               val jo = JsonObject()
               val jarry = JsonArray()
               forEach{
                   it.value.
               }
           }*/
        //  뽑은 방목록을 json스트링으로 바꿈
        val jo = Gson().toJson(filteredRoomL)
        val jOut = JsonObject().apply {
            addProperty("command", "방목록전달")
            addProperty("roomList", jo)
        }
        sessionManagerScope.launch {
            try {
                // user에 뽑은 방목록을 전달.
                mutex.withLock {
                    user.socket.send(Frame.Text(jOut.toString()))

                }

//                user.socket.send(Frame.Close())
            }catch (e: Exception){
                println(e)
            }
        }
//                user.socket.send(Frame.Text("df"))

//            user.socket.send(jOut.toString())

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
