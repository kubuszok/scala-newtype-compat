package io.estatico.newtype.compat

import dotty.tools.dotc.plugins.*
import dotty.tools.dotc.core.Contexts.Context

class NewTypePlugin extends StandardPlugin:
  val name = "newtype-compat"
  override val description = "Transforms @newtype/@newsubtype annotations for Scala 3 compatibility"

  override def initialize(options: List[String])(using Context): List[PluginPhase] =
    List(new NewTypePluginPhase)
