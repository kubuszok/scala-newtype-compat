package io.estatico.newtype.compat

import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.Context

case class NewTypeParams(
  optimizeOps: Boolean = true,
  unapply: Boolean = false,
  debug: Boolean = false,
  debugRaw: Boolean = false,
  isSubtype: Boolean = false
)

object AnnotationParams:

  /** Extract NewTypeParams from an annotation tree. */
  def extract(annotation: Tree, isSubtype: Boolean)(using Context): NewTypeParams =
    val base = NewTypeParams(isSubtype = isSubtype)
    extractArgs(annotation) match
      case Some(args) => parseArgs(base, args)
      case None => base

  private def extractArgs(tree: Tree): Option[List[Tree]] = tree match
    case New(_)              => Some(Nil)
    case Apply(_, args)      => Some(args)
    case Select(inner, _)    => extractArgs(inner)
    case _                   => None

  private def parseArgs(base: NewTypeParams, args: List[Tree]): NewTypeParams =
    args.foldLeft(base) { (params, arg) =>
      arg match
        case NamedArg(name, Literal(constant)) =>
          val value = constant.booleanValue
          name.toString match
            case "optimizeOps" => params.copy(optimizeOps = value)
            case "unapply"     => params.copy(unapply = value)
            case "debug"       => params.copy(debug = value)
            case "debugRaw"    => params.copy(debugRaw = value)
            case _             => params
        case _ => params
    }

  /** Check if the annotation name matches newtype or newsubtype. Returns Some(isSubtype). */
  def isNewTypeAnnotation(annotation: Tree): Option[Boolean] =
    annotationName(annotation).flatMap {
      case "newtype"    => Some(false)
      case "newsubtype" => Some(true)
      case _            => None
    }

  private def annotationName(tree: Tree): Option[String] = tree match
    case New(Ident(name))              => Some(name.toString)
    case New(Select(_, name))          => Some(name.toString)
    case Apply(inner, _)               => annotationName(inner)
    case Select(inner, _)              => annotationName(inner)
    case _                             => None
