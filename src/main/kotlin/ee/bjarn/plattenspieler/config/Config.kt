package ee.bjarn.plattenspieler.config

import io.github.cdimascio.dotenv.dotenv

object Config {
    val dotenv = dotenv()

    val MONGO_CONNECTION_STRING = get("MONGO_CONNECTION_STRING")
    val MONGO_DATABASE = get("MONGO_DATABASE")
    val JWT_AUDIENCE = get("JWT_AUDIENCE")
    val JWT_SECRET = get("JWT_SECRET")
    val JWT_REALM = get("JWT_REALM")
    val JWT_ISSUER = get("JWT_ISSUER")
    val SPOTIFY_CLIENT_ID = get("SPOTIFY_CLIENT_ID")
    val SPOTIFY_CLIENT_SECRET = get("SPOTIFY_CLIENT_SECRET")
    val SPOTIFY_REDIRECT_URI = get("SPOTIFY_REDIRECT_URI")
    val FRONTEND_REDIRECT_URL = get("FRONTEND_REDIRECT_URL")
    val STATIC_AUTH_SECRET = get("STATIC_AUTH_SECRET")
    val PATH_TO_PLATTENSPIELER_SCRIPT = get("PATH_TO_PLATTENSPIELER_SCRIPT")

    private fun get(key: String): String {
        if (System.getProperty(key) != null) {
            return System.getProperty(key)
        }
        return dotenv.get(key)
    }
}
