package ee.bjarn.plattenspieler.controller

import ee.bjarn.ktify.Ktify
import ee.bjarn.ktify.KtifyBuilder
import ee.bjarn.ktify.model.Track
import ee.bjarn.ktify.model.auth.Scope
import ee.bjarn.ktify.model.player.CurrentPlayingTrack
import ee.bjarn.ktify.model.player.Device
import ee.bjarn.ktify.tracks.getTrack
import ee.bjarn.plattenspieler.config.Config
import ee.bjarn.plattenspieler.database.Record
import ee.bjarn.plattenspieler.database.Repositories
import ee.bjarn.plattenspieler.database.User
import org.litote.kmongo.eq

object SpotifyController {

    private val builder =
        KtifyBuilder(Config.SPOTIFY_CLIENT_ID, Config.SPOTIFY_CLIENT_SECRET, Config.SPOTIFY_REDIRECT_URI)

    private val connecting = HashMap<String, Pair<KtifyBuilder, String>>()

    fun getAuthorizationURL(user: String): String {
        val builder: KtifyBuilder;
        if (connecting.contains(user)) {
            builder = connecting.get(user)?.first ?: return ""
        } else {
            builder = KtifyBuilder(Config.SPOTIFY_CLIENT_ID, Config.SPOTIFY_CLIENT_SECRET, Config.SPOTIFY_REDIRECT_URI)
        }
        val url = builder.getAuthorisationURL(listOf(Scope.USER_READ_PLAYBACK_STATE, Scope.USER_MODIFY_PLAYBACK_STATE, Scope.USER_READ_PRIVATE))
        val state = url.split("state=")[1].split("&")[0]
        connecting.put(user, (builder to state))
        return url
    }

    suspend fun callbackConnect(code: String, state: String) {
        val entry = connecting.entries.firstOrNull { it.value.second == state } ?: return
        val user = Repositories.users.findOne(User::name eq entry.key)
        if (user == null) return
        val ktify = entry.value.first.build(code)
        user.ktify = ktify.getClientCredentials()
        Repositories.users.updateOne(User::name eq entry.key, user)
    }

    suspend fun getTrack(user: User, trackId: String): Track? {
        val ktify = getKtify(user) ?: return null
        return ktify.getTrack(trackId)
    }

    suspend fun playTrack(id: String, recordId: Long?): Boolean {
        val user = Repositories.users.findOne(User::userid eq id)
        val ktify = getKtify(id) ?: return false

        val track = Repositories.records.findOne(Record::chipId eq recordId)?.trackId ?: return false
        var success = true
        success = success && ktify.player.addItemToQueue(ktify.getTrack(track), null).value < 300
        success = success && ktify.player.skipToNextTrack().value < 300
        success = success && ktify.player.startPlayback(user?.deviceId).value < 300
        return success
    }

    suspend fun getDevices(user: User): List<Device>? {
        val ktify = getKtify(user) ?: return null
        return ktify.player.getAvailableDevices().devices
    }

    suspend fun pausePlayback(id: String): Boolean {
        val ktify = getKtify(id) ?: return false
        return ktify.player.pausePlayback().value < 300
    }

    suspend fun getCurrentPlayback(id: String): CurrentPlayingTrack? {
        val ktify = getKtify(id) ?: return null
        return ktify.player.getCurrentPlayingTrack()
    }

    private suspend fun getKtify(id: String): Ktify? {
        val user = Repositories.users.findOne(User::userid eq id)
        return getKtify(user)
    }

    private suspend fun getKtify(user: User?): Ktify? {
        return builder.fromClientCredentials(user?.ktify ?: return null)
    }
}