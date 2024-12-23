package ee.bjarn.plattenspieler.database

import ee.bjarn.ktify.model.auth.ClientCredentials
import ee.bjarn.plattenspieler.config.Config
import kotlinx.serialization.Serializable
import org.litote.kmongo.Id
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.newId
import org.litote.kmongo.reactivestreams.KMongo
import java.util.UUID

object Repositories {
    private val client = KMongo.createClient(Config.MONGO_CONNECTION_STRING).coroutine
    private val database = client.getDatabase(Config.MONGO_DATABASE)
    val records = database.getCollection<Record>()
    val plattenspieler = database.getCollection<Plattenspieler>()
    val users = database.getCollection<User>()
}

@Serializable
data class Record(val chipId: Long, val trackId: String, val user: String, val image: String? = null, val title: String? = null)

@Serializable
data class Plattenspieler(val pid: String, val secret: String, val user: String, val lastActive: Long = -1, val ssid: String? = null, val password: String? = null)

@Serializable
data class User(
    val userid: String = UUID.randomUUID().toString(),
    val name: String,
    val password: ByteArray,
    val picture: String? = null,
    val isAdmin: Boolean = true,
    var ktify: ClientCredentials? = null,
    val deviceId: String? = null
)
