package io.softhedge.webreport.common

import java.time.{Instant, ZoneId}
import java.util.Date
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}

import scala.util.Try

object DateUtils {
  val DatePattern_yyyy_MM_dd = "yyyy-MM-dd"
  private val ScalaJavaTimeFormat_yyyy_MM_dd = java.time.format.DateTimeFormatter.ofPattern(DatePattern_yyyy_MM_dd)

  val DatePattern_MM_dd_yy = "dd-MM-yy"
  private val ScalaJavaTimeFormat_dd_MM_yy = java.time.format.DateTimeFormatter.ofPattern(DatePattern_MM_dd_yy)

  // Format any date in library cquiroz / scala-java-time in yyyy-MM-dd format
  extension (date: java.time.temporal.TemporalAccessor) def format_yyyy_MM_dd = dateTo_yyyy_MM_dd(date)

  extension (date: java.util.Date) def toLocalDate: java.time.LocalDate = Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate()
  extension (localDate: java.time.LocalDate) def toDate: java.util.Date = Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant())

  def dateTo_yyyy_MM_dd(date: java.time.temporal.TemporalAccessor): String = ScalaJavaTimeFormat_yyyy_MM_dd.format(date)

  def dateTo_dd_MM_yy(date: java.time.temporal.TemporalAccessor): String = ScalaJavaTimeFormat_dd_MM_yy.format(date)

  def isValid_yyyy_MM_dd(s: String): Boolean = Try(java.time.LocalDate.parse(s, ScalaJavaTimeFormat_yyyy_MM_dd)).fold(_ => false, _ => true)

  def yyyy_MM_dd_toLocalDate(s: String): java.time.LocalDate = java.time.LocalDate.parse(s, ScalaJavaTimeFormat_yyyy_MM_dd)

  import java.time.YearMonth

  def numberOfDaysInMonth(year: Int, month: Int): Int = {
    val yearMonthObject = YearMonth.of(year, month)
    val daysInMonth = yearMonthObject.lengthOfMonth
    daysInMonth
  }
}