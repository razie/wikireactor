/**
 *   ____    __    ____  ____  ____,,___     ____  __  __  ____
 *  (  _ \  /__\  (_   )(_  _)( ___)/ __)   (  _ \(  )(  )(  _ \           Read
 *   )   / /(__)\  / /_  _)(_  )__) \__ \    )___/ )(__)(  ) _ <     README.txt
 *  (_)\_)(__)(__)(____)(____)(____)(___/   (__)  (______)(____/    LICENSE.txt
 */
package razie.wiki.util

import org.json.{JSONArray, JSONObject}
import razie.Logging

import scala.collection.mutable.{HashMap, ListBuffer}

/**
 * json helpers
 *
 *  a json is represented as maps of (name,value) and lists of values, which can be recursive
 */
object js extends Logging {

  /** turn a map of name,value into json */
  def tojson(x: Map[_, _]): JSONObject = {
    val o = new JSONObject()
    x foreach {t:(_,_) =>
      t._2 match {
        case m: Map[_, _] => o.put(t._1.toString, tojson(m))
        case s: String => o.put(t._1.toString, s)
        case l: List[_] => o.put(t._1.toString, tojson(l))
        case h @ _ => o.put(t._1.toString, h.toString)
      }
    }
    o
  }

  /** turn a list into json */
  def tojson(x: List[_]): JSONArray = {
    val o = new JSONArray()
    x.foreach { t:Any =>
      t match {
        case s: Map[_, _] => o.put(tojson(s))
        case l: List[_] => o.put(tojson(l))
        case s: String => o.put(s)
        case s: JSONObject => o.put(s)
      }
    }
    o
  }

  /** recursively transform a name,value map */
  def jt(map: Map[_, _], path: String = "/")(f: PartialFunction[(String, String, Any), (String, Any)]): Map[String, Any] = {
    val o = new HashMap[String, Any]()
    map.foreach { t:(_,_) =>
      val ts = t._1.toString
      val r = if (f.isDefinedAt(path, ts, t._2)) f(path, ts, t._2) else (ts, t._2)
      if (r._1 != null && r._1.length() > 0)
        r._2 match {
          case s: Map[_, _] => o put (r._1.toString, jt(s, path + "/" + ts)(f))
          case l: List[_] => o put (r._1.toString, jt(l, path + "/" + ts)(f))
          case s @ _ => o put (r._1.toString, s)
        }
    }
    o.toMap
  }

  def jt(x: List[_])(f: PartialFunction[(String, String, Any), (String, Any)]): List[_] = jt(x, "/")(f)

  /** recursively transform a name,value map */
  def jt(x: List[_], path: String)(f: PartialFunction[(String, String, Any), (String, Any)]): List[_] = {
    val o = new ListBuffer[Any]()
    x.foreach { t:Any =>
      t match {
        case m: Map[_, _] => o.append(jt(m, path)(f))
        case l: List[_] => o.append(jt(l, path)(f))
      }
    }
    o.toList
  }

  val q = ""

  /** turn a map of name,value into json */
  def tojsons(x: Map[_, _], i:Int = 1): String = {
    var o = " "*(i-1) + "{\n"
    x.zipWithIndex foreach {t =>
      val (k,v) = t._1
      def comma = if(t._2 < x.size-1) ",\n" else "\n"
      v match {
        case m: Map[_, _] => o += (" "*i) + q + k.toString  + q+ tojsons(m, i+1) + comma
        case s: String => o += (" "*i) + q + k.toString + q+ ":"+ s + comma
        case l: List[_] => o += " "*i + q + k.toString + q + ":"+tojsons(l, i+1) + comma
        case h @ _ => o += " "*i + q + k.toString + q+ ":" + h.toString + comma
      }
    }
    o + " "*(i-1) + "}"
  }

  /** turn a list into json
    *
    * @param i is the level - start with 0
    */
  def tojsons(x: List[_], i:Int): String = {
    var o = " "*(i-1) + "[" + (if(x.headOption.exists(!_.isInstanceOf[String]))"\n" else "")
    x.zipWithIndex foreach { t =>
      def comma = if(t._2 < x.size-1) "," else ""
      t._1 match {
        case m: Map[_, _] => o += tojsons(m, i+1) +comma+"\n"
        case l: List[_] => o += tojsons(l, i+1) +comma+"\n"
        case s: String => o += " "*i+s +comma
        case s: JSONObject => o += " "*i+s.toString +comma
      }
    }
    o + (if(x.headOption.exists(!_.isInstanceOf[String])) " "*(i-1) else "") + "]"
  }

  def fromArray (a:JSONArray) : List[Any] = {
    (for (i <- 0 until a.length())
    yield a.get(i) match {
      case s: String => s
      case s: JSONObject => fromObject(s)
    }).toList
  }

  def fromObject (a:JSONObject) : Map[String, Any] = {
    import scala.collection.JavaConversions._
    val r = new HashMap[String, Any]
    for (k <- 0 until a.names.length)
    r.put(a.names.get(k).toString, a.get(a.names.get(k).toString) match {
          case s: String => s
          case s: JSONObject => fromObject(s)
          case s: JSONArray => fromArray(s)
        })
    r.toMap
  }
}


