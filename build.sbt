import Dependencies._

ThisBuild / scalaVersion := "3.3.0"

lazy val webPage = project
  .in(file("webPage"))
  .enablePlugins(ScalaJSPlugin, JSDependenciesPlugin)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
    },
    Compile / mainClass := Some("io.softhedge.webreport.pages.WebPage"),
    jsDependencies += plotlyJs.intransitive()./("plotly.min.js").commonJSName("Plotly"),
    libraryDependencies ++= scalaJsCross.value ++ scalaTagsCross.value ++ zioCross.value ++ sttpCross.value ++
      laminarCross.value ++ laminarUI5Cross.value ++ scalajsPlotlyJsCross.value //++ waypointCross.value
).dependsOn(core.js)


lazy val core = crossProject(JSPlatform, JVMPlatform).in(file("core")).
  settings(
    libraryDependencies ++= calibanClientCross.value ++ scalaJavaTimeCross.value ++ nscalaJavaTimeCross.value ++
      circeCross.value ++ munit ++ sttpCross.value
  ).
  jvmSettings(
    // JVM-specific settings
  ).
  jsSettings(
    // JS-specific settings
    libraryDependencies ++= laminextCross.value ++ calibanLaminextCross.value,
  )