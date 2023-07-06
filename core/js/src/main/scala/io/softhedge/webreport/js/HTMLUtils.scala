package io.softhedge.webreport.js

object HTMLUtils {
  val SavedPropertyName = "display_when_shown"
  val NoDisplay = "none"
  extension (e: org.scalajs.dom.HTMLElement) def hide(): Unit = {
    val currentDisplay = e.style.getPropertyValue("display")
    if (currentDisplay != NoDisplay) {
      e.dataset(SavedPropertyName) = currentDisplay
      e.style.display = NoDisplay
    }
  }

  extension (e: org.scalajs.dom.HTMLElement) def show(): Unit = {
    val previousDisplay = e.dataset.getOrElse(SavedPropertyName, "")
    e.style.setProperty("display", previousDisplay)
  }
}
