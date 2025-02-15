package mock

import dev.kord.common.entity.DiscordMessage
import dev.kord.common.entity.DiscordUser
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.BaseVoiceChannelBehavior
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.rest.json.request.MultipartInteractionResponseModifyRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

val mockedGuildId = Snowflake(123)

val mockUser = mockk<DiscordUser> {
    val mockedUserName = "Manolete"

    every { id } returns mockk()
    every { username } returns mockedUserName
    every { discriminator } returns mockk()
    every { globalName } returns mockk()
    every { avatar } returns null
    every { bot } returns mockk()
    every { publicFlags } returns mockk()
    every { banner } returns null
    every { accentColor } returns null
    every { avatarDecoration } returns mockk()
}

val mockedDiscordMessage = mockk<DiscordMessage> {
    every { id } returns mockk()
    every { channelId } returns mockk()
    every { guildId.value } returns mockedGuildId
    every { author } returns mockUser
    every { content } returns ""
    every { timestamp } returns mockk()
    every { editedTimestamp } returns mockk()
    every { tts } returns false
    every { mentionEveryone } returns false
    every { mentions } returns emptyList()
    every { mentionRoles } returns emptyList()
    every { mentionedChannels } returns mockk()
    every { attachments } returns emptyList()
    every { embeds } returns emptyList()
    every { reactions } returns mockk()
    every { nonce } returns mockk()
    every { pinned } returns false
    every { webhookId } returns mockk()
    every { type } returns mockk()
    every { activity } returns mockk()
    every { application } returns mockk()
    every { applicationId } returns mockk()
    every { messageReference } returns mockk()
    every { flags } returns mockk()
    every { stickers } returns mockk()
    every { referencedMessage } returns mockk()
    every { interaction } returns mockk()
    every { components } returns mockk()
    every { roleSubscriptionData } returns mockk()
    every { id } returns mockk()
    every { position } returns mockk()
}

val mockedTextChannel: MessageChannel = mockk {
    coEvery { asChannelOrNull() } returns mockk()
}

val mockedVoiceChannel: BaseVoiceChannelBehavior = mockk()

val mockedKord: Kord = mockk {
    every { resources } returns mockk {
        every { defaultStrategy } returns EntitySupplyStrategy.rest
    }
    every { rest } returns mockk {
        every { interaction } returns mockk {
            coEvery {
                modifyInteractionResponse(any<Snowflake>(), any<String>(), any<MultipartInteractionResponseModifyRequest>())
            } returns mockedDiscordMessage
        }
    }
    every { defaultSupplier } returns mockk()
}

val mockedResponse: DeferredPublicMessageInteractionResponseBehavior = mockk {
    val mockedToken = "token"

    every { kord } returns mockedKord
    every { applicationId } returns mockk()
    every { token } returns mockedToken
}
