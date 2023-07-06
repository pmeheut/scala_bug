import sbt._
import sbt.IO
import java.io.File
import Keys._
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

import scala.sys.process._

object JsBundler {
  def jsBundleSettings(jsProject: Project) = Seq(
    // We extend compiles
    Compile / compile := {
      // First we check what kind of build we using and we link the js project
      scalaJSStage.value match {
        case FastOptStage => jsProject / Compile / compile / fastLinkJS
        case FullOptStage => jsProject / Compile / compile / fullLinkJS
      }
      // We need to evalue a file to really do the link
      (jsProject / Compile / scalaJSLinkedFile).value.data

      // We build the directories named needed by esbuild
      val scalaSuffix = s"/scala-${scalaVersion.value}"
      val webPageRoot = (jsProject / baseDirectory).value
      val webPageTarget = (jsProject / Compile / target).value.getPath + scalaSuffix
      val localTarget = (Compile / target).value.getPath + scalaSuffix
      val jsDir = scalaJSStage.value match {
        case FastOptStage => "webpage-fastopt"
        case FullOptStage => "webpage-opt"
      }
      // We build the command
      val command = s"./node_modules/.bin/esbuild --bundle $webPageTarget/$jsDir/main.js --outdir=$localTarget/classes/jsbundle"
      // And we execute it, ignoring the result. If there is an error, it will be displayed anyway
      Process(command, webPageRoot).!!(ProcessLogger(_ => (), _ => ()))
      IO.copyFile(new File(f"$webPageTarget/webpage-jsdeps.js"), new File(f"$localTarget/classes/jsbundle/deps.js"))
      // We return the new compile
      (Compile / compile).value
    })

  // If a project is a JsBundler, we enable ScalaJSPlugin and we add settings so that compile will build everything
  // allowing the Web Server to serve static files too
  implicit class JsBundlerHelper(p: Project) {
    def jsBundler(jsProject: Project): Project = p.enablePlugins(ScalaJSPlugin).settings(jsBundleSettings(jsProject))
  }
}
