package ee.bjarn.plattenspieler.plugins

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import ee.bjarn.plattenspieler.config.Config
import ee.bjarn.plattenspieler.database.Plattenspieler
import ee.bjarn.plattenspieler.database.Repositories
import ee.bjarn.plattenspieler.database.User
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.respondText
import org.litote.kmongo.eq
import java.util.UUID

fun Application.configureSecurity() {
    val jwtAudience = Config.JWT_AUDIENCE
    val jwtIssuer = Config.JWT_ISSUER
    val jwtRealm = Config.JWT_REALM
    val jwtSecret = Config.JWT_SECRET
    authentication {
        jwt("jwt") {
            realm = jwtRealm
            verifier(
                    JWT.require(Algorithm.HMAC256(jwtSecret))
                            .withAudience(jwtAudience)
                            .withIssuer(jwtIssuer)
                            .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(jwtAudience) &&
                                credential.payload.getClaim("user").asString() != ""
                )
                        JWTPrincipal(credential.payload)
                else null
            }
        }

        jwt("jwt-admin") {
            realm = jwtRealm
            verifier(
                    JWT.require(Algorithm.HMAC256(jwtSecret))
                            .withAudience(jwtAudience)
                            .withIssuer(jwtIssuer)
                            .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(jwtAudience) &&
                                credential.payload.getClaim("user").asString() != "" &&
                                credential.payload.getClaim("admin").asBoolean()
                )
                        JWTPrincipal(credential.payload)
                else null
            }
        }

        staticsecret("plattenspieler") {}
    }
}

object PasswordManager {

    private const val SALT_ROUNDS = 12

    suspend fun addUser(name: String, password: String): Boolean {
        val found = Repositories.users.findOne(User::name eq name)
        if (found != null) return false

        val user =
                User(UUID.randomUUID().toString(), name, BCrypt.withDefaults().hash(SALT_ROUNDS, password.toCharArray()))
        return Repositories.users.insertOne(user).wasAcknowledged()
    }

    fun verify(user: User, password: String): BCrypt.Result? {
        return BCrypt.verifyer().verify(password.toCharArray(), user.password)
    }
}

fun AuthenticationConfig.staticsecret(
        name: String? = null,
        configure: StaticAuthSecretProvider.Config.() -> Unit
) {
    val provider = StaticAuthSecretProvider.Config(name).apply(configure).build()
    register(provider)
}

class StaticAuthSecretProvider internal constructor(config: Config) :
        AuthenticationProvider(config) {

    class Config internal constructor(name: String?) : AuthenticationProvider.Config(name) {
        internal fun build() = StaticAuthSecretProvider(this)
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val authString = context.call.parameters["auth"]

        val cause: AuthenticationFailedCause? = if (authString == null) {
            AuthenticationFailedCause.Error("")
        } else {
            val plattenspieler = Repositories.plattenspieler.findOne(Plattenspieler::secret eq authString)
            if (plattenspieler == null) {
                AuthenticationFailedCause.InvalidCredentials
            } else {
                null
            }
        }

        if (cause != null) {
            context.call.respondText("error.")
        }
    }
}
