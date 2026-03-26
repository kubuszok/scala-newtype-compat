package io.estatico.newtype.compat

import dotty.tools.dotc.plugins.*

class NewTypePlugin extends StandardPlugin:
  val name = "newtype-compat"
  override val description = "Transforms @newtype/@newsubtype annotations for Scala 3 compatibility"

  // Scala 3.3.x uses `init`, newer versions use `initialize`
  override def init(options: List[String]): List[PluginPhase] =
    List(new NewTypePluginPhase)
