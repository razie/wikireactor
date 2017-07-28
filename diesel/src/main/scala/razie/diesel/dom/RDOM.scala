package razie.diesel.dom

/** expression types */
object WTypes {
  final val NUMBER="Number"
  final val STRING="String"
  final val JSON="JSON"
  final val DATE="Date"
}

/**
 * simple, neutral domain model representation: class/object/function
 *
 * These are collected in RDomain
 */
object RDOM {
  // archtetypes

  class CM // abstract Class Member

  /** represents a Class
    *
    * @param name         name of the class
    * @param archetype
    * @param stereotypes
    * @param base         name of base class
    * @param typeParam    if higher kind, then type params
    * @param parms        class members
    * @param methods      class methods
    * @param assocs       assocs to other classes
    * @param props
    */
  case class C (name:String, archetype:String, stereotypes:String, base:List[String], typeParam:String, parms:List[P]=Nil, methods:List[F]=Nil, assocs:List[A]=Nil, props:List[P]=Nil) {
    override def toString = fullHtml

    def fullHtml = span("class::") + s""" <b><a href="/wikie/show/Category:$name">$name</a></b> """ +
      smap(typeParam) (" [" + _ + "]") +
      smap(archetype) (" &lt;" + _ + "&gt;") +
      smap(stereotypes) (" &lt;" + _ + "&gt;") +
      (if(base.exists(_.size>0)) "extends " else "") + base.map("<b>" + _ + "</b>").mkString +
      mks(parms, " (", ", ", ") ", "&nbsp;&nbsp;") +
      mks(methods, "{<br><hr>", "<br>", "<br><hr>}", "&nbsp;&nbsp;") +
      mks(props, " PROPS(", ", ", ") ", "&nbsp;&nbsp;")
  }

  /** create a P with the best guess for type */
  def typified (n:String, s:Any) = {
    s match {
      case i: Int => P(n, i.toString, WTypes.NUMBER)
      case _ => {
        if (s.toString.trim.startsWith("{")) P(n, s.toString, WTypes.JSON)
        else P(n, s.toString)
      }
    }
  }

  // todo complete type-aware
  case class PValue[T] (value:T, contentType:String)

  /** represents a parameter/member/attribute
    *
    * @param name   name of parm
    * @param dflt   current value or default value
    * @param ttype  type if known
    * @param ref
    * @param multi  is this a list/array?
    * @param expr   expression - for sourced parms
    */
  case class P (name:String, dflt:String, ttype:String="", ref:String="", multi:String="", expr:Option[Expr]=None,
                value:Option[PValue[_]] = None
               ) extends CM with razie.diesel.ext.CanHtml {

    def v[T](va:T, ctype:String="") = this.copy(value=Some(PValue[T](va, ctype)))

    /** current calculated value if any or the expression */
    def valExpr = if(dflt.nonEmpty || expr.isEmpty) CExpr(dflt, ttype) else expr.get

    override def toString =
      s"$name" +
        smap(ttype) (":" + ref + _) +
        smap(multi)(identity) +
        smap(dflt) (s=> "=" + (if("Number" == ttype) s else quot(s))) +
        (if(dflt=="") expr.map(x=>smap(x.toString) ("=" + _)).mkString else "")

    // todo refs for type, docs, position etc
    override def toHtml =
      s"<b>$name</b>" +
        smap(ttype) (":" + ref + _) +
        smap(multi)(identity) +
        smap(dflt) (s=> "=" + tokenValue(if("Number" == ttype) s else quot(s))) +
        (if(dflt=="") expr.map(x=>smap(x.toHtml) ("=" + _)).mkString else "")
  }

  /** represents a parameter match expression
    *
    * @param name   name to match
    * @param ttype  optional type to match
    * @param ref
    * @param multi
    * @param op
    * @param dflt
    * @param expr
    */
  case class PM (name:String, ttype:String, ref:String, multi:String, op:String, dflt:String, expr:Option[Expr] = None) extends CM with razie.diesel.ext.CanHtml {

    /** current calculated value if any or the expression */
    def valExpr = if(dflt.nonEmpty || expr.isEmpty) CExpr(dflt, ttype) else expr.get

    override def toString =
      s"$name" +
        (if(ttype!="String") smap(ttype) (":" + ref + _) else "") +
        smap(multi)(identity) +
        smap(dflt) (s=> op + (if("Number" == ttype) s else quot(s))) +
        (if(dflt=="") expr.map(x=>smap(x.toString) (" " + op +" "+ _)).mkString else "")

    override def toHtml =
      s"<b>$name</b>" +
        (if(ttype!="String") smap(ttype) (":" + ref + _) else "") +
      smap(multi)(identity) +
      smap(dflt) (s=> op + tokenValue(if("Number" == ttype) s else quot(s))) +
        (if(dflt=="") expr.map(x=>smap(x.toHtml) (" <b>"+op +"</b> "+ _)).mkString else "")
  }

  /** a function / method */
  case class F (name:String, parms:List[P], ttype:String, script:String="", body:List[Executable]=List.empty) extends CM {
    override def toString = "   "+  span("def:") + s" <b>$name</b> " +
      mks(parms, " (", ", ", ") ") +
      smap(ttype) (":" + _)
  }

  /** an executable statement */
  trait Executable {
    def sForm:String
    def exec(ctx:Any, parms:Any*):Any

    override def toString = sForm
  }

  case class V (name:String, value:String)  // attr value

  case class O (name:String, base:String, parms:List[V]) { // object = instance of class
    def toJson = parms.map{p=> p.name -> p.value}.toMap
  }

  /** Diamond */
  class D  (val roles:List[(String, String)], val ac:Option[AC]=None) //diamond association

  /** name is not required */
  case class A  (name:String, a:String, z:String, aRole:String, zRole:String, parms:List[P]=Nil, override val ac:Option[AC]=None) //association
    extends D (List(a->aRole, z->zRole), ac) {
    override def toString = s"assoc $name $a:$aRole -> $z:$zRole " +
      mks(parms, " (", ", ", ") ")
  }
  case class AC (name:String, a:String, z:String, aRole:String, zRole:String, cls:C) //association class

  // Diamond Class

  case class E (name:String, parms:List[P], methods:List[F]) //event
  case class R (name:String, parms:List[P], body:String) //rule
  case class X (body:String) //expression
  case class T (name:String, parms:List[P], body:String) //pattern

  case class TYP (name:String, ref:String, kind:String, multi:String)

}