package io.estatico.newtype

import io.estatico.newtype.Coercible
import io.estatico.newtype.macros.{newtype, newsubtype}
import io.estatico.newtype.ops._
import cats.{Eq, Order, Show}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class NewTypeApiTest extends AnyFlatSpec with Matchers {

  import NewTypeApiTest._

  // --- Field accessor (.value) ---

  behavior of "field accessor"

  it should "provide .value on a simple newtype" in {
    val x = Name("Alice")
    x.value shouldBe "Alice"
  }

  it should "work in Order.by(_.value)" in {
    val ord = implicitly[Order[Name]]
    ord.compare(Name("Alice"), Name("Bob")) should be < 0
    ord.compare(Name("Bob"), Name("Alice")) should be > 0
    ord.compare(Name("Alice"), Name("Alice")) shouldBe 0
  }

  it should "work in Show.show(_.value)" in {
    val show = implicitly[Show[Name]]
    show.show(Name("Alice")) shouldBe "Alice"
  }

  // --- Coercible ---

  behavior of "Coercible"

  it should "provide implicit wrap and unwrap" in {
    val w = implicitly[Coercible[Int, Age]]
    val u = implicitly[Coercible[Age, Int]]
    val age = w(25)
    u(age) shouldBe 25
  }

  it should "provide implicit wrapM and unwrapM" in {
    val wm = implicitly[Coercible[List[Int], List[Age]]]
    val um = implicitly[Coercible[List[Age], List[Int]]]
    val ages = wm(List(20, 30))
    um(ages) shouldBe List(20, 30)
  }

  it should "coerce with .coerce extension" in {
    val age = 25.coerce[Age]
    age.coerce[Int] shouldBe 25
  }

  it should "coerce nested type constructors via Coercible.unsafeWrapMM" in {
    val nested: Option[List[Int]] = Some(List(1, 2, 3))
    val coerced = implicitly[Coercible[Option[List[Int]], Option[List[Age]]]].apply(nested)
    coerced shouldBe Some(List(1, 2, 3))
  }

  // --- deriving ---

  behavior of "deriving"

  it should "derive a simple typeclass" in {
    val eqAge: Eq[Age] = Age.deriving[Eq]
    eqAge.eqv(Age(1), Age(1)) shouldBe true
    eqAge.eqv(Age(1), Age(2)) shouldBe false
  }

  // --- newsubtype ---

  behavior of "@newsubtype"

  it should "create a subtype that can be used as its repr" in {
    val sub: SubInt = SubInt(42)
    // newsubtype Type <: Repr, so it should coerce back
    sub.coerce[Int] shouldBe 42
  }

  // --- Type parameters ---

  behavior of "parameterized newtype"

  it should "work with type parameters" in {
    val w = Wrapper(List(1, 2, 3))
    w.coerce[List[Int]] shouldBe List(1, 2, 3)
  }

  it should "provide field accessor for parameterized newtype" in {
    val w = Wrapper(List("a", "b"))
    w.inner shouldBe List("a", "b")
  }

  // --- Instance methods ---

  behavior of "instance methods"

  it should "support methods that transform the value" in {
    val c = Counter(0)
    c.increment shouldBe Counter(1)
    c.increment.increment shouldBe Counter(2)
  }

  it should "support methods with arguments" in {
    val c = Counter(10)
    c.add(5) shouldBe Counter(15)
  }

  // --- Companion object with typeclass instances using _.value ---

  behavior of "companion object typeclass instances"

  it should "resolve Order instance defined with Order.by(_.value)" in {
    val ord = implicitly[Order[Score]]
    ord.compare(Score(10), Score(20)) should be < 0
  }

  it should "resolve Eq instance defined with Eq.by(_.value)" in {
    val eq = implicitly[Eq[Score]]
    eq.eqv(Score(10), Score(10)) shouldBe true
    eq.eqv(Score(10), Score(20)) shouldBe false
  }
}

object NewTypeApiTest {

  @newtype case class Name(value: String)
  object Name {
    implicit val orderForName: Order[Name] = Order.by(_.value)
    implicit val showForName: Show[Name] = Show.show(_.value)
  }

  @newtype case class Age(value: Int)

  @newsubtype case class SubInt(value: Int)

  @newtype case class Wrapper[A](inner: List[A])

  @newtype case class Counter(value: Int) {
    def increment: Counter = Counter(value + 1)
    def add(n: Int): Counter = Counter(value + n)
  }

  @newtype case class Score(value: Int)
  object Score {
    implicit val orderForScore: Order[Score] = Order.by(_.value)
    implicit val eqForScore: Eq[Score] = Eq.by(_.value)
  }
}
