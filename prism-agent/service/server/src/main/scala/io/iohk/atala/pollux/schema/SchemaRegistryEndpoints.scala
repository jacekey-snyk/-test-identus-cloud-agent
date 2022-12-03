package io.iohk.atala.pollux.schema

import io.iohk.atala.api.http.model.{Order, PaginationInput}
import io.iohk.atala.api.http.{BadRequest, FailureResponse, InternalServerError, NotFoundResponse, RequestContext}
import io.iohk.atala.pollux.schema.model.{
  VerifiableCredentialSchema,
  VerifiableCredentialSchemaPage,
  VerifiableCredentialSchemaInput
}
import io.iohk.atala.api.http.codec.OrderCodec.*
import sttp.tapir.EndpointIO.Info
import sttp.tapir.extractFromRequest
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.{
  Endpoint,
  EndpointInfo,
  PublicEndpoint,
  endpoint,
  oneOf,
  oneOfDefaultVariant,
  oneOfVariant,
  path,
  query,
  statusCode,
  stringToPath
}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder}
import sttp.model.StatusCode

import java.util.UUID

object SchemaRegistryEndpoints {

  val createSchemaEndpoint: PublicEndpoint[
    (RequestContext, VerifiableCredentialSchemaInput),
    FailureResponse,
    VerifiableCredentialSchema,
    Any
  ] =
    endpoint.post
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in("schema-registry" / "schemas")
      .in(
        jsonBody[VerifiableCredentialSchemaInput]
          .copy(info =
            Info.empty.description(
              "Create schema input object with the metadata and attributes"
            )
          )
      )
      .out(statusCode(StatusCode.Created))
      .out(jsonBody[VerifiableCredentialSchema])
      .errorOut(
        oneOf[FailureResponse](
          oneOfVariant(
            StatusCode.InternalServerError,
            jsonBody[InternalServerError]
          )
        )
      )
      .name("createSchema")
      .summary("Publish new schema to the schema registry")
      .description(
        "Publish the new schema with attributes to the schema registry on behalf of Cloud Agent. Schema will be signed by the keys of Cloud Agent and issued by the DID that corresponds to it"
      )
      .tag("Schema Registry")

  val getSchemaByIdEndpoint: PublicEndpoint[
    (RequestContext, UUID),
    FailureResponse,
    VerifiableCredentialSchema,
    Any
  ] =
    endpoint.get
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(
        "schema-registry" / "schemas" / path[UUID]("id")
          .copy(info = Info.empty.description("Get the schema by id"))
      )
      .out(jsonBody[VerifiableCredentialSchema])
      .errorOut(
        oneOf[FailureResponse](
          oneOfVariant(StatusCode.NotFound, jsonBody[NotFoundResponse])
        )
      )
      .name("getSchemaById")
      .summary("Fetch the schema from the registry by id")
      .description(
        "Fetch the schema by the unique identifier. Verifiable Credential Schema in json format is returned."
      )
      .tag("Schema Registry")

  val lookupSchemasByQueryEndpoint: PublicEndpoint[
    (
        RequestContext,
        VerifiableCredentialSchema.Filter,
        PaginationInput,
        Option[Order]
    ),
    FailureResponse,
    VerifiableCredentialSchemaPage,
    Any
  ] =
    endpoint.get
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in("schema-registry" / "schemas".description("Lookup schemas by query"))
      .in(
        query[Option[String]]("author")
          .and(
            query[Option[String]]("name")
              .and(
                query[Option[String]]("tags")
              )
          )
          .mapTo[VerifiableCredentialSchema.Filter]
      )
      .in(
        query[Option[Int]]("offset")
          .and(query[Option[Int]]("limit"))
          .mapTo[PaginationInput]
      )
      .in(query[Option[Order]]("order"))
      .out(jsonBody[VerifiableCredentialSchemaPage])
      .errorOut(
        oneOf[FailureResponse](
          oneOfVariant(
            StatusCode.InternalServerError,
            jsonBody[InternalServerError]
          )
        )
      )
      .name("lookupSchemasByQuery")
      .summary("Lookup schemas by indexed fields")
      .description(
        "Lookup schemas by `author`, `name`, `tags` parameters and control the pagination by `offset` and `limit` parameters "
      )
      .tag("Schema Registry")

  val testEndpoint: PublicEndpoint[
    RequestContext,
    Unit,
    String,
    Any
  ] =
    endpoint.get
      .in(
        "schema-registry" / "test"
          .copy(info = Info.empty.description("Debug endpoint"))
      )
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .out(jsonBody[String])
      .name("test")
      .summary("Trace the request input from the point of view of the server")
      .description(
        "Trace the request input from the point of view of the server"
      )
      .tag("Schema Registry")

}