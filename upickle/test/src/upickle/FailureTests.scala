package upickle

import scala.reflect.ClassTag

import ujson.{IncompleteParseException, ParseException, ParsingFailedException}
import upickle.Scala_2_11_Compat._
import upickle.core.AbortException
import upickle.legacy.{read, readEither, readTry}
import utest._

case class Fee(i: Int, s: String)
object Fee{
  implicit def rw: upickle.legacy.ReadWriter[Fee] = upickle.legacy.macroRW
}
sealed trait Fi
object Fi{
  implicit def rw1: upickle.legacy.ReadWriter[Fi] = upickle.legacy.ReadWriter.merge(Fo.rw1, Fum.rw1)
  implicit def rw2: upickle.default.ReadWriter[Fi] = upickle.default.ReadWriter.merge(Fo.rw2, Fum.rw2)
  case class Fo(i: Int) extends Fi
  object Fo{
    implicit def rw1: upickle.legacy.ReadWriter[Fo] = upickle.legacy.macroRW
    implicit def rw2: upickle.default.ReadWriter[Fo] = upickle.default.macroRW
  }
  case class Fum(s: String) extends Fi
  object Fum{
    implicit def rw1: upickle.legacy.ReadWriter[Fum] = upickle.legacy.macroRW
    implicit def rw2: upickle.default.ReadWriter[Fum] = upickle.default.macroRW
  }
}
/**
* Generally, every failure should be a Invalid.Json or a
* InvalidData. If any assertion errors, match errors, number
* format errors or similar leak through, we've failed
*/
object FailureTests extends TestSuite {

