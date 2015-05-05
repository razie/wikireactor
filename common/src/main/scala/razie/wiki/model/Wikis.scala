/**
 *   ____    __    ____  ____  ____,,___     ____  __  __  ____
 *  (  _ \  /__\  (_   )(_  _)( ___)/ __)   (  _ \(  )(  )(  _ \           Read
 *   )   / /(__)\  / /_  _)(_  )__) \__ \    )___/ )(__)(  ) _ <     README.txt
 *  (_)\_)(__)(__)(____)(____)(____)(___/   (__)  (______)(____/    LICENSE.txt
 */
package razie.wiki.model

import com.mongodb.DBObject
import com.mongodb.casbah.Imports._
import com.novus.salat._
import razie.Logging
import razie.db.{RMany, RazMongo}
import razie.db.RazSalatContext._
import razie.wiki.{WikiConfig, Services}
import razie.wiki.parser.{WAST, ParserSettings}
import razie.wiki.util.{VErrors, Validation}
import razie.wiki.admin.{Audit}

/** wiki factory and utils */
object Wikis extends Logging with Validation {
  //todo per realm
  final val PERSISTED = Array("Item", "Event", "Training", "Note", "Entry", "Form",
    "DRReactor", "DRElement", "DRDomain")

  final val TABLE_NAME = "WikiEntry"
  final val TABLE_NAMES = Map("DRReactor" -> "weDR", "DRElement" -> "weDR", "DRDomain" -> "weDR")

  final val RK = WikiConfig.RK
  final val DFLT = RK // todo replace with RK

  def apply (realm:String = RK) = Reactors(realm).wiki
  def rk = Reactors(RK).wiki
  def dflt = Reactors(Reactors.WIKI).wiki

  def fromGrated[T <: AnyRef](o: DBObject)(implicit m: Manifest[T]) = grater[T](ctx, m).asObject(o)

  // TODO refactor convenience
  def find(wid: WID): Option[WikiEntry] = apply(wid.getRealm).find(wid)

  // TODO find by ID is bad, no - how to make it work across wikis ?
  /** @deprecated optimize with realm */
  def findById(id: String) = find(new ObjectId(id))
  /** @deprecated optimize with realm */
  def find(id: ObjectId) =
    Reactors.reactors.foldLeft(None.asInstanceOf[Option[WikiEntry]])((a,b) => a orElse b._2.wiki.find(id))
  /** @deprecated optimize with realm */
  def findById(cat:String, id: String):Option[WikiEntry] = findById(cat, new ObjectId(id))
  /** @deprecated optimize with realm */
  def findById(cat:String, id: ObjectId): Option[WikiEntry] =
    Reactors.reactors.foldLeft(None.asInstanceOf[Option[WikiEntry]])((a,b) => a orElse b._2.wiki.findById(cat, id))

  /** @deprecated use realm */
  def category(cat: String) =
    if(cat.contains(".")) {
      val cs = cat.split("\\.")
      apply(cs(0)).category(cs(1))
    }
    else rk.category(cat)

  /** @deprecated use realm */
  def visibilityFor(cat: String): Seq[String] = rk.visibilityFor(cat)

  def linksFrom(from: UWID) = RMany[WikiLink]("from" -> from.grated)

  def linksTo(to: UWID) = RMany[WikiLink]("to" -> to.grated)

  def childrenOf(parent: UWID) =
    RMany[WikiLink]("to" -> parent.grated, "how" -> "Child").map(_.from)

  def linksFrom(from: UWID, role: String) =
    RMany[WikiLink]("from" -> from.grated, "how" -> role)

  def linksTo(to: UWID, role: String) =
    RMany[WikiLink]("to" -> to.grated, "how" -> role)


  // leave these vvvvvvvvvvvvvvvvvvvvvvvvvv

  def label(wid: WID):String = /*wid.page map (_.label) orElse*/
    apply(wid.getRealm).label(wid)

  def label(wid: UWID):String = /*wid.page map (_.label) orElse*/
    wid.wid.map(x=>label(x)).getOrElse(wid.nameOrId)

  // leave these ^^^^^^^^^^^^^^^^^^^^^^^^^^

    
   //todo refactor in own utils  vvv
    
  val MD = "md"
  val TEXT = "text"
  val markups = Array(MD, TEXT)

  import com.tristanhunt.knockoff.DefaultDiscounter._

  private def iformatName(name: String, pat: String, pat2: String = "") = name.replaceAll(pat, "_").replaceAll(pat2, "").replaceAll("_+", "_").replaceFirst("_$", "")

  /** format a simple name - try NOT to use this */
  //  def formatName(name: String): String = iformatName(name, """[ &?,;/:{}\[\]]""")

