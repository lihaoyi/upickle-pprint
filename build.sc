import mill._, mill.scalalib._, mill.scalalib.publish._, mill.scalajslib._, mill.scalanativelib._

val scala211  = "2.11.12"
val scala212  = "2.12.10"
val scala213  = "2.13.1"
val scalaJS06 = "0.6.32"
val scalaJS1  = "1.0.0"

def acyclicVersion(scalaVersion: String): String = if(scalaVersion.startsWith("2.11.")) "0.1.8" else "0.2.0"

val scalaJVMVersions = Seq(scala212, scala213)

val scalaJSVersions = Seq(
  (scala212, scalaJS06),
  (scala213, scalaJS06),
  (scala212, scalaJS1),
  (scala213, scalaJS1)
)

val scalaNativeVersions = Seq(
  (scala211, "0.3.9"),
  (scala211, "0.4.0-M2")
)

trait CommonModule extends ScalaModule {
  def scalacOptions = T{ if (scalaVersion() == scala212) Seq("-opt:l:method") else Nil }
  def platformSegment: String

  def sources = T.sources(
    millSourcePath / "src",
    millSourcePath / s"src-$platformSegment"
  )
}
trait CommonPublishModule extends CommonModule with PublishModule with CrossScalaModule{
  def publishVersion = "1.1.0"
  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "com.lihaoyi",
    url = "https://github.com/lihaoyi/upickle",
    licenses = Seq(License.MIT),
    scm = SCM(
      "git://github.com/lihaoyi/upickle.git",
      "scm:git://github.com/lihaoyi/upickle.git"
    ),
    developers = Seq(
      Developer("lihaoyi", "Li Haoyi","https://github.com/lihaoyi")
    )
  )
}

trait CommonTestModule extends CommonModule with TestModule{
  def ivyDeps = Agg(ivy"com.lihaoyi::utest::0.7.4", ivy"com.lihaoyi::acyclic:${acyclicVersion(scalaVersion())}")
  def testFrameworks = Seq("upickle.core.UTestFramework")
}
trait CommonJvmModule extends CommonPublishModule{
  def platformSegment = "jvm"
  def millSourcePath = super.millSourcePath / os.up
  trait Tests extends super.Tests with CommonTestModule{
    def platformSegment = "jvm"
  }

}
trait CommonJsModule extends CommonPublishModule with ScalaJSModule{
  def platformSegment = "js"
  def crossScalaJSVersion: String
  def scalaJSVersion = crossScalaJSVersion
  def millSourcePath = super.millSourcePath / os.up / os.up
  trait Tests extends super.Tests with CommonTestModule{
    def platformSegment = "js"
    def scalaJSVersion = crossScalaJSVersion
  }
}

trait CommonNativeModule extends CommonPublishModule with ScalaNativeModule{
  def platformSegment = "native"
  def crossScalaNativeVersion: String
  def scalaNativeVersion = crossScalaNativeVersion
  def millSourcePath = super.millSourcePath / os.up / os.up
  trait Tests extends super.Tests with CommonTestModule{
    def platformSegment = "native"
    def scalaNativeVersion = crossScalaNativeVersion
  }
}

trait CommonCoreModule extends ScalaModule {
  def artifactName = "upickle-core"
  def ivyDeps = Agg(
    ivy"org.scala-lang.modules::scala-collection-compat::2.1.4",
    ivy"com.lihaoyi::geny::0.6.0"
  )
}
object core extends Module {
  object js extends Cross[CoreJsModule](scalaJSVersions:_*)

  class CoreJsModule(val crossScalaVersion: String, val crossScalaJSVersion: String) extends CommonJsModule with CommonCoreModule{
    object test extends Tests
  }

  object jvm extends Cross[CoreJvmModule](scalaJVMVersions:_*)
  class CoreJvmModule(val crossScalaVersion: String) extends CommonJvmModule with CommonCoreModule{
    object test extends Tests
  }

  object native extends Cross[CoreNativeModule](scalaNativeVersions:_*)
  class CoreNativeModule(val crossScalaVersion: String, val crossScalaNativeVersion: String) extends CommonNativeModule with CommonCoreModule{
    object test extends Tests
  }
}

object implicits extends Module {

