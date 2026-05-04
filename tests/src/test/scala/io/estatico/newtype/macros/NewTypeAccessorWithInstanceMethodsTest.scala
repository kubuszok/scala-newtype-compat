package io.estatico.newtype.macros

import io.estatico.newtype.ops._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/*
 * Regression: a `@newtype` that declares instance methods should still expose the constructor-
 * parameter accessor on values of the type.
 *
 * The plugin's `buildOpsCodeFromMethods` is invoked when the @newtype body is non-empty, but it
 * only emits the user's instance methods inside `Ops$$newtype` — it does *not* add the
 * `def <reprName>: Repr` accessor that the no-methods branch emits. Result: a value of a
 * @newtype with instance methods cannot read its own constructor parameter from outside.
 *
 * In chili this manifests as `value value is not a member of Email.Type`:
 *
 *     @newtype final case class Email(value: RefinedEmailString) {
 *       def domain: Option[EmailDomain] = ...     // instance method present
 *     }
 *     object Email {
 *       implicit lazy val show: Show[Email] = Show.show(_.value.value)   // .value fails to resolve
 *     }
 */
class NewTypeAccessorWithInstanceMethodsTest extends AnyFlatSpec with Matchers {

  import NewTypeAccessorWithInstanceMethodsTest._

  behavior of "@newtype with instance methods"

  it should "expose the constructor accessor alongside the instance methods" in {
    val e = Email("user@example.com")
    e.value shouldBe "user@example.com"
    e.domain shouldBe Some("example.com")
  }

  it should "let typeclass instances built around the accessor compile" in {
    Email.show.toString(Email("user@example.com")) shouldBe "user@example.com"
  }

  it should "not interfere with .coerce" in {
    Email("a").coerce[String] shouldBe "a"
  }
}

object NewTypeAccessorWithInstanceMethodsTest {

  trait ShowToString[A] {
    def toString(a: A): String
  }

  @newtype final case class Email(value: String) {
    // Instance methods reference `value` internally — that already works via `rewriteBodyStr`.
    def domain: Option[String] = value.split('@').toList.lastOption
    def isEmpty: Boolean = value.isEmpty
  }
  object Email {
    // The companion accesses `.value` from the outside — this is what currently fails.
    implicit val show: ShowToString[Email] = (e: Email) => e.value
  }
}