  def tests = Tests {
//    test("test"){
//      read[ujson.Value](""" {unquoted_key: "keys must be quoted"} """)
//    }

    test("jsonFailures") {
      def checkFailure[E <: ParsingFailedException : ClassTag](failureCase: String): E = {
        val err = intercept[E](read[ujson.Value](failureCase))
        val errEither = readEither[ujson.Value](failureCase)
        val errTry = readTry[ujson.Value](failureCase)

        val msg = err.getMessage
        assert(errEither.isLeft)
        assert(exists(swap(errEither), (_: String) == msg))
        assert(errTry.isFailure)
        assert(exists(swap(toEither(errTry)), (_: Throwable).getMessage == msg))
        err
      }

      // Run through the test cases from the json.org validation suite,
      // skipping the ones which we don't support yet (e.g. leading zeroes,
      // extra commas) or will never support (e.g. too deep)

      val failureCases = Seq(
//        """ "A JSON payload should be an object or array, not a string." """,
        """ {"Extra value after close": true} "misplaced quoted value" """,
        """ {"Illegal expression": 1 + 2} """,
        """ {"Illegal invocation": alert()} """,
        """ {"Numbers cannot have leading zeroes": 013} """,
        """ {"Numbers cannot be hex": 0x14} """,
        """ ["Illegal backslash escape: \x15"] """,
        """ [\naked] """,
        """ ["Illegal backslash escape: \017"] """,
//        """ [[[[[[[[[[[[[[[[[[[["Too deep"]]]]]]]]]]]]]]]]]]]] """,
        """ {"Missing colon" null} """,

        """ {"Double colon":: null} """,
        """ {"Comma instead of colon", null} """,
        """ ["Colon instead of comma": false] """,
        """ ["Bad value", truth] """,
        """ ['single quote'] """,
        """ ["	tab	character	in	string	"] """,
        """ ["tab\   character\   in\  string\  "] """,
        """ ["line
          break"] """,
        """ ["line\
          break"] """,
        """ [0e] """,
        """ {unquoted_key: "keys must be quoted"} """,
        """ [0e+-1] """,

        """ ["mismatch"} """,
        """ ["extra comma",] """,
        """ ["double extra comma",,] """,
        """ [   , "<-- missing value"] """,
        """ ["Comma after the close"], """,
        """ ["Extra close"]] """,
        """ {"Extra comma": true,} """
      ).map(_.trim())
      val res =
        for(failureCase <- failureCases)
        yield try {
          checkFailure[ParseException](failureCase)
          None
        } catch {
          case _:Throwable =>
          Some(failureCase)
        }

      val nonFailures = res.flatten
      assert(nonFailures.isEmpty)

      checkFailure[IncompleteParseException](""" {"Comma instead if closing brace": true, """)
      checkFailure[IncompleteParseException](""" ["Unclosed array" """)
    }

    test("facadeFailures"){
      def assertErrorMsg[T: upickle.legacy.Reader](s: String, msgs: String*): Unit =
        assertMsg(upickle.legacy, s, msgs: _*)
      def assertErrorMsgDefault[T: upickle.default.Reader](s: String, msgs: String*): Unit =
        assertMsg(upickle.default, s, msgs: _*)
      def assertMsg[A <: Api, T](api: A, s: String, msgs: String*)(implicit reader: api.Reader[T]): Unit = {
        import api.{read, readEither, readTry}
        def r = read(s)
        val err = intercept[AbortException](r)
        val errEither = readEither(s)
        val errTry = readTry(s)

        for (msg <- msgs) {
          assert(err.getMessage.contains(msg))
          assert(errEither.isLeft)
          assert(exists(swap(errEither), (_: String) contains msg))
          assert(errTry.isFailure)
          assert(exists(swap(toEither(errTry)), (_: Throwable).getMessage contains msg))
        }
      }
      test("structs"){
        test - assertErrorMsg[Boolean]("\"lol\"", "expected boolean got string at index 0")
        test - assertErrorMsg[Int]("\"lol\"", "expected number got string at index 0")
        test - assertErrorMsg[Seq[Int]]("\"lol\"", "expected sequence got string at index 0")
        test - assertErrorMsg[Seq[String]]("""["1", 2, 3]""", "expected string got number at index 6")
        test("tupleShort"){
          assertErrorMsg[Seq[(Int, String)]](
            "[[1, \"1\"], [2, \"2\"], []]",
            "expected 2 items in sequence, found 0 at index 22"
          )
        }
      }
      test("caseClass"){
        // Separate this guy out because the read macro and
        // the intercept macro play badly with each other
        test("missingKey"){
          test - assertErrorMsg[Fee]("""{"i": 123}""", "missing keys in dictionary: s at index 9")
          test - assertErrorMsg[Fee](""" {"s": "123"}""", "missing keys in dictionary: i at index 1")
          test - assertErrorMsg[Fee]("""  {}""", "missing keys in dictionary: i, s at index 3")
        }
        test("badKey"){
          test - assertErrorMsg[Fee]("""{"i": true}""", "expected number got boolean")
        }

        test("wrongType"){
          test - assertErrorMsg[Fee]("""[1, 2, 3]""", "expected dictionary got sequence at index 0")
          test - assertErrorMsg[Fee]("""31337""", "expected dictionary got number at index 0")
        }

        test("invalidTag"){
          test - assertErrorMsg[Fi.Fo]("""["omg", {}]""", "invalid tag for tagged object: omg at index 1")
          test - assertErrorMsg[Fi]("""["omg", {}]""", "invalid tag for tagged object: omg at index 1")
          test - assertErrorMsgDefault[Fi.Fo]("""{"$type": "omg"}]""", "invalid tag for tagged object: omg at index 1")
          test - assertErrorMsgDefault[Fi]("""{"$type": "omg"}]""", "invalid tag for tagged object: omg at index 1")
        }

        test("taggedInvalidBody"){
          test - assertErrorMsg[Fi.Fo]("""["upickle.Fi.Fo", {"i": true, "z": null}]""", "expected number got boolean at index 24")
          test - assertErrorMsg[Fi]("""["upickle.Fi.Fo", {"i": true, "z": null}]""", "expected number got boolean at index 24")
          test - assertErrorMsgDefault[Fi.Fo]("""{"$type": "upickle.Fi.Fo", "i": true, "z": null}""", "expected number got boolean at index 32")
          test - assertErrorMsgDefault[Fi]("""{"$type": "upickle.Fi.Fo", "i": true, "z": null}""", "expected number got boolean at index 32")
        }
      }
    }
    test("compileErrors"){
      compileError("write(new Object)")
      compileError("""read[Object]("")""")
//      compileError("""read[Array[Object]]("")""").msg
      // Make sure this doesn't hang the compiler =/
      compileError("implicitly[upickle.default.Reader[Nothing]]")
    }
    test("expWholeNumbers"){
      upickle.default.read[Byte]("0e0") ==> 0.toByte
      upickle.default.read[Short]("0e0") ==> 0
      upickle.default.read[Char]("0e0") ==> 0.toChar
      upickle.default.read[Int]("0e0") ==> 0
      upickle.default.read[Long]("0e0") ==> 0

      upickle.default.read[Byte]("10e1") ==> 100
      upickle.default.read[Short]("10e1") ==> 100
      upickle.default.read[Char]("10e1") ==> 100
      upickle.default.read[Int]("10e1") ==> 100
      upickle.default.read[Long]("10e1") ==> 100

      upickle.default.read[Byte]("10.1e1") ==> 101
      upickle.default.read[Short]("10.1e1") ==> 101
      upickle.default.read[Char]("10.1e1") ==> 101
      upickle.default.read[Int]("10.1e1") ==> 101
      upickle.default.read[Long]("10.1e1") ==> 101

      // Not supporting these for now, since AFAIK none of the
      // JSON serializers I know generate numbers of this form
//      upickle.default.read[Byte]("10e-1") ==> 1
//      upickle.default.read[Short]("10e-1") ==> 1
//      upickle.default.read[Char]("10e-1") ==> 1
//      upickle.default.read[Int]("10e-1") ==> 1
//      upickle.default.read[Long]("10e-1") ==> 1
    }
    test("tooManyFields"){
      val b63 = Big63(
        0, 1, 2, 3, 4, 5, 6, 7,
        8, 9, 10, 11, 12, 13, 14,
        15, 16, 17, 18, 19, 20, 21,
        22, 23, 24, 25, 26, 27, 28,
        29, 30, 31, 32, 33, 34, 35,
        36, 37, 38, 39, 40, 41, 42,
        43, 44, 45, 46, 47, 48, 49,
        50, 51, 52, 53, 54, 55, 56,
        57, 58, 59, 60, 61, 62
      )
      val b64 = Big64(
        0, 1, 2, 3, 4, 5, 6, 7,
        8, 9, 10, 11, 12, 13, 14,
        15, 16, 17, 18, 19, 20, 21,
        22, 23, 24, 25, 26, 27, 28,
        29, 30, 31, 32, 33, 34, 35,
        36, 37, 38, 39, 40, 41, 42,
        43, 44, 45, 46, 47, 48, 49,
        50, 51, 52, 53, 54, 55, 56,
        57, 58, 59, 60, 61, 62, 63
      )
      val b65 = Big65(
        0, 1, 2, 3, 4, 5, 6, 7,
        8, 9, 10, 11, 12, 13, 14,
        15, 16, 17, 18, 19, 20, 21,
        22, 23, 24, 25, 26, 27, 28,
        29, 30, 31, 32, 33, 34, 35,
        36, 37, 38, 39, 40, 41, 42,
        43, 44, 45, 46, 47, 48, 49,
        50, 51, 52, 53, 54, 55, 56,
        57, 58, 59, 60, 61, 62, 63,
        64
      )
      implicit val b63rw: upickle.default.ReadWriter[Big63] = upickle.default.macroRW
      implicit val b64rw: upickle.default.ReadWriter[Big64] = upickle.default.macroRW
      val written63 = upickle.default.write(b63)
      assert(upickle.default.read[Big63](written63) == b63)
      val written64 = upickle.default.write(b64)
      assert(upickle.default.read[Big64](written64) == b64)
      val err = compileError("{implicit val b64rw: upickle.default.ReadWriter[Big65] = upickle.default.macroRW}")
      assert(err.msg.contains("uPickle does not support serializing case classes with >64 fields"))
    }
  }
}

