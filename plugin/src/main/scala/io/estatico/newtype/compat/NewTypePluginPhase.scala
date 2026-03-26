package io.estatico.newtype.compat

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.plugins.PluginPhase

class NewTypePluginPhase extends PluginPhase:
  val phaseName = "newtype-rewrite"

  override val runsAfter: Set[String] = Set("parser")
  override val runsBefore: Set[String] = Set("typer")

  override def runOn(units: List[CompilationUnit])(using ctx: Context): List[CompilationUnit] =
    val transformer = new NewTypeTreeTransformer
    for unit <- units do
      unit.untpdTree = transformer.transform(unit.untpdTree)(using ctx.withSource(unit.source))
    units

  override def run(using Context): Unit = ()
