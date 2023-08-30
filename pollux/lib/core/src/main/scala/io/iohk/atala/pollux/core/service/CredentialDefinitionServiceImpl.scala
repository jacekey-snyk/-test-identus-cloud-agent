package io.iohk.atala.pollux.core.service

import io.iohk.atala.agent.walletapi.storage
import io.iohk.atala.agent.walletapi.storage.DIDSecret
import io.iohk.atala.agent.walletapi.storage.DIDSecretStorage
import io.iohk.atala.mercury.model.DidId
import io.iohk.atala.pollux.anoncreds.AnoncredLib
import io.iohk.atala.pollux.anoncreds.SchemaDef
import io.iohk.atala.pollux.core.model.error.CredentialSchemaError
import io.iohk.atala.pollux.core.model.error.CredentialSchemaError.URISyntaxError
import io.iohk.atala.pollux.core.model.schema.CredentialDefinition
import io.iohk.atala.pollux.core.model.schema.CredentialDefinition.Filter
import io.iohk.atala.pollux.core.model.schema.CredentialDefinition.FilteredEntries
import io.iohk.atala.pollux.core.model.schema.CredentialSchema
import io.iohk.atala.pollux.core.model.schema.CredentialSchema.parseCredentialSchema
import io.iohk.atala.pollux.core.model.schema.`type`.anoncred.AnoncredSchemaSerDesV1
import io.iohk.atala.pollux.core.model.schema.validator.JsonSchemaError
import io.iohk.atala.pollux.core.repository.CredentialDefinitionRepository
import io.iohk.atala.pollux.core.repository.Repository.SearchQuery
import io.iohk.atala.pollux.core.service.CredentialDefinitionService.Error.*
import io.iohk.atala.pollux.core.service.serdes.PrivateCredentialDefinitionSchemaSerDesV1
import io.iohk.atala.pollux.core.service.serdes.ProofKeyCredentialDefinitionSchemaSerDesV1
import io.iohk.atala.pollux.core.service.serdes.PublicCredentialDefinitionSerDesV1
import zio.Task
import zio.URLayer
import zio.ZIO
import zio.ZIO.getOrFailWith
import zio.ZLayer

import java.net.URI
import java.util.UUID
import scala.util.Try

class CredentialDefinitionServiceImpl(
    didSecretStorage: DIDSecretStorage,
    credentialDefinitionRepository: CredentialDefinitionRepository[Task],
    uriDereferencer: URIDereferencer
) extends CredentialDefinitionService {
  private val KEY_ID = "anoncred-credential-definition-private-key"

  override def create(in: CredentialDefinition.Input): Result[CredentialDefinition] = {
    for {
      uri <- ZIO.attempt(new URI(in.schemaId))
      content <- uriDereferencer.dereference(uri)
      vcSchema <- parseCredentialSchema(content)
      anoncredSchema <- AnoncredSchemaSerDesV1.schemaSerDes.deserialize(vcSchema.schema)
      anoncredLibSchema =
        SchemaDef(
          in.schemaId,
          anoncredSchema.version,
          anoncredSchema.attrNames,
          anoncredSchema.issuerId
        )
      anoncredLibCredentialDefinition <-
        ZIO
          .fromEither(
            Try(
              AnoncredLib.createCredDefinition(
                in.author,
                anoncredLibSchema,
                in.tag,
                in.supportRevocation
              )
            ).toEither
          )
          .mapError((t: Throwable) => CredentialDefinitionCreationError(t.getMessage))
      publicCredentialDefinitionJson <-
        PublicCredentialDefinitionSerDesV1.schemaSerDes.deserializeAsJson(
          anoncredLibCredentialDefinition.cd.data
        )
      privateCredentialDefinitionJson <-
        PrivateCredentialDefinitionSchemaSerDesV1.schemaSerDes.deserializeAsJson(
          anoncredLibCredentialDefinition.cdPrivate.data
        )
      proofKeyCredentialDefinitionJson <-
        ProofKeyCredentialDefinitionSchemaSerDesV1.schemaSerDes.deserializeAsJson(
          anoncredLibCredentialDefinition.proofKey.data
        )
      cd <-
        CredentialDefinition.make(
          in,
          PublicCredentialDefinitionSerDesV1.version,
          publicCredentialDefinitionJson,
          ProofKeyCredentialDefinitionSchemaSerDesV1.version,
          proofKeyCredentialDefinitionJson
        )
      createdCredentialDefinition <- credentialDefinitionRepository.create(cd)
      _ <-
        didSecretStorage.insertKey(
          DidId(in.author),
          KEY_ID,
          DIDSecret(privateCredentialDefinitionJson, PrivateCredentialDefinitionSchemaSerDesV1.version)
        )
    } yield createdCredentialDefinition
  }.mapError {
    case e: CredentialDefinitionCreationError => e
    case j: JsonSchemaError                   => UnexpectedError(j.error)
    case s: URISyntaxError                    => UnexpectedError(s.message)
    case u: URIDereferencerError              => UnexpectedError(u.error)
    case e: CredentialSchemaError             => CredentialDefinitionValidationError(e)
    case t: Throwable                         => RepositoryError(t)
  }

  override def delete(guid: UUID): Result[CredentialDefinition] =
    for {
      deleted_row_opt <- credentialDefinitionRepository
        .delete(guid)
        .mapError(RepositoryError.apply)
      deleted_row <- getOrFailWith(NotFoundError.byGuid(guid))(deleted_row_opt)
    } yield deleted_row

  override def lookup(filter: CredentialDefinition.Filter, skip: Int, limit: Int): Result[FilteredEntries] = {
    credentialDefinitionRepository
      .search(SearchQuery(filter, skip, limit))
      .mapError(t => RepositoryError(t))
      .map(sr => FilteredEntries(sr.entries, sr.count.toInt, sr.totalCount.toInt))
  }

  override def getByGUID(guid: UUID): Result[CredentialDefinition] = {
    credentialDefinitionRepository
      .getByGuid(guid)
      .mapError[CredentialDefinitionService.Error](t => RepositoryError(t))
      .flatMap(
        getOrFailWith(NotFoundError.byGuid(guid))(_)
      )
  }
}

object CredentialDefinitionServiceImpl {
  val layer: URLayer[
    DIDSecretStorage & CredentialDefinitionRepository[Task] & URIDereferencer,
    CredentialDefinitionService
  ] =
    ZLayer.fromFunction(CredentialDefinitionServiceImpl(_, _, _))
}