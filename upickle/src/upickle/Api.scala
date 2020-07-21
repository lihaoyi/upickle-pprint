package upickle

import java.io.ByteArrayOutputStream

import scala.language.experimental.macros
import scala.language.higherKinds
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

import ujson.IndexedValue
import upickle.core._

/**
 * An instance of the upickle API. There's a default instance at
 * `upickle.default`, but you can also implement it yourself to customize
 * its behavior. Override the `annotate` methods to control how a sealed
 * trait instance is tagged during reading and writing.
 */
trait Api
    extends upickle.core.Types
    with implicits.Readers
    with implicits.Writers
    with WebJson
    with Api.NoOpMappers
    with JsReadWriters
    with MsgReadWriters{

  def serializeDefaults: Boolean = false


  /**
    * Reads the given MessagePack input into a Scala value
    */
  def readBinary[T: Reader](s: upack.Readable, trace: Boolean = false): T = {
    TraceVisitor.withTrace(trace, reader[T])(s.transform(_))
  }

  /**
   * Same as `readBinary` but any non-fatal exception thrown by `readBinary` is returned as a `Failure` instead.
   */
  final def readBinaryTry[T: Reader](s: upack.Readable): Try[T] = Try(readBinary[T](s))

  /**
   * Same as `readBinary` but the message of any non-fatal exception thrown by `readBinary` is returned as a `Left` instead.
   */
  final def readBinaryEither[T: Reader](s: upack.Readable): Either[String, T] = toMessageOrT(readBinaryTry[T](s))

  /**
    * Reads the given JSON input into a Scala value
    */
  def read[T: Reader](s: ujson.Readable, trace: Boolean = false): T = {
    TraceVisitor.withTrace(trace, reader[T])(s.transform(_))
  }

  /**
   * Same as `read` but any non-fatal exception thrown by `read` is returned as a `Failure` instead.
   */
  final def readTry[T: Reader](s: ujson.Readable): Try[T] = Try(read[T](s))

  /**
   * Same as `read` but the message of any non-fatal exception thrown by `read` is returned as a `Left` instead.
   */
  final def readEither[T: Reader](s: ujson.Readable): Either[String, T] = toMessageOrT(readTry[T](s))

  private def toMessageOrT[T](result: Try[T]): Either[String, T] =
    result match {
      case Success(r) => Right(r)
      case Failure(e) => Left(e.getMessage)
    }

  def reader[T: Reader] = implicitly[Reader[T]]

  /**
    * Write the given Scala value as a JSON string
    */
  def write[T: Writer](t: T,
                       indent: Int = -1,
                       escapeUnicode: Boolean = false): String = {
    transform(t).to(ujson.StringRenderer(indent, escapeUnicode)).toString
  }
  /**
    * Write the given Scala value as a MessagePack binary
    */
  def writeBinary[T: Writer](t: T): Array[Byte] = {
    transform(t).to(new upack.MsgPackWriter(new ByteArrayOutputStream())).toByteArray
  }

  /**
    * Write the given Scala value as a JSON struct
    */
  def writeJs[T: Writer](t: T): ujson.Value = transform(t).to[ujson.Value]

  /**
    * Write the given Scala value as a MessagePack struct
    */
  def writeMsg[T: Writer](t: T): upack.Msg = transform(t).to[upack.Msg]

  /**
    * Write the given Scala value as a JSON string to the given Writer
    */
  def writeTo[T: Writer](t: T,
                         out: java.io.Writer,
                         indent: Int = -1,
                         escapeUnicode: Boolean = false): Unit = {
    transform(t).to(new ujson.Renderer(out, indent = indent, escapeUnicode))
  }
  /**
    * Write the given Scala value as a JSON string via a `geny.Writable`
    */
  def stream[T: Writer](t: T,
                        indent: Int = -1,
                        escapeUnicode: Boolean = false): geny.Writable = new geny.Writable{
    override def httpContentType = Some("application/json")
    def writeBytesTo(out: java.io.OutputStream) = {
      val w = new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.UTF_8)
      transform(t).to(new ujson.BaseRenderer(w, indent = indent, escapeUnicode))
      w.flush()
    }
  }
  /**
    * Write the given Scala value as a MessagePack binary to the given OutputStream
    */
  def writeBinaryTo[T: Writer](t: T, out: java.io.OutputStream): Unit = {
    streamBinary[T](t).writeBytesTo(out)
  }
  /**
    * Write the given Scala value as a MessagePack binary via a `geny.Writable`
    */
  def streamBinary[T: Writer](t: T): geny.Writable = new geny.Writable{
    override def httpContentType = Some("application/octet-stream")
    def writeBytesTo(out: java.io.OutputStream) = transform(t).to(new upack.MsgPackWriter(out))
  }

  def writer[T: Writer] = implicitly[Writer[T]]

  def readwriter[T: ReadWriter] = implicitly[ReadWriter[T]]

  case class transform[T: Writer](t: T) extends upack.Readable with ujson.Readable {
    def transform[V](f: Visitor[_, V]): V = writer[T].transform(t, f)
    def to[V](f: Visitor[_, V]): V = transform(f)
    def to[V](implicit f: Reader[V]): V = transform(f)
  }
  // End Api
}
object Api{
  trait NoOpMappers{

