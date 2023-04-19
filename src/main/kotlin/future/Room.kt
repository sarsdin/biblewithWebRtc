package future

import com.google.gson.JsonArray
import com.google.gson.JsonObject
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
    var users: Vector<User> = Vector<User>(),
    var requestL: Vector<User> = Vector<User>()
) {
    fun getJsonRequestL(): JsonArray {
        val tmp = JsonArray()
        requestL.forEach {
            tmp.add(
                JsonObject().apply {
                    addProperty("userId", it.id)
                    addProperty("groupId", it.groupId)
                    addProperty("nick", it.nick)
                    addProperty("privilege", it.privilege)
                }
            )
        }
        println("getJsonRequestL(): $tmp")
        return tmp
    }

}

data class User(
    var id: String = "",
    var nick: String ="",
    var groupId: String = "",
    var socket: DefaultWebSocketServerSession,
    var privilege: String = ""
){

}