/**
 *  ____    __    ____  ____  ____,,___     ____  __  __  ____
 * (  _ \  /__\  (_   )(_  _)( ___)/ __)   (  _ \(  )(  )(  _ \           Read
 *  )   / /(__)\  / /_  _)(_  )__) \__ \    )___/ )(__)(  ) _ <     README.txt
 * (_)\_)(__)(__)(____)(____)(____)(___/   (__)  (______)(____/    LICENSE.txt
 */
package razie.diesel.ext

import com.mongodb.casbah.Imports._
import mod.diesel.model.DomAst
import razie.diesel.dom.RDOM._
import razie.diesel.dom._

import scala.Option.option2Iterable
import scala.collection.mutable.ListBuffer
import scala.util.Try

/** test - expect a message m. optional guard */
case class ExpectM(not:Boolean, m: EMatch) extends CanHtml with HasPosition {
  var when : Option[EMatch] = None
  var pos : Option[EPos] = None
  def withPos(p:Option[EPos]) = {this.pos = p; this}

  override def toHtml = kspan("expect::") +" "+ m.toHtml

  override def toString = "expect:: " + m.toString

  def withGuard (guard:EMatch) = { this.when = Some(guard); this}
  def withGuard (guard:Option[EMatch]) = { this.when = guard; this}

  /** check to match the arguments */
  def sketch(cole: Option[MatchCollector] = None)(implicit ctx: ECtx) : List[EMsg] = {
    var e = EMsg("generated", m.cls, m.met, sketchAttrs(m.attrs, cole))
    e.pos = pos
    //      e.spec = destSpec
    //      count += 1
    List(e)
  }
}

// todo use a EMatch and combine with ExpectM - empty e/a
/** test - expect a value or more. optional guard */
case class ExpectV(not:Boolean, pm:MatchAttrs) extends CanHtml with HasPosition {
  var when : Option[EMatch] = None
  var pos : Option[EPos] = None
  def withPos(p:Option[EPos]) = {this.pos = p; this}

  override def toHtml = kspan("expect::") +" "+ pm.map(_.toHtml).mkString("(", ",", ")")

  override def toString = "expect:: " + pm.mkString("(", ",", ")")

  def withGuard (guard:EMatch) = { this.when = Some(guard); this}
  def withGuard (guard:Option[EMatch]) = { this.when = guard; this}

  /** check to match the arguments */
  def test(a:Attrs, cole: Option[MatchCollector] = None, nodes:List[DomAst])(implicit ctx: ECtx) = {
    testA(a, pm, cole, Some({p=>
      // start a new collector to mark this value
      cole.foreach(c=>nodes.find(_.value.asInstanceOf[EVal].p.name == p.name).foreach(n=>c.newMatch(n)))
    }))
  }

  /** check to match the arguments */
  def sketch(cole: Option[MatchCollector] = None)(implicit ctx: ECtx) : Attrs = {
    sketchAttrs(pm, cole)
  }

}

// a match case
case class EMatch(cls: String, met: String, attrs: MatchAttrs, cond: Option[EIf] = None) extends CanHtml {
  // todo match also the object parms if any and method parms if any
  def test(e: EMsg, cole: Option[MatchCollector] = None)(implicit ctx: ECtx) = {
    if ("*" == cls || e.entity == cls || regexm(cls, e.entity)) {
      cole.map(_.plus(e.entity))
      if ("*" == met || e.met == met || regexm(met, e.met)) {
        cole.map(_.plus(e.met))
        testA(e.attrs, attrs, cole) && cond.fold(true)(_.test(e.attrs, cole))
      } else false
    } else false
  }

  /** extract a message signature from the match */
  def asMsg = EMsg("", cls, met, attrs.map{p=>
    // extract the sample value
    val df = if(p.dflt.nonEmpty) p.dflt else p.expr match {
      case Some(CExpr(e, _)) => e
      case _ => ""
    }
    P (p.name, df, p.ttype, p.ref, p.multi)
  })

  override def toHtml = ea(cls, met) + " " + toHtmlMAttrs(attrs)

  override def toString = cls + "." + met + " " + attrs.mkString("(", ",", ")")
}

/**
  * just a call to next - how to call next: wait => or no wait ==>
  */
case class ENext (msg:EMsg, arrow:String, cond: Option[EIf] = None) extends CanHtml {
  // todo match also the object parms if any and method parms if any
  def test(cole: Option[MatchCollector] = None)(implicit ctx: ECtx) = {
    cond.fold(true)(_.test(List.empty, cole))
  }

  override def toHtml   = arrow + " " + msg.toHtml
  override def toString = arrow + " " + msg.toString
}

// a context
case class ERule(e: EMatch, i: List[EMap]) extends CanHtml with EApplicable with HasPosition {
  var pos : Option[EPos] = None
  override def test(m: EMsg, cole: Option[MatchCollector] = None)(implicit ctx: ECtx) =
    e.test(m, cole)

  override def apply(in: EMsg, destSpec: Option[EMsg])(implicit ctx: ECtx): List[Any] =
    i.flatMap(_.apply(in, destSpec, pos))

  override def toHtml = span("when::", "default") + s" ${e.toHtml} ${i.map(_.toHtml).mkString("<br>")} <br>"

  override def toString = "when:: " + e + " => " + i.mkString
}

// a simple condition
case class EIf(attrs: MatchAttrs) extends CanHtml {
  def test(e:Attrs, cole: Option[MatchCollector] = None)(implicit ctx: ECtx) =
    testA(e, attrs, cole)

  override def toHtml = span("$if::") + attrs.mkString("<small>(", ", ", ")</small>")

  override def toString = "$if " + attrs.mkString
}

// a wrapper
case class EMock(rule: ERule) extends CanHtml with HasPosition {
  var pos : Option[EPos] = rule.pos
  override def toHtml = span(count.toString) + " " + rule.toHtml

  override def toString = count.toString + " " + rule.toString

  def count = rule.i.map(_.count).sum  // todo is slow
}

// just a wrapper for type
case class EVal(p: RDOM.P) extends CanHtml with HasPosition {
  def this(name:String, value:String) = this(P(name, value))

  var pos : Option[EPos] = None
  def withPos(p:Option[EPos]) = {this.pos = p; this}

  def toj =
    Map (
      "class" -> "EVal",
      "name" -> p.name,
      "value" -> p.dflt
    ) ++ {
      pos.map{p=>
        Map ("ref" -> p.toRef,
         "pos" -> p.toJmap
        )
      }.getOrElse(Map.empty)
    }

  override def toHtml   = kspan("val::") + p.toHtml
  override def toString = "val: " + p.toString
}

  /** some error, with a message and details */
  case class EError (msg:String, details:String="") extends CanHtml {
    override def toHtml =
    if(details.length > 0)
      span("error::", "danger", details, "style=\"cursor:help\"") + " " + msg
    else
      span("error::", "danger", details) + " " + msg
    override def toString = "error::"+msg
  }

  /** a simple info node with a message and details - details are displayed as a popup */
  case class EInfo (msg:String, details:String="") extends CanHtml with HasPosition {
    var pos : Option[EPos] = None
    def withPos(p:Option[EPos]) = {this.pos = p; this}

    override def toHtml =
      if(details.length > 0)
        span("info::", "info", details, "style=\"cursor:help\"") + " " + msg
      else
        span("info::", "info", details) + " " + msg
    override def toString = "info::"+msg
  }
