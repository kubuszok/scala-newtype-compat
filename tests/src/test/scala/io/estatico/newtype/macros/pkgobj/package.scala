package io.estatico.newtype.macros

import io.estatico.newtype.macros.newtype

/*
 * Fixture: a package object that holds @newtype definitions, with and without companions.
 *
 * This mirrors a common Scala 2 idiom where a top-level `package object common { ... }` groups
 * domain ID types as `@newtype final case class FooId(...)` with companion objects holding
 * typeclass instances or constants.
 *
 * Two distinct issues observed when the plugin processes this on Scala 3:
 *
 *   1. The `package object` is reconstructed via `untpd.ModuleDef(md.name, newImpl)`, which
 *      drops the `Package` flag carried on `md.mods`. As a result the package object is emitted
 *      as a regular object — its sub-packages cannot resolve, and identifiers it declares are
 *      no longer visible to nested packages through normal Scala scoping.
 *      See `nested/package.scala` and the failing assertions in NewTypeInPackageObjectTest.
 *
 *   2. Inside a `package object`, body items are walked by the plugin's `transformStats`. The
 *      generated replacement source is parsed in isolation (`<newtype-generated-N>`); cross-
 *      references to other package-object members (e.g. type aliases declared a few lines up)
 *      are fine within the same compilation unit but break for downstream consumers.
 */
package object pkgobj {

  // A type alias used by @newtype definitions below; also the kind of alias a sub-package would
  // expect to inherit by being inside this package.
  type SaneString = String

  @newtype final case class Tenant(value: SaneString)
  object Tenant {
    def of(s: SaneString): Tenant = Tenant(s)
    val Default: Tenant = Tenant("default")
    implicit lazy val ordering: Ordering[Tenant] = Ordering.by(_.value)
  }

  @newtype final case class User(value: SaneString)
  object User {
    val Anonymous: User = User("anonymous")
  }

  // @newtype without a companion object — should be unaffected by companion-merging logic.
  @newtype final case class JobName(value: SaneString)
}