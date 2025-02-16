package org.hyperledger.identus.mercury.protocol.issuecredential

import io.circe.Json
import io.circe.parser.*
import io.circe.syntax.*
import org.hyperledger.identus.mercury.model.AttachmentDescriptor.attachmentDescriptorEncoderV2
import org.hyperledger.identus.mercury.model.{AttachmentDescriptor, DidId}
import munit.*
class RequestCredentialSpec extends ZSuite {

  test("Holder RequestCredential") {

    val attribute1 = Attribute(name = "name", value = "Joe Blog")
    val attribute2 = Attribute(name = "dob", value = "01/10/1947")
    val credentialPreview = CredentialPreview(attributes = Seq(attribute1, attribute2))
    val body = RequestCredential.Body(goal_code = Some("Request Credential"))
    val attachmentDescriptor = AttachmentDescriptor.buildJsonAttachment[CredentialPreview](payload = credentialPreview)
    val attachmentDescriptorJson = attachmentDescriptor.asJson.deepDropNullValues.noSpaces

    val expectedRequestedCredentialJson = parse(s"""{
         |    "id": "051bf917-2cbe-460b-8d12-b1a9609505c2",
         |    "type": "https://didcomm.org/issue-credential/3.0/request-credential",
         |    "body":
         |    {
         |        "goal_code": "Request Credential"
         |    },
         |    "attachments":
         |    [
         |    $attachmentDescriptorJson
         |    ],
         |    "from": "did:prism:test123",
         |    "to" : "did:prism:test123"
         |}""".stripMargin).getOrElse(Json.Null)

    val requestCredential = RequestCredential(
      id = "051bf917-2cbe-460b-8d12-b1a9609505c2",
      body = body,
      attachments = Seq(attachmentDescriptor),
      to = DidId("did:prism:test123"),
      from = DidId("did:prism:test123")
    )

    val result = requestCredential.asJson.deepDropNullValues

    assertEquals(result, expectedRequestedCredentialJson)
  }
}