    def objectAttributeKeyReadMap(s: CharSequence): CharSequence = s
    def objectAttributeKeyWriteMap(s: CharSequence): CharSequence = s

    def objectTypeKeyReadMap(s: CharSequence): CharSequence = s
    def objectTypeKeyWriteMap(s: CharSequence): CharSequence = s
  }

}
/**
 * The default way of accessing upickle
 */
object default extends AttributeTagged{

}
/**
 * An instance of the upickle API that follows the old serialization for
 * tagged instances of sealed traits: as a list with two items, the first
 * being the type-tag and the second being the serialized object
 */
object legacy extends LegacyApi
trait LegacyApi extends Api{
  def annotate[V](rw: Reader[V], n: String) = new TaggedReader.Leaf[V](n, rw)

  def annotate[V](rw: CaseW[V], n: String)(implicit c: ClassTag[V]) = {
    new TaggedWriter.Leaf[V](c, n, rw)
  }

  def taggedExpectedMsg = "expected sequence"
  sealed trait TaggedReaderState
  object TaggedReaderState{
    case object Initializing extends TaggedReaderState
    case class Parsing(f: Reader[_]) extends TaggedReaderState
    case class Parsed(res: Any) extends TaggedReaderState
  }
  override def taggedArrayContext[T](taggedReader: TaggedReader[T], index: Int) = new ArrVisitor[Any, T] {
    var state: TaggedReaderState = TaggedReaderState.Initializing

    def subVisitor = state match{
      case TaggedReaderState.Initializing => StringReader
      case TaggedReaderState.Parsing(f) => f
      case TaggedReaderState.Parsed(res) => NoOpVisitor
    }

    def visitValue(v: Any, index: Int): Unit = state match{
      case TaggedReaderState.Initializing =>
        val typeName = objectTypeKeyReadMap(v.toString).toString
        val delegate = taggedReader.findReader(typeName)
        if (delegate == null) {
          throw new Abort("invalid tag for tagged object: " + typeName)
        }
        state = TaggedReaderState.Parsing(delegate)
      case TaggedReaderState.Parsing(f) =>
        state = TaggedReaderState.Parsed(v)
      case TaggedReaderState.Parsed(res) => res.asInstanceOf[T]
        throw new Abort("expected tagged dictionary")
    }

    def visitEnd(index: Int) = state match{
      case TaggedReaderState.Parsed(res) => res.asInstanceOf[T]
      case _ => throw new Abort("expected tagged dictionary")
    }

  }
  def taggedWrite[T, R](w: CaseW[T], tag: String, out: Visitor[_,  R], v: T): R = {
    val ctx = out.asInstanceOf[Visitor[Any, R]].visitArray(2, -1)
    ctx.visitValue(ctx.subVisitor.visitString(objectTypeKeyWriteMap(tag), -1), -1)

    ctx.visitValue(w.write(ctx.subVisitor, v), -1)

    ctx.visitEnd(-1)
  }
}

