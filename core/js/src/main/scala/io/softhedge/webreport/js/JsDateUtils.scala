package io.softhedge.webreport.js

import io.softhedge.webreport.js.{FullDateTimeFormat, FullDateTimeFormatPart}
import org.scalajs.dom.intl.DateTimeFormatOptions

import java.time.format.DateTimeFormatter
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.{typeOf, |}

trait FullDateTimeFormatPart extends js.Object {
  var `type`: js.UndefOr[String] = js.undefined
  var value: js.UndefOr[Int] = js.undefined
}

@js.native
@JSGlobal("Intl.DateTimeFormat")
class FullDateTimeFormat(locales: js.UndefOr[String | js.Array[String]] = js.undefined,
                         options: js.UndefOr[DateTimeFormatOptions] = js.undefined)
  extends js.Object {
  def format(date: js.Date): String = js.native

  // See https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/DateTimeFormat/formatToParts
  def formatToParts(date: js.Date): js.Array[FullDateTimeFormatPart] = js.native

  def resolvedOptions(): DateTimeFormatOptions = js.native

  def supportedLocalesOf(locales: String | js.Array[String], options: js.Any): js.Array[String] = js.native
}

object JsDateUtils {
  // Build using https://stackoverflow.com/questions/43368659/how-to-determine-users-locale-date-format-using-javascript-format-is-dd-mm-or-m
  def getDateFormatString(lang: String = "default") = {
    val formatObj = new FullDateTimeFormat(lang).formatToParts(new js.Date());
    val format = formatObj.map(obj => obj.`type` match {
      case "day" => "dd"
      case "month" => "MM"
      case "year" => "yyyy"
      case _ => obj.value
    }).mkString
    format
  }
}
