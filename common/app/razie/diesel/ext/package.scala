package razie.diesel

import razie.diesel.dom._
import razie.diesel.dom.RDOM.{P, PM}
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 * simple, neutral domain model representation: class/object/function
 *
 * These are collected in RDomain
 */
package object ext {

  type Attrs = List[RDOM.P]
  type MatchAttrs = List[RDOM.PM]

  /** check if it matches a regex */
  def regexm(re:String, str:String) = {
    if((re startsWith "/") && (re endsWith "/")) {
      str.matches(re.substring(1,re.length-1))
    } else
      false
  }

  /** check to match the arguments */
  def sketchAttrs(defs:MatchAttrs, cole: Option[MatchCollector] = None)(implicit ctx: ECtx) : Attrs = {
    defs.map(p=> P(p.name, p.dflt, p.ttype, p.ref, p.multi))
  }

  /** a single match, collected when looking for expectations */
  class SingleMatch(val x: Any) {
    var score = 0;
    val diffs = new mutable.HashMap[String, (Any, Any)]() // (found, expected)

    def plus(s: String) = {
      score += 1
    }

    def minus(name: String, found: Any, expected:Any) = {
      diffs.put(name, (found.toString, expected.toString))
    }
  }

  /** collects the intermediary tests for a match, when looknig for expectations */
  class MatchCollector {
    var cur = new SingleMatch("")
    var highestScore = 0;
    var highestMatching: Option[SingleMatch] = None
    val old = new ListBuffer[SingleMatch]()

    def done = {
      if (cur.score >= highestScore) {
        highestScore = cur.score
        highestMatching = Some(cur)
      }
      old.append(cur)
    }

    def newMatch(x: Any) = {
      done
      cur = new SingleMatch(x)
    }

    def plus(s: String) = cur.plus(s)

    def minus(name: String, found: Any, expected:Any) = cur.minus(name, found, expected)
  }

  def check (in:P, pm:PM)(implicit ctx: ECtx) = {
    in.name == pm.name && {
      val r = new BCMP2(in.valExpr, pm.op, pm.valExpr).apply("")
      if(! r) {
        // name found but no value match - mark the name
      }
      r
    }
  }

  /**
   * matching attrs
   *
   * (a,b,c) they occur in whatever sequence
   *
   * (1,b,c) it occurs in position with value
   *
   * (a=1) it occurs with value
   *
   */
  def testA(in: Attrs, cond: MatchAttrs, cole: Option[MatchCollector] = None, foundName:Option[RDOM.P => Unit]=None)(implicit ctx: ECtx) = {
    // for each match
    cond.zipWithIndex.foldLeft(true)((a, b) => a && {
      var res = false

      // testing for name and value
      if (b._1.dflt.size > 0 || b._1.expr.isDefined) {
        if (b._1.name.size > 0) {
          res = in.exists(x => check(x, b._1)) || ctx.exists(x => check(x, b._1))
          if(!res) in.find(_.name == b._1.name).map {p=>
            // mark it in the cole
            foundName.map(_.apply(p))
          }
          if (res) cole.map(_.plus(b._1.name + b._1.op + b._1.dflt))
          else cole.map(_.minus(b._1.name, in.find(_.name == b._1.name).mkString, b._1))
        }
      } else {
        // test just the name (presence): check and record the name failure
        if (b._1.name.size > 0) {
          res = in.exists(_.name == b._1.name) || ctx.exists(_.name == b._1.name)
          if (res) cole.map(_.plus(b._1.name))
          else cole.map(_.minus(b._1.name, b._1.name, b._1))
        }
      }
      res
    })
  }

  trait HasPosition {
    def pos : Option[EPos]

    def kspan(s: String, k: String = "default", specPos:Option[EPos] = None) = {
      def mkref: String = pos.orElse(specPos).map(_.toRef).mkString
      pos.map(p =>
        s"""<span onclick="$mkref" style="cursor:pointer" class="label label-$k">$s</span>"""
      ) getOrElse
        s"""<span class="label label-$k">$s</span>"""
    }
  }

  object CanHtml {
    def prepTitle(title:String) = {
      val x = title.replaceAll("\\\"", "\\\"")
      val t = if(title.length > 0) s"""title="$x" """ else ""
      t
    }

    def span(s: String, k: String = "default", title:String="") = {
      val t = prepTitle(title)
      s"""<span class="label label-$k" $t>$s</span>"""
    }
  }

  /** instances have an toHtml method */
  trait CanHtml {
    /** format an html keyword span
      *
      * @param s the keyword
      * @param k the color code
      * @param title optional hover title
      * @param extra optional other attrs
      * @return
      */
    def span(s: String, k: String = "default", title:String="", extra:String="") = {
      val t = CanHtml.prepTitle(title)
      s"""<span class="label label-$k" $t $extra>$s</span>"""
    }

    /** format an html message span
      *
      * @param e entity
      * @param a action
      * @param title optional hover title
      * @return
      */
    def ea(e: String, a: String, title:String="") = {
      val t = CanHtml.prepTitle(title)
      s"""<span class="label label-default" xstyle="background:lightgray" $t><span style="font-weight:bold; color:lightblue">$e</span>.<span class="" style="font-weight:bold; color:moccasin">$a</span></span>"""
    }

    /** format an html element span
      */
    def token(s: String, title:String="", extra:String="") = {
      val t = CanHtml.prepTitle(title)
      s"""<span $t $extra>$s</span>"""
    }

    def tokenValue(s: String) =
      "<code>"+token (s, "value", """ class="string" """)+"</code>"

    def toHtml: String
  }

  def toHtmlAttrs(attrs: Attrs)      = if(attrs.nonEmpty) s"""${attrs.map(_.toHtml).mkString("(", ", ", ")")}""" else ""
  def toHtmlMAttrs(attrs: MatchAttrs) = if(attrs.nonEmpty) s"""${attrs.map(_.toHtml).mkString("(", ", ", ")")}""" else ""

  //todo when types are supported, remove this method and all its uses
  def stripQuotes(s:String) =
    if(s.startsWith("\"") && s.endsWith("\"")) s.substring(1,s.length-1) else s

}
