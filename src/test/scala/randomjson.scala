package test.scala.randomjson

import scala.collection.mutable
import scala.language.postfixOps
import scala.util.Random

import org.codehaus.jackson.JsonNode
import org.codehaus.jackson.node.ArrayNode
import org.codehaus.jackson.node.BinaryNode
import org.codehaus.jackson.node.BooleanNode
import org.codehaus.jackson.node.DoubleNode
import org.codehaus.jackson.node.IntNode
import org.codehaus.jackson.node.JsonNodeFactory
import org.codehaus.jackson.node.LongNode
import org.codehaus.jackson.node.NullNode
import org.codehaus.jackson.node.ObjectNode
import org.codehaus.jackson.node.TextNode

import org.junit.runner.RunWith

import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.Matchers

import org.codehaus.jackson.JsonNode
import org.codehaus.jackson.JsonFactory

import org.scoringengine.pfa.ast._
import org.scoringengine.pfa.data._
import org.scoringengine.pfa.reader._
import org.scoringengine.pfa.types._
import org.scoringengine.pfa.types.AvroConversions._
import org.scoringengine.pfa.util._
import org.scoringengine.pfa.yaml._
import test.scala._

@RunWith(classOf[JUnitRunner])
class RandomJsonSuite extends FlatSpec with Matchers {
  val factory = JsonNodeFactory.instance

  def m(pairs: (String, Any)*): ObjectNode = {
    val out = factory.objectNode
    for ((k, value) <- pairs) value match {
      case null => out.put(k, NullNode.getInstance)
      case x: Boolean => out.put(k, if (x) BooleanNode.getTrue else BooleanNode.getFalse)
      case x: Int => out.put(k, new IntNode(x))
      case x: Long => out.put(k, new LongNode(x))
      case x: Float => out.put(k, new DoubleNode(x))
      case x: Double => out.put(k, new DoubleNode(x))
      case x: String => out.put(k, new TextNode(x))
      case x: JsonNode => out.put(k, x)
    }
    out
  }

  def a(items: Any*): ArrayNode = {
    val out = factory.arrayNode
    for (item <- items) item match {
      case null => out.add(NullNode.getInstance)
      case x: Boolean => out.add(if (x) BooleanNode.getTrue else BooleanNode.getFalse)
      case x: Int => out.add(new IntNode(x))
      case x: Long => out.add(new LongNode(x))
      case x: Float => out.add(new DoubleNode(x))
      case x: Double => out.add(new DoubleNode(x))
      case x: String => out.add(new TextNode(x))
      case x: JsonNode => out.add(x)
    }
    out
  }

  val randomSeed = 54321
  val rng = new Random(randomSeed)
  val alphanumeric = rng.alphanumeric.iterator

  def data(): JsonNode = {
    rng.nextInt(9) match {
      case 0 => NullNode.getInstance
      case 1 => if (rng.nextBoolean()) BooleanNode.getTrue else BooleanNode.getFalse
      case 2 => new IntNode(rng.nextInt())
      case 3 => new LongNode(rng.nextLong())
      case 4 => new DoubleNode(rng.nextFloat())
      case 5 => new DoubleNode(rng.nextDouble())
      case 6 => new TextNode(rng.nextString(rng.nextInt(30)))
      case 7 => m((for (i <- 0 until rng.nextInt(3)) yield (rng.nextString(rng.nextInt(10)) -> data())): _*)
      case 8 => a((for (i <- 0 until rng.nextInt(3)) yield data()): _*)
    }
  }

  val oldNames = mutable.Set[String]()
  def name(maxLength: Int): String = {
    var out: String = null
    while (out == null  ||  oldNames.contains(out)) {
      val first: Char = alphanumeric find { x => ('A' <= x  &&  x <= 'Z') } get
      val rest: String = alphanumeric.take(rng.nextInt(maxLength) - 1).mkString
      out = first + rest
    }
    oldNames.add(out)
    out
  }

  def names(number: Int, maxLength: Int): ArrayNode =
    a((for (i <- 0 until number) yield name(maxLength)): _*)

  def string(maxLength: Int): String = if (rng.nextBoolean()) rng.nextString(rng.nextInt(maxLength)) else name(maxLength)

  def strings(number: Int, maxLength: Int): ArrayNode =
    a((for (i <- 0 until number) yield string(maxLength)): _*)

  def fields(number: Int): ArrayNode = a(
    (for (i <- 0 until number) yield {
      val out = m("name" -> name(10), "type" -> avroType())
      //// FIXME: have to ensure that default satisfies type
      // if (rng.nextBoolean())
      //   out.put("default", data())
      if (rng.nextBoolean())
        out.put("order", List("ascending", "descending", "ignore")(rng.nextInt(3)))
      if (rng.nextBoolean())
        out.put("aliases", names(rng.nextInt(3), 10))
      if (rng.nextBoolean())
        out.put("doc", string(30))
      out
    }): _*)

