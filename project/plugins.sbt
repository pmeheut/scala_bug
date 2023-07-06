addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.12.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.2.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
addSbtPlugin("com.github.ghostdogpr" % "caliban-codegen-sbt" % "2.0.1")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.10.1")
addSbtPlugin("org.scala-js" % "sbt-jsdependencies" % "1.0.2")
addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.17")

addDependencyTreePlugin
// If we need timezones. See build.sbt
//addSbtPlugin("io.github.cquiroz" % "sbt-tzdb" % "1.0.1")
