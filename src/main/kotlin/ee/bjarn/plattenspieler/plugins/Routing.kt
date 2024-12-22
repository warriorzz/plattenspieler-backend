package ee.bjarn.plattenspieler.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import ee.bjarn.plattenspieler.config.Config
import ee.bjarn.plattenspieler.controller.SpotifyController
import ee.bjarn.plattenspieler.database.Plattenspieler
import ee.bjarn.plattenspieler.database.Repositories
import ee.bjarn.plattenspieler.database.User
import ee.bjarn.plattenspieler.logger
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.Serializable
import org.litote.kmongo.eq
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

fun Application.configureRouting() {
    logger.info("Configuring routing...")

    routing {
        route("/api") {
            post("/login") {
                logger.info("Requested /api/login")
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
                    .withClaim("user", user.userid)
                    .withClaim("admin", user.isAdmin)
                    .withExpiresAt(Date(System.currentTimeMillis() + 100000 * 60 * 60 * 24 * 30))
                    .sign(Algorithm.HMAC256(Config.JWT_SECRET))
                call.respondText(token)
            }

            post("/create") {
                logger.info("Requested /api/create")
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
                            logger.info("Requested /user/account/")
                            val name = call.principal<JWTPrincipal>()?.get("user") ?: return@get
                            val user = Repositories.users.findOne(User::userid eq name) ?: return@get
                            call.respond(UserResponse(user.name, user.picture ?: "", user.ktify != null, user.isAdmin, user.deviceId ?: ""))
                        }

                        get("/devices") {
                            logger.info("Requested /user/account/devices")
                            val name = call.principal<JWTPrincipal>()?.get("user") ?: return@get
                            val user = Repositories.users.findOne(User::userid eq name) ?: return@get
                            val devices = SpotifyController.getDevices(user)
                            if (devices == null) {
                                call.respond(HttpStatusCode.BadRequest)
                                return@get
                            }

                            call.respond(DevicesResponse(devices.map { DeviceR(it.name, it.id, it.type) }, user.deviceId ?: ""))
                        }

                        post("/device") {
                            logger.info("Requested /user/account/device")
                            val name = call.principal<JWTPrincipal>()?.get("user") ?: return@post
                            val user = Repositories.users.findOne(User::userid eq name) ?: return@post

                            val request = call.receive<ChangeDeviceRequest>()
                            val updated = User(user.userid, user.name, user.password, user.picture, user.isAdmin, user.ktify, request.device)
                            Repositories.users.updateOne(User::userid eq user.userid, updated)
                            call.respond(HttpStatusCode.Accepted)
                        }

                        post("/update") {
                            logger.info("Requested /user/account/update")
                            // update information - name
                        }

                        post("/password") {
                            logger.info("Requested /user/account/password")
                            // update password
                        }

                        post("/connect") {
                            logger.info("Requested /user/account/connect")
                            val principal = call.principal<JWTPrincipal>() ?: return@post
                            val url = SpotifyController.getAuthorizationURL(
                                principal.getClaim("user", String::class) ?: return@post
                            )
                            call.respondText(url)
                        }
                    }

                    webSocket("/socket") {
                        logger.info("Requested /user/account/socket")
                        // currently playing etc
                    }
                }
                route("/content") {
                    post("/create") {
                        logger.info("Requested /content/create")
                        // create record
                        val request = call.receive<CreateRecordRequest>()
                        val principal = call.principal<JWTPrincipal>()
                        val user = Repositories.users.findOne(
                            (User::userid eq principal?.getClaim("user", String::class))
                        ) ?: return@post
                        val track = SpotifyController.getTrack(user, request.track) ?: return@post

                        SpotifyController.addCreate(user, track)
                        call.respond(HttpStatusCode.Accepted)
                    }

                    post("/modify") {
                        logger.info("Requested /content/modify")
                        // modify record
                    }

                    get("/info") {
                        logger.info("Requested /content/info")
                        // information about (multiple) plattenspieler
                        val principal = call.principal<JWTPrincipal>() ?: return@get

                        val user = Repositories.users.findOne(User::userid eq principal.getClaim("user", String::class))
                            ?: return@get

                        val plattenspieler = Repositories.plattenspieler.find(Plattenspieler::user eq user.userid).toList()
                        val id = call.queryParameters["id"]
                        if (id == null) {
                            call.respond(MultiplePlattenspielerResponse(plattenspieler.map { it -> SinglePlattenspielerResponse(it.pid, it.lastActive) }))
                        } else {
                            call.respond(plattenspieler.firstOrNull { it.pid == id }.let { if (it == null) SinglePlattenspielerResponse("", -1) else SinglePlattenspielerResponse(it.pid, it.lastActive) })
                        }
                    }

                    post("/wifi") {
                        logger.info("Requested /content/wifi")
                        val principal = call.principal<JWTPrincipal>() ?: return@post

                        val user = Repositories.users.findOne(User::userid eq principal.getClaim("user", String::class))
                        if (user == null) {
                            call.respond(HttpStatusCode.Forbidden)
                            return@post
                        }
                        val request = call.receive<PlattenspielerWifiRequest>()
                        val plattenspieler = Repositories.plattenspieler.findOne(Plattenspieler::pid eq request.pid)
                        if (plattenspieler == null) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@post
                        }

                        if (plattenspieler.user != user.userid) {
                            call.respond(HttpStatusCode.Forbidden)
                            return@post
                        }

                        val updated = Plattenspieler(plattenspieler.pid, plattenspieler.secret, plattenspieler.user, plattenspieler.lastActive, request.ssid, request.password)
                        Repositories.plattenspieler.updateOne(Plattenspieler::pid eq request.pid, updated)
                        call.respond(HttpStatusCode.Accepted)
                    }

                    post("/register") {
                        logger.info("Requested /content/register")
                        // register plattenspieler
                        val principal = call.principal<JWTPrincipal>() ?: return@post

                        val user = Repositories.users.findOne(User::userid eq principal.getClaim("user", String::class))
                            ?: return@post

                        val request = call.receive<RegisterPlattenspielerRequest>()
                        val found = Repositories.plattenspieler.find(Plattenspieler::secret eq request.auth)
                        if (found.first() != null) {
                            call.respond(HttpStatusCode.Conflict)
                            return@post
                        }

                        val plattenspieler = Plattenspieler(UUID.randomUUID().toString(), request.auth, user.userid)
                        Repositories.plattenspieler.insertOne(plattenspieler)
                        call.respond(HttpStatusCode.Accepted)
                    }
                }
            }

            authenticate("plattenspieler") {
                route("/plattenspieler") {
                    post("/") {
                        logger.info("Requested /plattenspieler/")
                        val update = call.receive<PlattenspielerUpdate>()

                        val plattenspieler = Repositories.plattenspieler.findOne(Plattenspieler::secret eq update.auth)
                        if (update.pause) {
                            SpotifyController.pausePlayback(plattenspieler?.user ?: return@post)
                        } else {
                            SpotifyController.playTrack(plattenspieler?.user ?: return@post, update.id)
                        }
                        val updated = Plattenspieler(plattenspieler.pid, plattenspieler.secret, plattenspieler.user,
                            System.currentTimeMillis(), plattenspieler.ssid, plattenspieler.password)
                        Repositories.plattenspieler.updateOne(Plattenspieler::pid eq plattenspieler.pid, updated)

                        call.respond(HttpStatusCode.Accepted)
                    }

                    post("/update") {
                        logger.info("Requested /plattenspieler/update")
                        val request = call.receive<PlattenspielerRequestUpdate>()
                        val plattenspieler = Repositories.plattenspieler.findOne(Plattenspieler::secret eq request.auth)
                        if (plattenspieler == null) {
                            call.respond(HttpStatusCode.NotFound)
                            return@post
                        }

                        if (request.version == Config.PLATTENSPIELER_SCRIPT_VERSION) {
                            call.respond("")
                            return@post
                        }

                        val text = Files.readString(Path.of(Config.PATH_TO_PLATTENSPIELER_SCRIPT), Charsets.UTF_8)
                        call.respond(Config.PLATTENSPIELER_SCRIPT_VERSION + "/=====/" + text)

                        val updated = Plattenspieler(plattenspieler.pid, plattenspieler.secret, plattenspieler.user,
                            System.currentTimeMillis(), plattenspieler.ssid, plattenspieler.password)
                        Repositories.plattenspieler.updateOne(Plattenspieler::pid eq plattenspieler.pid, updated)
                    }

                    post("/ssid") {
                        logger.info("Requested /plattenspieler/ssid")
                        val request = call.receive<PlattenspielerAuthRequest>()
                        val plattenspieler = Repositories.plattenspieler.findOne(Plattenspieler::secret eq request.auth) ?: return@post

                        call.respond(plattenspieler.ssid ?: "")
                    }

                    post("/password") {
                        logger.info("Requested /plattenspieler/password")
                        val request = call.receive<PlattenspielerAuthRequest>()
                        val plattenspieler = Repositories.plattenspieler.findOne(Plattenspieler::secret eq request.auth) ?: return@post

                        call.respond(plattenspieler.password ?: "")
                    }
                }
            }

            route("/callback") {
                get("/spotify") {
                    logger.info("Requested /callback/spotify")
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
data class PlattenspielerRequestUpdate(val auth: String, val version: String)

@Serializable
data class PlattenspielerAuthRequest(val auth: String)

@Serializable
data class PlattenspielerWifiRequest(val ssid: String, val password: String, val pid: String)

@Serializable
data class DevicesResponse(val devices: List<DeviceR>, val selected: String)

@Serializable
data class DeviceR(val name: String, val id: String, val type: String)

@Serializable
data class UserResponse(val name: String, val picture: String, val spotify: Boolean, val admin: Boolean, val device: String)

@Serializable
data class CreateAccountRequest(val name: String, val password: String, val code: String)

@Serializable
data class CreateRecordRequest(val track: String)

@Serializable
data class RegisterPlattenspielerRequest(val auth: String)

@Serializable
data class SinglePlattenspielerResponse(val id: String, val lastActive: Long)

@Serializable
data class MultiplePlattenspielerResponse(val plattenspieler: List<SinglePlattenspielerResponse>)

@Serializable
data class ChangeDeviceRequest(val device: String)