/**
 * A `upickle.Api` that follows the default sealed-trait-instance-tagging
 * behavior of using an attribute, but allow you to control what the name
 * of the attribute is.
 */
trait AttributeTagged extends Api{
  def tagName = "$type"
  def annotate[V](rw: CaseR[V], n: String) = {
    new TaggedReader.Leaf[V](n, rw)
  }

  def annotate[V](rw: CaseW[V], n: String)(implicit c: ClassTag[V]) = {
    new TaggedWriter.Leaf[V](c, n, rw)
  }

  def taggedExpectedMsg = "expected dictionary"
  override def taggedObjectContext[T](taggedReader: TaggedReader[T], index: Int) = {
    new ObjVisitor[Any, T]{
      private[this] var fastPath = false
      private[this] var context: ObjVisitor[Any, _] = null
      def subVisitor: Visitor[_, _] =
        if (context == null) upickle.core.StringVisitor
        else context.subVisitor

      def visitKey(index: Int) = {
        if (context != null) context.visitKey(index)
        else upickle.core.StringVisitor
      }
      def visitKeyValue(s: Any): Unit = {
        if (context != null) context.visitKeyValue(s)
        else {
          if (s.toString == tagName) () //do nothing
          else {
            // otherwise, go slow path
            val slowCtx = IndexedValue.Builder.visitObject(-1, index).narrow
            val keyVisitor = slowCtx.visitKey(index)
            val xxx = keyVisitor.visitString(s.toString, index)
            slowCtx.visitKeyValue(xxx)
            context = slowCtx
          }
        }
      }

      def visitValue(v: Any, index: Int): Unit = {
        if (context != null) context.visitValue(v, index)
        else {
          val typeName = objectTypeKeyReadMap(v.toString).toString
          val facade0 = taggedReader.findReader(typeName)
          if (facade0 == null) {
            throw new Abort("invalid tag for tagged object: " + typeName)
          }
          val fastCtx = facade0.visitObject(-1, index)
          context = fastCtx
          fastPath = true
        }
      }
      def visitEnd(index: Int) = {
        if (context == null) throw new Abort("expected tagged dictionary")
        else if (fastPath) context.visitEnd(index).asInstanceOf[T]
        else{
          val x = context.visitEnd(index).asInstanceOf[IndexedValue.Obj]
          val keyAttr = x.value0.find(_._1.toString == tagName).get._2
          val key = keyAttr.asInstanceOf[IndexedValue.Str].value0.toString
          val delegate = taggedReader.findReader(key)
          if (delegate == null){
            throw new AbortException("invalid tag for tagged object: " + key, keyAttr.index, -1, -1, null)
          }
          val ctx2 = delegate.visitObject(-1, -1)
          for (p <- x.value0) {
            val (k0, v) = p
            val k = k0.toString
            if (k != tagName){
              val keyVisitor = ctx2.visitKey(-1)

              ctx2.visitKeyValue(keyVisitor.visitString(k, -1))
              ctx2.visitValue(IndexedValue.transform(v, ctx2.subVisitor), -1)
            }
          }
          ctx2.visitEnd(index)
        }
      }

    }
  }
  def taggedWrite[T, R](w: CaseW[T], tag: String, out: Visitor[_,  R], v: T): R = {
    val ctx = out.asInstanceOf[Visitor[Any, R]].visitObject(w.length(v) + 1, -1)
    val keyVisitor = ctx.visitKey(-1)

    ctx.visitKeyValue(keyVisitor.visitString(tagName, -1))
    ctx.visitValue(ctx.subVisitor.visitString(objectTypeKeyWriteMap(tag), -1), -1)
    w.writeToObject(ctx, v)
    val res = ctx.visitEnd(-1)
    res
  }
}
