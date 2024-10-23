package ee.bjarn.plattenspieler.config

import io.github.cdimascio.dotenv.dotenv

object Config {
    val dotenv = dotenv()

    val MONGO_CONNECTION_STRING = dotenv.get("MONGO_CONNECTION_STRING")
    val MONGO_DATABASE = dotenv.get("MONGO_DATABASE")
    val JWT_AUDIENCE = dotenv.get("JWT_AUDIENCE")
    val JWT_SECRET = dotenv.get("JWT_SECRET")
    val JWT_REALM = dotenv.get("JWT_REALM")
    val JWT_DOMAIN = dotenv.get("JWT_DOMAIN")
    val JWT_ISSUER = dotenv.get("JWT_ISSUER")
    val SPOTIFY_CLIENT_ID = dotenv.get("SPOTIFY_CLIENT_ID")
    val SPOTIFY_CLIENT_SECRET = dotenv.get("SPOTIFY_CLIENT_SECRET")
    val SPOTIFY_REDIRECT_URI = dotenv.get("SPOTIFY_REDIRECT_URI")
    val FRONTEND_REDIRECT_URL = dotenv.get("FRONTEND_REDIRECT_URL")
    val STATIC_AUTH_SECRET = dotenv.get("STATIC_AUTH_SECRET")
}
