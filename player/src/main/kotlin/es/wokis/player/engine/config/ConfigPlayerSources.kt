package es.wokis.player.engine.config

import es.wokis.services.config.Config

/** Maps the shared configuration onto the neutral player sources; values are copied verbatim. */
fun Config.toPlayerSourcesConfig(): PlayerSourcesConfig = PlayerSourcesConfig(
    youtube = YoutubeSourceConfig(
        enabled = youtube.enabled,
        oauth2Token = youtube.oauth2Token,
        poToken = youtube.poToken,
        visitorData = youtube.visitorData,
        remoteCipherUrl = youtube.remoteCipherUrl,
        remoteCipherPassword = youtube.remoteCipherPassword,
    ),
    deezer = DeezerSourceConfig(
        enabled = deezer.enabled,
        masterDecryptionKey = deezer.masterDecryptionKey,
        arlToken = deezer.arlToken,
    ),
    spotify = SpotifySourceConfig(
        enabled = spotify.enabled,
        clientId = spotify.clientId,
        clientSecret = spotify.clientSecret,
        customEndpoint = spotify.customEndpoint,
    ),
    tidal = TidalSourceConfig(
        enabled = tidal.enabled,
        countryCode = tidal.countryCode,
        token = tidal.token,
    ),
    kokoro = KokoroSourceConfig(
        baseUrl = kokoro.baseUrl,
        defaultVoice = kokoro.defaultVoice,
        defaultSpeed = kokoro.defaultSpeed,
        defaultLangCode = kokoro.defaultLangCode,
    ),
)
