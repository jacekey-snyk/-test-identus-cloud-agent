package io.iohk.atala.connect.controller

import io.iohk.atala.api.http.RequestContext
import io.iohk.atala.api.http.model.PaginationInput
import io.iohk.atala.connect.controller.ConnectionEndpoints.*
import io.iohk.atala.connect.controller.http.{AcceptConnectionInvitationRequest, CreateConnectionRequest}
import io.iohk.atala.pollux.credentialschema.SchemaRegistryServerEndpoints
import io.iohk.atala.pollux.credentialschema.controller.CredentialSchemaController
import sttp.tapir.ztapir.*
import zio.{URIO, ZIO}

import java.util.UUID

class ConnectionServerEndpoints(connectionController: ConnectionController) {

  private val createConnectionServerEndpoint: ZServerEndpoint[Any, Any] =
    createConnection.zServerLogic { case (ctx: RequestContext, request: CreateConnectionRequest) =>
      connectionController.createConnection(request)(ctx)
    }

  private val getConnectionServerEndpoint: ZServerEndpoint[Any, Any] =
    getConnection.zServerLogic { case (ctx: RequestContext, connectionId: UUID) =>
      connectionController.getConnection(connectionId)(ctx)
    }

  private val getConnectionsServerEndpoint: ZServerEndpoint[Any, Any] =
    getConnections.zServerLogic { case (ctx: RequestContext, paginationInput: PaginationInput) =>
      connectionController.getConnections(paginationInput.toPagination)(ctx)
    }

  private val acceptConnectionInvitationServerEndpoint: ZServerEndpoint[Any, Any] =
    acceptConnectionInvitation.zServerLogic { case (ctx: RequestContext, request: AcceptConnectionInvitationRequest) =>
      connectionController.acceptConnectionInvitation(request)(ctx)
    }

  val all: List[ZServerEndpoint[Any, Any]] = List(
    createConnectionServerEndpoint,
    getConnectionServerEndpoint,
    getConnectionsServerEndpoint,
    acceptConnectionInvitationServerEndpoint
  )
}

object ConnectionServerEndpoints {
  def all: URIO[ConnectionController, List[ZServerEndpoint[Any, Any]]] = {
    for {
      connectionController <- ZIO.service[ConnectionController]
      connectionEndpoints = new ConnectionServerEndpoints(connectionController)
    } yield connectionEndpoints.all
  }
}