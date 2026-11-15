package commands

import dev.kord.common.entity.DiscordMessage
import dev.kord.common.entity.DiscordUser
import dev.kord.common.entity.MessageType
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.OptionalSnowflake
import kotlin.time.Instant

/**
 * Minimal real Discord message used by command tests that go through Kord's
 * `respond`/`edit` entity conversion, instead of broadly relaxed mocks.
 */
fun discordMessage(id: ULong = 999uL): DiscordMessage = DiscordMessage(
    id = Snowflake(id),
    channelId = Snowflake(1_000uL),
    guildId = OptionalSnowflake.Value(Snowflake(1_001uL)),
    author = DiscordUser(
        id = Snowflake(1_002uL),
        username = "bot",
        avatar = null,
    ),
    content = "",
    timestamp = Instant.fromEpochMilliseconds(0),
    editedTimestamp = null,
    tts = false,
    mentionEveryone = false,
    mentions = emptyList(),
    mentionRoles = emptyList(),
    attachments = emptyList(),
    embeds = emptyList(),
    pinned = false,
    type = MessageType.Default,
)
