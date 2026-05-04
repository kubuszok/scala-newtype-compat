package io.estatico.newtype

import io.estatico.newtype.macros.newtype
import io.estatico.newtype.ops._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.string.MatchesRegex
import eu.timepit.refined.refineV
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class NewTypeRefinedTest extends AnyFlatSpec with Matchers {

  import NewTypeRefinedTest._

  behavior of "@newtype with refined types"

  it should "wrap a refined String" in {
    val name = NonEmptyName(refineV[NonEmpty].unsafeFrom("Alice"))
    name.value.value shouldBe "Alice"
  }

  it should "coerce refined newtypes" in {
    val name = NonEmptyName(refineV[NonEmpty].unsafeFrom("Bob"))
    name.coerce[String Refined NonEmpty].value shouldBe "Bob"
  }

  it should "wrap a refined Int" in {
    val id = PositiveId(refineV[Positive].unsafeFrom(42))
    id.value.value shouldBe 42
  }

  it should "allow constructing with refineV" in {
    val refined = refineV[NonEmpty].unsafeFrom("hello")
    val name: NonEmptyName = NonEmptyName(refined)
    name.value.value shouldBe "hello"
  }

  it should "support Order.by(_.value.value) for newtypes wrapping refined" in {
    import cats.Order
    val ord = implicitly[Order[NonEmptyName]]
    val a = NonEmptyName(refineV[NonEmpty].unsafeFrom("Alice"))
    val b = NonEmptyName(refineV[NonEmpty].unsafeFrom("Bob"))
    ord.compare(a, b) should be < 0
    ord.compare(b, a) should be > 0
    ord.compare(a, a) shouldBe 0
  }

  it should "support Show.show(_.value.value) for newtypes wrapping refined" in {
    import cats.Show
    val show = implicitly[Show[NonEmptyName]]
    val name = NonEmptyName(refineV[NonEmpty].unsafeFrom("Charlie"))
    show.show(name) shouldBe "Charlie"
  }

  // Regression: when a @newtype wraps a refined type AND declares an instance method
  // whose body uses `value.value` (param accessor + Refined's .value), the plugin's
  // rewriteBodyStr replaces *both* word-boundary matches of `value`, producing
  //   $this$.asInstanceOf[RefinedEmailString].$this$.asInstanceOf[RefinedEmailString]
  // and the compiler reports:
  //   value $this is not a member of ...RefinedEmailString,
  //   but could be made available as an extension method.
  it should "expose instance methods that access .value on the wrapped Refined" in {
    val email = Email(refineV[EmailString].unsafeFrom("user@example.com"))
    email.domain shouldBe "example.com"
    email.localPart shouldBe "user"
  }
}

object NewTypeRefinedTest {
  import cats.{Order, Show}

  @newtype case class NonEmptyName(value: String Refined NonEmpty)
  object NonEmptyName {
    implicit val orderForNonEmptyName: Order[NonEmptyName] = Order.by(_.value.value)
    implicit val showForNonEmptyName: Show[NonEmptyName] = Show.show(_.value.value)
  }

  @newtype case class PositiveId(value: Int Refined Positive)

  type EmailString = MatchesRegex[".+@.+"]
  type RefinedEmailString = String Refined EmailString

  @newtype case class Email(value: RefinedEmailString) {
    def domain: String = value.value.split('@')(1)
    def localPart: String = value.value.split('@')(0)
  }
}
