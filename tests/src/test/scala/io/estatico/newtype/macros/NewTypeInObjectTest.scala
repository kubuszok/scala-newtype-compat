package io.estatico.newtype.macros

import cats.Show
import io.estatico.newtype.macros.pkgobj.Pagination
import io.estatico.newtype.macros.pkgobj.Pagination.{Page, PageSize}
import io.estatico.newtype.ops._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/*
 * Tests for `@newtype` declarations placed inside a regular (non-package) `object`. The
 * `Pagination` fixture exercises several non-trivial shapes the plugin must handle together:
 *
 *   - two @newtypes inside the same enclosing object;
 *   - one companion (`PageSize`) declared *before* the @newtype class, the other (`Page`)
 *     declared *after* — both must be merged with the synthesized companion;
 *   - constructor-param type referenced via the companion's own type alias
 *     (e.g. `Page(value: Page.PageType)` where `PageType` lives in `object Page`);
 *   - a sibling regular case class (`Pagination`) whose fields default to @newtype values
 *     and whose method body calls `.value` on them.
 */
class NewTypeInObjectTest extends AnyFlatSpec with Matchers {

  behavior of "@newtype nested in a regular object"

  it should "construct via apply and unwrap via .value" in {
    Page(7L).value shouldBe 7L
    PageSize(25L).value shouldBe 25L
  }

  it should "coerce to the underlying repr" in {
    Page(3L).coerce[Long] shouldBe 3L
    PageSize(50L).coerce[Long] shouldBe 50L
  }

  it should "merge a companion declared after the @newtype class" in {
    val show = implicitly[Show[Page]]
    show.show(Page(42L)) shouldBe "42"
  }

  it should "merge a companion declared before the @newtype class" in {
    val show = implicitly[Show[PageSize]]
    show.show(PageSize(100L)) shouldBe "100"
  }

  it should "resolve the constructor-param type via the companion's type alias" in {
    val pageType: Page.PageType = 99L
    Page(pageType).value shouldBe 99L

    val sizeType: PageSize.PageSizeType = 11L
    PageSize(sizeType).value shouldBe 11L
  }

  behavior of "a regular case class wrapping @newtype fields"

  it should "use the @newtype default arguments" in {
    val p = Pagination()
    p.page.value shouldBe 0L
    p.pageSize.value shouldBe 10L
  }

  it should "let a method body call .value on @newtype-typed fields" in {
    val p = Pagination(Page(3L), PageSize(20L))
    p.offset shouldBe 60L
    p.limit shouldBe 20L
  }

  it should "support equality between @newtype values" in {
    Page(5L) shouldBe Page(5L)
    Page(5L) should not be Page(6L)
    PageSize(10L) shouldBe PageSize(10L)
  }
}