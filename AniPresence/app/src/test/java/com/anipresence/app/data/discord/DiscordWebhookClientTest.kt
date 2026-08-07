package com.anipresence.app.data.discord

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordWebhookClientTest {
    @Test fun `accepts official Discord webhook URL`() {
        assertTrue(
            DiscordWebhookClient.isValidWebhook(
                "https://discord.com/api/webhooks/123456789/placeholder-token"
            )
        )
    }

    @Test fun `rejects non Discord and insecure URLs`() {
        assertFalse(DiscordWebhookClient.isValidWebhook("https://example.com/api/webhooks/1/token"))
        assertFalse(DiscordWebhookClient.isValidWebhook("http://discord.com/api/webhooks/1/token"))
    }
}