  def avroType(parentIsUnion: Boolean = false): JsonNode = {
    rng.nextInt(if (parentIsUnion) 21 else 22) match {
      case 0 => new TextNode("null")
      case 1 => m("type" -> "null")
      case 2 => new TextNode("boolean")
      case 3 => m("type" -> "boolean")
      case 4 => new TextNode("int")
      case 5 => m("type" -> "int")
      case 6 => new TextNode("long")
      case 7 => m("type" -> "long")
      case 8 => new TextNode("float")
      case 9 => m("type" -> "float")
      case 10 => new TextNode("double")
      case 11 => m("type" -> "double")
      case 12 => new TextNode("bytes")
      case 13 => m("type" -> "bytes")
      case 14 => {
        val out = m("type" -> "fixed", "name" -> name(10), "size" -> rng.nextInt(100))
        if (rng.nextBoolean())
          out.put("namespace", name(10))
        if (rng.nextBoolean())
          out.put("aliases", names(rng.nextInt(3), 10))
        if (rng.nextBoolean())
          out.put("doc", string(30))
        out
      }
      case 15 => new TextNode("string")
      case 16 => m("type" -> "string")
      case 17 => {
        val out = m("type" -> "enum", "name" -> name(10), "symbols" -> names(rng.nextInt(10), 10))
        if (rng.nextBoolean())
          out.put("namespace", name(10))
        if (rng.nextBoolean())
          out.put("aliases", names(rng.nextInt(3), 10))
        if (rng.nextBoolean())
          out.put("doc", string(30))
        out
      }
      case 18 => m("type" -> "array", "items" -> avroType())
      case 19 => m("type" -> "map", "values" -> avroType())
      case 20 => {
        val out = m("type" -> "record", "name" -> name(10), "fields" -> fields(rng.nextInt(3)))
        if (rng.nextBoolean())
          out.put("namespace", name(10))
        if (rng.nextBoolean())
          out.put("aliases", names(rng.nextInt(3), 10))
        if (rng.nextBoolean())
          out.put("doc", string(30))
        out
      }
      case 21 => {
        val subtypes: Map[String, JsonNode] =
          (for (i <- 1 until rng.nextInt(2) + 2) yield avroType(parentIsUnion = true)).map({
            case x: TextNode => (x.getTextValue, m("type" -> x))
            case x: ObjectNode => (x.get("type").getTextValue, x)
          }).toMap
        a(subtypes.values.toSeq: _*)
      }
    }
  }

  def expressions(length: Int): ArrayNode = a((for (i <- 0 until length) yield expression()): _*)

  def arguments(length: Int): ArrayNode = a((for (i <- 0 until length) yield if (rng.nextInt(10) == 0) argument() else expression()): _*)

  def argument(): ObjectNode =
    if (rng.nextBoolean())
      m("fcnref" -> name(10))
    else {
      val params = a((for (j <- 0 until rng.nextInt(3)) yield m(name(10) -> avroType())): _*)
      val ret = avroType()
      m("params" -> params, "ret" -> ret, "do" -> expressions(rng.nextInt(3)))
    }

  def nameExprPairs(length: Int): ObjectNode = m((for (i <- 0 until length) yield name(10) -> expression()): _*)

  def expression(): JsonNode = rng.nextInt(35) match {
    case 0 => m(("u." + name(10)) -> arguments(rng.nextInt(3)))
    case 1 => NullNode.getInstance
    case 2 => if (rng.nextBoolean()) BooleanNode.getTrue else BooleanNode.getFalse
    case 3 => new LongNode(rng.nextLong())
    case 4 => new DoubleNode(rng.nextDouble())
    case 5 => new TextNode(name(10))
    case 6 => m("int" -> rng.nextInt())
    case 7 => m("float" -> rng.nextFloat())
    case 8 => m("string" -> string(30))
    case 9 => m("base64" -> new BinaryNode({
      val out = Array.ofDim[Byte](100)
      rng.nextBytes(out)
      out
    }))
    case 10 => m("type" -> avroType(), "value" -> data())
    case 11 => m("do" -> expressions(rng.nextInt(3)))
    case 12 => m("let" -> nameExprPairs(rng.nextInt(3)))
    case 13 => m("set" -> nameExprPairs(rng.nextInt(3)))
    case 14 => m("attr" -> name(10), "path" -> expressions(1 + rng.nextInt(2)))
    case 15 => m("attr" -> name(10), "path" -> expressions(1 + rng.nextInt(2)), "to" -> expression())
    case 16 => m("cell" -> name(10), "path" -> expressions(rng.nextInt(3)))
    case 17 => m("cell" -> name(10), "path" -> expressions(rng.nextInt(3)), "to" -> expression())
    case 18 => m("pool" -> name(10), "path" -> expressions(1 + rng.nextInt(2)))
    case 19 => m("pool" -> name(10), "path" -> expressions(1 + rng.nextInt(2)), "to" -> expression())
    case 20 => m("if" -> expression(), "then" -> expressions(rng.nextInt(3)))
    case 21 => m("if" -> expression(), "then" -> expressions(rng.nextInt(3)), "else" -> expressions(rng.nextInt(3)))
    case 22 => m("cond" -> a((for (i <- 0 until rng.nextInt(3)) yield m("if" -> expression(), "then" -> expressions(rng.nextInt(3)))): _*))
    case 23 => m("cond" -> a((for (i <- 0 until rng.nextInt(3)) yield m("if" -> expression(), "then" -> expressions(rng.nextInt(3)))): _*), "else" -> expressions(rng.nextInt(3)))
    case 24 => m("while" -> expression(), "do" -> expressions(rng.nextInt(3)))
    case 25 => m("do" -> expressions(rng.nextInt(3)), "until" -> expression())
    case 26 => m("for" -> nameExprPairs(rng.nextInt(3)), "until" -> expression(), "step" -> nameExprPairs(rng.nextInt(3)), "do" -> expressions(rng.nextInt(3)), "seq" -> rng.nextBoolean())
    case 27 => m("foreach" -> name(10), "in" -> expression(), "do" -> expressions(rng.nextInt(3)), "seq" -> rng.nextBoolean())
    case 28 => m("forkey" -> name(10), "forval" -> name(10), "in" -> expression(), "do" -> expressions(rng.nextInt(3)))
    case 29 => m("cast" -> expression(), "cases" -> a((for (i <- 0 until rng.nextInt(3)) yield m("as" -> avroType(), "named" -> name(10), "do" -> expressions(rng.nextInt(3)))): _*))
    case 30 => m("doc" -> string(30))
    case 31 => m("error" -> string(10))
    case 32 => m("error" -> string(10), "code" -> rng.nextInt())
    case 33 => m("log" -> expressions(rng.nextInt(3)))
    case 34 => m("log" -> expressions(rng.nextInt(3)), "namespace" -> name(10))
  }

