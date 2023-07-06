package io.softhedge.webreport.pages

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.DatePicker.events.DateEventData
import be.doeraene.webcomponents.ui5.configkeys.*
import com.github.nscala_java_time.time.Imports.*
import com.raquo.domtypes.generic.Modifier
import com.raquo.domtypes.generic.keys.Style
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.softhedge.webreport.js.JsDateUtils
import org.scalajs
import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.dom.intl.{DateTimeFormat, DateTimeFormatOptions}
import zio.Cause

import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object MailHelper {
  val MaintenanceContact = "contact@sofhedge.io"
  val MaintenanceContactHref = f"mailto:${MaintenanceContact}"
}

// Used to add and remove class from a Node. Not used yet
object ClassStateHelper {
  type ClassState = Var[Set[String]]

  def apply(initialClasses: String*): ClassState = Var[Set[String]](Set(initialClasses: _*))

  extension (c: ClassState) def add(classes: String*): Unit = c.set(c.now() ++ Set(classes: _*))
  extension (c: ClassState) def remove(classes: String*): Unit = c.set(c.now() -- Set(classes: _*))
  extension (c: ClassState) def classesSignal: Signal[Seq[String]] = c.signal.map(_.toSeq)
}

object DomHelper {
  val OkColor = "#008000"
  val ErrorColor = "#C00000"
  val UI5CompactClass = "ui5-content-density-compact"

  def labeledElement(labelText: String, e: HtmlElement, required: Boolean = false) = {
    val labelClass = if (required) Seq(cls := "required") else Seq()
    val labelComponent = label(labelText, labelClass)
    div(display := "flex", flexDirection := "column", alignItems := "left", labelComponent, e)
  }

  def horizontalDiv(children: Modifier[HtmlElement]*) = {
    div(display := "flex", flexDirection := "row", flexWrap := "wrap", columnGap := "20px", alignItems := "center", children)
  }

  def compactButton(modifiers: Button.ComponentMod*) = {
    Button((cls := UI5CompactClass) +: Seq(modifiers: _*): _*)
  }

  def compactSelect(modifiers: Select.ComponentMod*) = {
    Select((cls := UI5CompactClass) +: Seq(modifiers: _*): _*)
  }

  def centeredSpan(children: Modifier[HtmlElement]*) = {
    span(display := "inline-flex", alignItems := "center", children)
  }

  def divFlexRowElement(text: String) = div(margin := "5px 15px 5px 0px", text)
}

object DatePickerHelper {
  type ValidChecker = DateEventData => (ValueState, Option[String])

  val MinDate = LocalDate(1, 1, 1) // LocalDate.MIN does not work because the year is negative
  val dateFormat = JsDateUtils.getDateFormatString()
  val formatter = DateTimeFormatter.ofPattern(dateFormat)

  type Mods = DatePicker.ModFunction | Mod[ReactiveHtmlElement[DatePicker.Ref]]

  def format(d: LocalDate): String = formatter.format(d)

  def parse(s: String): Option[LocalDate] = Try(LocalDate.parse(s, formatter)).toOption

  def parse(s: DateEventData): Option[LocalDate] = if (s.valid) parse(s.value) else None

  def apply(validChecker: ValidChecker, mods: Mods*): HtmlElement = {
    val dateEventBus: EventBus[DateEventData] = EventBus()
    val defaultMods: Seq[Mods] = Seq(_.placeholder := dateFormat,
      _.formatPattern := dateFormat,
      _.slots.valueStateMessage <-- dateEventBus.events.map(e => validChecker(e)._2.map(div(_)).getOrElse(div())),
      _.valueState <-- dateEventBus.events.map(e => validChecker(e)._1),
      _.events.onChange.map(_.detail) --> dateEventBus.writer,
      // We do not display "Invalid date" when typing but if it is correct, we remove any such message
      _.events.onInput.map(_.detail).filter(_.valid) --> dateEventBus.writer
    )
    val allMods = defaultMods ++ mods
    DatePicker.apply(allMods: _*)
  }

  def apply(validChecker: ValidChecker, allEventsObserver: Observer[DateEventData], mods: Mods*): HtmlElement = {
    val allDatesMods: Seq[Mods] = Seq(
      _.events.onChange.map(_.detail) --> allEventsObserver,
      _.events.onInput.map(_.detail) --> allEventsObserver,
    )
    apply(validChecker, allDatesMods ++ mods: _*)
  }
}

object HTMLHelper {
  def isHTML(text: String): Boolean = {
    import org.scalajs.dom.*;
    val parser = new DOMParser()
    val doc = parser.parseFromString(text, MIMEType.`text/html`)
    doc match {
      case h: HTMLDocument => h.body.childNodes.exists(_.nodeType == 1)
      case _ => false
    }
  }
}

class ErrorDialog() {

  import DomHelper.*

  val details = Var(Option.empty[String])

  def dialog() = {
    Dialog(
      _.showFromEvents(details.signal.changes.filter(_.nonEmpty).mapTo(())),
      _.closeFromEvents(details.signal.changes.filter(_.isEmpty).mapTo(())),
      section(detailsNodes()),
      _.slots.header := horizontalDiv(columnGap := "10px", Icon(_.name := IconName.`quality-issue`, color := ErrorColor, width := "1.5rem", height := "1.5rem"),
        Title(_.level := TitleLevel.H2, "Error details"),
        marginTop := "10px", marginBottom := "10px"),
      _.slots.footer := div(
        div(flex := "1"),
        Button(
          _.design := ButtonDesign.Emphasized,
          "Close",
          _.events.onClick.mapTo(None) --> details
        )
      )
    )
  }

  def buttonAndDialog(detailsText: String) = Seq(button(detailsText), dialog())

  protected def button(detailsText: String) = Button(_.icon := IconName.`technical-object`, "Show error details", _.events.onClick.mapTo(Some(detailsText)) --> details)

  protected def detailsNodes(): Seq[Node] = Seq(div(children <-- details.signal.map(t => t.map(text => Seq(div(text))).getOrElse(Seq())),
    width := "80em", height := "10em", overflow := "auto", whiteSpace := "pre-wrap"))
}
