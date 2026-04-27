package io.estatico.newtype.macros

import io.estatico.newtype.ops._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/*
 * Regression: `@newtype` with a companion whose body contains a nested `object`.
 *
 * The plugin merges the existing companion body by calling `tree.show` on each member of
 * `companionOpt.impl.body`, then splicing the resulting strings into a generated source that
 * gets re-parsed by `Parsers.Parser`. For nested `object`s, Dotty's untyped `tree.show` emits the
 * `module` soft keyword (e.g. `module object Patterns { ... }`), which is not valid Scala source.
 * The resulting source fails parsing inside `<newtype-generated-N>`.
 *
 * Example failure mode when cross-compiling to Scala 3:
 *
 *     [error] <newtype-generated-84>:35:10: end of statement expected but 'object' found
 *     [error]   module object Predef {
 *
 * A fix likely needs to render companion body members through a printer that does not emit the
 * `module` modifier, or rebuild the companion structurally rather than via string splicing.
 */
class NewTypeCompanionWithNestedObjectTest extends AnyFlatSpec with Matchers {

  import NewTypeCompanionWithNestedObjectTest._

  behavior of "@newtype companion containing a nested object"

  it should "preserve a nested object declared in the companion" in {
    val s = Phone("+1 555 0100")
    s.coerce[String] shouldBe "+1 555 0100"
    Phone.Patterns.us shouldBe "^\\+1.*"
    Phone.fromString("+1 555 0100").coerce[String] shouldBe "+1 555 0100"
  }
}

object NewTypeCompanionWithNestedObjectTest {
  @newtype final case class Phone(value: String)
  object Phone {
    object Patterns {
      val us: String = "^\\+1.*"
      val intl: String = "^\\+\\d{1,3}.*"
    }
    def fromString(s: String): Phone = Phone(s)
  }
}