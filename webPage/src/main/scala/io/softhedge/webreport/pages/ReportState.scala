package io.softhedge.webreport.pages


import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.DatePicker.events.DateEventData
import be.doeraene.webcomponents.ui5.configkeys.*
import com.github.nscala_java_time.time.Imports.LocalDateTime
import com.raquo.domtypes.generic.Modifier
import com.raquo.domtypes.generic.keys.Style
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.softhedge.webreport.common.*
import io.softhedge.webreport.pages.DatePickerHelper
import io.softhedge.webreport.pages.DomHelper.*
import org.scalajs
import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.dom.intl.{DateTimeFormat, DateTimeFormatOptions}
import zio.Cause.Fail
import zio.{Cause, Runtime, Task, Unsafe}

import java.text.DateFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

// DateState is the status of start date or end date
object ReportState {
  val MinStartDate = java.time.LocalDate.of(2000, 1, 1)

  object NoDate

  object InvalidDate

  type DateState = LocalDate | NoDate.type | InvalidDate.type
}


// ReportState is the status of the report parameters implemented in subclasses to keep the transitions handling
// and the messages easy to read and maintain
trait ReportState {
  val client: Option[String]

  def setClient(c: Option[String]): ReportState

  def buildSucceeded(): ReportState

  def buildFailed(message: String, optionalDetails: Option[String] = None): ReportState

  def start: ReportState.DateState

  def end: ReportState.DateState

  def mustBeBuilt(): Boolean

  def statusMessageChildren(): Seq[Node]
}

case class NoTimePeriodSelected(client: Option[String]) extends ReportState {
  override def setClient(c: Option[String]): ReportState = copy(client=c)

  // We could display an error message instead of ignoring because we should never be here
  override def buildSucceeded(): ReportState = this

  override def buildFailed(message: String, optionalDetails: Option[String] = None): ReportState = this

  override def start: ReportState.DateState = ReportState.NoDate

  override def end: ReportState.DateState = ReportState.NoDate

  override def mustBeBuilt(): Boolean = false

  override def statusMessageChildren(): Seq[Node] = Seq()
}

case class StandardTimePeriod(client: Option[String],
                              start: ReportState.DateState,
                              end: ReportState.DateState,
                              statusMessage: StatusMessage = NoStatusMessage,
                              built: Boolean = false) extends ReportState {
  override def setClient(c: Option[String]): ReportState = copy(client=c, built=false)

  override def buildSucceeded(): ReportState = this.copy(statusMessage = ReportBuilt(start, end, LocalDateTime.now()), built = true)

  override def buildFailed(message: String, optionalDetails: Option[String] = None): ReportState = this.copy(statusMessage = ReportBuildFailed(start, end, message, optionalDetails), built = true)

  override def statusMessageChildren(): Seq[Node] = statusMessage.statusMessageChildren()

  override def mustBeBuilt(): Boolean = !built
}


case class DateInput(client: Option[String],
                     startVar: Var[ReportState.DateState],
                     endVar: Var[ReportState.DateState],
                     statusMessage: StatusMessage = NoStatusMessage,
                     buildAsked: Boolean = false,
                     built: Boolean = false) extends ReportState {

  import ReportState.*

  override def start: ReportState.DateState = startVar.now()

  override def end: ReportState.DateState = endVar.now()

  override def setClient(c: Option[String]): ReportState = copy(client=c, buildAsked=built, built=false)

  override def buildSucceeded(): ReportState = this.copy(statusMessage = ReportBuilt(start, end, LocalDateTime.now()), buildAsked = false, built = true)

  override def buildFailed(message: String, optionalDetails: Option[String] = None): ReportState = {
    this.copy(statusMessage = ReportBuildFailed(start, end, message, optionalDetails), buildAsked = false, built = true)
  }

  def canBeBuilt(): Boolean = {
    start match {
      case s: LocalDate => {
        end match {
          case _: InvalidDate.type => false
          case _: NoDate.type => true
          case e: LocalDate => s.isBefore(e)
        }
      }
      case _ => false
    }
  }

  override def mustBeBuilt(): Boolean = buildAsked && !built

  override def statusMessageChildren(): Seq[Node] = statusMessage.statusMessageChildren()
}

trait StatusMessage {
  def statusMessageChildren(): Seq[Node]
}

object NoStatusMessage extends StatusMessage {
  override def statusMessageChildren(): Seq[Node] = Seq()
}

case class ReportBuilt(start: ReportState.DateState, end: ReportState.DateState, buildDate: LocalDateTime) extends StatusMessage {
  override def statusMessageChildren(): Seq[Node] = Seq(centeredSpan(color := OkColor, f"report built the ${buildDate.getDayOfMonth}/${buildDate.getMonthValue} at ${buildDate.getHour}%02d:${buildDate.getMinute}%02d:${buildDate.getSecond}%02d"))
}

case class ReportBuildFailed(start: ReportState.DateState, end: ReportState.DateState, errorMessage: String, optionalDetails: Option[String]) extends StatusMessage {
  val detailsDialog = new ErrorDialog() {
    private def detailsFromText(text: String) = {
      Seq(if (HTMLHelper.isHTML(text)) iframe(src := f"data:text/html,$text") else pre(text))
    }

    override protected def detailsNodes(): Seq[Node] = {
      Seq(div(children <-- details.signal.map(ot => ot.map(detailsFromText).getOrElse(Seq()))))
    }
  }

  override def statusMessageChildren(): Seq[Node] = {
    val error = Seq(centeredSpan(Icon(_.name := IconName.error, color := ErrorColor, marginRight := "5px"), color := ErrorColor, errorMessage))
    val details = optionalDetails.map(t => detailsDialog.buttonAndDialog(t)).getOrElse(Seq())
    error ++ details
  }
}
