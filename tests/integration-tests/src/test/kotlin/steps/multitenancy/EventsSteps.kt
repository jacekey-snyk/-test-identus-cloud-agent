package steps.multitenancy

import interactions.Post
import io.iohk.atala.automation.serenity.ensure.Ensure
import org.hyperledger.identus.client.models.CreateWebhookNotification
import net.serenitybdd.screenplay.Actor
import org.apache.http.HttpStatus

class EventsSteps {
    fun registerNewWebhook(actor: Actor, webhookUrl: String) {
        actor.attemptsTo(
            Post.to("/events/webhooks")
                .with {
                    it.body(
                        CreateWebhookNotification(url = webhookUrl),
                    )
                },
        )

        actor.attemptsTo(
            Ensure.thatTheLastResponse().statusCode().isEqualTo(HttpStatus.SC_CREATED),
        )
    }
}
