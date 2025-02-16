package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.pollux.core.model.error.CredentialSchemaError
import org.hyperledger.identus.pollux.core.model.schema.CredentialSchema
import org.hyperledger.identus.pollux.core.model.schema.CredentialSchema.FilteredEntries
import org.hyperledger.identus.pollux.core.repository.CredentialSchemaRepository
import org.hyperledger.identus.pollux.core.repository.Repository.SearchQuery
import org.hyperledger.identus.pollux.core.service.CredentialSchemaService.Error.*
import zio.ZIO.{fail, getOrFailWith, succeed}
import zio.{URLayer, ZLayer}
import zio.IO

import java.util.UUID

class CredentialSchemaServiceImpl(
    credentialSchemaRepository: CredentialSchemaRepository
) extends CredentialSchemaService {
  override def create(in: CredentialSchema.Input): Result[CredentialSchema] = {
    for {
      credentialSchema <- CredentialSchema.make(in)
      _ <- CredentialSchema.validateCredentialSchema(credentialSchema)
      createdCredentialSchema <- credentialSchemaRepository
        .create(credentialSchema)
    } yield createdCredentialSchema
  }.mapError {
    case e: CredentialSchemaError => CredentialSchemaValidationError(e)
    case t: Throwable             => RepositoryError(t)
  }

  override def getByGUID(guid: UUID): IO[CredentialSchemaService.Error, CredentialSchema] = {
    credentialSchemaRepository
      .getByGuid(guid)
      .mapError[CredentialSchemaService.Error](t => RepositoryError(t))
      .flatMap(
        getOrFailWith(NotFoundError.byGuid(guid))(_)
      )
  }

  def getBy(
      author: String,
      id: UUID,
      version: String
  ): Result[CredentialSchema] = {
    getByGUID(CredentialSchema.makeGUID(author, id, version))
  }

  override def update(
      id: UUID,
      in: CredentialSchema.Input
  ): Result[CredentialSchema] = {
    for {
      cs <- CredentialSchema.make(id, in)
      _ <- CredentialSchema.validateCredentialSchema(cs).mapError(CredentialSchemaValidationError.apply)
      existingVersions <- credentialSchemaRepository
        .getAllVersions(id, in.author)
        .mapError[CredentialSchemaService.Error](RepositoryError.apply)
      _ <- existingVersions.headOption match {
        case None =>
          fail(NotFoundError.byId(id))
        case _ =>
          succeed(cs)
      }
      _ <- existingVersions.find(_ > in.version) match {
        case Some(higherVersion) =>
          fail(
            UpdateError(
              id,
              in.version,
              in.author,
              s"Higher version is found: $higherVersion"
            )
          )
        case None =>
          succeed(cs)
      }
      _ <- existingVersions.find(_ == in.version) match {
        case Some(existingVersion) =>
          fail(
            UpdateError(
              id,
              in.version,
              in.author,
              s"The version already exists: $existingVersion"
            )
          )
        case None => succeed(cs)
      }
      updated <- credentialSchemaRepository
        .create(cs)
        .mapError[CredentialSchemaService.Error](RepositoryError.apply)
    } yield updated
  }
  override def delete(guid: UUID): Result[CredentialSchema] = {
    for {
      deleted_row_opt <- credentialSchemaRepository
        .delete(guid)
        .mapError(RepositoryError.apply)
      deleted_row <- getOrFailWith(NotFoundError.byGuid(guid))(deleted_row_opt)
    } yield deleted_row
  }

  override def lookup(
      filter: CredentialSchema.Filter,
      skip: Int,
      limit: Int
  ): Result[CredentialSchema.FilteredEntries] = {
    credentialSchemaRepository
      .search(SearchQuery(filter, skip, limit))
      .mapError(t => RepositoryError(t))
      .map(sr => FilteredEntries(sr.entries, sr.count.toInt, sr.totalCount.toInt))
  }
}

object CredentialSchemaServiceImpl {
  val layer: URLayer[CredentialSchemaRepository, CredentialSchemaService] =
    ZLayer.fromFunction(CredentialSchemaServiceImpl(_))
}
