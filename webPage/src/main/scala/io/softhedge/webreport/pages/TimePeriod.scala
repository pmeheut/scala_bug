package io.softhedge.webreport.pages

import io.softhedge.webreport.common.*
import be.doeraene.webcomponents.ui5.DatePicker.events.DateEventData
import be.doeraene.webcomponents.ui5.configkeys.*
import be.doeraene.webcomponents.ui5.{SegmentedButton, *}
import com.github.nscala_java_time.time.Imports.{LocalDateTime, LocalDate}
import com.raquo.domtypes.generic.Modifier
import com.raquo.domtypes.generic.keys.Style
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.softhedge.webreport.pages.DatePickerHelper
import io.softhedge.webreport.pages.DomHelper.*
import org.scalajs
import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.dom.intl.{DateTimeFormat, DateTimeFormatOptions}

import java.text.DateFormatSymbols
import WebPage.*


enum Period(val buttonName: String) {
  case MTD extends Period("MTD")
  case YTD extends Period("YTD")
  case Inception extends Period("Inception")
  case Custom extends Period("Custom")
}

class TimePeriod(val reportState: Var[ReportState], val currentReport: Var[Option[Report]], clientInceptionDate: String => Option[java.time.LocalDate]) {

  import ReportState.*
  import Period.*

  val now = LocalDateTime.now()
  val segmentedButtonPressed = Var(Option.empty[String])

  def segmentedButtonItem(s: String, callback: () => Unit): SegmentedButton.ModFunction = {
    def onClick(e: Any) = {
      segmentedButtonPressed.set(Some(s))
      monthSelected.set(None)
      callback()
    }

    _.item(s,
      _.events.onClick --> Observer[Any](onClick),
      _.pressed <-- segmentedButtonPressed.signal.map(sbp => sbp.map(_ == s).getOrElse(false)))
  }

  def mtd(client: Option[String]) = {
    val firstOfMonth = java.time.LocalDate.of(now.getYear, now.getMonthValue, 1)
    StandardTimePeriod(client, firstOfMonth, NoDate)
  }

  def ytd(client: Option[String]) = {
    val firstOfYear = java.time.LocalDate.of(now.getYear, 1, 1)
    StandardTimePeriod(client, firstOfYear, NoDate)
  }

  def inception(client: Option[String]) = {
    StandardTimePeriod(client, MinStartDate, NoDate)
  }

  def updateReportStateFromCustom() = reportState.update(r => DateInput(r.client, startDate, endDate))


  val timePeriodButton = SegmentedButton(idAttr := "timePeriodButton", cls := UI5CompactClass,
    segmentedButtonItem(MTD.buttonName, () => reportState.update(r => mtd(r.client))),
    segmentedButtonItem(YTD.buttonName, () => reportState.update(r => ytd(r.client))),
    segmentedButtonItem(Inception.buttonName, () => reportState.update(r => inception(r.client))),
    segmentedButtonItem(Custom.buttonName, () => {
      currentReport.set(None)
      updateReportStateFromCustom()
    })
  )

  val monthSelected = Var(Option.empty[String])

  def orderedMonths() = {
    val months = DateFormatSymbols().getMonths().filter(_.nonEmpty)
    // Do not use java.time.LocalDate.now() as it fails in scalajs
    val year = now.getYear
    val currentMonth = now.getMonthValue - 1
    val ordered = months.slice(0, currentMonth).reverse.map(m => f"$m $year") ++ months.slice(currentMonth, months.length).reverse.map(m => f"$m ${year - 1}")
    ordered
  }

  def monthSelectorOption(name: String) = {
    Select.option(name, dataAttr("name") := name, _.selected <-- monthSelected.signal.map(ms => ms.map(_ == name).getOrElse(false)))
  }

  def monthTimePeriod(client: Option[String], month: Option[String], orderedMonths: Seq[String]): ReportState = month match {
    case None => NoTimePeriodSelected(client)
    case Some(m) => {
      val index = orderedMonths.indexOf(m)
      if (index >= 0) {
        var newMonth = now.getMonthValue - index - 1
        var newYear = now.getYear
        if (newMonth <= 0) {
          newMonth += 12
          newYear += -1
        }
        val monthStart = java.time.LocalDate.of(newYear, newMonth, 1)
        val monthEnd = java.time.LocalDate.of(newYear, newMonth, DateUtils.numberOfDaysInMonth(newYear, newMonth))
        StandardTimePeriod(client, monthStart, monthEnd)
      } else {
        println(f"Cannot find month ${m}")
        NoTimePeriodSelected(client)
      }
    }
  }

  val months = orderedMonths()
  // We add an empty entry just for the selector to handle "we are not the active selection mode" case
  val monthsPlusEmpty = Seq("") ++ months

  val monthSelector =
    horizontalDiv(paddingLeft := "10px", columnGap := "5px", "Month", compactSelect(monthsPlusEmpty.map(monthSelectorOption),
      backgroundColor <-- monthSelected.signal.map(handleMonthSelectorChangeAndReturnBackground),
      _.events.onChange.map(_.detail.selectedOption.dataset.get("name")) --> Observer[Option[String]](onNext = o => {
        monthSelected.set(o)
        reportState.update(r => monthTimePeriod(r.client, o, months))
      }))
    )