case class Big63(_0: Byte, _1: Byte, _2: Byte, _3: Byte, _4: Byte, _5: Byte, _6: Byte, _7: Byte,
                 _8: Byte, _9: Byte, _10: Byte, _11: Byte, _12: Byte, _13: Byte, _14: Byte,
                 _15: Byte, _16: Byte, _17: Byte, _18: Byte, _19: Byte, _20: Byte, _21: Byte,
                 _22: Byte, _23: Byte, _24: Byte, _25: Byte, _26: Byte, _27: Byte, _28: Byte,
                 _29: Byte, _30: Byte, _31: Byte, _32: Byte, _33: Byte, _34: Byte, _35: Byte,
                 _36: Byte, _37: Byte, _38: Byte, _39: Byte, _40: Byte, _41: Byte, _42: Byte,
                 _43: Byte, _44: Byte, _45: Byte, _46: Byte, _47: Byte, _48: Byte, _49: Byte,
                 _50: Byte, _51: Byte, _52: Byte, _53: Byte, _54: Byte, _55: Byte, _56: Byte,
                 _57: Byte, _58: Byte, _59: Byte, _60: Byte, _61: Byte, _62: Byte)
case class Big64(_0: Byte, _1: Byte, _2: Byte, _3: Byte, _4: Byte, _5: Byte, _6: Byte, _7: Byte,
                 _8: Byte, _9: Byte, _10: Byte, _11: Byte, _12: Byte, _13: Byte, _14: Byte,
                 _15: Byte, _16: Byte, _17: Byte, _18: Byte, _19: Byte, _20: Byte, _21: Byte,
                 _22: Byte, _23: Byte, _24: Byte, _25: Byte, _26: Byte, _27: Byte, _28: Byte,
                 _29: Byte, _30: Byte, _31: Byte, _32: Byte, _33: Byte, _34: Byte, _35: Byte,
                 _36: Byte, _37: Byte, _38: Byte, _39: Byte, _40: Byte, _41: Byte, _42: Byte,
                 _43: Byte, _44: Byte, _45: Byte, _46: Byte, _47: Byte, _48: Byte, _49: Byte,
                 _50: Byte, _51: Byte, _52: Byte, _53: Byte, _54: Byte, _55: Byte, _56: Byte,
                 _57: Byte, _58: Byte, _59: Byte, _60: Byte, _61: Byte, _62: Byte, _63: Byte)
case class Big65(_0: Byte, _1: Byte, _2: Byte, _3: Byte, _4: Byte, _5: Byte, _6: Byte, _7: Byte,
                 _8: Byte, _9: Byte, _10: Byte, _11: Byte, _12: Byte, _13: Byte, _14: Byte,
                 _15: Byte, _16: Byte, _17: Byte, _18: Byte, _19: Byte, _20: Byte, _21: Byte,
                 _22: Byte, _23: Byte, _24: Byte, _25: Byte, _26: Byte, _27: Byte, _28: Byte,
                 _29: Byte, _30: Byte, _31: Byte, _32: Byte, _33: Byte, _34: Byte, _35: Byte,
                 _36: Byte, _37: Byte, _38: Byte, _39: Byte, _40: Byte, _41: Byte, _42: Byte,
                 _43: Byte, _44: Byte, _45: Byte, _46: Byte, _47: Byte, _48: Byte, _49: Byte,
                 _50: Byte, _51: Byte, _52: Byte, _53: Byte, _54: Byte, _55: Byte, _56: Byte,
                 _57: Byte, _58: Byte, _59: Byte, _60: Byte, _61: Byte, _62: Byte, _63: Byte,
                 _64: Byte)