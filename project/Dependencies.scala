import sbt._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

object Dependencies {
  val calibanVersion = "2.0.1"

  val sttpVersion = "3.8.3"

  val zioVersion = "2.0.5"
  val zioInteropCatsVersion = "22.0.0.0"
  val zioMagicVersion = "0.3.12"
  val zioLoggingVersion = "2.1.5"
  val zhttpVersion = "2.0.0-RC10"

  val munitVersion = "1.0.0-M7"

  val jtdsVersion = "1.3.1"
  val quillJdbcVersion = "4.6.0.1"
  val slf4jNopVersion = "2.0.5"
  val javaActivationVersion = "1.2.0"

  val scoptVersion = "4.1.0"

  val scalaJavaTimeVersion = "2.4.0"
  val nscalaJavaTimeVersion = "0.1.1"
  val scalaJsVersion = "2.3.0"
  val scalaTagsVersion = "0.12.0"
  val circeVersion = "0.14.3"

  val laminarVersion = "0.14.5"
  val laminextVersion = "0.14.3"
  val laminarUI5Version = "1.9.0"
  val waypointVersion = "6.0.0"
  val scalajsPlotlyJsVersion = "1.6.2"
  val plotlyJsVersion = "2.12.1"

  val scalaJavaTimeCross = Def.setting(Seq("io.github.cquiroz" %%% "scala-java-time" % scalaJavaTimeVersion))
  val nscalaJavaTimeCross = Def.setting(Seq("io.github.pmeheut" %%% "nscala-java-time" % nscalaJavaTimeVersion))
  val calibanClientCross = Def.setting(Seq("com.github.ghostdogpr" %%% "caliban-client" % Dependencies.calibanVersion))
  val calibanLaminextCross = Def.setting(Seq("com.github.ghostdogpr" %%% "caliban-client-laminext" % Dependencies.calibanVersion))
  val calibanCore = Seq("com.github.ghostdogpr" %% "caliban" % calibanVersion)

  val calibanServer = calibanCore ++ Seq(
    "com.github.ghostdogpr" %% "caliban-tapir" % calibanVersion,
    "com.github.ghostdogpr" %% "caliban-zio-http" % calibanVersion
  )

  val circeCross = Def.setting(Seq(
    "io.circe" %%% "circe-generic" % circeVersion,
    "io.circe" %%% "circe-parser" % circeVersion
  ))

  val scalaJsCross = Def.setting(Seq("org.scala-js" %%% "scalajs-dom" % scalaJsVersion))

  val scalaTagsCross = Def.setting(Seq("com.lihaoyi" %%% "scalatags" % scalaTagsVersion))

  val zioMagic = Seq("io.github.kitlangton" %% "zio-magic" % zioMagicVersion)

  val tests = Seq(
    "dev.zio" %% "zio-test" % zioVersion,
    "dev.zio" %% "zio-test-sbt" % zioVersion,
    "dev.zio" %% "zio-test-magnolia" % zioVersion,
    "dev.zio" %% "zio-interop-cats" % zioInteropCatsVersion
  )

  val calibanHttp4s = Seq(
    "com.github.ghostdogpr" %% "caliban-http4s" % calibanVersion
  )

  val sttpCross = Def.setting(Seq(
    "com.softwaremill.sttp.client3" %%% "core" % sttpVersion,
    "com.softwaremill.sttp.client3" %%% "circe" % sttpVersion,
    "com.softwaremill.sttp.client3" %%% "zio" % sttpVersion
  ))

  val zioCross = Def.setting(Seq("dev.zio" %%% "zio" % zioVersion))

  val zioLogging = Seq("dev.zio" %% "zio-logging" % zioLoggingVersion)

  val zhttp = Seq("io.d11" %% "zhttp" % zhttpVersion)

  val munit = Seq("org.scalameta" %% "munit" % munitVersion % Test)

  val jdbc = Seq("net.sourceforge.jtds" % "jtds" % jtdsVersion,
    "io.getquill" %% "quill-jdbc-zio" % quillJdbcVersion,
    "org.slf4j" % "slf4j-nop" % slf4jNopVersion, // to avoid the message about missing class and defaulting to nop
    "javax.activation" % "javax.activation-api" % javaActivationVersion, // needed for timebase & jdk 11
  )

  val scopt = Seq("com.github.scopt" %% "scopt" % scoptVersion)

  val laminarCross = Def.setting(Seq("com.raquo" %%% "laminar" % laminarVersion))

  val laminarUI5Cross = Def.setting(Seq("be.doeraene" %%% "web-components-ui5" % laminarUI5Version))

  val laminextCross = Def.setting(Seq("io.laminext" %%% "core" % laminextVersion,
    "io.laminext" %%% "fetch" % laminextVersion,
    "io.laminext" %%% "fetch-circe" % laminextVersion,
    "io.laminext" %%% "websocket" % laminextVersion,
    "io.laminext" %%% "websocket-circe" % laminextVersion
  ))

  val waypointCross = Def.setting(Seq("com.raquo" %%% "waypoint" % waypointVersion))

  val scalajsPlotlyJsCross =  Def.setting(Seq("org.openmole" %%% "scala-js-plotlyjs" % scalajsPlotlyJsVersion))

  val plotlyJs = "org.webjars.npm" % "plotly.js-dist-min" % plotlyJsVersion
}
