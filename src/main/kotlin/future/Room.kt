import future.WebRTCSessionState
import io.ktor.server.websocket.*
import java.util.*

data class Room(
    var roomId: String = "",
    var groupId: String = "",
    var title: String = "방제목",
    var size: Int = 4,
    var pwd: String = "",
    var sessionState: WebRTCSessionState = WebRTCSessionState.Impossible,
//    var sessionState: SessionManager.WebRTCSessionState = SessionManager.WebRTCSessionState.Impossible,
    var users: Vector<User> = Vector<User>()
) {

}

data class User(
    var id: String = "",
    var nick: String ="",
    var groupId: String = "",
    var socket: DefaultWebSocketServerSession,
    var privilege: String = ""
){

}