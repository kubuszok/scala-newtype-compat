package io.estatico.newtype.compat

import dotty.tools.dotc.ast.{Trees, untpd}
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.core.Constants.Constant
import dotty.tools.dotc.core.Decorators.*
import dotty.tools.dotc.core.Flags.*
import dotty.tools.dotc.core.Names.*
import dotty.tools.dotc.core.StdNames.*
import dotty.tools.dotc.parsing.Parsers
import dotty.tools.dotc.util.SourceFile

import scala.collection.mutable.ListBuffer

private val AnsiColorRegex = "\u001b\\[[0-9;]*m".r

class NewTypeTreeTransformer extends UntypedTreeMap:

  /** Transform a list of statements, expanding @newtype/@newsubtype classes. */
  private def transformStats(stats: List[Tree])(using Context): List[Tree] =
    val statsList = stats.toIndexedSeq
    // Pre-scan: for each @newtype TypeDef, find a ModuleDef anywhere later in the same
    // statement list whose term-name matches. Macro paradise on Scala 2 finds companions via the
    // typer's symbol lookup, which works regardless of intervening `type`/`val` decls. We need to
    // emulate that here — using only the immediately-next stat is too narrow for codebases that
    // place companion objects after `type` aliases (a common idiom inside `package object`s).
    val consumedCompanion = scala.collection.mutable.Set.empty[Int]
    val companionForNewtype = scala.collection.mutable.Map.empty[Int, Int]
    for i <- statsList.indices do
      statsList(i) match
        case td: TypeDef if td.rhs.isInstanceOf[Template] && findNewTypeAnnotation(td).isDefined =>
          val target = td.name.toTermName
          var j = i + 1
          var found = -1
          while j < statsList.length && found < 0 do
            statsList(j) match
              case md: ModuleDef if md.name == target && !consumedCompanion(j) =>
                found = j
              case _ =>
            j += 1
          if found >= 0 then
            companionForNewtype(i) = found
            consumedCompanion += found
        case _ =>

    val result = ListBuffer.empty[Tree]
    var i = 0
    while i < statsList.length do
      if consumedCompanion(i) then
        i += 1
      else
        statsList(i) match
          case td: TypeDef if td.rhs.isInstanceOf[Template] =>
            findNewTypeAnnotation(td) match
              case Some((annotation, isSubtype)) =>
                val params = AnnotationParams.extract(annotation, isSubtype)
                val companionOpt = companionForNewtype.get(i)
                  .map(j => statsList(j).asInstanceOf[ModuleDef])
                val expanded = expandNewType(td, companionOpt, params)
                result ++= expanded
              case None =>
                result += transform(statsList(i))
          case _ =>
            result += transform(statsList(i))
        i += 1
    result.toList

  /** Find @newtype or @newsubtype annotation on a TypeDef. */
  private def findNewTypeAnnotation(td: TypeDef)(using Context): Option[(Tree, Boolean)] =
    td.mods.annotations.flatMap { ann =>
      AnnotationParams.isNewTypeAnnotation(ann).map(isSubtype => (ann, isSubtype))
    }.headOption

  /** Expand a @newtype/@newsubtype class into a type alias + companion object. */
  private def expandNewType(
    td: TypeDef,
    companionOpt: Option[ModuleDef],
    params: NewTypeParams
  )(using Context): List[Tree] =
    val name = td.name
    val nameStr = name.toString
    val termNameStr = nameStr

    val tmpl = td.rhs.asInstanceOf[Template]
    val constr = tmpl.constr

    // Extract type parameters from the class
    val tparams: List[TypeDef] = constr.leadingTypeParams
    val tparamNames = tparams.map(_.name.toString)

    // Extract constructor parameters — must be exactly one parameter list with one param
    val vparamss = constr.trailingParamss
    val (reprNameStr, reprTypeStr, isPrivate) = vparamss match
      case List(List(vd: ValDef)) =>
        val priv = vd.mods.is(Private) || vd.mods.is(Protected)
        (vd.name.toString, plain(vd.tpt), priv)
      case _ =>
        throw new IllegalArgumentException(
          s"@${if params.isSubtype then "newsubtype" else "newtype"} $nameStr must have exactly one parameter"
        )

    // Extract instance methods from the class body (excluding constructor)
    val instanceMethods = tmpl.body.collect {
      case dd: DefDef if dd.name != nme.CONSTRUCTOR => dd
      case vd: ValDef => vd
    }

    // Build type parameter strings
    val tparamsDecl = if tparams.isEmpty then "" else {
      val ps = tparams.map { tp =>
        val variance = if tp.mods.is(Covariant) then "+" else if tp.mods.is(Contravariant) then "-" else ""
        val bounds = tp.rhs match
          case TypeBoundsTree(lo, hi, _) =>
            val loStr = if lo.isEmpty then "" else s" >: ${plain(lo)}"
            val hiStr = if hi.isEmpty then "" else s" <: ${plain(hi)}"
            loStr + hiStr
          case _ => ""
        s"$variance${tp.name}$bounds"
      }
      s"[${ps.mkString(", ")}]"
    }
    val tparamsRef = if tparams.isEmpty then "" else s"[${tparamNames.mkString(", ")}]"
    val reprTypeRef = if tparams.isEmpty then "Repr" else s"Repr$tparamsRef"

    // Build the base type
    val baseType = if params.isSubtype then reprTypeRef
    else s"Any { type ${nameStr}$$newtype }"

    // Build Ops class for accessor or instance methods
    val opsCode = if instanceMethods.nonEmpty && params.optimizeOps then
      buildOpsCodeFromMethods(nameStr, reprNameStr, reprTypeStr, instanceMethods, tparams)
    else if !isPrivate && params.optimizeOps then
      s"""
         |  implicit class Ops$$$$newtype$tparamsDecl(val $$this$$: Type$tparamsRef) {
         |    def $reprNameStr: $reprTypeStr = $$this$$.asInstanceOf[$reprTypeStr]
         |  }""".stripMargin
    else ""

    // Build unapply
    val unapplyCode = if params.unapply then
      s"""
         |  def unapply$tparamsDecl(x: Type$tparamsRef): Some[$reprTypeRef] = Some(x.asInstanceOf[$reprTypeRef])""".stripMargin
    else ""

    // Build deriving
    val derivingCode = if tparams.isEmpty then
      s"  def deriving[TC[_]](implicit ev: TC[Repr]): TC[Type] = ev.asInstanceOf[TC[Type]]"
    else {
      val tpNames = tparamNames.mkString(", ")
      s"  def deriving[$tpNames, TC[_]](implicit ev: TC[Repr$tparamsRef]): TC[Type$tparamsRef] = ev.asInstanceOf[TC[Type$tparamsRef]]"
    }

    // Build derivingK
    val derivingKCode = if tparams.nonEmpty then
      s"""
         |  def derivingK[TC[_[_]]](implicit ev: TC[Repr]): TC[Type] = ev.asInstanceOf[TC[Type]]""".stripMargin
    else ""

    // Coercible implicits: for parameterized types, use def with type params
    val C = "_root_.io.estatico.newtype.Coercible"
    val coercibleImplicits = if tparams.isEmpty then
      s"""  implicit val unsafeWrap: $C[Repr, Type] = $C.instance[Repr, Type]
         |  implicit val unsafeUnwrap: $C[Type, Repr] = $C.instance[Type, Repr]
         |  implicit def unsafeWrapM[M[_]]: $C[M[Repr], M[Type]] = $C.instance[M[Repr], M[Type]]
         |  implicit def unsafeUnwrapM[M[_]]: $C[M[Type], M[Repr]] = $C.instance[M[Type], M[Repr]]
         |  implicit val cannotWrapArrayAmbiguous1: $C[Array[Repr], Array[Type]] = $C.instance[Array[Repr], Array[Type]]
         |  implicit val cannotWrapArrayAmbiguous2: $C[Array[Repr], Array[Type]] = $C.instance[Array[Repr], Array[Type]]
         |  implicit val cannotUnwrapArrayAmbiguous1: $C[Array[Type], Array[Repr]] = $C.instance[Array[Type], Array[Repr]]
         |  implicit val cannotUnwrapArrayAmbiguous2: $C[Array[Type], Array[Repr]] = $C.instance[Array[Type], Array[Repr]]""".stripMargin
    else
      // For parameterized newtypes, combine type params with M[_] in one bracket
      val tpNames = tparamNames.mkString(", ")
      val tpMDecl = s"[${tpNames}, M[_]]" // e.g. [A, M[_]]
      s"""  implicit def unsafeWrap$tparamsDecl: $C[Repr$tparamsRef, Type$tparamsRef] = $C.instance[Repr$tparamsRef, Type$tparamsRef]
         |  implicit def unsafeUnwrap$tparamsDecl: $C[Type$tparamsRef, Repr$tparamsRef] = $C.instance[Type$tparamsRef, Repr$tparamsRef]
         |  implicit def unsafeWrapM$tpMDecl: $C[M[Repr$tparamsRef], M[Type$tparamsRef]] = $C.instance[M[Repr$tparamsRef], M[Type$tparamsRef]]
         |  implicit def unsafeUnwrapM$tpMDecl: $C[M[Type$tparamsRef], M[Repr$tparamsRef]] = $C.instance[M[Type$tparamsRef], M[Repr$tparamsRef]]
         |  implicit def cannotWrapArrayAmbiguous1$tparamsDecl: $C[Array[Repr$tparamsRef], Array[Type$tparamsRef]] = $C.instance[Array[Repr$tparamsRef], Array[Type$tparamsRef]]
         |  implicit def cannotWrapArrayAmbiguous2$tparamsDecl: $C[Array[Repr$tparamsRef], Array[Type$tparamsRef]] = $C.instance[Array[Repr$tparamsRef], Array[Type$tparamsRef]]
         |  implicit def cannotUnwrapArrayAmbiguous1$tparamsDecl: $C[Array[Type$tparamsRef], Array[Repr$tparamsRef]] = $C.instance[Array[Type$tparamsRef], Array[Repr$tparamsRef]]
         |  implicit def cannotUnwrapArrayAmbiguous2$tparamsDecl: $C[Array[Type$tparamsRef], Array[Repr$tparamsRef]] = $C.instance[Array[Type$tparamsRef], Array[Repr$tparamsRef]]""".stripMargin

    // Generate the source code
    val source = s"""
      |type $nameStr$tparamsDecl = $nameStr.Type$tparamsRef
      |object $nameStr {
      |  type Repr$tparamsDecl = $reprTypeStr
      |  type Base$tparamsDecl = $baseType
      |  trait Tag$tparamsDecl extends Any
      |  type Type$tparamsDecl <: Base$tparamsRef & Tag$tparamsRef
      |
      |  def apply$tparamsDecl($reprNameStr: $reprTypeStr): Type$tparamsRef = $reprNameStr.asInstanceOf[Type$tparamsRef]
      |$unapplyCode
      |$coercibleImplicits
      |$opsCode
      |$derivingCode
      |$derivingKCode
      |}""".stripMargin

    if params.debug then
      println(s"[newtype-compat] Generated source for @${if params.isSubtype then "newsubtype" else "newtype"} $nameStr:")
      source.linesIterator.foreach(line => println(s"[newtype-compat]   $line"))

    // Parse the generated source code into untyped trees, then splice the companion's body
    // structurally (rather than via tree.show round-trip — which emits the `module` soft keyword
    // for nested objects and produces source that won't reparse).
    val stats = parseStats(source)
    companionOpt match
      case None => stats
      case Some(companion) =>
        val companionTermName = name.toTermName
        stats.map {
          case md: ModuleDef if md.name == companionTermName =>
            // Splice the companion's full Template into the synthesized one. We need to carry
            // over `parents` (e.g. `extends WithGenericSalesforceId[...]`), `derived` (any
            // `derives` clauses), and `self` from the original — the synthesized object only
            // provides default-empty values for those. Body members from both are concatenated.
            val mergedImpl = cpy.Template(md.impl)(
              parents = if companion.impl.parents.nonEmpty then companion.impl.parents else md.impl.parents,
              derived = if companion.impl.derived.nonEmpty then companion.impl.derived else md.impl.derived,
              self    = if companion.impl.self != md.impl.self then companion.impl.self else md.impl.self,
              body    = md.impl.body ++ companion.impl.body
            )
            untpd.ModuleDef(md.name, mergedImpl).withMods(md.mods).withSpan(md.span)
          case other => other
        }

  private def buildOpsCodeFromMethods(
    nameStr: String, reprNameStr: String, reprTypeStr: String,
    methods: List[Tree], tparams: List[TypeDef]
  )(using Context): String =
    val tparamsDecl = if tparams.isEmpty then "" else {
      s"[${tparams.map(tp => tp.name.toString).mkString(", ")}]"
    }
    val tparamsRef = tparamsDecl

    val methodStrs = methods.map {
      case dd: DefDef =>
        val methodParams = dd.paramss.map { paramClause =>
          paramClause.map {
            case vd: ValDef => s"${vd.name}: ${plain(vd.tpt)}"
            case td: TypeDef => plain(td)
          }.mkString(", ")
        }.map(p => s"($p)").mkString
        val retType = if dd.tpt.isEmpty then "" else s": ${plain(dd.tpt)}"
        val body = rewriteBodyStr(dd.rhs, reprNameStr, reprTypeStr)
        s"    def ${dd.name}$methodParams$retType = $body"
      case vd: ValDef =>
        val retType = if vd.tpt.isEmpty then "" else s": ${plain(vd.tpt)}"
        val body = rewriteBodyStr(vd.rhs, reprNameStr, reprTypeStr)
        s"    def ${vd.name}$retType = $body"
    }

    s"""
       |  implicit class Ops$$$$newtype$tparamsDecl(val $$this$$: Type$tparamsRef) {
       |${methodStrs.mkString("\n")}
       |  }""".stripMargin

  /** Rewrite method body string: replace references to constructor param with $this$.asInstanceOf[ReprType] */
  private def rewriteBodyStr(body: Tree, reprNameStr: String, reprTypeStr: String)(using Context): String =
    // Show the body as a string, then textually replace the param reference
    // This is a simple approach; for complex cases we'd need proper tree rewriting
    val bodyStr = plain(body)
    // Replace standalone references to the repr param with the cast expression
    // Use word boundary matching to avoid replacing partial matches
    bodyStr.replaceAll(s"\\b${java.util.regex.Pattern.quote(reprNameStr)}\\b", s"\\$$this\\$$.asInstanceOf[$reprTypeStr]")

  /** Strip ANSI color codes from .show output */
  private def plain(tree: Tree)(using Context): String =
    AnsiColorRegex.replaceAllIn(tree.show, "")

  /** Parse a string of Scala statements into a list of trees. */
  private var counter = new java.util.concurrent.atomic.AtomicLong(0)

  private def parseStats(source: String)(using Context): List[Tree] =
    val virtualSource = SourceFile.virtual(s"<newtype-generated-${counter.incrementAndGet()}>", source)
    val parser = new Parsers.Parser(virtualSource)
    val tree = parser.parse()
    // The parser returns a PackageDef; extract its stats
    val stats = tree match
      case PackageDef(_, stats) => stats
      case _ => List(tree)
    // Strip positions to avoid confusing the compiler when trees from virtual sources
    // are inserted into the real source's tree
    stats.map(stripPositions)

  /** Recursively strip source positions from all trees to avoid position conflicts. */
  private def stripPositions(tree: Tree)(using Context): Tree =
    import dotty.tools.dotc.util.Spans.NoSpan
    new UntypedTreeMap:
      override def transform(tree: Tree)(using Context): Tree =
        val t = super.transform(tree)
        if t.isEmpty then t else t.withSpan(NoSpan)
    .transform(tree)

  // --- Override tree traversal to handle stats rewriting ---

  override def transform(tree: Tree)(using Context): Tree = tree match
    case PackageDef(pid, stats) =>
      val transformedPid = transform(pid).asInstanceOf[RefTree]
      stats.partition { case md: ModuleDef => md.mods.is(Package); case _ => false } match
        case (pkgObj :: Nil, otherStats) =>
          val md = pkgObj.asInstanceOf[ModuleDef]
          val innerBody = transformStats(md.impl.body)
          val allInnerStats = otherStats.map(transform) ++ innerBody
          val newPid = Select(transformedPid, md.name)
          untpd.PackageDef(newPid, allInnerStats)
        case _ =>
          cpy.PackageDef(tree)(transformedPid, transformStats(stats))
    case Block(stats, expr) =>
      cpy.Block(tree)(transformStats(stats), transform(expr))
    case td @ TypeDef(name, rhs: Template) if !findNewTypeAnnotation(td).isDefined =>
      // Regular class/trait — recurse into body
      val newRhs = cpy.Template(rhs)(
        constr = transformSub(rhs.constr),
        parents = transform(rhs.parents),
        derived = transform(rhs.derived),
        self = transformSub(rhs.self),
        body = transformStats(rhs.body)
      )
      cpy.TypeDef(tree)(name, newRhs)
    case md: ModuleDef =>
      val newImpl = cpy.Template(md.impl)(
        constr = transformSub(md.impl.constr),
        parents = transform(md.impl.parents),
        derived = transform(md.impl.derived),
        self = transformSub(md.impl.self),
        body = transformStats(md.impl.body)
      )
      // Preserve the original modifiers (incl. the package-object marker) — without `withMods`,
      // a `package object` would be reconstructed as a regular `object`, breaking sub-packages
      // and parent-package-object scope inheritance.
      untpd.ModuleDef(md.name, newImpl).withMods(md.mods).withSpan(md.span)
    case _ =>
      super.transform(tree)
