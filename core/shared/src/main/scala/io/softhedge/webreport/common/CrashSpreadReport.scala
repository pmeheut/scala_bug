package io.softhedge.webreport.common

import zio.{RIO, Task, ZIO}

import java.time.*
import scala.concurrent.Future

final case class ClientInformation(name: String, inceptionDate: java.time.LocalDate)

final case class CrashSpreadClientsInformation(clients: List[ClientInformation])

final case class CrashSpreadSummary(client: String, monthToDatePnL: Double, monthToDateDrawdown: Double, yearToDatePnL: Double, yearToDateDrawdown: Double)

final case class DailyPnL(date: java.time.LocalDate, pnl: Double)

final case class CrashSpeadCumulativePnLHistoryParameters(client: String, start: java.time.LocalDate, end: Option[java.time.LocalDate])

final case class CrashSpeadCumulativePnLHistory(client: String, start: java.time.LocalDate, end: java.time.LocalDate,
                                                pnl: Double, drawdown: Double, fees: Double, cumulativePnL: List[DailyPnL])

trait CrashSpreadReportService {
  def clientsInformation(): Task[CrashSpreadClientsInformation]

  def summary(client: String): Task[CrashSpreadSummary]

  def cumulativePnLHistory(parameters: CrashSpeadCumulativePnLHistoryParameters): Task[CrashSpeadCumulativePnLHistory]
}