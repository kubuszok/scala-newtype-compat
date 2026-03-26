# scala-newtype-compat

Scala 3 compatibility layer for [scala-newtype](https://github.com/estatico/scala-newtype).

scala-newtype provides `@newtype` and `@newsubtype` macro annotations for zero-cost wrapper types in Scala 2. This project makes them work on Scala 3 by providing a compiler plugin that performs the same rewrite the Scala 2 macro did.

## Status

Work in progress. Tested on Scala 2.13.16 and all Scala 3 versions from 3.3.0 to 3.8.2.

## Setup

```scala
// build.sbt

// Brings io.estatico:newtype onto the classpath (both Scala 2.13 and 3)
libraryDependencies += "com.kubuszok" %% "newtype-compat" % "<version>"

// Scala 3 only: compiler plugin that rewrites @newtype annotations
libraryDependencies ++= {
  if (scalaBinaryVersion.value == "3")
    Seq(compilerPlugin("com.kubuszok" %% "newtype-plugin" % "<version>"))
  else Nil
}

// Scala 2.13: enable macro annotations
scalacOptions ++= {
  if (scalaBinaryVersion.value == "2.13") Seq("-Ymacro-annotations")
  else Nil
}
```

## Usage

Your existing `@newtype` code works unchanged on both Scala 2.13 and 3:

```scala
import io.estatico.newtype.macros.newtype
import io.estatico.newtype.ops._

@newtype case class UserId(value: Int)

val id: UserId = UserId(42)
val raw: Int = id.coerce[Int] // 42
id.value                      // 42
```

### Type parameters

```scala
@newtype case class Nel[A](toList: List[A])

val xs: Nel[Int] = Nel(List(1, 2, 3))
xs.coerce[List[Int]] // List(1, 2, 3)
```

### Instance methods

```scala
@newtype case class Score(value: Int) {
  def add(n: Int): Score = Score(value + n)
}

Score(10).add(5) // Score(15)
```

### Subtypes

```scala
@newsubtype case class PosInt(value: Int)
// PosInt is a subtype of Int at the type level
```

### Deriving typeclass instances

```scala
import cats._

@newtype case class Name(value: String)
object Name {
  implicit val eq: Eq[Name] = deriving
  implicit val show: Show[Name] = deriving
}
```

### Pattern matching

```scala
@newtype(unapply = true) case class Token(value: String)

Token("abc") match {
  case Token(s) => s // "abc"
}
```

### Coercible

All newtypes generate `Coercible` instances for zero-cost conversions:

```scala
import io.estatico.newtype.Coercible

@newtype case class Email(value: String)

val wrap   = implicitly[Coercible[String, Email]]
val unwrap = implicitly[Coercible[Email, String]]

wrap("user@example.com") // Email("user@example.com")
```

## How it works

- On **Scala 2.13**, the original `@newtype` macro annotation does the transformation at compile time.
- On **Scala 3**, the `newtype-plugin` compiler plugin runs after the parser but before the typer. It detects `@newtype`/`@newsubtype` annotated case classes and rewrites them into the same expanded form the Scala 2 macro would produce: a type alias + companion object with `Coercible` implicits, accessor methods, and deriving support.

Both Scala 2.13 and 3 depend on the same `io.estatico:newtype_2.13` artifact for the runtime types (`Coercible`, `CoercibleIdOps`, etc.). Scala 3 can consume Scala 2.13 jars natively.

## Modules

| Module | Description | Scala versions |
|--------|-------------|----------------|
| `newtype-compat` | Empty artifact that brings in `io.estatico:newtype_2.13:0.4.4` | 2.13, 3.3.x - 3.8.x |
| `newtype-plugin` | Scala 3 compiler plugin | 3.3.x - 3.8.x |

## Known limitations

- The generated `Ops` implicit class does not extend `AnyVal` on Scala 3 (value classes with abstract type members cause codegen issues in dotty).
- `@newtype` inside local scopes (e.g. inside a method body) is not supported.

## License

Apache 2.0, same as the original scala-newtype.