  /** these are the safe url characters. I also included ',which are confusing many sites */
  val SAFECHARS = """[^0-9a-zA-Z\$\-_\.()',]""" // DO NOT TOUCH THIS PATTERN!

  def formatName(name: String): String = iformatName(name, SAFECHARS, "") // DO NOT TOUCH THIS PATTERN!

  /** format a complex name cat:name */
  def formatName(wid: WID): String =
    if ("WikiLink" == wid.cat)
      iformatName(wid.name, """[ /{}\[\]]""")
    else
      formatName(wid.name)

  /** format an even more complex name
    * @param rk force links back to RK main or leave them
    */
  def formatWikiLink(curRealm:String, wid: WID, nicename: String, label: String, hover: Option[String] = None, rk: Boolean = false) = {
    val name = formatName(wid.name)
    val title = hover.map("title=\"" + _ + "\"") getOrElse ("")

    val r = wid.realm.getOrElse(curRealm)
    // all pages wihtout realm are assumed in current realm

    val bigName = Wikis.apply(r).index.getForLower(name.toLowerCase())
    if (bigName.isDefined || wid.cat.matches("User")) {
      var newwid = Wikis.apply(r).index.getWids(bigName.get).headOption getOrElse wid.copy(name=bigName.get)
//      var newwid = wid.copy(name=bigName.get)
      var u = Services.config.urlmap(newwid.formatted.urlRelative)

      if (rk && (u startsWith "/")) u = "http://" + Services.config.rk + u

      (s"""<a href="$u" title="$title">$label</a>""", Some(ILink(newwid, label)))
    } else if (rk)
      (s"""<a href="http://${Services.config.rk}${wid.formatted.urlRelative}" title="$title">$label<sup><b style="color:red">^</b></sup></a>""" ,
        Some(ILink(wid, label)))
    else {
      // hide it from google
      val prefix = (wid.realm.filter(_ != curRealm).map(r=>s"/we/$r").getOrElse("/wikie"))
      (s"""<a href="$prefix/show/${wid.wpath}" title="%s">$label<sup><b style="color:red">++</b></sup></a>""".format
        (hover.getOrElse("Missing page")),
        Some(ILink(wid, label)))
    }
  }

  def shouldFlag(name: String, label: String, content: String): Option[String] = {
    val a = Array(name, label, content)

    if (a.exists(_.matches("(?i)^.*<(" + ParserSettings.hnok + ")([^>]*)>"))) Some("WIKI_FORBIDDEN_HTML")
    else if (hasBadWords(content, adultWords)) Some("WIKI_HAS_ADULT")
    else None
  }

  private def include(c2: String)(implicit errCollector: VErrors): Option[String] = {
    var done = false
    val res = try {
      val INCLUDE = """(?<!`)\[\[include:([^\]]*)\]\]""".r
      val res1 = INCLUDE.replaceAllIn(c2, { m =>
        val content = for (
          wid <- WID.fromPath(m.group(1)) orErr ("bad format for page");
          c <- wid.content orErr s"content for ${wid.wpath} not found"
        ) yield c

        done = true
        //regexp uses $ as a substitution
        content.map(_.replaceAll("\\$", "\\\\\\$")).getOrElse("`[ERR Can't include $1 " + errCollector.mkString + "]`")
      })

      val TEMPLATE = """(?<!`)\[\[template:([^\]]*)\]\]""".r
      TEMPLATE.replaceAllIn(res1, { m =>
        done = true
        //todo this is parse-ahead, maybe i can make it lazy?
        val parms = WikiForm.parseFormData(c2)
        val content = template (m.group(1), Map()++parms)
        //regexp uses $ as a substitution
        content.replaceAll("\\$", "\\\\\\$")
      })
    } catch {
      case s: Throwable => log("Error: ", s); "`[ERR Can't process an include]`"
    }
    if (done) Some(res) else None
  }

  // TODO better escaping of all url chars in wiki name
  def preprocess(wid: WID, markup: String, content: String) = markup match {
    case MD =>
      implicit val errCollector = new VErrors()

      var c2 = content

      if (c2 contains "[[./")
        c2 = content.replaceAll("""\[\[\./""", """[[%s/""".format(wid.cat + ":" + wid.name)) // child topics
      if (c2 contains "[[../")
        c2 = c2.replaceAll("""\[\[\../""", """[[%s/""".format(wid.parentWid.map(wp => wp.cat + ":" + wp.name).getOrElse("?"))) // siblings topics

      // TODO stupid - 3 levels of include...
      include(c2).map { c2 = _ }.flatMap { x =>
        include(c2).map { c2 = _ }.flatMap { x =>
          include(c2).map { c2 = _ }
        }
      }

      Reactors(wid.getRealm).wiki.mkParser apply c2

    case TEXT => WAST.SState(content.replaceAll("""\[\[([^]]*)\]\]""", """[[\(1\)]]"""))

    case _ => WAST.SState("UNKNOWN MARKUP " + markup + " - " + content)
  }

