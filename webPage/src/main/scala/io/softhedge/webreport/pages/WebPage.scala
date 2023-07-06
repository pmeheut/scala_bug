package io.softhedge.webreport.pages

import be.doeraene.webcomponents.ui5.{SegmentedButton, *}
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


type SimpleDisplayFunction = () => Seq[Node]

trait FullDisplayFunction {
  def nodes(): Seq[Node]
}

type DisplayFunction = SimpleDisplayFunction | FullDisplayFunction

object DisplayFunction {
  def display(f: DisplayFunction): Seq[Node] = f match {
    case ff: FullDisplayFunction => ff.nodes()
    case sf => sf.asInstanceOf[SimpleDisplayFunction]() // We cannot pattern match on the function type in scala.js thanks to Javascript
  }
}

case class ProgressDisplay(backgroundFunction: DisplayFunction) extends FullDisplayFunction {
  def progressOverlay = div(cursor := "wait", width := "100%", height := "100%", position := "absolute",
    backgroundColor := "#eceaea", zIndex := 10000000, opacity := 0.4,
    display := "flex", flexDirection := "column", alignItems := "center", justifyContent := "center",
    BusyIndicator(_.size := BusyIndicatorSize.Large, _.active := true))

  override def nodes(): Seq[Node] = {
    Seq(progressOverlay) ++ DisplayFunction.display(backgroundFunction)
  }
}


class WebPage(val service: CrashSpreadReportService)(using ExecutionContext) {

  import WebPage.*
  import ReportState.*

  val displayState = Var[DisplayFunction](() => start())

  val reportState = Var[ReportState](NoTimePeriodSelected(None))

  var clientsInformation: List[ClientInformation] = List()

  def findLastClient(): Option[String] = {
    val lastClient = dom.window.localStorage.getItem(LastClientKey)
    if (clientsInformation.contains(lastClient)) Some(lastClient) else clientsInformation.headOption.map(_.name)
  }

  def setClientList(csl: CrashSpreadClientsInformation): Unit = {
    clientsInformation = csl.clients
    reportState.update(r => r.setClient(findLastClient()))
    displayState.set(() => mainScreen())
  }

  def clientInceptionDate(name: String): Option[LocalDate] = {
    clientsInformation.find(_.name == name).map(_.inceptionDate)
  }

  def queryClientList() = {
    val query = service.clientsInformation()
    executeQuery(query, onSuccess = setClientList, onError = cannotInitialize)
  }

  def start(): Seq[Node] = {
    // We cannot stay as the display function because we will be called back by startProgress() during the query and called recursively
    // So we switch to a pure display function
    displayState.set(() => initScreen())
    // We query the client. The success/error callback will handle the next screen
    queryClientList()
    // No need to return something, initScreen will do it
    Seq()
  }

  def initScreen(): Seq[Node] = {
    DisplayFunction.display(ProgressDisplay(() => mainScreen()))
  }

  val initErrorDetailsDialog = ErrorDialog()

  def cannotInitialize(cause: Cause[Throwable]): Unit = {
    displayState.set(() => Seq(IllustratedMessage(
      _.name := IllustratedMessageType.UnableToLoad,
      _.titleText := "Something went wrong...",
      _.slots.subtitle := div("Please try again or contact us at ", Link(_.href := MailHelper.MaintenanceContactHref, MailHelper.MaintenanceContact)),
      Button(_.icon := IconName.refresh, "Try again", _.design := ButtonDesign.Emphasized, _.events.onClick.mapTo(() => start()) --> displayState),
      initErrorDetailsDialog.buttonAndDialog(cause.toString),
    )))
  }

  val currentSummary = Var(Option.empty[Summary])

  val currentReport = Var(Option.empty[Report])

  private def defaultOnError(cause: Cause[Throwable]): Unit = {
    val InternalErrorMessage = "Internal Error"

    val buildFailed = cause.failureOption match {
      case Some(f: QueryError) => (f.shortMessage, f.details)
      case Some(f) => (InternalErrorMessage, Some(f.getMessage))
      case None => (InternalErrorMessage, Some(cause.prettyPrint))
    }
    reportState.update(r => r.buildFailed(buildFailed._1, buildFailed._2))
  }

  private def defaultOnSuccess[T](t: T) = {}


  private def executeQuery[T](task: Task[T],
                              onSuccess: T => Unit = defaultOnSuccess,
                              onError: Cause[Throwable] => Unit = defaultOnError): Unit = {

    def run[A, B](f: A => B): A => B = {
      endProgress()
      f
    }

    startProgress()
    Unsafe.unsafe { implicit unsafe =>
      // We cannot use the obvious Runtime.default.unsafe.run(task)
      // because it does not work in Javascript as it awaits
      // So we create another ZIO fiber with fork and handle its result in the observer
      Runtime.default.unsafe.fork(task).unsafe.addObserver(_.foldExit(run(onError), run(onSuccess)))
    }
  }

  def buildSummary(client: String): Unit = {
    val summaryQuery = service.summary(client)
    executeQuery(summaryQuery, onSuccess = summaryBuilt)
  }

  def summaryBuilt(summary: Summary): Unit = {
    currentSummary.set(Some(summary))
  }

  def reportBuilt(report: Report): Unit = {
    currentReport.set(Some(report))
    reportState.update(r => r.buildSucceeded())
  }

  def buildReport(r: ReportState): Unit = {
    r.client.map(client => {
      val start = r.start.asInstanceOf[LocalDate]
      val end = r.end match {
        case l: LocalDate => Some(l)
        case _ => None
      }
      val reportQuery = service.cumulativePnLHistory(CrashSpeadCumulativePnLHistoryParameters(client, start, end))
      executeQuery(reportQuery, onSuccess = reportBuilt)
    })
  }

