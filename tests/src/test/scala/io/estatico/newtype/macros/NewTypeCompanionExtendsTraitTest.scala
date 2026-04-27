package io.estatico.newtype.macros

import io.estatico.newtype.ops._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/*
 * Regression: a `@newtype` whose companion `extends` a trait (and supplies abstract members or
 * overrides via the trait's API).
 *
 * Example pattern:
 *
 *     @newtype final case class FooId(value: String Refined FooIdRefinement)
 *     object FooId extends WithGenericId[FooIdRefinement] { override type N = FooId }
 *
 * The plugin merges `companion.impl.body` into the synthesized object's body, but it does not
 * propagate `companion.impl.parents`, `companion.impl.derived`, or `companion.impl.self`. As a
 * result the trait extension is silently dropped — the synthesized object becomes a plain
 * `object Foo { ... }` with the body but without the parent it was declared to extend.
 *
 * The visible failure is downstream: callers expecting `Foo: HasN` lose the inherited members,
 * and `override`s in the companion body refer to symbols that no longer exist on the companion.
 */
class NewTypeCompanionExtendsTraitTest extends AnyFlatSpec with Matchers {

  import NewTypeCompanionExtendsTraitTest._

  behavior of "@newtype companion that extends a trait"

  it should "preserve the parent trait so inherited members are reachable" in {
    Foo.label shouldBe "foo-label"
    Foo("x").coerce[String] shouldBe "x"
    (Foo: HasLabel) shouldBe Foo
  }

  it should "preserve overrides that depend on the parent trait" in {
    Bar.emptyString shouldBe ""
    (Bar: HasEmptyString[BarMarker]).emptyString shouldBe ""
  }

  it should "preserve generic parent traits with type-member overrides" in {
    // Mirrors a common pattern: `object FooId extends WithGenericId[FooIdRefinement] { override type N = FooId }`.
    Baz.label shouldBe "baz-refined"
    val ev: WithRefinement[BazRefinement] = Baz
    ev.label shouldBe "baz-refined"
  }
}

object NewTypeCompanionExtendsTraitTest {

  trait HasLabel {
    def label: String
  }

  trait HasEmptyString[Phantom] {
    def emptyString: String
  }

  trait WithRefinement[R] {
    type N
    def label: String
  }

  @newtype final case class Foo(value: String)
  object Foo extends HasLabel {
    val label: String = "foo-label"
  }

  final class BarMarker
  @newtype final case class Bar(value: String)
  object Bar extends HasEmptyString[BarMarker] {
    val emptyString: String = ""
  }

  // BazRefinement is a phantom type used as a refinement marker.
  final class BazRefinement
  @newtype final case class Baz(value: String)
  object Baz extends WithRefinement[BazRefinement] {
    override type N = Baz
    val label: String = "baz-refined"
  }
}