  def handleMonthSelectorChangeAndReturnBackground(month: Option[String]): String = {
    def setProperty(name: String, color: String) = {
      monthSelector.ref.style.setProperty(name, color)
    }

    month match {
      case Some(_) => {
        val foreground = dom.window.getComputedStyle(timePeriodButton.ref).getPropertyValue("--sapButton_Selected_TextColor")
        // It should be --_ui5_select_label_color but there is a typo in some SAP UI5 version so we set both
        setProperty("--_ui5_select_label_color", foreground)
        setProperty("--_ui5_select_label_olor", foreground)
        segmentedButtonPressed.set(None)
        dom.window.getComputedStyle(timePeriodButton.ref).getPropertyValue("--sapButton_Selected_Background")
      }
      case None => ""
    }
  }

  def timePeriodRow() = horizontalDiv(timePeriodButton, monthSelector, columnGap := "5px", marginTop := "10px")

  val buildReportKeyObserver = Observer[dom.KeyboardEvent](onNext = _ => {
    if (canBeBuilt()) askBuild()
  }).filter(_.key == "Enter")

  def minEndDate(start: DateState, default: LocalDate = ReportState.MinStartDate): LocalDate = start match {
    case d: LocalDate => d
    case _ => default
  }

  def checkDateEvent(d: DateEventData, minDate: => DateState): (ValueState, Option[String]) = {
    if (d.valid) {
      (ValueState.None, None)
    } else {
      DatePickerHelper.parse(d.value).map(d => d.isBefore(minEndDate(minDate))).match {
        case None => (ValueState.Error, Some("Invalid Date"))
        case _ => (ValueState.Error, Some("Too soon"))
      }
    }
  }


  // We keep track of the startDate and endDate below so that when we come back to Custom, the previous selection is still there
  // We could read it again from the graphic component but this is simpler for now
  val minDate: Signal[LocalDate] = reportState.signal.map(rs => rs.client.flatMap(clientInceptionDate).getOrElse(MinStartDate))
  val startDate = Var[DateState](NoDate)
  val startDateWriter = Observer[DateState](onNext = d => startDate.set(d))
  val startDatePicker = DatePickerHelper(checkDateEvent(_, MinStartDate),
    startDateWriter.contramap((d: DateEventData) => DatePickerHelper.parse(d).getOrElse(InvalidDate)),
    _.minDateStr <-- minDate.map(DatePickerHelper.format),
  )
  val startDateDiv = div(labeledElement("Start", startDatePicker, required = true), onKeyPress --> buildReportKeyObserver)

  val endDate = Var[DateState](NoDate)
  val endDateWriter = Observer[DateState](onNext = d => endDate.set(d))

  // We always parse because if not, we do not set the dateState with valid end dates but before start date
  // causing an incoherent UI
  val endDatePicker = DatePickerHelper(checkDateEvent(_, startDate.now()), endDateWriter.contramap((d: DateEventData) => {
    if (d.value.isEmpty) NoDate else DatePickerHelper.parse(d.value).getOrElse(InvalidDate)
  }),
    _.minDateStr <-- startDate.signal.combineWith(minDate).map(t => minEndDate(t._1, t._2)).map(DatePickerHelper.format),
  )

  val endDateDiv = div(labeledElement("End", endDatePicker), onKeyPress --> buildReportKeyObserver)

  def canBeBuilt(): Boolean = {
    reportState.now() match {
      case d: DateInput => d.canBeBuilt()
      case _ => true
    }
  }

  def askBuild(): Unit = {
    updateReportStateFromCustom()
    reportState.now() match {
      case d: DateInput => reportState.set(d.copy(buildAsked = true))
      case _ => println(f"Error, build button pressed when report state is not DateInput but ${reportState.now()}")
    }
  }

  val buildButton = Button(_.design := ButtonDesign.Emphasized,
    "Build Report",
    // ReportState does not change when dates change so we combine startDate and endDate signals to now when we must update
    // the Build button. And at startup, it is disabled as dates are empty
    _.disabled <-- EventStream.merge(startDate.signal.changes, endDate.signal.changes).startWith(true).map(_ =>
      !canBeBuilt()),
    _.events.onClick --> (_ => askBuild()),
  )

  def horizontalDivWithMarginTop(modifiers: Modifier[HtmlElement]*) = horizontalDiv((marginTop := "10px") +: Seq(modifiers: _*): _*)

  val statusMessage = horizontalDivWithMarginTop(children <-- reportState.signal.map(_.statusMessageChildren()))

  def apply() = {
    val cs = reportState.signal.map { r =>
      r match {
        case _: DateInput => Seq(timePeriodRow(), horizontalDivWithMarginTop(startDateDiv, endDateDiv),
          horizontalDivWithMarginTop(buildButton), statusMessage)
        case _ => Seq(timePeriodRow(), statusMessage)
      }
    }
    div(children <-- cs)
  }
}