  /** partial formatting function
    *
    * @param wid - the wid being formatted
    * @param markup - markup language being formatted
    * @param icontent - the content being formatted or "" if there is a WikiEntry being formatted
    * @param we - optional page for context for formatting
    * @return
    */
  private def format1(wid: WID, markup: String, icontent: String, we: Option[WikiEntry] = None) = {
    val res = try {
      var content =
        if(icontent == null || icontent.isEmpty) {
          if (wid.section.isDefined)
            preprocess(wid, markup, noBadWords(wid.content.mkString)).fold(we).s
          else
            // use preprocessed cache
            we.map(_.preprocessed).getOrElse(preprocess(wid, markup, noBadWords(icontent))).fold(we).s
        }
        else
          preprocess(wid, markup, noBadWords(icontent)).fold(we).s

      // TODO index nobadwords when saving/loading page, in the WikiIndex
      // TODO have a pre-processed and formatted page index I can use - for non-scripted pages, refreshed on save
      // run scripts
      val S_PAT = """`\{\{(call):([^#}]*)#([^}]*)\}\}`""".r

      content = S_PAT replaceSomeIn (content, { m =>
        try {
          // find the page with the scripts and call them
          val pageWithScripts = WID.fromPath(m group 2).flatMap(x => Wikis(x.getRealm).find(x)).orElse(we)
          pageWithScripts.flatMap(_.scripts.find(_.name == (m group 3))).filter(_.checkSignature).map(s => runScript(s.content, we))
        } catch { case _: Throwable => Some("!?!") }
      })

      // TODO this is experimental
//      val E_PAT = """`\{\{(e):([^}]*)\}\}`""".r
//
//      content = E_PAT replaceSomeIn (content, { m =>
//        try {
//          find the page with the scripts and call them
//          if((m group 2) startsWith "api.wix") Some(runScript(m group 2, we))
//          else None
//        } catch { case _: Throwable => Some("!?!") }
//      })

      // todo move to an AST approach of states that are folded here instead of sequential replaces
      val XP_PAT = """`\{\{\{(xp[l]*):([^}]*)\}\}\}`""".r

      content = XP_PAT replaceSomeIn (content, { m =>
        try {
          we.map(x => runXp(m group 1, x, m group 2))
        } catch { case _: Throwable => Some("!?!") }
      })

      val TAG_PAT = """`\{\{(tag)[: ]([^}]*)\}\}`""".r

      content = TAG_PAT replaceSomeIn (content, { m =>
        try {
          Some(hrefTag(wid, m group 2, m group 2))
        } catch { case _: Throwable => Some("!?!") }
      })

      // for forms
      we.map { x => content = new WForm(x).formatFields(content) }

      markup match {
        case MD => toXHTML(knockoff(content)).toString
        case TEXT => content
        case _ => "UNKNOWN MARKUP " + markup + " - " + content
      }
    } catch {
      case e : Throwable => {
        Audit.logdbWithLink("ERR_FORMATTING", wid.ahref, "[[ERROR FORMATTING]]: " + e.toString)
        log("[[ERROR FORMATTING]]: " + icontent.length + e.toString + "\n"+e.getStackTraceString)
        if(Services.config.isLocalhost) throw e
        "[[ERROR FORMATTING]] - sorry, dumb program here! The content is not lost: try editing this topic... also, please report this topic with the error and we'll fix it for you!"
      }
    }
    res
  }

  private def runXp(what: String, w: WikiEntry, path: String) = {
    var root = new razie.Snakk.Wrapper(new WikiWrapper(w.wid), WikiXpSolver)
    var xpath = "*/" + path // TODO why am I doing this?

    if (path startsWith "root(") {
      val parser = """root\(([^:]*):([^:)/]*)\)/(.*)""".r //\[[@]*(\w+)[ \t]*([=!~]+)[ \t]*[']*([^']*)[']*\]""".r
      val parser(cat, name, p) = path
      root = new razie.Snakk.Wrapper(new WikiWrapper(WID(cat, name)), WikiXpSolver)
      xpath = "*/" + p
    }

    val res: List[_] =
      if (razie.GPath(xpath).isAttr) (root xpla xpath).filter(_.length > 0) // sometimes attributes come as zero value?
      else {
        (root xpl xpath).collect {
          case ww: WikiWrapper => formatWikiLink(w.realm, ww.wid, ww.wid.name, ww.page.map(_.label).getOrElse(ww.wid.name))._1
        }
      }

//    println("XP:" + res.mkString)

    what match {
      case "xp" => res.headOption.getOrElse("?").toString
      case "xpl" => "<ul>" + res.map { x: Any => "<li>" + x.toString + "</li>" }.mkString + "</ul>"
    }
    //        else "TOO MANY to list"), None))
  }

