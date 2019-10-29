/**
  *  ____    __    ____  ____  ____,,___     ____  __  __  ____
  * (  _ \  /__\  (_   )(_  _)( ___)/ __)   (  _ \(  )(  )(  _ \           Read
  * )   / /(__)\  / /_  _)(_  )__) \__ \    )___/ )(__)(  ) _ <     README.txt
  * (_)\_)(__)(__)(____)(____)(____)(___/   (__)  (______)(____/    LICENSE.txt
  **/
package razie.wiki.parser

import razie.diesel.expr.{AExprIdent, Expr}
import razie.diesel.ext._

/** A simple parser for our simple specs
  *
  * DomParser is the actual Diesel/Dom parser.
  * We extend from it to include its functionality and then we add its parsing rules with withBlocks()
  */
class SimpleExprParser extends ExprParser {

  def parseExpr (input: String):Option[Expr] = {
    parseAll(expr, input) match {
      case Success(value, _) => Some(value)
      case NoSuccess(msg, next) => None
    }
  }

  def parseIdent (input: String):Option[AExprIdent] = {
    parseAll(aidentExpr, input) match {
      case Success(value, _) => Some(value)
      case NoSuccess(msg, next) => None
    }
  }
}

/** assignment - needed because the left side is more than just a val */
case class PAS (left:AExprIdent, right:Expr) extends CanHtml {
  override def toHtml = left.toHtml + "=" + right.toHtml
  override def toString = left.toString + "=" + right.toString
}
