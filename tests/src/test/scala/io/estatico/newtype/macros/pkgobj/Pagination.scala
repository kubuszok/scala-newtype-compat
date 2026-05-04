package io.estatico.newtype.macros.pkgobj

import cats.kernel.CommutativeSemigroup
import cats.{Order, Show}
import io.estatico.newtype.macros.newtype
import io.estatico.newtype.macros.pkgobj.Pagination.{Page, PageSize}

final case class Pagination(
  page: Page = Page(0L),
  pageSize: PageSize = PageSize(10L),
) {
  def offset: Long = page.value * pageSize.value

  def limit: Long = pageSize.value
}

object Pagination {
  @newtype final case class Page(value: Page.PageType)

  object PageSize {
    type PageSizeType = Long
    implicit lazy val showForPageSize: Show[PageSize] = Show.show(_.value.toString)
  }

  @newtype final case class PageSize(value: PageSize.PageSizeType)

  object Page {
    type PageType = Long
    implicit lazy val showForPage: Show[Page] = Show.show(_.value.toString)
  }

}
