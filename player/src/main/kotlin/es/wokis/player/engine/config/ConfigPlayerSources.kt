package es.wokis.player.engine.config

import es.wokis.services.config.Config

/**
 * Maps the shared JSON configuration onto the neutral source-provider configuration.
 *
 * The mapping lives in `:player` (rather than in a gateway module) so the root Kord adapter and the
 * isolated JDA runtime register exactly the same YouTube, LavaSrc, Kokoro, local and remote sources
 * from the same values. The dependency is one-way: `:player` knows the shared `Config`, while
 * `:configuration` never depends on the player or on any gateway.
 *
 * Every provider field is carried over verbatim, including nullable YouTube credentials and empty
 * strings: a `null` token stays `null` and is not normalised to `""`, so the registered provider
 * options keep their existing semantics.
 */
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
