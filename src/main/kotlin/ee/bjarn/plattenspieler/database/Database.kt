package ee.bjarn.plattenspieler.database

import ee.bjarn.ktify.model.auth.ClientCredentials
import ee.bjarn.plattenspieler.config.Config
import kotlinx.serialization.Serializable
import org.litote.kmongo.Id
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.newId
import org.litote.kmongo.reactivestreams.KMongo

object Repositories {
    private val client = KMongo.createClient(Config.MONGO_CONNECTION_STRING).coroutine
    private val database = client.getDatabase(Config.MONGO_DATABASE)
    val records = database.getCollection<Record>()
    val plattenspieler = database.getCollection<Plattenspieler>()
    val users = database.getCollection<User>()
}

@Serializable
data class Record(val id: Id<String>, val trackId: String, val image: String? = null)

@Serializable
data class Plattenspieler(val id: Id<Plattenspieler>, val secret: String, val user: Id<User>)

@Serializable
data class User(
    val id: Id<User> = newId(),
    val name: String,
    val password: ByteArray,
    val picture: String? = null,
    val isAdmin: Boolean = true,
    var ktify: ClientCredentials? = null,
    val deviceId: String? = null
)
