package io.softhedge.webreport.common

import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.generic.auto.*
import io.circe.parser.decode
import caliban.client.*
import caliban.client.Operations.RootQuery
import sttp.client3.*
import sttp.client3.circe.*
import sttp.model.*
import zio.*
import sttp.capabilities.WebSockets

import scala.concurrent.{ExecutionContext, Future}

trait QueryError {
  def shortMessage: String // Pure text, short

  def details: Option[String] // text, HTML or text with some HTML
}

// Represents a normal error during the query processing such as a database not available, a bug in the code
// Everything processed and returned as expected.
// We expected extensions to be indexed by numbers as string with the first one containing the exception class name
// and the others one the stack trace
case class ServerCleanProcessingError(message: String, extensions: Option[Map[String, String]]) extends Exception(message) with QueryError {
  override def shortMessage: String = message

  override def details: Option[String] = {
    def mapToDetails(m: Map[String, String]): String = {
      m.map((k, v) => k.toInt -> v).toSeq.sortBy(_._1).map(_._2) match {
        case e :: Nil => s"$e: $shortMessage"
        case e :: st => s"$e: $shortMessage\n" + st.mkString("\n")
        case _ => ""
      }
    }

    extensions.map(mapToDetails(_))
  }
}

// Represents a unexpected technical error such as a bug inside the libraries processing http
// The message can be either text or HTML
case class ServerInternalError(details: Option[String]) extends Exception("Internal server error") with QueryError {
  override def shortMessage: String = getMessage
}


object GraphQLUtils {
  private val TYPENAME = "__typename"

  def transformCalibanJsonToCirceDefault(json: Json): Json = {

    def fieldsFromKeys(cursor: ACursor, keys: Iterable[String]): Iterable[(String, Json)] = {
      def fieldFromKey(cursor: ACursor, key: String): (String, Json) = (key, tranformCursorRecursively(cursor))

      keys.map(k => fieldFromKey(cursor.downField(k), k))
    }

    def fieldsFromKeysWithTypeNameRewritten(cursor: ACursor, keys: Iterable[String]): Iterable[(String, Json)] = {
      cursor.get[String](TYPENAME) match {
        case Left(_) => fieldsFromKeys(cursor, keys)
        case Right(value) => Seq((value, Json.fromFields(fieldsFromKeys(cursor, keys.filterNot(_ == TYPENAME)))))
      }
    }

    def tranformCursorRecursively(cursor: ACursor): Json = {
      // First we try for a Json object
      cursor.keys.match {
        // If it is one, we go through each field recursively
        case Some(keys) => Json.fromFields(fieldsFromKeysWithTypeNameRewritten(cursor, keys))
        case None =>
          // If not an object, we try for an array
          cursor.values match {
            // If an array, we loop for each value
            case Some(vs) => Json.fromValues(vs.map(tranformJsonRecursively))
            // if not an object nor an array, we return the json itself
            case None => cursor.focus.getOrElse(Json.Null)
          }
      }
    }

    def tranformJsonRecursively(json: Json): Json = tranformCursorRecursively(json.hcursor)

    tranformJsonRecursively(json)
  }

  type Accessor = Json => Decoder.Result[Json]

  private def accessorIdentity(j: Json) = Right(j)

  def fromGraphQLJson[T](jsonText: String, accessor: Accessor = accessorIdentity)(using decoder: Decoder[T]): Either[Exception, T] = {
    def processErrors(errors: List[ServerCleanProcessingError]): Either[Exception, T] = errors match {
      case Nil => Left(ServerCleanProcessingError("Unexected error with no message", None)) // We should never be here
      case e :: _ => Left(e) // Even if we have multiple errors, we display only the first one as we are client side
    }

    def processData(json: Either[ParsingFailure, Json]): Either[Exception, T] = {
      val rawJson = json.flatMap(accessor)
      val circeDefaultJson = rawJson.map(GraphQLUtils.transformCalibanJsonToCirceDefault)
      circeDefaultJson.flatMap(decoder.decodeJson)
    }

    val json = parse(jsonText)
    val result = parse(jsonText).flatMap(_.hcursor.downField("errors").as[List[ServerCleanProcessingError]])
    result.fold(_ => processData(json), processErrors)
  }

  // See def fromQuery[T] for an explanation
  private[GraphQLUtils] class QueryExecutor[T] {
    def apply[R, O](query: SelectionBuilder[R, O], uri: Uri, backend: SttpBackend[Future, WebSockets], accessor: Accessor = accessorIdentity)
                   (using ExecutionContext, Decoder[T], caliban.client.Operations.IsOperation[R]): Task[T] = {
      val graphql = query.toGraphQL()
      val request = basicRequest.post(uri).body(graphql)
      val jsonFuture = request.send(backend).map(_.body)
      val resultFuture = jsonFuture.flatMap {
        case Left(error) => Future.failed(ServerInternalError(Some(error)))
        case Right(jsonText) =>
          GraphQLUtils.fromGraphQLJson[T](jsonText, accessor).fold(error => Future.failed(error),
            t => Future.successful(t))
      }
      ZIO.fromFuture(_ => resultFuture)
    }
  }

  // We want to use type inference for SelectionBuilder[R, O] but still need to specify T explicitely
  // So we do it this way, returning a class where the type inference will be done by apply()
  def fromQuery[T] = new QueryExecutor[T]
}