  trait ImplicitsModule extends CommonPublishModule{
    def compileIvyDeps = Agg(
      ivy"com.lihaoyi::acyclic:${acyclicVersion(scalaVersion())}",
      ivy"org.scala-lang:scala-reflect:${scalaVersion()}",
      // Needed for @XmlTransient and @Transient in java9+
      ivy"com.sun.xml.bind:jaxb-core:2.3.0.1",
      ivy"com.sun.xml.bind:jaxb-impl:2.3.1",
      ivy"javax.xml.bind:jaxb-api:2.3.1",
    )
    def generatedSources = T{
      val dir = T.ctx().dest
      val file = dir / "upickle" / "Generated.scala"
      ammonite.ops.mkdir(dir / "upickle")
      val tuples = (1 to 22).map{ i =>
        def commaSeparated(s: Int => String) = (1 to i).map(s).mkString(", ")
        val writerTypes = commaSeparated(j => s"T$j: Writer")
        val readerTypes = commaSeparated(j => s"T$j: Reader")
        val typeTuple = commaSeparated(j => s"T$j")
        val implicitWriterTuple = commaSeparated(j => s"implicitly[Writer[T$j]]")
        val implicitReaderTuple = commaSeparated(j => s"implicitly[Reader[T$j]]")
        val lookupTuple = commaSeparated(j => s"x(${j-1})")
        val fieldTuple = commaSeparated(j => s"x._$j")
        s"""
        implicit def Tuple${i}Writer[$writerTypes]: TupleNWriter[Tuple$i[$typeTuple]] =
          new TupleNWriter[Tuple$i[$typeTuple]](Array($implicitWriterTuple), x => if (x == null) null else Array($fieldTuple))
        implicit def Tuple${i}Reader[$readerTypes]: TupleNReader[Tuple$i[$typeTuple]] =
          new TupleNReader(Array($implicitReaderTuple), x => Tuple$i($lookupTuple).asInstanceOf[Tuple$i[$typeTuple]])
        """
      }

      ammonite.ops.write(file, s"""
      package upickle.implicits
      import acyclic.file
      import language.experimental.macros
      /**
       * Auto-generated picklers and unpicklers, used for creating the 22
       * versions of tuple-picklers and case-class picklers
       */
      trait Generated extends upickle.core.Types{
        ${tuples.mkString("\n")}
      }
    """)
      Seq(PathRef(dir))
    }

  }
  object js extends Cross[JsModule](scalaJSVersions:_*)

  class JsModule(val crossScalaVersion: String, val crossScalaJSVersion: String) extends ImplicitsModule with CommonJsModule{
    def moduleDeps = Seq(core.js(crossScalaVersion, crossScalaJSVersion))
    def artifactName = "upickle-implicits"

    object test extends Tests {
      def moduleDeps = super.moduleDeps ++ Seq(
        ujson.js(crossScalaVersion, crossScalaJSVersion).test,
        core.js(crossScalaVersion, crossScalaJSVersion).test
      )
    }
  }

  object jvm extends Cross[JvmModule](scalaJVMVersions:_*)
  class JvmModule(val crossScalaVersion: String) extends ImplicitsModule with CommonJvmModule{
    def moduleDeps = Seq(core.jvm())
    def artifactName = "upickle-implicits"
    object test extends Tests {
      def moduleDeps = super.moduleDeps ++ Seq(ujson.jvm().test, core.jvm().test)
    }
  }

  object native extends Cross[NativeModule](scalaNativeVersions:_*)

  class NativeModule(val crossScalaVersion: String, val crossScalaNativeVersion: String) extends ImplicitsModule with CommonNativeModule{
    def moduleDeps = Seq(core.native(crossScalaVersion, crossScalaNativeVersion))
    def artifactName = "upickle-implicits"

    object test extends Tests {
      def moduleDeps = super.moduleDeps ++ Seq(
        ujson.native(crossScalaVersion, crossScalaNativeVersion).test,
        core.native(crossScalaVersion, crossScalaNativeVersion).test
      )
    }
  }
}

object upack extends Module {

  object js extends Cross[JsModule](scalaJSVersions:_*)

  class JsModule(val crossScalaVersion: String, val crossScalaJSVersion: String) extends CommonJsModule {
    def moduleDeps = Seq(core.js(crossScalaVersion, crossScalaJSVersion))
    def artifactName = "upack"

    object test extends Tests {
      def moduleDeps = super.moduleDeps ++ Seq(
        ujson.js(crossScalaVersion, crossScalaJSVersion).test,
        core.js(crossScalaVersion, crossScalaJSVersion).test
      )
    }
  }