  // scaled down formatting of jsut some content
  def sformat(content: String, markup:String="md") =
    format (WID("1","2"), markup, content)

  /** main formatting function
   *
   * @param wid - the wid being formatted
   * @param markup - markup language being formatted
   * @param icontent - the content being formatted or "" if there is a WikiEntry being formatted
   * @param we - optional page for context for formatting
   * @return
   */
  def format(wid: WID, markup: String, icontent: String, we: Option[WikiEntry] = None) = {
    var res = format1(wid, markup, icontent, we)

    // mark the external links
    val A_PAT = """(<a +href="http://)([^>]*)>([^<]*)(</a>)""".r
    res = A_PAT replaceSomeIn (res, { m =>
      if (Option(m group 2) exists (s=> !s.startsWith(Services.config.hostport)  &&
        !Services.isSiteTrusted(s))
        )
        Some("""$1$2 title="External site"><i>$3</i><sup>&nbsp;<b style="color:darkred">^</b></sup>$4""")
      else None
    })

    //    // modify external sites mapped to external URLs
    //    // TODO optimize - either this logic or a parent-based approach
    //    for (site <- Wikis.urlmap)
    //      res = res.replaceAll ("""<a +href="%s""".format(site._1), """<a href="%s""".format(site._2))

    // get some samples of what people get stuck on...
    if(res contains "CANNOT PARSE")
      Audit.logdbWithLink(
        "CANNOT_PARSE",
        wid.urlRelative,
        s"""${wid.wpath} ver ${we.map(_.ver)}""")

    res
  }

  private def runScript(s: String, page: Option[WikiEntry]) = {
    val up = razie.NoStaticS.get[WikiUser]
    val q = razie.NoStaticS.get[QueryParms]
    Services.runScript(s, page, up, q.map(_.q.map(t => (t._1, t._2.mkString))).getOrElse(Map()))
  }

  /** format content from a template, given some parms */
  def template(wpath: String, parms:Map[String,String]) = {
    (for (wid <- WID.fromPath(wpath).map(x=>if(x.realm.isDefined)x else x.r("wiki")); // templates are in wiki or rk
          c <- wid.content
    ) yield {
      val s1 = parms.foldLeft(c)((a,b)=>a.replaceAll("\\$\\{"+b._1+"\\}", b._2))
      s1.replaceAll("\\{\\{`section", "{{section").replaceAll("\\{\\{`.section", "{{.section").replaceAll("\\{\\{/`section", "{{/section")
    }) getOrElse (
      "No content template for: " + wpath + "\n\nAttributes:\n\n" + parms.map{t=>s"* ${t._1} = ${t._2}\n"}.mkString
      )
  }

  def noBadWords(s: String) = badWords.foldLeft(s)((x, y) => x.replaceAll("""\b%s\b""".format(y), "BLIP"))

  def hasBadWords(s: String, what: Array[String] = badWords): Boolean = s.toLowerCase.split("""\w""").exists(what.contains(_))

  def flag(we: WikiEntry) { flag(we.wid) }

  def flag(wid: WID, reason: String = "?") {
    Audit.logdb("WIKI_FLAGGED", reason, wid.toString)
  }

  final val badWords = "boohoo,hell".split(",")
  final val adultWords = "damn,heck".split(",")

  //todo who uses this
  def updateUserName(uold: String, unew: String) = {
    // TODO 1 optimize with find()
    // TODO 2 rename references
    val we = RazMongo("WikiEntry")
    for (u <- we.findAll() if "User" == u.get("category") && uold == u.get("name")) {
        u.put("name", unew)
        we.save(u)
    }
    val weo = RazMongo("WikiEntryOld")
    for (u <- weo.findAll() if "User" == u.get("category") && uold == u.get("name")) {
      u.put("name", unew)
      weo.save(u)
    }
  }
  
  def w(we: UWID):String = we.wid.map(wid=>w(wid)).getOrElse("ERR_NO_URL_FOR_"+we.toString)
  def w(we: WID, shouldCount: Boolean = true):String =
    Services.config.urlmap(we.urlRelative + (if (!shouldCount) "?count=0" else ""))

  /** make a relative href for the given tag. give more tags with 1/2/3 */
  def hrefTag(wid:WID, t:String,label:String) = {
    if(Array("Blog","Forum") contains wid.cat) {
      s"""<b><a href="${w(wid)}/tag/$t">$label</a></b>"""
    } else {
      if(wid.parentWid.isDefined) {
        s"""<b><a href="${w(wid.parentWid.get)}/tag/$t">$label</a></b>"""
      } else {
        s"""<b><a href="/wiki/tag/$t">$label</a></b>"""
      }
    }
  }

}