  def functions(length: Int): ObjectNode = m(
    (for (i <- 0 until length) yield {
      val params = a((for (j <- 0 until rng.nextInt(3)) yield m(name(10) -> avroType())): _*)
      val ret = avroType()
      name(10) -> m("params" -> params, "ret" -> ret, "do" -> expressions(rng.nextInt(3)))
    }): _*)

  def cells(length: Int): ObjectNode = m(
    (for (i <- 0 until length) yield {
      name(10) -> m("type" -> avroType(), "init" -> data(), "shared" -> rng.nextBoolean())
    }): _*)

  def pools(length: Int): ObjectNode = m(
    (for (i <- 0 until length) yield {
      name(10) -> m("type" -> avroType(), "init" -> m("some" -> data()), "shared" -> rng.nextBoolean())
    }): _*)

  def engineConfig(): ObjectNode = {
    val out =
      m("name" -> name(10),
        "input" -> avroType(),
        "output" -> avroType(),
        "action" -> expressions(rng.nextInt(3))
      )
    val method =
    if (rng.nextBoolean())
      List("map", "emit", "fold")(rng.nextInt(3))
    else
      "map"
    if (method != "map")
      out.put("method", method)
    if (rng.nextBoolean())
      out.put("begin", expressions(rng.nextInt(3)))
    if (rng.nextBoolean())
      out.put("end", expressions(rng.nextInt(3)))
    if (rng.nextBoolean())
      out.put("fcns", functions(rng.nextInt(3)))
    if (method == "fold")
      out.put("zero", data())
    if (rng.nextBoolean())
      out.put("cells", cells(rng.nextInt(3)))
    if (rng.nextBoolean())
      out.put("pools", pools(rng.nextInt(3)))
    if (rng.nextBoolean())
      out.put("randseed", rng.nextLong())
    if (rng.nextBoolean())
      out.put("doc", string(30))
    if (rng.nextBoolean())
      out.put("metadata", data())
    if (rng.nextBoolean())
      out.put("options", m((for (i <- 0 until rng.nextInt(3)) yield string(10) -> data()): _*))
    out
  }

  var jsons: List[String] = Nil
  var asts: List[Ast] = Nil

  "Random PFA" must "generate valid configurations" taggedAs(RandomJson) in {
    for (i <- 0 until 100) yield {
      try {
        oldNames.clear()
        val json = engineConfig().toString
        if (json.size > 100)
          println(json.substring(0, 100) + "...")
        else
          println(json)

        jsons = json :: jsons
      }
      catch {
        case _: java.lang.StackOverflowError =>
      }
    }
    jsons = jsons.reverse
  }

  it must "be able to convert them to AST" taggedAs(RandomJson) in {
    for (oldJson <- jsons) {
      val ast = jsonToAst(oldJson)
      val newJson = ast.toString

      if (newJson.size > 100)
        println(newJson.substring(0, 100) + "...")
      else
        println(newJson)

      asts = ast :: asts
    }
    asts = asts.reverse
  }

  it must "convert to JSON that converts back to the same AST (full cycle)" taggedAs(RandomJson) in {
    for (ast <- asts) {
      val newJson = ast.toString
      if (newJson.size > 100)
        println(newJson.substring(0, 100) + "...")
      else
        println(newJson)

      jsonToAst(newJson) should be (ast)
    }
  }
}