  object jvm extends Cross[JvmModule](scalaJVMVersions:_*)
  class JvmModule(val crossScalaVersion: String) extends CommonJvmModule {
    def moduleDeps = Seq(core.jvm())
    def artifactName = "upack"
    object test extends Tests with CommonModule  {
      def moduleDeps = super.moduleDeps ++ Seq(ujson.jvm().test, core.jvm().test)
    }
  }

  object native extends Cross[NativeModule](scalaNativeVersions:_*)

  class NativeModule(val crossScalaVersion: String, val crossScalaNativeVersion: String) extends CommonNativeModule {
    def moduleDeps = Seq(core.native(crossScalaVersion, crossScalaNativeVersion))
    def artifactName = "upack"

    object test extends Tests {
      def moduleDeps = super.moduleDeps ++ Seq(
        ujson.native(crossScalaVersion, crossScalaNativeVersion).test,
        core.native(crossScalaVersion, crossScalaNativeVersion).test
      )
    }
  }
}


object ujson extends Module{
  trait JsonModule extends CommonPublishModule{
    def artifactName = "ujson"
    trait JawnTestModule extends CommonTestModule{
      def ivyDeps = T{
        Agg(
          ivy"org.scalatest::scalatest::3.1.1",
          ivy"org.scalatestplus::scalacheck-1-14::3.1.1.1"
        )
      }
      def testFrameworks = Seq("org.scalatest.tools.Framework")
    }
  }

  object js extends Cross[JsModule](scalaJSVersions:_*)
  class JsModule(val crossScalaVersion: String, val crossScalaJSVersion: String) extends JsonModule with CommonJsModule{
    def moduleDeps = Seq(core.js(crossScalaVersion, crossScalaJSVersion))

    object test extends Tests with JawnTestModule
  }

  object jvm extends Cross[JvmModule](scalaJVMVersions:_*)
  class JvmModule(val crossScalaVersion: String) extends JsonModule with CommonJvmModule{
    def moduleDeps = Seq(core.jvm())
    object test extends Tests with JawnTestModule
  }

  object native extends Cross[NativeModule](scalaNativeVersions:_*)
  class NativeModule(val crossScalaVersion: String, val crossScalaNativeVersion: String) extends JsonModule with CommonNativeModule{
    def moduleDeps = Seq(core.native(crossScalaVersion, crossScalaNativeVersion))

    object test extends Tests with JawnTestModule {
      def ivyDeps = if(crossScalaNativeVersion == "0.3.9") T(Agg.empty) else super.ivyDeps()
      def testFrameworks = if(crossScalaNativeVersion == "0.3.9") T(Seq.empty[String]) else super.testFrameworks()
      def sources = if(crossScalaNativeVersion == "0.3.9") T.sources(Seq.empty) else super.sources
    }
  }

  object argonaut extends Cross[ArgonautModule](scalaJVMVersions:_*)
  class ArgonautModule(val crossScalaVersion: String) extends CommonPublishModule{
    def artifactName = "ujson-argonaut"
    def platformSegment = "jvm"
    def moduleDeps = Seq(ujson.jvm())
    def ivyDeps = Agg(ivy"io.argonaut::argonaut:6.2.3")
  }
  object json4s extends Cross[Json4sModule](scalaJVMVersions:_*)
  class Json4sModule(val crossScalaVersion: String) extends CommonPublishModule{
    def artifactName = "ujson-json4s"
    def platformSegment = "jvm"
    def moduleDeps = Seq(ujson.jvm())
    def ivyDeps = Agg(
      ivy"org.json4s::json4s-ast:3.6.7",
      ivy"org.json4s::json4s-native:3.6.7"
    )
  }

  object circe extends Cross[CirceModule](scalaJVMVersions:_*)
  class CirceModule(val crossScalaVersion: String) extends CommonPublishModule{
    def artifactName = "ujson-circe"
    def platformSegment = "jvm"
    def moduleDeps = Seq(ujson.jvm())
    def ivyDeps = Agg(ivy"io.circe::circe-parser:0.12.1")
  }

  object play extends Cross[PlayModule](scalaJVMVersions:_*)
  class PlayModule(val crossScalaVersion: String) extends CommonPublishModule{
    def artifactName = "ujson-play"
    def platformSegment = "jvm"
    def moduleDeps = Seq(ujson.jvm())
    def ivyDeps = Agg(
      ivy"com.typesafe.play::play-json:2.7.4"
    )
  }
}

