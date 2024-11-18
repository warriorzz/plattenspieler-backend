package ee.bjarn.plattenspieler.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import ee.bjarn.plattenspieler.config.Config
import ee.bjarn.plattenspieler.controller.SpotifyController
import ee.bjarn.plattenspieler.database.Plattenspieler
import ee.bjarn.plattenspieler.database.Record
import ee.bjarn.plattenspieler.database.Repositories
import ee.bjarn.plattenspieler.database.User
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.server.websocket.webSocket
import kotlinx.serialization.Serializable
import org.litote.kmongo.eq
import java.nio.file.Files
import java.nio.file.Path
import java.util.Date
import java.util.UUID

fun Application.configureRouting() {

    routing {
        route("/api") {
            post("/login") {
                val request = call.receive<AuthenticationRequest>()
                val user = Repositories.users.findOne(User::name eq request.user)
                if (user == null) {
                    call.respondText("Authentication failed", status = HttpStatusCode.Unauthorized)
                    return@post
                }

                val result = PasswordManager.verify(user, request.password)
                if (result?.verified != true) {
                    call.respondText("Authentication failed", status = HttpStatusCode.Unauthorized)
                    return@post
                }

                val token = JWT.create()
                    .withAudience(Config.JWT_AUDIENCE)
                    .withIssuer(Config.JWT_ISSUER)
                    .withClaim("user", user.name)
                    .withClaim("admin", user.isAdmin)
                    .withExpiresAt(Date(System.currentTimeMillis() + 10000 * 60 * 60 * 24 * 30))
                    .sign(Algorithm.HMAC256(Config.JWT_SECRET))
                call.respondText(token)
            }

            post("/create") {
                val request = call.receive<CreateAccountRequest>()

                if (request.code != Config.STATIC_AUTH_SECRET) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                val success = PasswordManager.addUser(request.name, request.password)

                if (success) {
                    call.respond(HttpStatusCode.Created)
                } else {
                    call.respond(HttpStatusCode.BadRequest)
                }
            }

            authenticate("jwt") {
                route("/user") {
                    route("/account") {
                        get("/") {
                            val name = call.principal<JWTPrincipal>()?.get("user") ?: return@get
                            val user = Repositories.users.findOne(User::name eq name) ?: return@get
                            call.respond(UserResponse(user.name, user.picture ?: "", user.ktify != null, user.isAdmin, user.deviceId ?: ""))
                        }

                        get("/devices") {
                            val name = call.principal<JWTPrincipal>()?.get("user") ?: return@get
                            val user = Repositories.users.findOne(User::name eq name) ?: return@get
                            val devices = SpotifyController.getDevices(user)
                            if (devices == null) {
                                call.respond(HttpStatusCode.BadRequest)
                                return@get
                            }

                            call.respond(DevicesResponse(devices.map { DeviceR(it.name, it.id, it.type) }))
                        }

                        post("/update") {
                            // update information - name
                        }

                        post("/password") {
                            // update password
                        }

                        post("/connect") {
                            val principal = call.principal<JWTPrincipal>() ?: return@post
                            val url = SpotifyController.getAuthorizationURL(
                                principal.getClaim("user", String::class) ?: return@post
                            )
                            call.respondText(url)
                        }
                    }

                    webSocket("/socket") {
                        // currently playing etc
                    }
                }
                route("/content") {
                    post("/create") {
                        // create record
                        val request = call.receive<CreateRecordRequest>()
                        val principal = call.principal<JWTPrincipal>()
                        val user = Repositories.users.findOne(
                            (User::name eq principal?.getClaim("user", String::class))
                        ) ?: return@post
                        val track = SpotifyController.getTrack(user, request.track) ?: return@post

                        val found = Repositories.records.findOne(Record::chipId eq request.id)
                        if (found != null) {
                            call.respond(HttpStatusCode.Conflict)
                            return@post
                        }

                        val record = Record(request.id, track.id, track.album?.images[0]?.url)
                        Repositories.records.insertOne(record)
                        call.respond(HttpStatusCode.Accepted)
                    }

                    post("/modify") {
                        // modify record
                    }

                    get("/information") {
                        // information about (multiple) plattenspieler
                    }

                    post("/register") {
                        // register plattenspieler
                        val request = call.receive<RegisterPlattenspielerRequest>()
                        val principal = call.principal<JWTPrincipal>() ?: return@post

                        val user = Repositories.users.findOne(User::name eq principal.getClaim("user", String::class))
                            ?: return@post
                        val plattenspieler = Plattenspieler(UUID.randomUUID().toString(), request.auth, user.userid)
                        Repositories.plattenspieler.insertOne(plattenspieler)
                        call.respond(HttpStatusCode.Accepted)
                    }
                }
            }

            authenticate("plattenspieler") {
                route("/plattenspieler") {
                    post("") {
                        val update = call.receive<PlattenspielerUpdate>()

                        val plattenspieler = Repositories.plattenspieler.findOne(Plattenspieler::secret eq update.auth)
                        if (update.pause) {
                            SpotifyController.pausePlayback(plattenspieler?.user ?: return@post)
                        } else {
                            SpotifyController.playTrack(plattenspieler?.user ?: return@post, update.id)
                        }
                        Repositories.plattenspieler.updateOne(Plattenspieler::pid eq plattenspieler.pid, Plattenspieler::lastActive eq System.currentTimeMillis())
                    }

                    post("/update") {
                        val request = call.receive<PlattenspielerRequestUpdate>()
                        val plattenspieler = Repositories.plattenspieler.findOne(Plattenspieler::secret eq request.auth) ?: return@post

                        val text = Files.readString(Path.of(Config.PATH_TO_PLATTENSPIELER_SCRIPT), Charsets.UTF_8)
                        call.respond(text)

                        Repositories.plattenspieler.updateOne(Plattenspieler::pid eq plattenspieler.pid, Plattenspieler::lastActive eq System.currentTimeMillis())
                    }
                }
            }

            route("/callback") {
                get("/spotify") {
                    val error = call.queryParameters["error"]
                    if (error != null) {
                        call.respondText("Error: $error", status = HttpStatusCode.BadRequest)
                        return@get
                    }

                    val state = call.queryParameters["state"]
                    val code = call.queryParameters["code"]

                    if (state == null || code == null) {
                        call.respondText("Error. Please try again.", status = HttpStatusCode.BadRequest)
                        return@get
                    }

                    SpotifyController.callbackConnect(code, state)
                    call.respondRedirect(Config.FRONTEND_REDIRECT_URL)
                }

            }
        }
    }
}

@Serializable
data class AuthenticationRequest(val user: String, val password: String)

@Serializable
data class PlattenspielerUpdate(val auth: String, val id: Long? = null, val pause: Boolean = false)

@Serializable
data class PlattenspielerRequestUpdate(val auth: String)

@Serializable
data class DevicesResponse(val devices: List<DeviceR>)

@Serializable
data class DeviceR(val name: String, val id: String, val type: String)

@Serializable
data class UserResponse(val name: String, val picture: String, val spotify: Boolean, val admin: Boolean, val device: String)

@Serializable
data class CreateAccountRequest(val name: String, val password: String, val code: String)

@Serializable
data class CreateRecordRequest(val id: Long, val track: String)

@Serializable
data class RegisterPlattenspielerRequest(val auth: String)