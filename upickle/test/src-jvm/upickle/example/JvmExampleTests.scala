package upickle.example

import java.io.StringWriter

import acyclic.file
import ujson.json4s.Json4sJson
import upickle.TestUtil
import utest._
import ujson.StringRenderer

import Simple._

object JvmExampleTests extends TestSuite {

  import TestUtil._
  val tests = Tests {
    test("sources"){
      import upickle.default._
      val original = """{"myFieldA":1,"myFieldB":"gg"}"""

      import java.nio.file.Files
      val f = Files.createTempFile("", "")
      Files.write(f, original.getBytes)

      read[Thing](f) ==> Thing(1, "gg")
      read[Thing](f.toFile) ==> Thing(1, "gg")
    }
    test("JavaBean") {
      import collection.JavaConverters._
      import upickle.default._

      val bean: JavaBean = new JavaBean()

      bean.setName("Hello World")
      bean.setNumber(123)
      bean.setBool(true)
      bean.setJavaEnum(JavaEnum.ONE)

      bean.setChildren(Vector({
        val child: JavaBean = new JavaBean()
        child.setName("Hello World Child")
        child
      }).asJava)
      bean.setList(Vector("aa", "bb", "cc").asJava)
      bean.getListWithoutSetter().addAll(Vector("One", "Two", "Three").asJava)

      bean.setIgnoredProtectedTransientAnnotationField("ignoredProtectedTransientAnnotation")
      bean.setIgnoredProtectedXmlTransientAnnotationField("ignoredProtectedXmlTransientAnnotation")
      bean.setIgnoredProtectedTransientField("ignoredProtectedTransient")

      bean.setIgnoredPublicTransientAnnotationField("ignoredPublicTransientAnnotation")
      bean.setIgnoredPublicXmlTransientAnnotationField("ignoredPublicXmlTransientAnnotation")
      bean.setIgnoredPublicTransientField("ignoredPublicTransient")

      bean.setShadowedInterfaceMethod("not transient")

      // Round trip test
      val serialized: String = write(bean)
      val bean2: JavaBean = read[JavaBean](serialized)

      // Test primitive fields
      bean2.getName              ==> bean.getName
      bean2.getNumber            ==> bean.getNumber
      bean2.isBool               ==> bean.isBool
      bean2.getJavaEnum          ==> bean.getJavaEnum

      // Test collection fields
      bean2.getChildren.get(0).getName() ==> bean.getChildren.get(0).getName()
      bean2.getList.asScala              ==> bean.getList.asScala
      bean2.getListWithoutSetter.asScala ==> bean.getListWithoutSetter.asScala

      // Test ignore - transient fields
      bean2.getIgnoredProtectedTransientAnnotationField    ==> null
      bean2.getIgnoredProtectedXmlTransientAnnotationField ==> null
      bean2.getIgnoredProtectedTransientField              ==> null
      bean2.getIgnoredPublicTransientAnnotationField       ==> null
      bean2.getIgnoredPublicXmlTransientAnnotationField    ==> null
      bean2.getIgnoredPublicTransientField                 ==> null

      // Test other - special fields
      bean2.getShadowedInterfaceMethod ==> "not transient"
    }
    test("other"){
      test("argonaut"){
        import ujson.argonaut.ArgonautJson
        val argJson: argonaut.Json = ArgonautJson(
          """["hello", "world"]"""
        )

        val updatedArgJson = argJson.withArray(_.map(_.withString(_.toUpperCase)))

        val items: Seq[String] = ArgonautJson.transform(
          updatedArgJson,
          upickle.default.reader[Seq[String]]
        )

        items ==> Seq("HELLO", "WORLD")

        val rewritten = upickle.default.transform(items).to(ArgonautJson)

        val stringified = ArgonautJson.transform(rewritten, StringRenderer()).toString

        stringified ==> """["HELLO","WORLD"]"""
      }
      test("circe"){
        import ujson.circe.CirceJson
        val circeJson: io.circe.Json = CirceJson(
          """["hello", "world"]"""
        )

        val updatedCirceJson =
          circeJson.mapArray(_.map(x => x.mapString(_.toUpperCase)))

        val items: Seq[String] = CirceJson.transform(
          updatedCirceJson,
          upickle.default.reader[Seq[String]]
        )

        items ==> Seq("HELLO", "WORLD")

        val rewritten = upickle.default.transform(items).to(CirceJson)

        val stringified = CirceJson.transform(rewritten, StringRenderer()).toString

        stringified ==> """["HELLO","WORLD"]"""
      }
      test("json4s"){
        import org.json4s.JsonAST
        val json4sJson: JsonAST.JValue = Json4sJson(
          """["hello", "world"]"""
        )

        val updatedJson4sJson = JsonAST.JArray(
          for(v <- json4sJson.children)
            yield JsonAST.JString(v.values.toString.toUpperCase())
        )

        val items: Seq[String] = Json4sJson.transform(
          updatedJson4sJson,
          upickle.default.reader[Seq[String]]
        )

        items ==> Seq("HELLO", "WORLD")

        val rewritten = upickle.default.transform(items).to(Json4sJson)

        val stringified = Json4sJson.transform(rewritten, StringRenderer()).toString

        stringified ==> """["HELLO","WORLD"]"""
      }
      test("playJson"){
        import ujson.play.PlayJson
        import play.api.libs.json._
        val playJson: play.api.libs.json.JsValue = PlayJson(
          """["hello", "world"]"""
        )

        val updatedPlayJson = JsArray(
          for(v <- playJson.as[JsArray].value)
            yield JsString(v.as[String].toUpperCase())
        )

        val items: Seq[String] = PlayJson.transform(
          updatedPlayJson,
          upickle.default.reader[Seq[String]]
        )

        items ==> Seq("HELLO", "WORLD")

        val rewritten = upickle.default.transform(items).to(PlayJson)

        val stringified = PlayJson.transform(rewritten, StringRenderer()).toString

        stringified ==> """["HELLO","WORLD"]"""
      }
      test("crossAst"){
        import ujson.circe.CirceJson
        val circeJson: io.circe.Json = CirceJson(
          """["hello", "world"]"""
        )

        val updatedCirceJson =
          circeJson.mapArray(_.map(x => x.mapString(_.toUpperCase)))

        import ujson.play.PlayJson
        import play.api.libs.json._

        val playJson: play.api.libs.json.JsValue = CirceJson.transform(
          updatedCirceJson,
          PlayJson
        )

        val updatedPlayJson = JsArray(
          for(v <- playJson.as[JsArray].value)
            yield JsString(v.as[String].reverse)
        )

        val stringified = PlayJson.transform(updatedPlayJson, StringRenderer()).toString

        stringified ==> """["OLLEH","DLROW"]"""
      }
    }

  }
}