trait UpickleModule extends CommonPublishModule{
  def artifactName = "upickle"
  def compileIvyDeps = Agg(
    ivy"com.lihaoyi::acyclic:${acyclicVersion(scalaVersion())}",
    ivy"org.scala-lang:scala-reflect:${scalaVersion()}",
    ivy"org.scala-lang:scala-compiler:${scalaVersion()}"
  )
  def scalacOptions = Seq(
    "-unchecked",
    "-deprecation",
    "-encoding", "utf8",
    "-feature",
  )
}


object upickle extends Module{
  object jvm extends Cross[JvmModule](scalaJVMVersions:_*)
  class JvmModule(val crossScalaVersion: String) extends UpickleModule with CommonJvmModule{
    def moduleDeps = Seq(ujson.jvm(), upack.jvm(), implicits.jvm())

    object test extends Tests with CommonModule{
      def moduleDeps = {
        super.moduleDeps ++ Seq(
          ujson.argonaut(),
          ujson.circe(),
          ujson.json4s(),
          ujson.play(),
          core.jvm().test
        )
      }
    }
  }

  object js extends Cross[JsModule](scalaJSVersions:_*)
  class JsModule(val crossScalaVersion: String, val crossScalaJSVersion: String) extends UpickleModule with CommonJsModule {
    def moduleDeps = Seq(
      ujson.js(crossScalaVersion, crossScalaJSVersion),
      upack.js(crossScalaVersion, crossScalaJSVersion),
      implicits.js(crossScalaVersion, crossScalaJSVersion)
    )

    object test extends Tests with CommonModule{
      def testFrameworks = Seq("upickle.core.UTestFramework")
      def moduleDeps = super.moduleDeps ++ Seq(core.js(crossScalaVersion, crossScalaJSVersion).test)
    }
  }
  object native extends Cross[NativeModule](scalaNativeVersions:_*)
  class NativeModule(val crossScalaVersion: String, val crossScalaNativeVersion: String) extends UpickleModule with CommonNativeModule {
    def moduleDeps = Seq(
      ujson.native(crossScalaVersion, crossScalaNativeVersion),
      upack.native(crossScalaVersion, crossScalaNativeVersion),
      implicits.native(crossScalaVersion, crossScalaNativeVersion)
    )

    object test extends Tests with CommonModule{
      def testFrameworks = if(crossScalaNativeVersion == "0.3.9") T(Seq.empty[String]) else super.testFrameworks()
      def sources = if(crossScalaNativeVersion == "0.3.9") T.sources(Seq.empty) else super.sources
      def allSourceFiles = T(super.allSourceFiles().filterNot(pr => Seq("Primitive", "Durations").map(s => s"${s}Tests.scala").contains(pr.path.last)))
      def moduleDeps = super.moduleDeps ++ Seq(core.native(crossScalaVersion, crossScalaNativeVersion).test)
    }
  }
}

trait BenchModule extends CommonModule{
  def scalaVersion = scala213
  def millSourcePath = build.millSourcePath / "bench"
  def ivyDeps = Agg(
    ivy"io.circe::circe-core::0.12.1",
    ivy"io.circe::circe-generic::0.12.1",
    ivy"io.circe::circe-parser::0.12.1",
    ivy"com.typesafe.play::play-json::2.7.4",
    ivy"io.argonaut::argonaut:6.2.3",
    ivy"org.json4s::json4s-ast:3.6.7",
    ivy"com.lihaoyi::sourcecode::0.2.1",
  )
}

object bench extends Module {
  object js extends BenchModule with ScalaJSModule {
    def scalaJSVersion = scalaJSVersions.head._2
    def platformSegment = "js"
    def moduleDeps = Seq(upickle.js(scala213, scalaJS1).test)
    def run(args: String*) = T.command {
      finalMainClassOpt() match{
        case Left(err) => mill.eval.Result.Failure(err)
        case Right(_) =>
          ScalaJSWorkerApi.scalaJSWorker().run(
            toolsClasspath().map(_.path),
            jsEnvConfig(),
            fullOpt().path.toIO
          )
          mill.eval.Result.Success(())
      }
    }
  }

  object jvm extends BenchModule {
    def platformSegment = "jvm"
    def moduleDeps = Seq(upickle.jvm(scala213).test)
    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"com.fasterxml.jackson.module::jackson-module-scala:2.9.10",
      ivy"com.fasterxml.jackson.core:jackson-databind:2.9.4",
    )
  }
}
