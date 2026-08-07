package com.liftly.app.integration.discord

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordWebhookUrlValidatorTest {
    @Test
    fun `accepts official https webhook`() {
        assertTrue(DiscordWebhookUrlValidator.isValid("https://discord.com/api/webhooks/123456/Abc_def-123.token"))
        assertTrue(DiscordWebhookUrlValidator.isValid("https://canary.discord.com/api/v10/webhooks/42/token"))
    }

    @Test
    fun `rejects non https and lookalike hosts`() {
        assertFalse(DiscordWebhookUrlValidator.isValid("http://discord.com/api/webhooks/123/token"))
        assertFalse(DiscordWebhookUrlValidator.isValid("https://discord.com.attacker.example/api/webhooks/123/token"))
        assertFalse(DiscordWebhookUrlValidator.isValid("https://127.0.0.1/api/webhooks/123/token"))
    }

    @Test
    fun `rejects credentials query fragments and malformed paths`() {
        assertFalse(DiscordWebhookUrlValidator.isValid("https://user@discord.com/api/webhooks/123/token"))
        assertFalse(DiscordWebhookUrlValidator.isValid("https://discord.com/api/webhooks/123/token?wait=true"))
        assertFalse(DiscordWebhookUrlValidator.isValid("https://discord.com/api/webhooks/not-an-id/token"))
        assertFalse(DiscordWebhookUrlValidator.isValid("https://discord.com/channels/123/token"))
    }
}
