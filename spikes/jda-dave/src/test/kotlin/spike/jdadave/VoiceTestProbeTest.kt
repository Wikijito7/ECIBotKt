package spike.jdadave

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class VoiceTestProbeTest {

    private val testGuildId = 123_456_789_012_345_678L

    @Test
    fun `defer reply failure finishes the run exactly once and releases the guard`() {
        val outcomes = CopyOnWriteArrayList<ProbeOutcome>()
        val probe = VoiceTestProbe(testGuildId) { outcomes += it }
        try {
            val event = mockEvent()
            val failedDefer = mockk<ReplyCallbackAction>()
            every { event.deferReply(true) } returns failedDefer
            every {
                failedDefer.queue(any<Consumer<InteractionHook>>(), any<Consumer<Throwable>>())
            } answers {
                secondArg<Consumer<Throwable>>().accept(RuntimeException("discord unavailable"))
            }

            probe.onSlashCommandInteraction(event)

            assertEquals(1, outcomes.size)
            assertFailureContains(outcomes.single(), "did not acknowledge")

            // The running guard must have been released, or the second invocation would be
            // answered with "already running" and never produce an outcome.
            probe.onSlashCommandInteraction(event)

            assertEquals(2, outcomes.size)
            outcomes.forEach { assertFailureContains(it, "did not acknowledge") }
        } finally {
            probe.close()
        }
    }

    @Test
    fun `a synchronous deferReply throw finishes once without retrying the acknowledgement`() {
        val outcomes = CopyOnWriteArrayList<ProbeOutcome>()
        val probe = VoiceTestProbe(testGuildId) { outcomes += it }
        try {
            val event = mockEvent()
            val hook = mockk<InteractionHook>()
            every { event.hook } returns hook
            every { event.deferReply(true) } throws IllegalStateException("detached event")

            probe.onSlashCommandInteraction(event)

            assertEquals(1, outcomes.size)
            assertFailureContains(outcomes.single(), "did not acknowledge")
            // No queue() registration is possible after deferReply itself threw, so there is
            // no second acknowledgement and no best-effort reply edit.
            verify(exactly = 1) { event.deferReply(true) }
            verify(exactly = 0) { hook.editOriginal(any<String>()) }

            // The guard was released even though no callback ever ran.
            val retryEvent = mockEvent()
            every { retryEvent.deferReply(true) } throws IllegalStateException("detached event")

            probe.onSlashCommandInteraction(retryEvent)

            assertEquals(2, outcomes.size)
            outcomes.forEach { assertFailureContains(it, "did not acknowledge") }
        } finally {
            probe.close()
        }
    }

    @Test
    fun `a synchronous queue registration throw finishes once and releases the guard`() {
        val outcomes = CopyOnWriteArrayList<ProbeOutcome>()
        val probe = VoiceTestProbe(testGuildId) { outcomes += it }
        try {
            val event = mockEvent()
            val hook = mockk<InteractionHook>()
            val deferAction = mockk<ReplyCallbackAction>()
            every { event.hook } returns hook
            every { event.deferReply(true) } returns deferAction
            every {
                deferAction.queue(any<Consumer<InteractionHook>>(), any<Consumer<Throwable>>())
            } throws IllegalStateException("queue rejected")

            probe.onSlashCommandInteraction(event)

            assertEquals(1, outcomes.size)
            assertFailureContains(outcomes.single(), "did not acknowledge")
            // Neither callback ran, so no reply may be edited and no retry acknowledgement
            // may be attempted.
            verify(exactly = 0) { hook.editOriginal(any<String>()) }

            val retryEvent = mockEvent()
            val retryDeferAction = mockk<ReplyCallbackAction>()
            every { retryEvent.deferReply(true) } returns retryDeferAction
            every {
                retryDeferAction.queue(any<Consumer<InteractionHook>>(), any<Consumer<Throwable>>())
            } throws IllegalStateException("queue rejected")

            probe.onSlashCommandInteraction(retryEvent)

            assertEquals(2, outcomes.size)
            outcomes.forEach { assertFailureContains(it, "did not acknowledge") }
        } finally {
            probe.close()
        }
    }

    @Test
    fun `a queue success callback followed by a throw leaves completion to the callback`() {
        val outcomes = CopyOnWriteArrayList<ProbeOutcome>()
        val probe = VoiceTestProbe(testGuildId) { outcomes += it }
        try {
            val event = mockEvent()
            val hook = mockk<InteractionHook>()
            val deferAction = mockk<ReplyCallbackAction>()
            every { event.hook } returns hook
            every { event.deferReply(true) } returns deferAction
            every {
                deferAction.queue(any<Consumer<InteractionHook>>(), any<Consumer<Throwable>>())
            } answers {
                firstArg<Consumer<InteractionHook>>().accept(hook)
                throw IllegalStateException("queue registration failed after the callback ran")
            }
            every { hook.editOriginal(any<String>()) } throws IllegalStateException("hook unavailable")

            // The worker is shut down, so the success callback fails to schedule the probe
            // and owns the terminal outcome. The throw after it must not finish the run a
            // second time or release the guard out from under the finished invocation.
            probe.close()

            probe.onSlashCommandInteraction(event)

            assertEquals(1, outcomes.size)
            assertFailureContains(outcomes.single(), "could not be scheduled")
            verify(exactly = 1) {
                hook.editOriginal(match<String> { it.contains("could not be scheduled") })
            }

            // The success callback released the guard; the second invocation must run
            // instead of being rejected as "already running".
            probe.onSlashCommandInteraction(event)

            assertEquals(2, outcomes.size)
            outcomes.forEach { assertFailureContains(it, "could not be scheduled") }
            verify(exactly = 2) {
                hook.editOriginal(match<String> { it.contains("could not be scheduled") })
            }
        } finally {
            probe.close()
        }
    }

    @Test
    fun `worker scheduling failure finishes the run once instead of leaving it hanging`() {
        val outcomes = CopyOnWriteArrayList<ProbeOutcome>()
        val probe = VoiceTestProbe(testGuildId) { outcomes += it }
        try {
            val event = mockEvent()
            val hook = mockk<InteractionHook>()
            val acceptedDefer = mockk<ReplyCallbackAction>()
            every { event.deferReply(true) } returns acceptedDefer
            every {
                acceptedDefer.queue(any<Consumer<InteractionHook>>(), any<Consumer<Throwable>>())
            } answers {
                firstArg<Consumer<InteractionHook>>().accept(hook)
            }
            every { event.hook } returns hook
            // Best-effort reply edit: even if it fails, the run must still be completed.
            every { hook.editOriginal(any<String>()) } throws IllegalStateException("hook unavailable")

            // Closing the probe shuts the single worker down, so the deferred callback's
            // worker.execute { ... } is rejected exactly like a saturated executor.
            probe.close()

            probe.onSlashCommandInteraction(event)

            assertEquals(1, outcomes.size)
            assertFailureContains(outcomes.single(), "could not be scheduled")
            verify(exactly = 1) {
                hook.editOriginal(match<String> { it.contains("could not be scheduled") })
            }

            // The guard is released after a scheduling failure as well.
            probe.onSlashCommandInteraction(event)

            assertEquals(2, outcomes.size)
            outcomes.forEach { assertFailureContains(it, "could not be scheduled") }
        } finally {
            probe.close()
        }
    }

    @Test
    fun `a member-less invocation replies with guidance and does not start a probe`() {
        val outcomes = CopyOnWriteArrayList<ProbeOutcome>()
        val probe = VoiceTestProbe(testGuildId) { outcomes += it }
        try {
            val event = mockEvent()
            val hook = mockk<InteractionHook>()
            val edit = mockk<WebhookMessageEditAction<Message>>()
            every { event.member } returns null
            every { event.hook } returns hook
            every { hook.editOriginal(any<String>()) } returns edit
            every { edit.submit() } returns CompletableFuture.completedFuture(mockk<Message>())

            val outcome = probe.doProbe(event)

            assertNull(outcome, "a guidance-only invocation must not return a probe outcome")
            verify(exactly = 1) {
                hook.editOriginal("Could not resolve your guild member. Try again from the staging guild.")
            }
            assertEquals(0, outcomes.size)
        } finally {
            probe.close()
        }
    }

    @Test
    fun `replyAndAwait swallows an edit failure instead of failing the worker`() {
        val probe = VoiceTestProbe(testGuildId) {}
        try {
            val event = mockEvent()
            val hook = mockk<InteractionHook>()
            every { event.hook } returns hook
            every { hook.editOriginal(any<String>()) } throws IllegalStateException("hook unavailable")

            // The production helper catches the failure itself; an escaping exception here
            // would fail the test (and, in production, kill the worker's error reporting).
            probe.replyAndAwait(event, "guidance text")

            verify(exactly = 1) { hook.editOriginal("guidance text") }
        } finally {
            probe.close()
        }
    }

    private fun mockEvent(): SlashCommandInteractionEvent {
        val event = mockk<SlashCommandInteractionEvent>()
        val guild = mockk<Guild>()
        every { event.name } returns VoiceTestProbe.COMMAND_NAME
        every { event.guild } returns guild
        every { guild.idLong } returns testGuildId
        return event
    }

    private fun assertFailureContains(outcome: ProbeOutcome, fragment: String) {
        val failure = assertIs<ProbeOutcome.Failure>(outcome)
        assertContains(failure.userMessage, fragment)
    }
}
