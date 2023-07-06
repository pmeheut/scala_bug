package io.softhedge.webreport.js

import caliban.client.Operations.{IsOperation, RootSubscription}
import caliban.client.ws.{GraphQLWSRequest, GraphQLWSResponse}
import caliban.client.{CalibanClientError, GraphQLResponseError, SelectionBuilder}
import caliban.client.laminext.Subscription as CalibanSubscription
import com.raquo.airstream.core.EventStream
import io.circe.Json
import io.laminext.websocket.WebSocket

import java.util.UUID

object GraphQLJSClientUtils {
  extension[Origin, A] (self: SelectionBuilder[Origin, A]) def toSubscriptionWith[B](ws: WebSocket[GraphQLWSResponse, GraphQLWSRequest],
                                                                                     useVariables: Boolean = false,
                                                                                     queryName: Option[String] = None
                                                                                    )(mapResponse: (A, Json, List[GraphQLResponseError], Option[Json]) => B
                                                                                    )(implicit ev1: IsOperation[Origin], ev2: Origin <:< RootSubscription): CalibanSubscription[B] = {
    val id = UUID.randomUUID().toString
    ws.sendOne(GraphQLWSRequest("start", Some(id), Some(self.toGraphQL(useVariables, queryName))))
    new CalibanSubscription[B] {
      def received: EventStream[Either[CalibanClientError, B]] =
        ws.received.collect { case GraphQLWSResponse("data", Some(`id`), Some(payload)) =>
          self.decode(payload.noSpaces).map { case (result, errors, extensions) =>
            mapResponse(result, payload, errors, extensions)
          }
        }

      def unsubscribe(): Unit =
        ws.sendOne(GraphQLWSRequest("stop", Some(id), None))
    }
  }
}