  def pnlGraph(report: Report) = {
    import org.openmole.plotlyjs.all._
    import org.openmole.plotlyjs.PlotlyImplicits._
    import org.openmole.plotlyjs._
    import scala.scalajs.js.JSConverters._
    val plotDiv = div()

    val layout = Layout
      .title("Performance")
      .showlegend(false)
      .xaxis(axis.title("Date").`type`(AxisType.category))
      .yaxis(axis.title("P&L"))
      .width(dom.window.innerWidth)


    val data = linechart.lines

    val dates = report.cumulativePnL.map(c => DateUtils.dateTo_dd_MM_yy(c.date))
    val pnl = report.cumulativePnL.map(_.pnl)
    val dataRef = data
      .x(dates.toJSArray)
      .y(pnl.toJSArray)
      .setMode(PlotMode.lines)


    val config = Config.displayModeBar(false).responsive(true)

    Plotly.newPlot(plotDiv.ref,
      Seq(dataRef._result).toJSArray,
      layout,
      config)

    plotDiv
  }

  val summaryDiv = div(width := "100%", children <-- currentSummary.signal.map(formatSummary))

  val reportDiv = div(width := "100%", children <-- currentReport.signal.map(formatReport))


  def formatSummary(summary: Option[Summary]): Seq[Node] = {
    def reportNodes(summary: Summary): Seq[Node] = {
      Seq(
        div(
          horizontalDiv(cls := "report",
            divFlexRowElement(f"MTD P&L=${summary.monthToDatePnL}%.2f"),
            divFlexRowElement(f"MTD Drawdown=${summary.monthToDateDrawdown}%.2f"),
            divFlexRowElement(f"YTD P&L=${summary.yearToDatePnL}%.2f"),
            divFlexRowElement(f"YTD Drawdown=${summary.yearToDateDrawdown}%.2f"),
          )
        )
      )
    }

    summary.map(r => reportNodes(r)).getOrElse(Seq())
  }

  def formatReport(report: Option[Report]): Seq[Node] = {
    def reportNodes(report: Report): Seq[Node] = {
      Seq(
        horizontalDiv(cls := "report",
          divFlexRowElement(f"Current Period P&L=${report.pnl}%.2f"),
          divFlexRowElement(f"Drawdown=${report.drawdown}%.2f"),
          divFlexRowElement(f"Fees=${report.fees}%.2f")),
        pnlGraph(report)
      )
    }

    report.map(r => reportNodes(r)).getOrElse(Seq())
  }

  private def startProgress(): Unit = displayState.update(ProgressDisplay(_))

  private def endProgress(): Unit = displayState.now() match {
    case ProgressDisplay(original) => displayState.set(original) // We should not be here but this is defensive programming
    case _ => // Do nothing. It means that either the onSuccess or onError callbacks from executeQuery have already set the next screen
  }

  def clientSelectorOption(name: String) = {
    Select.option(name, dataAttr("name") := name, _.selected <-- reportState.signal.map(ms => ms.client.map(_ == name).getOrElse(false)))
  }

  def clientListSelect() = {
    compactSelect(clientsInformation.map(_.name).map(clientSelectorOption),
      _.events.onChange.map(_.detail.selectedOption.dataset.get("name")) --> Observer[Option[String]](onNext = o => {
        dom.window.localStorage.setItem(LastClientKey, o.getOrElse(""))
        reportState.update(r => r.setClient(o))
      }))
  }

  val timePeriod = TimePeriod(reportState, currentReport, clientInceptionDate)

  def mainScreen(): Seq[Node] = {
    Seq(div(
      // h1 margin top makes the html/body height at 100% scroll. So we set it to 0 and replace it with padding
      div(width := "100%", textAlign := "center", h1(marginTop := "0px", paddingTop := "10px", "IB Trades Report")),
      hr(cls := "separator-gradient"),
      div(cls := "report-form",
        div(cls := "report-block", div(paddingBottom := "10px", labeledElement("Client", clientListSelect()))),
        div(cls := "report-block date-block", labeledElement("Time period", timePeriod()
        )),
      ),
      summaryDiv,
      reportDiv,
      onMountCallback { ctx =>
        reportState.signal.changes.filter(_.mustBeBuilt()).foreach(buildReport)(ctx.owner)
        reportState.signal.map(_.client).changes.collect { case Some(c) => c }.foreach(buildSummary)(ctx.owner)
      })
    )
  }

  val app = div(width := "100%", height := "100%",
    children <-- displayState.signal.map(DisplayFunction.display(_))
  )

  def run(): Unit = {
    documentEvents.onDomContentLoaded.foreach { _ =>
      val appContainer = document.querySelector("#appContainer")
      render(appContainer, app)
    }(unsafeWindowOwner)
  }
}

object MokeReportBuilder extends CrashSpreadReportService {
  override def clientsInformation(): Task[CrashSpreadClientsInformation] = ???

  override def summary(client: String): Task[CrashSpreadSummary] = ???

  override def cumulativePnLHistory(parameters: CrashSpeadCumulativePnLHistoryParameters): Task[CrashSpeadCumulativePnLHistory] = ???
}

object WebPage {
  val LastClientKey = "lastClient"
  type Summary = CrashSpreadSummary

  type Report = CrashSpeadCumulativePnLHistory


  def main(args: Array[String]): Unit = {
    given ExecutionContext = ExecutionContext.global

    WebPage(MokeReportBuilder).run()
  }
}
