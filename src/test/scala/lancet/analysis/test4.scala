/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/agpl.html.
 * 
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package lancet
package analysis

class TestAnalysis4 extends FileDiffSuite {

  val prefix = "test-out/test-analysis-4"

/* 
  make loop ranges explicit, reason about
  an infinite number of memory addresses
  (allocation site indexed by loop iteration)

  TODO -- WORK IN PROGRESS

  TODO/DONE: 
  - switch to optimistic? (done) 
      can we even talk about opt/pess here? 
      yes, see testProg1c: indirect store updates in
      loops rely on the address being loop invariant
  - make sense of inequalities/recurrences (mostly done)
  - allocations in loops: treat as arrays
      if we're storing the address, the loop variable
      may escape. is that a problem? (skolemize?)
  - towards lancet integration: unstructured control flow
      do we need arbitrary jumps? at least loops with
      several exits (no problem: just take fixindex
      of a different condition / cf. gated ssa).
*/

  object Test1 {

    // *** util

    def captureOutputResult[A](func: => A): (String,A) = {
      import java.io._
      val bstream = new ByteArrayOutputStream
      val r = withOutput(new PrintStream(bstream))(func) //func
      (bstream.toString, r)
    }
    def withOutput[A](out: java.io.PrintStream)(func: => A): A = {
      //val oldStdOut = System.out
      //val oldStdErr = System.err
      try {
        //System.setOut(out)
        //System.setErr(out)
        scala.Console.withOut(out)(scala.Console.withErr(out)(func))
      } finally {
        out.flush()
        out.close()
        //System.setOut(oldStdOut)
        //System.setErr(oldStdErr)
      }
    }


    // *** intermediate language / IR interfaces

    abstract class GVal {
      override def toString: String = this match {
        case GRef(s)   => s
        case GConst(x: String) => "\""+x+"\""
        case GConst(x) => s"$x"
      }
    }

    case class GRef(s: String) extends GVal
    case class GConst(x: Any) extends GVal

    abstract class Def {
      override def toString: String = mirrorDef(this, DString)
    }

    case class DMap(m: Map[GVal,GVal]) extends Def
    case class DUpdate(x: GVal, f: GVal, y: GVal) extends Def
    case class DSelect(x: GVal, f: GVal) extends Def
    case class DPlus(x: GVal, y: GVal) extends Def
    case class DTimes(x: GVal, y: GVal) extends Def
    case class DLess(x: GVal, y: GVal) extends Def
    case class DEqual(x: GVal, y: GVal) extends Def
    case class DNotEqual(x: GVal, y: GVal) extends Def
    case class DPair(x: GVal, y: GVal) extends Def
    case class DIf(c: GVal, x: GVal, y: GVal) extends Def
    case class DSum(n: GVal, x: String, c: GVal) extends Def
    case class DCollect(n: GVal, x: String, c: GVal) extends Def
    case class DFixIndex(x: String, c: GVal) extends Def
    case class DCall(f: GVal, x: GVal) extends Def
    case class DFun(f: String, x: String, y: GVal) extends Def
    case class DOther(s: String) extends Def

    def mirrorDef(d: Def, dst: DIntf { type From >: GVal }): dst.To = d match {
      case DMap(m)                                  => dst.map(m.asInstanceOf[Map[dst.From, dst.From]])
      case DUpdate(x: GVal, f: GVal, y: GVal)       => dst.update(x,f,y)
      case DSelect(x: GVal, f: GVal)                => dst.select(x,f)
      case DPlus(x: GVal, y: GVal)                  => dst.plus(x,y)
      case DTimes(x: GVal, y: GVal)                 => dst.times(x,y)
      case DLess(x: GVal, y: GVal)                  => dst.less(x,y)
      case DEqual(x: GVal, y: GVal)                 => dst.equal(x,y)
      case DNotEqual(x: GVal, y: GVal)              => dst.notequal(x,y)
      case DPair(x: GVal, y: GVal)                  => dst.pair(x,y)
      case DIf(c: GVal, x: GVal, y: GVal)           => dst.iff(c,x,y)
      case DSum(n: GVal, x: String, c: GVal)        => dst.sum(n,x,c)
      case DCollect(n: GVal, x: String, c: GVal)    => dst.collect(n,x,c)
      case DFixIndex(x: String, c: GVal)            => dst.fixindex(x,c)
      case DCall(f: GVal, x: GVal)                  => dst.call(f,x)
      case DFun(f: String, x: String, y: GVal)      => dst.fun(f,x,y)
      case DOther(s: String)                        => dst.other(s)
    }

    trait DIntf {
      type From
      type To
      def map(m: Map[From,From]): To
      def update(x: From, f: From, y: From): To
      def select(x: From, f: From): To
      def plus(x: From, y: From): To
      def times(x: From, y: From): To
      def less(x: From, y: From): To
      def equal(x: From, y: From): To
      def notequal(x: From, y: From): To
      def pair(x: From, y: From): To
      def iff(c: From, x: From, y: From): To
      def sum(n: From, x: String, c: From): To
      def collect(n: From, x: String, c: From): To
      def fixindex(x: String, c: From): To
      def call(f: From, x: From): To
      def fun(f: String, x: String, y: From): To
      def other(s: String): To
    }

    object DString extends DIntf {
      type From = Any
      type To = String
      def map(m: Map[From,From])                   = s"$m"
      def update(x: From, f: From, y: From)        = s"$x + ($f -> $y)"
      def select(x: From, f: From)                 = s"$x($f)"
      def plus(x: From, y: From)                   = s"$x + $y"
      def times(x: From, y: From)                  = s"$x * $y"
      def less(x: From, y: From)                   = s"$x < $y"
      def equal(x: From, y: From)                  = s"$x == $y"
      def notequal(x: From, y: From)               = s"$x != $y"
      def pair(x: From, y: From)                   = s"($x,$y)"
      def iff(c: From, x: From, y: From)           = s"if ($c) $x else $y"
      def sum(n: From, x: String, c: From)         = s"sum($n) { $x => $c }"
      def collect(n: From, x: String, c: From)     = s"collect($n) { $x => $c }"
      def fixindex(x: String, c: From)             = s"fixindex { $x => $c }"
      def call(f: From, x: From)                   = s"$f($x)"
      def fun(f: String, x: String, y: From)       = s"{ $x => $y }"
      def other(s: String)                         = s
    }

    object DDef extends DIntf {
      type From = GVal
      type To = Def
      def map(m: Map[From,From])                   = DMap(m)
      def update(x: From, f: From, y: From)        = DUpdate(x,f,y)
      def select(x: From, f: From)                 = DSelect(x,f)
      def plus(x: From, y: From)                   = DPlus(x,y)
      def times(x: From, y: From)                  = DTimes(x,y)
      def less(x: From, y: From)                   = DLess(x,y)
      def equal(x: From, y: From)                  = DEqual(x,y)
      def notequal(x: From, y: From)               = DNotEqual(x,y)
      def pair(x: From, y: From)                   = DPair(x,y)
      def iff(c: From, x: From, y: From)           = DIf(c,x,y)
      def sum(n: From, x: String, c: From)         = DSum(n,x,c)
      def collect(n: From, x: String, c: From)     = DCollect(n,x,c)
      def fixindex(x: String, c: From)             = DFixIndex(x,c)
      def call(f: From, x: From)                   = DCall(f,x)
      def fun(f: String, x: String, y: From)       = DFun(f,x,y)
      def other(s: String)                         = DOther(s)
    }

    trait DXForm extends DIntf {
      type From
      type To
      val next: DIntf
      def pre(x: From): next.From
      def post(x: next.To): To
      def map(m: Map[From,From])                   = post(next.map(m.map(kv=>pre(kv._1)->pre(kv._2))))
      def update(x: From, f: From, y: From)        = post(next.update(pre(x),pre(f),pre(y)))
      def select(x: From, f: From)                 = post(next.select(pre(x),pre(f)))
      def plus(x: From, y: From)                   = post(next.plus(pre(x),pre(y)))
      def times(x: From, y: From)                  = post(next.times(pre(x),pre(y)))
      def less(x: From, y: From)                   = post(next.less(pre(x),pre(y)))
      def equal(x: From, y: From)                  = post(next.equal(pre(x),pre(y)))
      def notequal(x: From, y: From)               = post(next.notequal(pre(x),pre(y)))
      def pair(x: From, y: From)                   = post(next.pair(pre(x),pre(y)))
      def iff(c: From, x: From, y: From)           = post(next.iff(pre(c),pre(x),pre(y)))
      def sum(n: From, x: String, c: From)         = post(next.sum(pre(n),x,pre(c)))
      def collect(n: From, x: String, c: From)     = post(next.collect(pre(n),x,pre(c)))
      def fixindex(x: String, c: From)             = post(next.fixindex(x,pre(c)))
      def call(f: From, x: From)                   = post(next.call(pre(f),pre(x)))
      def fun(f: String, x: String, y: From)       = post(next.fun(f,x,pre(y)))
      def other(s: String)                         = post(next.other(s))
    }

    object IRS extends DXForm {
      type From = String
      type To = String
      val next = DString
      def const(x: Any) = s"$x"
      def pre(x: String) = x
      def post(x: String): String = reflect(x)
      override def fun(f: String, x: String, y: From) = reflect(f,next.fun(f,x,pre(y)))
    }
    object IRS_Term extends DXForm {
      type From = GVal
      type To = String
      val next = DString
      def const(x: Any) = s"$x"
      def pre(x: GVal) = findDefinition(x.toString).map(d=>mirrorDef(d,this)).getOrElse(x.toString)
      def preBlock(x: GVal) = {
        val s = pre(x)
        if (s startsWith "if") s"{\n  ${s.replace("\n","\n  ")}\n}"
        else s
      }
      def post(x: String): String = x
      var rec: List[String] = Nil
      def reset = rec = Nil // HACK
      def scope[T](a: =>T): T = { reset; a }
      override def fun(f: String, x: String, y: From) = if (rec contains f) f else {
        rec ::= f; reflect(f,next.fun(f,x,pre(y)))
      }
      override def iff(c: From, x: From, y: From) = post(next.iff(pre(c),preBlock(x),preBlock(y)))
    }

    object IRD extends DXForm {
      type From = GVal
      type To = GVal
      val next = DDef
      //def const(x: Any) = GConst(x)
      def pre(x: GVal) = x
      def post(x: Def): GVal = dreflect(x)
      //override def fun(f: String, x: String, y: From) = dreflect(f,next.fun(f,x,pre(y)))

      object Def {
        def unapply(x:GVal): Option[Def] = x match {
          case GConst(_) => None
          case GRef(s)   => findDefinition(s)
        }
      }

      // dependencies / schedule
      def syms(d: Def): List[String] = {
        var sl: List[String] = Nil
        object collector extends DXForm {
          type From = GVal
          type To = String // ignore
          val next = DString
          def pre(x: GVal) = x match { case GRef(s) => sl ::= s; s case _ => "" }
          def post(x: String) = x
          //override def fun(f: String,x: String,y: GVal) = ""
        }
        mirrorDef(d,collector)
        sl
      }
      def boundSyms(d: Def): List[String] = d match { case DFun(f,x,y) => List(f,x) case _ => Nil }
      def deps(st: List[String]): List[(String,Def)] =
        globalDefs.filter(p=>st contains p._1) // TODO: opt
      def schedule(x: GVal) = {
        val start = x match { case GRef(s) => List(s) case _ => Nil }
        val xx = scala.virtualization.lms.util.GraphUtil.stronglyConnectedComponents[(String,Def)](deps(start), t => deps(syms(t._2)))
        xx.flatten.reverse
      }

      def printStm(p: (String,Def)) = println(s"val ${p._1} = ${p._2}")
      def termToString(p: GVal) = captureOutput(println(IRS_Term.scope(IRS_Term.pre(p))))
      def printTerm(p: GVal) = println(termToString(p))

      def dependsOn(a: GVal, b: GVal) = schedule(a).exists(p => GRef(p._1) == b || syms(p._2).contains(b.toString))

      def mkey(f: GVal, x: GVal): GVal = x match {
        case GConst(s) => GRef(f+"_"+s)
        case GRef(s) => GRef(f+"_"+s)
      }

      // evaluate with substitution, i.e. compute trans closure of subst
      def substTrans(env0: Map[GVal,GVal]): Map[GVal,GVal] = {
        var env = env0
        object XXO extends DXForm {
          type From = GVal
          type To = GVal
          val next = IRD
          def pre(x: GVal) = {/*println(s"pre $x / $env");*/ env.getOrElse(x,x) }
          def post(x: GVal) = x
          override def fun(f: String, x: String, y: From) = {
            //println(s"not changing fundef $f $x $y -> ${pre(y)}")
            GRef(f) // don't transform fundef
          }
        }
        for ((e,d) <- globalDefs.reverse) {
          val e2 = mirrorDef(d,XXO)
          //println(s"$e -> $e2 = $d")
          if (e2 != GRef(e))
            env = env + (GRef(e) -> e2)
        }
        // need to iterate because of sched order
        if (env == env0) env else substTrans(env)
      }


      def subst(x: GVal, a: GVal, b: GVal): GVal = x match {
        case `a`                 => b
        case GConst(_)           => x
        case Def(DUpdate(x,f,y)) => update(subst(x,a,b),subst(f,a,b),subst(y,a,b))
        case Def(DSelect(x,f))   => select(subst(x,a,b),subst(f,a,b))
        case Def(DMap(m))        => 

          // TODO: what if stuff is substituted to the same key??
          map(m.map(kv => subst(kv._1,a,b) -> subst(kv._2,a,b)))

        case Def(dd@DIf(c@Def(o@DEqual(u,v)),x,y)) => 
          // in general, if a implies c we can take branch x; if a refutes c, y.
          // if a & c implies that something is a constant, propagate that
          a match { 
            case Def(p@DEqual(`u`,s)) =>
              //println(s"another == flying by: $o, $p -> $b")
              if (b == const(1)) { // u == s
                if (equal(s,v) == const(1))      // u == s && s == v --> u == v:
                  return subst(x,c,const(1))
                else if (equal(s,v) == const(0)) // u == s && s != v --> u != v:
                  return subst(y,c,const(0))
              }
              if (b == const(0)) { // u != s
                if (equal(s,v) == const(1))      // u != s && s == v --> u != v:
                  return subst(y,c,const(0))
                //else if (equal(s,v) == const(0)) // u != s && s != v --> u == v:
                //  return subst(x,c,const(1))
              }
            case _ =>
          }
          iff(subst(c,a,b),subst(x,a,b),subst(y,a,b))
        case Def(dd@DIf(c@Def(o@DLess(u,v)),x,y)) => 
          // in general, if a implies c we can take branch x; if a refutes c, y.
          // if a & c implies that something is a constant, propagate that
          a match { 
            case Def(p@DLess(`u`,s)) =>
              // evaluate u < v  given that u < s or ¬(u < s)
              //println(s"another < flying by: $o, $p -> $b")
              // look for: a < x && !(a < x-1) ---> x == 1
              if (s == plus(v,const(-1))) { // other constants?
                if (b == const(1)) {
                  println(s"hit: $u<$v-1 implies $u<$v in $dd")
                  return subst(x,a,b)
                }
                if (b == const(0)) {
                  println(s"¬$u<$v-1 and $u<$v implies $u=$v-1 in $dd")
                  if (u.isInstanceOf[GConst])
                    return iff(subst(c,a,b),subst(x,s,plus(u,const(1))),subst(y,a,b))
                  else
                    return iff(subst(c,a,b),subst(x,u,s),subst(y,a,b))
                }
              }
              if (v == plus(s,const(-1))) { // other constants?
                if (b == const(0)) {
                  println(s"hit2: ¬$u<$s refutes $u<$s-1 in $dd")
                  return subst(y,a,b)
                }
                if (b == const(1)) {
                  println(s"hit2: $u<$s and ¬$u<$s-1 implies $u=$s-1 in $dd")
                  if (u.isInstanceOf[GConst])
                    return iff(subst(c,a,b),subst(x,a,b),subst(y,s,plus(u,const(1))))
                  else
                    return iff(subst(c,a,b),subst(x,a,b),subst(y,u,s))
                }
              }

            // look for: 0 < x6 && !(0 < x6 + -1) ---> x6 == 1
                // if (0 < x6) if (0 < x6 + -1) if (0 < x6 + -2) if (x6 < 102) if (x6 < 101) x100 + 1 
                // else x100 else x100 else x100 + 1 else x100 + 1 else 0

            case Def(p@DLess(`v`,s)) =>
              // !(x9 < 100) && (0 < 100) --> !(0<x9)
              // !(s,u)
              if (less(u,s) == const(1) && b == const(0)) {
                println(s"hit3: $v<$s=$b && $u<$s=${less(u,s)} --> $u<$v=0")
                // !(s < u) && (s < v) ---> (u < v)
                return subst(x,a,b)
              }

            case _ => 
          }
          iff(subst(c,a,b),subst(x,a,b),subst(y,a,b))
        case Def(DIf(c,x,y))     => iff(subst(c,a,b),subst(x,a,b),subst(y,a,b))
        case Def(DPair(x,y))     => pair(subst(x,a,b),subst(y,a,b))
        case Def(DPlus(x,y))     => plus(subst(x,a,b),subst(y,a,b))
        case Def(DTimes(x,y))    => times(subst(x,a,b),subst(y,a,b))
        case Def(o@DLess(u,v))     => 
          a match { // TODO
            case Def(p@DLess(`u`,s)) =>
              //if (v == s || less(s,v) == const(1)) return const(1)
            case _ =>
          }
          less(subst(u,a,b),subst(v,a,b))
        case Def(DEqual(x,y))    => equal(subst(x,a,b),subst(y,a,b))
        case Def(DNotEqual(x,y)) => notequal(subst(x,a,b),subst(y,a,b))
        case Def(DCall(f,y))     => call(subst(f,a,b),subst(y,a,b))
        case Def(DFun(f,x1,y))   => x//subst(y,a,b); x // binding??
        case Def(DSum(n,x,y))    => sum(subst(n,a,b),x,subst(y,a,b))
        case Def(DCollect(n,x,y))=> collect(subst(n,a,b),x,subst(y,a,b))
        case Def(DFixIndex(x,y)) => fixindex(x,subst(y,a,b))
        case Def(d)              => println("no subst: "+x+"="+d); x
        case _                   => x // TOOD
      }

      override def update(x: From, f: From, y: From): From = x match {
        case GConst("undefined") => update(dreflect(DMap(Map())),f,y) // f may be non-const
        //case GConst("undefined") => x 
        case GConst(m:Map[_,_]) if m.isEmpty => update(dreflect(DMap(Map())),f,y) // f may be non-const
        case Def(DMap(m)) => 
          f match {
            case GConst((u,v)) => update(x,const(u),update(select(x,const(u)),const(v),y))
            case GConst(_) => map(m + (f -> y)) // TODO: y = DIf ??
            case Def(DIf(c,u,v)) => iff(c,update(x,u,y),update(x,v,y))
            case Def(DPair(u,v)) => update(x,u,update(select(x,u),v,y))
            case _ => 
              // It would be nice to use f as a key even if it
              // is not a constant:
              //    map(m + (f -> y))
              // At present it is not quite clear under what conditions
              // this would work. Clearly, all keys in the map must
              // be statically known to be definitely different.
              super.update(x,f,y)
          }
        // TODO: DUpdate
        // case Def(DUpdate(x2,f2,y2)) => if (f2 == f) y2 else select(x2,f)
        case Def(DUpdate(x2,f2,y2)) if f2 == f => update(x2,f,y) // this one is conservative: m + (f -> y1) + (f -> y2)   --->  m + (f -> y2)  TODO: more aggressive, e.g. remove f in m, too?
        case Def(DIf(c,u,v)) => iff(c,update(u,f,y),update(v,f,y))
        case Def(DPair(u,v)) => update(x,u,update(select(x,u),v,y))
        case _ => super.update(x,f,y)
      }
      override def select(x: From, f: From): From          = x match {
        // TODO: should we really say "undefined".x = "undefined" ?
        case GConst("undefined") => GConst("undefined")
        case GConst(m:Map[_,_]) if m.isEmpty => GConst("undefined") // f may be non-const
        case Def(DMap(m)) => 
          f match {
            case GConst((u,v)) => select(select(x,const(u)),const(v))
            case GConst(_) => m.getOrElse(f, GConst("undefined"))
            case Def(DIf(c,u,v)) => iff(c,select(x,u),select(x,v))
            case Def(DPair(u,v)) => select(select(x,u),v)
            case _ => 
              var res: GVal = const("undefined")
              for ((k,v) <- m) {
                res = iff(equal(f,k), v, res)
              }
              res
              //return super.select(x,f)
              //m.getOrElse(f, GConst("undefined"))
          }
        case Def(DUpdate(x2,f2,y2)) => iff(equal(f2,f), y2, select(x2,f))
        case Def(DIf(c,x,y)) => iff(c,select(x,f),select(y,f))
        case Def(DPair(u,v)) => select(select(x,u),v)
        case Def(DCollect(n,x,c)) => subst(c,GRef(x),f)// FIXME: check bounds!!
        case _ => super.select(x,f)
      }
      def const(x: Any) = x match {
        case x: Double if x.toInt.toDouble == x => GConst(x.toInt)
        case _ => GConst(x)
      }
      override def plus(x: From, y: From)            = (x,y) match {
        case (GConst(x:Int),GConst(y:Int))       => const(x+y)
        case (GConst(x:Double),GConst(y:Int))    => const(x+y)
        case (GConst(x:Int),GConst(y:Double))    => const(x+y)
        case (GConst(x:Double),GConst(y:Double)) => const(x+y)
        case (GConst(0),_) => y
        case (_,GConst(0)) => x
        case (GConst("undefined"),_) => GConst("undefined")
        case (_,GConst("undefined")) => GConst("undefined")
        case (Def(DIf(c,x,z)),_) => iff(c,plus(x,y),plus(z,y))
        // random simplifications ...
        case (GConst(c),b:GRef) => plus(b,const(c)) // CAVE: non-int consts!
        case (Def(DPlus(a,b)),_) => plus(a,plus(b,y))
        case (a,Def(DTimes(a1,GConst(-1)))) if a == a1 => const(0) // a + (a * -1) --> 0
        case (Def(DTimes(a1,GConst(-1))),a) if a == a1 => const(0) // (a * -1) + a --> 0
        case (a,Def(DPlus(Def(DTimes(a1,GConst(-1))),c))) if a == a1 => c // a + (a * -1) + c --> c
        case (Def(DTimes(a1,GConst(-1))),Def(DPlus(a,c))) if a == a1 => c // (a * -1) + a + c --> c
        case (Def(DTimes(a1,GConst(c1:Int))),Def(DTimes(a2,GConst(c2:Int)))) if a1 == a2 => times(a1,const(c1+c2)) // (a * c1) + (a * c2) --> a * (c1 + c2)
        case (Def(DTimes(a1,GConst(c1:Int))),Def(DPlus(Def(DTimes(a2,GConst(c2:Int))),r))) if a1 == a2 => plus(times(a1,const(c1+c2)),r) // (a * c1) + (a * c2) + r --> a * (c1 + c2) + r
        //case (Def(DTimes(a,GConst(-1))),GConst(c:Int)) => plus(a,GConst(-c)) //(-a+c)=-(-c+a)
        // special case for address-tuples... HACK TODO: proper diff operator!!
        case (Def(DPair(u1,u2)), Def(DPair(v1,v2))) => pair(plus(u1,v1),plus(u2,v2))
        case (GConst((u1,u2)), GConst((v1,v2))) => pair(plus(const(u1),const(v1)),plus(const(u2),const(v2)))
        case (Def(DPair(u1,u2)), GConst((v1,v2))) => pair(plus(u1,const(v1)),plus(u2,const(v2)))
        case _ => super.plus(x,y)
      }
      override def times(x: From, y: From)            = (x,y) match {
        case (GConst(x:Int),GConst(y:Int))       => const(x*y)
        case (GConst(x:Double),GConst(y:Int))    => const(x*y)
        case (GConst(x:Int),GConst(y:Double))    => const(x*y)
        case (GConst(x:Double),GConst(y:Double)) => const(x*y)
        case (GConst(0),_) => GConst(0)
        case (_,GConst(0)) => GConst(0)
        case (GConst(1),_) => y
        case (_,GConst(1)) => x
        case (GConst("undefined"),_) => GConst("undefined")
        case (_,GConst("undefined")) => GConst("undefined")
        case (Def(DIf(c,x,z)),_) => iff(c,times(x,y),times(z,y))
        // random simplifications ...
        case (GConst(c),b:GRef) => times(b,const(c)) // CAVE: non-int consts!
        case (Def(DTimes(a,b)),_) => times(a,times(b,y))
        case (Def(DPlus(a,b)),c) => plus(times(a,c), times(b,c))
        case (a,Def(DPlus(b,c))) => plus(times(a,b), times(a,c))
        // special case for address-tuples... (u,v) * -1
        case (Def(DPair(u1,u2)), y) => pair(times(u1,y),times(u2,y))
        case (GConst((u1,u2)), y) => pair(times(const(u1),y),times(const(u2),y))
        case (x,GConst((v1,v2))) => pair(times(x,const(v1)),times(x,const(v2)))
        case _ => super.times(x,y)
      }
      override def less(x: From, y: From)            = (x,y) match {
        case (GConst(x:Int),GConst(y:Int)) => GConst(if (x < y) 1 else 0)
        case (Def(DIf(c,x,z)),_) => iff(c,less(x,y),less(z,y))
        case (_,Def(DIf(c,y,z))) => iff(c,less(x,y),less(x,z))
        // random simplifications ...
        case (GConst(0),Def(DPlus(a,GConst(b:Int)))) if b < 0 =>  less(const(-b),a)
        // 0 < -a + b  -->  a < b
        case (GConst(0),Def(DPlus(Def(DTimes(a,GConst(-1))),GConst(b:Int)))) =>  less(a,const(b))
        case (Def(DPlus(a,GConst(b:Int))),c) =>  less(a,plus(c,const(-b)))
        case _ if x == y => const(0)
        // case (GConst(0),Def(DPlus())) => y
        case _ => super.less(x,y)
      }
      override def equal(x: From, y: From)           = (x,y) match {
        case (GConst(x),GConst(y)) => GConst(if (x == y) 1 else 0)
        case (GConst(x:Int),Def(DPair(_,_))) => const(0)
        case (GConst(x:String),Def(DPair(_,_))) => const(0)
        case (Def(DPair(_,_)),GConst(x:Int)) => const(0)
        case (Def(DPair(_,_)),GConst(x:String)) => const(0)
        case (Def(DIf(c,x,z)),_) => iff(c,equal(x,y),equal(z,y))
        case (_,Def(DIf(c,y,z))) => iff(c,equal(x,y),equal(x,z))
        case _ if x == y => const(1)
        case _ => super.equal(x,y)
      }
      override def notequal(x: From, y: From)         = (x,y) match {
        case (GConst(x),GConst(y)) => GConst(if (x == y) 0 else 1)
        case (GConst(x:Int),Def(DPair(_,_))) => const(1)
        case (GConst(x:String),Def(DPair(_,_))) => const(1)
        case (Def(DPair(_,_)),GConst(x:Int)) => const(1)
        case (Def(DPair(_,_)),GConst(x:String)) => const(1)
        case (Def(DPair(GConst(u1),_)),GConst((v1,v2))) if u1 != v1 => const(1) // generalize?
        case (Def(DIf(c,x,z)),_) => iff(c,notequal(x,y),notequal(z,y))
        case (_,Def(DIf(c,y,z))) => iff(c,notequal(x,y),notequal(x,z))
        case _ if x == y => const(0)
        case _ => super.notequal(x,y)
      }
      override def pair(x: From, y: From)            = (x,y) match {
        case (GConst(x),GConst(y)) => const((x,y))
        case _ => super.pair(x,y)
      }
      override def iff(c: From, x: From, y: From):GVal = c match {
        case GConst(0) => y
        case GConst(_) => x
        case Def(DIf(c1,x1,y1)) => iff(c1,iff(x1,x,y),iff(y1,x,y))
        case _ if x == y => x
        // TODO: if (1 < x6) x6 < 100 else true = x6 < 100
        // Taking the else branch: x6 <= 1 implies x6 < 100, so both branches 
        // would return true, ergo condition is redundant.
        // This is a bit of a hack:
        case Def(DLess(GConst(a:Int),xx)) if { x match { 
          case Def(DLess(`xx`, GConst(b:Int))) => a<b && y == const(1) case _ => false }} => x
        // Another, similar case: if (1<x6) u-x6 else u-1 = 
        // Here we extend to if (0<x6) u-x6 else u-1 in the hope that the condition
        // becomes redundant later
        case Def(DLess(GConst(1),xx)) if subst(x,xx,const(1)) == y => iff(less(const(0),xx),x,y)
        case _ => 
          (x,y) match {
            case (Def(DMap(m1)), Def(DMap(m2))) => 
              // push inside maps
              map((m1.keys++m2.keys) map { k => k -> iff(c,m1.getOrElse(k,const("nil")),m2.getOrElse(k,const("nil")))} toMap)
            case _ =>
              // generate node, but remove nested tests on same condition
              val thenp = subst(x,c,GConst(1))
              val elsep = subst(y,c,GConst(0))

              // maybe we don't need conditional: 
              /*val thenpTry = subst(x,c,GConst(0))
              val elsepTry = subst(y,c,GConst(1))
              
              if (thenp == elsepTry && elsep == thenpTry) {
                println(s"### strange if $c $x $y")
                return x
              }*/
              if (thenp == elsep) thenp
              else if (thenp == const(1) && elsep == const(0)) c
              else super.iff(c,thenp,elsep)
          }
      }

      // LowBound(lowVal,lowBound,highBound,highVal) (not used!)
      object LowBound {
        def unapply(x: GVal): Option[(GVal,GVal,GVal,GVal)] = {
          x match {
            case Def(DIf(Def(DLess(lb,hb)), lv, hv)) => Some(lv,lb,hb,hv)
            case _ => None
          }
        }
      }

      override def sum(n: From, x: String, c: From) = c match {
        case _ =>
          super.sum(n,x,c) //subst(c,less(const(0),GRef(x)),const(1)))
      }

      override def collect(n: From, x: String, c: From) = c match {
        case _ =>
          super.collect(n,x,c) //subst(c,less(const(0),GRef(x)),const(1)))
      }

      override def fixindex(x: String, c: From)       = c match {
        case GConst(0) => const(0)
        // Q: why exactly are we subtracting 1 ?
        // it doesn't seem quite right but appears necessary
        case Def(DLess(GRef(`x`),u)) => u //plus(u,const(-1))
        case _ =>
          super.fixindex(x,c)
          //super.fixindex(x,subst(c,less(const(0),GRef(x)),const(1)))
      }

      override def call(f: From, x: From)            = f match {
        // inline calls to non-recursive functions
        case Def(DFun(f1,x1,y1)) if !dependsOn(y1,f) =>
          //println(s"*** will inline call $f($x1) = $y1 / $x1 -> $x")
          val res = subst(y1,GRef(x1),x)
          //println(s"**** inlined $f($x1)=$y1 --> $f($x)=$res")
          res
        case _ =>
          super.call(f,x)
      }

      override def fun(f: String, x: String, y: From) = y match {
        // (0)
        // f(x) = if (0 < x) f(x-1) + d else z    --->    f(x) = x * d + z
        // f(x) = if (0 < x) f(x-1)     else z    --->    f(x) = z
        // 
        // (1)
        // f(x) = if (0 < x) 
        //            if (f(x-1) < u) f(x-1) + d else f(x-1)
        //        else z                          --->    ?
        // (tricky because of recursion in condition: first
        // transform to non-recursive condition using monotonicity?)
        // 
        // TODO:
        // (2)
        // f(x) = if (0 < x) 
        //            f(x-1) + x * c + d
        //        else z                          --->    ?
        // summing the loop variable
        // (extension: e.g.  f(x-1) + k * x  )
        case Def(DIf(zc @ Def(DLess(GConst(0),GRef(`x`))),
            incRes, zeroRes)) =>

          // alt: calc y - subst(y,x,x-1) and see if it depends on x ...
          val prevx = plus(GRef(x),const(-1))
          val prevRes = call(GRef(f),prevx)

          incRes match {
            case `prevRes` =>
              fun(f,x,zeroRes)

            case Def(DPlus(`prevRes`, d)) if true && !dependsOn(d,GRef(x)) => 
              println(s"invariant stride $d")
              println(s"result = $zeroRes + $x * $d")
              val y0 = plus(times(GRef(x),d), zeroRes)
              // Q: do we ever access below-zero values? need > 0 condition?
              val y1 = iff(zc, y0, zeroRes) // CAREFUL!!
              fun(f,x,y1)
            
            /*case d @ GConst(_) if !dependsOn(d,GRef(x)) =>  // error in iterateAll if not a const ??
              // Q: do we need this case ?? it doesn't seem to do anything
              println(s"invariant res $d")
              println(s"result = $d")
              val y1 = iff(zc,d,zeroRes)
              fun(f,x,y1)*/

            case Def(DIf(Def(DLess(`prevRes`, uprBound)), // (1)
              Def(DPlus(`prevRes`, GConst(1))),  // TODO: non-unit stride
              `prevRes`)) =>
              println(s"upper bounded result")
              println(s"result = $uprBound")
              val y0 = plus(times(GRef(x),const(1)), zeroRes)
              val y1 = iff(less(GRef(x),uprBound), y0, uprBound)
              // Q: do we ever access below-zero values? need > 0 condition?
              val y2 = iff(zc, y1, zeroRes)
              fun(f,x,y2)

            case Def(DPlus(`prevRes`,  // (2)
              Def(DPlus(Def(DTimes(GRef(`x`), GConst(-1))), GConst(d))))) if true => 
              println(s"summing the loop var: -$x+$d")
              println(s"result = - $x * ($x + 1)/2 + $x*$d")
              // (0 to n).sum = n*(n+1)/2
              val xx = GRef(x)
              val y0 = times(times(xx,plus(xx,const(1))),const(-0.5))
              val y1 = plus(y0, times(xx,const(d)))
              val y2 = iff(zc, y1, zeroRes)
              fun(f,x,y2)
              // test case result: 405450
            case _ =>
              dreflect(f,next.fun(f,x,pre(y)))            
          }

        case _ =>
          dreflect(f,next.fun(f,x,pre(y))) // reuse fun sym (don't call super)
      }


      // *** lub computation for while loops

      // a: value before loop. b0: value before iteration. b1: value after iteration. 
      // returns: new values before,after
      def lub(a: GVal, b0: GVal, b1: GVal)(fsym: GVal, n0: GVal): (GVal, GVal) = { println(s"lub_$fsym($a,$b0,$b1)"); (a,b0,b1) } match {
        case (a,b0,b1) if a == b1 => (a,a)
        case (_, _, Def(DMap(m2))) => 
          val m = (m2.keys) map { k => (k, lub(select(a,k),select(b0,k),select(b1,k))(mkey(fsym,k),n0)) }
          println(m)
          (map(m.map(kv=>(kv._1,kv._2._1)).toMap), map(m.map(kv=>(kv._1,kv._2._2)).toMap))
        case (a,b0, Def(DUpdate(bX, `n0`, y))) if bX == b0 || (bX == map(Map()) && b0 == const("undefined")) => // array creation
          IRD.printTerm(a)
          IRD.printTerm(b0)
          IRD.printTerm(b1)
          //use real index var !! 
          val nX = mkey(fsym,n0)
          println(s"hit update at loop index -- assume collect")
          val r = collect(plus(n0,const(1)), nX.toString, subst(y,n0,nX))
          (r, r)
        case (a,b0, Def(DMap(m2))) if false /*disable*/=> // allocation!
          IRD.printTerm(a)
          IRD.printTerm(b0)
          IRD.printTerm(b1)
          println(s"hit map -- assume only 0 case differs (loop peeling)")
          val b0X = subst(b1,n0,plus(n0,const(-1)))
          (iff(less(const(0),n0),b0X,a), iff(less(const(0),n0),b1,a))
        case (a,Def(DIf(c0,x0,y0)),Def(DIf(c1,x1,y1))) if c0 == c1 && false /*disable*/=>
          // loop unswitching: treat branches separately
          // TODO: presumably the condition needs to fulfill some conditions for this to be valid - which?
          // simplest case: c < n, n < c

          print(s"break down if b0: "); IRD.printTerm(b0)
          print(s"break down if b1: "); IRD.printTerm(b1)

          val (zx0,zx1) = lub(a,x0,x1)(GRef(fsym.toString+"_+"+c1),n0)
          val (zy0,zy1) = lub(a,y0,y1)(GRef(fsym.toString+"_-"+c1),n0)

          (iff(c0, zx0, zy0), iff(c1, zx1, zy1))
        case _ if !IRD.dependsOn(b1, n0) && false /*disable*/=> 
          // value after the loop does not depend on loop index (but differs from val before loop).
          // we're probably in the first iteration, with a and b constants.
          // widen: assume a linear correspondence, with d = b - a
          val d = plus(b1,times(b0,const(-1))) // TODO: proper diff operator
          println(s"try iterative loop, d = $d")
          //(iff(less(const(0), n0), plus(a,times(plus(n0,const(-1)),d)), a),
          // iff(less(const(0), n0), plus(a,times(n0,d)), a))
          (plus(a,times(plus(n0,const(-1)),d)),
           plus(a,times(n0,d)))
        case (a/*@Def(DPair(a1,a2))*/,b0/*@Def(DPair(b01,b02))*/,Def(DPair(_,_)) | GConst(_: Tuple2[_,_])) 
          if !plus(b1,times(b0,const(-1))).isInstanceOf[GConst] => // XXX diff op should take precedence
          // example: (A,1), (B,(1,i)) TODO: safe??
          IRD.printTerm(a)
          IRD.printTerm(b0)
          IRD.printTerm(b1)
          println(s"hit pair -- assume only 0 case differs (loop peeling)")
          val b0X = subst(b1,n0,plus(n0,const(-1)))
          (iff(less(const(0),n0),b0X,a), iff(less(const(0),n0),b1,a))
          //(iff(less(const(0),n0),b0X,a), b1) XX FIXME?
        case (a/*@Def(DPair(a1,a2))*/,b0/*@Def(DPair(b01,b02))*/,b1@Def(DIf(Def(DLess(`n0`,u1)),b10,b20)))
          // dual example: (B,(1,i)),(A,1)
          if !dependsOn(u1,n0) =>
          IRD.printTerm(a)
          IRD.printTerm(b0)
          IRD.printTerm(b1)
          println(s"hit if dual -- assume only last case differs")
          val b10X = subst(b10,n0,plus(n0,const(-1)))
          val b20X = subst(b20,n0,plus(n0,const(-1)))
          (iff(less(plus(n0,const(-1)),u1),b10X,b20X), b1)
          //(iff(less(const(0),n0),b0X,a), b1) XX FIXME?

          // XXXXX FIXME / TODO
          // PROBLEM: boundary may change with each iteration!!!

        case _ =>
          // value after the loop (b) does depend on loop index and differs from val before loop.
          // handle in
          // TODO: case for alloc in loop -- x(0)=(A,1), x(i>0)=(B,(1,i))
          // (trying to handle this one above...)

          println("checky checky")
          IRD.printTerm(a)
          IRD.printTerm(b0)
          IRD.printTerm(b1)


          // look at numeric difference. see if symbolic values before/after are generalized in a corresponding way.
          // widen: compute new symbolic val before from symbolic val after (e.g. closed form)
          // if that's not possible, widen to explicit recursive form.
          //val b0 = iff(less(const(0), n0), subst(subst(b,less(const(0),n0),const(1)),n0,plus(n0,const(-1))), a) // take from 'init'?
          //val b1 = iff(less(const(0), n0), b, a)
          val d1 = plus(b1,times(b0,const(-1)))

          if (d1 != const("undefined")) { // do we have an integer?
            println("integer diff")
            IRD.printTerm(b0)
            IRD.printTerm(b1)
            IRD.printTerm(d1)

            def deriv(x: GVal): GVal = x match { // not used!
              case GConst(_) => const(0)
              case `n0` => const(1)
              case Def(DPlus(a,b)) => plus(deriv(a),deriv(b))
              case Def(DTimes(a,b)) => plus(times(a,deriv(b)),times(deriv(a),b)) // not accurate in discrete calculus?
              case _ => GRef(s"d$x/d$n0")
            }

            def poly(x: GVal): List[GVal] = x match {
              case `n0` => List(const(0),const(1))
              case Def(DTimes(`n0`,y)) => 
                val py = poly(y)
                if (py.isEmpty) Nil else const(0)::py
              case Def(DPlus(a,b)) => 
                val (pa,pb) = (poly(a),poly(b))
                if (pa.isEmpty || pb.isEmpty) Nil else {
                  val degree = pa.length max pb.length
                  (pa.padTo(degree,const(0)),pb.padTo(degree,const(0))).zipped.map(plus)
                    .reverse.dropWhile(_ == const(0)).reverse // dropRightWhile
                }
              case _ if !IRD.dependsOn(x, n0) => List(x)
              case _ => Nil // marker: not a simple polynomial
            }

            /*if (!IRD.dependsOn(d1, b0)) { // prevent self loops
              val nX = mkey(fsym,n0)
              println(s"assume sum")
              val r = sum(n0, nX.toString, subst(d1,n0,nX))
              return (r, r)
            }*/

            /*
              piecewise composition, multiple intervals.

              input:
                (value at start index, start index, end index, value increment)
              output:
                (value before iteration, value after iteration, value at end index)

              current iteration is assumed to be between start and end index
            */
            val fail = new Exception
            def break(ulo: GVal, nlo: GVal, nhi: GVal, d: GVal): (GVal,GVal,GVal) = d match {
              // loop invariant stride, i.e. constant delta i.e. linear in loop index
              case d if !IRD.dependsOn(d, n0) && d != const("undefined") => 
                println(s"confirmed iterative loop, d = $d")
                // before: ul + n * d
                // after:  ul + (n+1) * d
                val dn = plus(n0,times(nlo,const(-1)))
                val dh = plus(nhi,times(nlo,const(-1)))
                (plus(ulo,times(dn,d)),
                 plus(ulo,times(plus(dn,const(1)),d)),
                 plus(ulo,times(dh,d)))
              // piece-wise linear, e.g. if (n < 18) 1 else 0
              case Def(DIf(Def(DLess(`n0`, up)), dx, dy))
                if !IRD.dependsOn(up, n0) =>
                val dn = plus(nhi,times(nlo,const(-1)))
                println(s"split range of $n0 at $up: dx=$dx dy=$dy ulo=$ulo nlo=$nlo nhi=$nhi")
                val (u0,u1,uhi) = break(ulo,nlo,up,dx)
                val (v0,v1,vhi) = break(uhi,up,nhi,dy)
                println(s"before ($u0,$v0), after ($u1,$v1)")
                val (r0,r1) = (iff(less(n0,up), u0, v0), iff(less(n0,up), u1, v1))
                IRD.printTerm(r0)
                IRD.printTerm(r1)
                (r0,r1,vhi)
              case Def(DLess(`n0`, up)) // short cut
                if !IRD.dependsOn(up, n0) =>
                val (u0,u1,uhi) = break(ulo,nlo,up,const(1))
                val (v0,v1,vhi) = break(uhi,up,nhi,const(0))
                (iff(less(n0,up), u0, v0), iff(less(n0,up), u1, v1), vhi)
              // lower bounded case, atm only (0 < n) 1 else 0
              //case Def(DIf(Def(DLess(GConst(0), `n0`)), dx, dy)) =>
                // always true
                //break(ulo,nlo,nhi,dx)
              case _ => 

                val pp = poly(d)
                println("poly: " + pp)
                pp match {
                  case List(coeff0, coeff1) =>
                    println(s"found 2nd order polynomial: f'($n0)=$coeff1*$n0+$coeff0 -> f($n0)=$coeff1*$n0/2($n0+1)+$coeff0*$n0")

                    // f(n) = c1 * n/2*(n+1) + c0 * n
                    def eval(nX: GVal) = 
                      plus(times(times(times(nX,plus(nX,const(-1))),const(0.5)), coeff1), times(nX, coeff0))

                    val r0 = eval(n0)
                    val r1 = eval(plus(n0,const(1)))
                    val rh = eval(nhi)

                    // sanity check that we get the same diff
                    IRD.printTerm(r0)
                    IRD.printTerm(r1)
                    val dd = plus(r1,times(r0, const(-1)))
                    IRD.printTerm(dd)
                    val pp2 = poly(dd)
                    println("poly2: " + pp2)
                    assert(pp === pp2)

                    (plus(ulo,r0), plus(ulo,r1), plus(ulo,rh))

                  case _ =>
                    println("giving up for term:")
                    IRD.printTerm(d)
                    throw fail
                }
            }

            try { 
              val (u0,u1,uhi) = break(a,const(0),n0,d1)
              return (u0,u1)
            } catch {
              case `fail` =>
            }


            if (false) d1 match {
              // loop invariant stride, i.e. constant delta i.e. linear in loop index
              case d if !IRD.dependsOn(d, n0) && d != const("undefined") => 
                println(s"confirmed iterative loop, d = $d")
                return (plus(a,times(plus(n0,const(-1)),d)),
                 plus(a,times(n0,d)))
              // piece-wise linear, e.g. if (n < 18) 1 else 0
              case Def(DIf(Def(DLess(`n0`, up)), dx, dy))
                if !IRD.dependsOn(up, n0) && !IRD.dependsOn(dx, n0) && !IRD.dependsOn(dy, n0) => 
                val (u0,u1) = 
                (plus(a,times(plus(n0,const(-1)),dx)),
                 plus(a,times(n0,dx)))
                val n0minusUp = plus(n0,times(up,const(-1)))
                val (v0,v1) = 
                (plus(times(plus(up,const(-1)),dx),times(plus(n0minusUp,const(-1)),dy)),
                 plus(times(plus(up,const(-1)),dx),times(n0minusUp,dy)))
                return (iff(less(n0,up), u0, v0), iff(less(n0,up), u1, v1))
              case Def(DLess(`n0`, up)) // short cut
                if !IRD.dependsOn(up, n0) => 
                val (dx,dy) = (const(1),const(0))
                val (u0,u1) = 
                (plus(a,times(plus(n0,const(-1)),dx)),
                 plus(a,times(n0,dx)))
                val n0minusUp = plus(n0,times(up,const(-1)))
                val (v0,v1) = 
                (plus(times(plus(up,const(-1)),dx),times(plus(n0minusUp,const(-1)),dy)),
                 plus(times(plus(up,const(-1)),dx),times(n0minusUp,dy)))
                return (iff(less(n0,up), u0, v0), iff(less(n0,up), u1, v1))
              // no simple structure
              case d =>

                val pp = poly(d1)
                println("poly: " + pp)
                pp match {
                  case List(coeff0, coeff1) =>
                    println(s"found 2nd order polynomial: f'($n0)=$coeff1*$n0+$coeff0 -> f($n0)=$coeff1*$n0/2($n0+1)+$coeff0*$n0")
                    // c1 * n/2*(n+1) + c0 * n

                    val r0 = plus(times(times(times(plus(n0,const(-1)),n0),const(0.5)), coeff1), times(plus(n0,const(-1)), coeff0))
                    val r1 = plus(times(times(times(n0,plus(n0,const(1))),const(0.5)), coeff1), times(n0, coeff0))

                    // sanity check that we get the same diff
                    IRD.printTerm(r0)
                    IRD.printTerm(r1)
                    val dd = plus(r1,times(r0, const(-1)))
                    IRD.printTerm(dd)
                    val pp2 = poly(dd)
                    println("poly2: " + pp2)
                    assert(pp === pp2)

                    return (plus(a,r0), plus(a,r1))
                  case xx =>
                    println(s"giving up: deriv $xx")
                }
              }
          }

          // fall-through case
          println(s"recursive fun $fsym (init $a)")

          def wrapZero(x: GVal): GVal = iff(less(const(0), n0), x, a)

          (wrapZero(call(fsym,plus(n0,const(-1)))),
           b1)//wrapZero(call(fsym,n0)))
      }

      // generate function calls for recursive functions

      def lubfun(a: GVal, b: GVal)(fsym: GVal, n0: GVal): GVal = (a,b) match {
        case (a,b) if a == b => a
        case (_, Def(DMap(m2))) => 
          val m = (m2.keys) map { k => k -> lubfun(select(a,k),select(b,k))(mkey(fsym,k),n0) }
          map(m.toMap)
        case _ => 
          val b1 = b // iff(less(const(0),n0), b, a) // explicit zero case. needed??
          fun(fsym.toString, n0.toString, b1) 
          call(fsym,n0)
      }

    }


    // reflect/reify

    val varCount0 = 0
    var varCount = varCount0

    val globalDefs0 = Nil 
    var globalDefs: List[(String,Def)] = globalDefs0

    var globalDefsRhs: Map[String,Def] = Map()
    var globalDefsLhs: Map[Def,String] = Map()

    def rebuildGlobalDefsCache() = { globalDefsRhs = globalDefs.reverse.toMap; globalDefsLhs = globalDefs.reverse.map(kv => (kv._2,kv._1)).toMap }

    def freshVar = { varCount += 1; "x"+(varCount - 1) }

    def reflect(x: String, s: String): String = { println(s"val $x = $s"); x }

    def reflect(s: String): String = { val x = freshVar; println(s"val $x = $s"); x }
    def reify(x: => String): String = captureOutputResult(x)._1

    def findDefinition(s: String): Option[Def] = globalDefsRhs.get(s)
      //globalDefs.reverse.collectFirst { case (`s`,d) => d }

    def dreflect(x0: => String, s: Def): GVal = globalDefsLhs.get(s).map(GRef).getOrElse {
      val x = x0; 
      globalDefs = (x->s)::globalDefs
      globalDefsRhs = globalDefsRhs + (x->s)
      globalDefsLhs = globalDefsLhs + (s->x)
      println(s"val $x = $s")
      GRef(x)
    }
      //globalDefs.collect { case (k,`s`) => GRef(k) }.headOption getOrElse { 
      //val x = x0; globalDefs = globalDefs :+ (x->s); println(s"val $x = $s"); GRef(x) }
    def dreflect(s: Def): GVal = dreflect(freshVar,s)


    // *** input language Exp

    val IR = IRD

    type Val = IR.From
    type Var = String
    type Addr = String
    type Alloc = String
    type Field = String

    def vref(x: String): Val = IR.const(x)

    abstract class Exp
    case class Const(x: Any) extends Exp
    case class Direct(x: Val) extends Exp
    case class Ref(x: Var) extends Exp
    case class Assign(x: Var, y: Exp) extends Exp
    case class Plus(x: Exp, y: Exp) extends Exp
    case class Times(x: Exp, y: Exp) extends Exp
    case class Less(x: Exp, y: Exp) extends Exp
    case class Equal(x: Exp, y: Exp) extends Exp
    case class NotEqual(x: Exp, y: Exp) extends Exp
    case class New(x: Alloc) extends Exp
    case class Get(x: Exp, f: Exp) extends Exp
    case class Put(x: Exp, f: Exp, y: Exp) extends Exp
    case class If(c: Exp, a: Exp, b: Exp) extends Exp
    case class While(c: Exp, b: Exp) extends Exp
    case class Block(xs: List[Exp]) extends Exp {
      override def toString = "{\n  " + xs.map(_.toString).mkString("\n").replace("\n","\n  ") + "\n}"
    }

    // *** evaluator: Exp -> IR

    val store0 = IR.const(Map())
    val itvec0 = IR.const("top")

    var store: Val = store0
    var itvec: Val = itvec0

    def eval(e: Exp): Val = e match {
      case Const(x)    => IR.const(x)
      case Direct(x)   => IR.const(x)
      case Ref(x)      => IR.select(IR.select(store,IR.const("&"+x)), IR.const("val"))
      case Assign(x,y) => 
        val v = eval(y)
        store = IR.update(store, IR.const("&"+x), IR.update(IR.const(Map()), IR.const("val"), v))
        IR.const(())
      case Plus(x,y)      => IR.plus(eval(x),eval(y))
      case Times(x,y)     => IR.times(eval(x),eval(y))
      case Less(x,y)      => IR.less(eval(x),eval(y))
      case Equal(x,y)     => IR.equal(eval(x),eval(y))
      case NotEqual(x,y)  => IR.notequal(eval(x),eval(y))
      case New(x) => 
        val a = IR.pair(IR.const(x),itvec)
        store = IR.update(store, a, IR.const(Map()))
        a
      case Get(x, f) => 
        IR.select(IR.select(store, eval(x)), eval(f))
      case Put(x, f, y) => 
        val a = eval(x)
        val old = IR.select(store, a)
        store = IR.update(store, a, IR.update(old, eval(f), eval(y)))
        IR.const(())
      case If(c,a,b) => 
        val c1 = eval(c)
        //if (!mayZero(c1)) eval(a) else if (mustZero(c1)) eval(b) else {
          val save = store
          //assert(c1)
          val e1 = eval(a)
          val s1 = store
          store = save
          //assertNot(c1)
          val e2 = eval(b)
          val s2 = store
          store = IR.iff(c1,s1,s2)
          IR.iff(c1,e1,e2)
        //}
      case While(c,b) =>  

        /* Example:
            var y = 0
            while (y < 100) {
              if (y < 0)
                y -= 1
              else
                y += 1
            }
          Note that the behavior crucially depends on the initial condition.

            (0) Assume ŷ(i) = 0
                Evaluate loop body
                  z(i) = if (0 < i) { if (ŷ(i-1) < 0) ŷ(i-1)-1 else ŷ(i-1)+1 } else 0
                       = if (0 < i) { if (0 < 0) 0-1 else 0+1 } else 0
                       = if (0 < i) 1 else 0
                Detect conflict: ŷ(i) = 0 can't be true
                Generalize!

              --> for all i is misleading. just do
              y0 = 0 (assume)
              y1 = if (y0 < 0) y0-1 else y0+1
                 = if (0 < 0) 0-1 else 0+1
                 = 1
              pick ŷ(i) so that it fits ŷ(0)=y0, ŷ(1)=y1 and extrapolate the rest 
                                                   ^ why 1, really?

                ŷ(i) = if (0 < i) i else 0

              assume y0' = ŷ(i-1) = if (0 < i-1) i-1 else 0

              y1' = if (y0 < 0) y0-1 else y0+1
                  = if ((if (0 < i-1) i-1 else 0) < 0) y0-1 else y0+1
                  = ...

            (1) Naive iteration: ŷ(i) = if (0 < i) 1 else 0
                This won't terminate because we'll end up with:
                ŷ(i) = if (0 < i) if (0 < i-1) 2 else 1 else 0

            (2) Generalize: ŷ(i) = if (0 < i) i else 0
        */

        import IR._
        val saveit = itvec

        val loop = GRef(freshVar)
        val n0 = GRef(freshVar)

        val before = store

        itvec = pair(itvec,n0)


        var init = before
        var path = Nil: List[GVal]
        def iter: GVal = {
          println(s"starting spec loop with $init")
          assert(!path.contains(init), "hitting recursion: "+(init::path))
          path = init::path

          store = init

          //store = subst(store,less(n0,const(0)),const(0)) // 0 <= i

          val cv = eval(c)

          //store = subst(store,cv,const(1)) // assertTrue

          val afterC = store

          eval(b)

          store = subst(store,less(n0,const(0)),const(0)) // 0 <= i
          store = subst(store,less(fixindex(n0.toString, cv),n0),const(0)) // i <= n-1

          println("trip count:")
          IRD.printTerm(fixindex(n0.toString, cv))

          val afterB = store

          //val next = IR.iff(cv,afterB,afterC)
          // inside the loop we know the check succeeded.
          // TODO: need to worry about boundary cases!
          val next = subst(afterB,cv,const(1))

          println(s"lub($before, $next) = ?")

          val (initNew,nextNew) = lub(before, init, next)(loop,n0)

          println(s"lub($before, $next) = $initNew")
          if (init != initNew) { init = initNew; iter } else {

            //store = lubfun(before, afterB)(loop,n0)
            store = lubfun(before, nextNew)(loop,n0)
            // XXX why not just nextNew ??? 
            // need to define functions that are called from nextNew
            //store = nextNew

            // TODO: clarify intended semantics!
            // Is elem 0 the value after 0 iterations,
            // or the value computed in iteration 0?
            // The analogy of modeling values computed in
            // loops as arrays indexed by iteration would
            // suggest the meaning 'computed in iteration i'.
            // But then the value before the loop has index -1.
            // Need to investigate whether this is a problem.
            // It seems like we can avoid referring to -1
            // by proper index handling after the loop.

            // store at this point describes result *after* iteration i
            //  1 + (if (0<x) f(x-1) else 0)  =   if (0<x) f(x-1) + 1 else 1
            // but what we want for the function body:
            //  if (0<x) f(x-1) + 1 else 0
            // we rely on propagation of conditions to get there:

            //store = iff(less(const(0), n0), store, before)

            // The alternative would be to make f(i) denote
            // the computed element in iteration i, and then pick
            // element n-1 after the loop.
            // It may seem unintuitive that f(i) = i+1 for a
            // simple counting loop and we might want to fix
            // it up with rewriting.
            // On the other hand, for dynamic allocations, 
            // we get f(i) = new A_i, which makes a lot of
            // sense.
            //store = init
            cv
          }
        }

        val cv = iter

        if (findDefinition(loop.toString) == None) // ensure we have a top-level function
          fun(loop.toString, n0.toString, store)

        val nX = fixindex(n0.toString, cv) // TODO: check this ...
        println(s"fixindex: $nX")

        // TODO: if (0 < nX) !
        store = call(loop,plus(nX,const(-1)))
        //val cv1 = eval(c)
        //store = subst(store,cv1,const(0)) // assertFalse

        itvec = saveit
        
        println(s"*** after loop $store ***")

        IR.const(())

      case Block(Nil) => IR.const(())
      case Block(xs) => xs map eval reduceLeft ((a,b) => b)
    }


    // *** run and test

    def run(testProg: Exp): Unit = runAndCheck(testProg)("")

    def runAndCheck(testProg: Exp)(want: Any): Unit = {
      println("prog: " + testProg)
      store = store0
      itvec = itvec0
      varCount = varCount0
      globalDefs = globalDefs0
      rebuildGlobalDefsCache()
      val res = eval(testProg)
      println("res: " + res)
      println("store: " + store)
      val store2 = store//IR.iterateAll(store)
      println("transformed: " + store2)
      val sched = IR.schedule(store2)
      println("sched:")
      sched.foreach(IR.printStm)
      println("term:")
      val out = IR.termToString(store2)
      println(out)

      def clean(s: String) = s.replaceAll("\"","").replaceAll("\n","").replaceAll(" +","")

      if (want != "")
        expect(clean(want.toString))(clean(out)) //sanitize...

      //store.printBounds
      println("----")
    }
  }

  // test some integer computations

  def testA = withOutFileChecked(prefix+"A") {
    import Test1._
    Test1.runAndCheck {
      Block(List(
        Assign("i", Const(0)),
        Assign("y", Const(0)),
        Assign("x", Const(8)),
        While(Less(Ref("i"),Const(100)), Block(List(
          Assign("x", Const(7)),
          Assign("x", Plus(Ref("x"), Const(1))),
          Assign("y", Plus(Ref("y"), Const(1))),
          Assign("i", Plus(Ref("i"), Const(1)))
        )))
      ))
    } {
      """Map(
        "&i" -> Map("val" -> 100), 
        "&y" -> Map("val" -> 100), 
        "&x" -> Map("val" -> 8)
      )"""
    }

    Test1.runAndCheck {
      Block(List(
        Assign("x", Const(900)), // input
        Assign("y", Const(0)),
        While(Less(Const(0), Ref("x")), Block(List(
//          If(Less(Ref("y"),Const(17)), 
//            Block(List(
              Assign("y", Plus(Ref("y"), Const(1)))
//            )),
//            Block(Nil)
,//          ),
          Assign("x", Plus(Ref("x"), Const(-1)))
        ))),
        Assign("r", Ref("x"))
      ))
    }{
      """Map(
        "&x"  -> Map("val" -> 0), 
        "&y"  -> Map("val" -> 17), 
        "&r"  -> Map("val" -> 0)
      )"""
    }

    Test1.runAndCheck {
      Block(List(
        Assign("x", Const(900)), // input
        Assign("z", Const(0)),
        While(Less(Const(0), Ref("x")), Block(List(
          Assign("z", Plus(Ref("z"), Ref("x"))),
          Assign("x", Plus(Ref("x"), Const(-1)))
        ))),
        Assign("r", Ref("x"))
      ))
    }{
      """Map(
        "&x"  -> Map("val" -> 0), 
        "&z"  -> Map("val" -> 405450), 
        "&r"  -> Map("val" -> 0)
      )"""
    }


    Test1.runAndCheck {
      Block(List(
        Assign("x", Const(900)), // input
        Assign("y", Const(0)),
        Assign("z", Const(0)),
        Assign("z2", Const(0)),
        While(Less(Const(0), Ref("x")), Block(List(
          Assign("z", Plus(Ref("z"), Ref("x"))),
          Assign("z2", Plus(Ref("z2"), Plus(Times(Ref("x"),Const(3)), Const(5)))),
          If(Less(Ref("y"),Const(17)), 
            Block(List(
              Assign("y", Plus(Ref("y"), Const(1)))
            )),
            Block(Nil)
          ),
          Assign("x", Plus(Ref("x"), Const(-1)))
        ))),
        Assign("r", Ref("x"))
      ))
    }{
      """Map(
        "&x"  -> Map("val" -> 0), 
        "&z"  -> Map("val" -> 405450), 
        "&y"  -> Map("val" -> 17), 
        "&r"  -> Map("val" -> 0), 
        "&z2" -> Map("val" -> 1220850)
      )"""
    }
  }

  // test arrays / computed index access

  //   first, some unit tests
  def testA2 = withOutFileChecked(prefix+"A2") {
    import Test1._
    Test1.runAndCheck {
      Block(List(
        Assign("x", Const(0)), // "input"
        Assign("a", New("A")),
        Put(Ref("a"), Const("field"), Times(Ref("x"),Const(2))),
        Assign("r", Ref("a"))
      ))
    }{
      """Map(
        "&x" -> Map("val" -> 0), 
        "A"  -> Map(top -> Map("field" -> 0)),
        "&a" -> Map("val" -> (A,top)), 
        "&r" -> Map("val" -> (A,top)))"""   
    }    
    Test1.runAndCheck {
      Block(List(
        Assign("x", Const(0)), // "input"
        Assign("a", New("A")),
        Put(Ref("a"), Ref("x"), Times(Ref("x"),Const(2))),
        Assign("r", Ref("a"))
      ))
    }{
      """Map(
        "&x" -> Map("val" -> 0), 
        "A"  -> Map(top -> Map(0 -> 0)), 
        "&a" -> Map("val" -> (A,top)), 
        "&r" -> Map("val" -> (A,top)))"""
    } 
  }

  def testA3 = withOutFileChecked(prefix+"A3") {
    import Test1._
    Test1.runAndCheck {
      Block(List(
        Assign("x", Const(0)),
        Assign("a", New("A")),
        Put(Ref("a"), Const("field"), Const(7)),
        While(Less(Ref("x"),Const(100)), Block(List(
          Put(Ref("a"), Const("field"), Const(7)),
          Assign("x", Plus(Ref("x"), Const(1)))
        ))),
        Assign("r", Ref("a"))
      ))
    }{
      """Map(
        "&x" -> Map("val" -> 100), 
        "A"  -> Map(top -> Map("field" -> 7)), 
        "&a" -> Map("val" -> (A,top)), 
        "&r" -> Map("val" -> (A,top)))"""
    } 
  }

  //   update array at loop index
  def testA4 = withOutFileChecked(prefix+"A4") {
    import Test1._
    Test1.runAndCheck {
      Block(List(
        Assign("x", Const(0)),
        Assign("y", Const(10)),
        Assign("a", New("A")), 
        While(Less(Ref("x"),Const(100)), Block(List(
          Put(Ref("a"), Ref("x"), Times(Ref("x"),Const(2))),
          Assign("x", Plus(Ref("x"), Const(1))),
          Assign("y", Plus(Ref("y"), Const(1)))
        ))),
        Assign("r", Ref("a"))
      ))
    }{
      """
      Map(
        "&a" -> Map("val" -> (A,top)), 
        "A"  -> Map("top" -> collect(100) { x9_A_top_x10 => x9_A_top_x10 * 2 }), 
        "&x" -> Map("val" -> 100), 
        "&y" -> Map("val" -> 110), 
        "&r" -> Map("val" -> (A,top))
      )
      """
    }
  }

  // test store logic (1): build a linked list

  def testB = withOutFileChecked(prefix+"B") {
    import Test1._
    Test1.runAndCheck { // test3
      Block(List(
        Assign("i", Const(0)),
        Assign("z", New("A")),
        Assign("x", Ref("z")),
        While(Less(Ref("i"),Const(100)), Block(List(
          Assign("y", New("B")),
          Put(Ref("y"), Const("head"), Ref("i")),
          Put(Ref("y"), Const("tail"), Ref("x")),
          Assign("x", Ref("y")),
          Assign("i", Plus(Ref("i"), Const(1)))
        )))
      ))
    } {
      """
      Map(
        "&i" -> Map("val" -> 100), 
        "B"  -> Map("top" -> 
          collect(100) { x8_B_top_x9 => 
            Map(
              "head" -> x8_B_top_x9, 
              "tail" -> if (0 < x8_B_top_x9) ("B",("top",x8_B_top_x9 + -1)) 
                        else                 (A,top)
            ) 
          }), 
        "A"  -> Map("top" -> Map()), 
        "&x" -> Map("val" -> (B,(top,99))), 
        "&z" -> Map("val" -> (A,top)), 
        "&y" -> Map("val" -> (B,(top,99)))
      )
      """
    }


  // back to simpler tests (compare to test3)
  // 3 and 4 should be different: alloc within the loop vs before
  
    Test1.runAndCheck { // test4
      Block(List(
        Assign("i", Const(0)),
        Assign("z", New("A")),
        Assign("x", Ref("z")),
        Assign("y", New("B")),
        While(Less(Ref("i"),Const(100)), Block(List(
          Put(Ref("y"), Const("head"), Ref("i")),
          Put(Ref("y"), Const("tail"), Ref("x")),
          Assign("x", Ref("y")),
          Assign("i", Plus(Ref("i"), Const(1)))
        )))
      ))
    }{
      """
      Map(
        "&i" -> Map("val" -> 100), 
        "B"  -> Map("top" -> Map(
                              "head" -> 99, 
                              "tail" -> (B,top))), 
        "A"  -> Map("top" -> Map()), 
        "&x" -> Map("val" -> (B,top)), 
        "&z" -> Map("val" -> (A,top)), 
        "&y" -> Map("val" -> (B,top))
      )
      """
    }

    Test1.runAndCheck { // test5
      Block(List(
        Assign("i", Const(0)),
        Assign("z", New("A")),
        Assign("x", Ref("z")),
        While(Less(Ref("i"),Const(100)), Block(List(
          Put(Ref("x"), Const("head"), Ref("i")),
          Assign("i", Plus(Ref("i"), Const(1)))
        )))
      ))
    } {
      """
      Map(
        "&i" -> Map("val" -> 100), 
        "A"  -> Map("top" -> Map("head" -> 99)), 
        "&z" -> Map("val" -> (A,top)), 
        "&x" -> Map("val" -> (A,top))
      )
      """
    }
  }



/*
  var i = 0
  var z = new A
  var x = z
  while (i < 100) {
    var y = new B
    y.head = i
    y.tail = x
    x = y
    i = i + 1
  }

  Version 1: Optimistic rewriting, but flat stores. We obtain this code:

  val x7 = { x8 => 
  if (0 < x8) 
    x7(x8 + -1) 
      + ("&y" -> Map("val" -> ("B",(1,x8)))) 
      + (("B",(1,x8)) -> 
          x7(x8 + -1)(("B",(1,x8))) 
          + ("head" -> x7(x8 + -1)("&i")("val"))) 
      + (("B",(1,x8)) -> 
          x7(x8 + -1)(("B",(1,x8))) 
          + ("head" -> x7(x8 + -1)("&i")("val")) 
          + ("tail" -> x7(x8 + -1)("&x")("val"))) 
      + ("&x" -> Map("val" -> ("B",(1,x8)))) 
      + ("&i" -> Map("val" -> x7(x8 + -1)("&i")("val") + 1)) 
  else 
    Map("&i" -> Map("val" -> 0), "&z" -> Map("val" -> (A,1)), "&x" -> Map("val" -> (A,1)), "&y" -> Map("val" -> ("B",(1,x8)))) 
      + (("B",(1,x8)) -> Map("head" -> 0)) 
      + (("B",(1,x8)) -> Map("head" -> 0, "tail" -> (A,1))) 
      + ("&x" -> Map("val" -> ("B",(1,x8)))) 
      + ("&i" -> Map("val" -> 1)) 
  }
  x7(fixindex(x8 => x7(x8 + -1)("&i")("val") < 100))

  The store can't be safely split into a Map because (("B",(1,x8))
  is not a unique value. The idea is to make the store hierarchical: 
  first address with "B", then (1,x8). Essentially this models
  allocations inside loops as arrays, although the representation is
  a little different from objects accessed as first class arrays 
  (see testProg1c). In comparison, we remove a level of indirection (or
  should we try to be completely uniform?). Store lookups will need to 
  become hierarchy aware in general, too. If we do a lookup like store(x99), 
  x99 could be either a tuple, or a flat address.

  Version 2: Preliminary support for nested stores. We obtain:

  val x7_&x_val = { x8 => ("B",(1,x8)) }
  val x7_B = { x8 => 
    if (0 < x8) 
      x7_B(x8 + -1) 
        + ((1,x8) -> 
            x7_B(x8 + -1)((1,x8)) 
            + ("head" -> x8 + -1)) 
        + ((1,x8) -> 
            x7_B(x8 + -1)((1,x8)) 
            + ("head" -> x8 + -1) 
            + ("tail" -> x7_&x_val(x8 + -1)))   <--- why not inlined? (call doesn't see rhs -- still in iteration mode)
    else 
      Map(1 ->
        Map() 
        + (x8 -> 
            "undefined"((1,x8))       <--- accessing "undefined": base case? 
            + ("head" -> x8 + -1)) 
        + (x8 -> 
            "undefined"((1,x8)) 
            + ("head" -> x8 + -1) 
            + ("tail" -> (A,1)))) 
  }
  
  Map(
    "&i" -> Map("val" -> 100), 
    "B" -> x7_B(100), 
    "&x" -> Map("val" -> (B,(1,100))), 
    "&z" -> Map("val" -> (A,1)), 
    "&y" -> Map("val" -> (B,(1,100))))


  Version 3: Tweak it! Speculative loop peeling for tuple addresses 
  removes x7_&x_val fundef; rewriting on 'update' ops removes dead stores. 
  
  val x7_B = { x8 => 
    if (0 < x8) 
      x7_B(x8 + -1) 
        + ((1,x8) -> 
            x7_B(x8 + -1)((1,x8)) 
            + ("head" -> x8 + -1) 
            + ("tail" -> ("B",(1,x8 + -1)))) 
    else 
      Map(1 -> 
        Map() 
        + (x8 -> 
            "undefined"((1,x8)) 
            + ("head" -> x8 + -1) 
            + ("tail" -> (A,1)))) 
  }

  Map(
    "&i" -> Map("val" -> 100), 
    "B" -> x7_B(100), 
    "&x" -> Map("val" -> (B,(1,100))), 
    "&z" -> Map("val" -> (A,1)), 
    "&y" -> Map("val" -> (B,(1,100)))
  )

  Version 4: (XXX tentative; rolled back for the time being)
  fix 'undefined' access; explicit 0 case in fundef

  val x7_B = { x8 => 
    if (0 < x8) 
      x7_B(x8 + -1) 
        + ((1,x8) -> 
            x7_B(x8 + -1)((1,x8)) 
            + ("head" -> x8 + -1) 
            + ("tail" -> ("B",(1,x8 + -1)))) 
    else "undefined" }

    Map(
      "&i" -> Map("val" -> 100), 
      "B" -> x7_B(100), 
      "&x" -> Map("val" -> (B,(1,100))), 
      "&z" -> Map("val" -> (A,1)), 
      "&y" -> Map("val" -> (B,(1,100)))
    )

    FIXME: base case at index 0 should have 'tail' pointing to (A,1)
    (question about base index again: value before or after iteration i?)

    TODO: recursive reference to previous value in 
      x7_B(x8 + -1)((1,x8)) + ("head" -> ...) + ("tail" -> ...)
    is not necessary. first, we know that x7_B only ever contains head 
    and tail fields, which would be overridden here. second, we
    know that key (1,x8) is undefined at index x8-1.

*/

  // test store logic (2): build and traverse a linked list

  def testB1 = withOutFileChecked(prefix+"B1") {
  import Test1._
    Test1.runAndCheck { // test3a
      Block(List(
        Assign("i", Const(0)),
        Assign("z", New("A")),
        Assign("x", Ref("z")),
        While(Less(Ref("i"),Const(100)), Block(List(
          Assign("y", New("B")),
          Put(Ref("y"), Const("head"), Ref("i")),
          Put(Ref("y"), Const("tail"), Ref("x")),
          Assign("x", Ref("y")),
          Assign("i", Plus(Ref("i"), Const(1)))
        ))),
        Assign("s", Const(0)),
        Assign("i", Get(Ref("x"), Const("head"))),
        Assign("x", Get(Ref("x"), Const("tail"))),
        Assign("s", Plus(Ref("s"), Ref("i")))
      ))
    }{
      """
        Map(
          "&i" -> Map("val" -> 99), 
          "B"  -> Map("top" -> 
            collect(100) { x8_B_top_x9 => 
              Map(
                "head" -> x8_B_top_x9, 
                "tail" -> if (0 < x8_B_top_x9) ("B",("top",x8_B_top_x9 + -1)) else (A,top)
              ) 
            }), 
          "&s" -> Map("val" -> 99), 
          "A"  -> Map("top" -> Map()), 
          "&x" -> Map("val" -> (B,(top,98))), 
          "&z" -> Map("val" -> (A,top)), 
          "&y" -> Map("val" -> (B,(top,99)))
        )
      """
    }
  }


  def testB2 = withOutFileChecked(prefix+"B2") {
    import Test1._
    Test1.runAndCheck { //test3b
      Block(List(
        Assign("i", Const(0)),
        Assign("z", New("A")),
        Assign("x", Ref("z")),
        While(Less(Ref("i"),Const(100)), Block(List(
          Assign("y", New("B")),
          Put(Ref("y"), Const("head"), Ref("i")),
          Put(Ref("y"), Const("tail"), Ref("x")),
          Assign("x", Ref("y")),
          Assign("i", Plus(Ref("i"), Const(1)))
        ))),
        Assign("s", Const(0)),
        Assign("i2", Ref("i")),
        Assign("x2", Ref("x")),
        While(NotEqual(Ref("x2"),Ref("z")), Block(List(
          Assign("i2", Get(Ref("x2"), Const("head"))),
          Assign("x2", Get(Ref("x2"), Const("tail"))),
          Assign("s", Plus(Ref("s"), Ref("i2")))
        )))
      ))
    } {
      """
        Map(
          "&i"  -> Map("val" -> 100), 
          "&i2" -> Map("val" -> 0), 
          "&x2" -> Map("val" -> (A,top)), 
          "B"   -> Map("top" -> collect(100) { x8_B_top_x9 => 
                      Map(
                        "head" -> x8_B_top_x9, 
                        "tail" -> if (0 < x8_B_top_x9) ("B",("top",x8_B_top_x9 + -1)) else (A,top)
                      ) 
                    }), 
          "&s" -> Map("val" -> 4950), 
          "A"  -> Map("top" -> Map()), 
          "&x" -> Map("val" -> (B,(top,99))), 
          "&z" -> Map("val" -> (A,top)), 
          "&y" -> Map("val" -> (B,(top,99)))
        )
      """


/* old
      """
        val x8_B_top = { x9 => 
          if (0 < x9) 
            x8_B_top(x9 + -1) 
            + (x9 -> Map("head" -> x9 + -1, "tail" -> ("B",("top",x9 + -1)))) 
          else 
            Map() 
            + (x9 -> Map("head" -> x9 + -1, "tail" -> (A,top))) 
        }
        Map(
          "&i" -> Map("val" -> 
            if (0 < fixindex(x92 => if (1 < x92) 1 else x8_B_top(100)(100)("tail") != (A,top))) 
              "undefined" 
            else 
              x8_B_top(100)(100)("head")), 
          "B"  -> Map("top" -> x8_B_top(100)), 
          "&s" -> Map("val" -> 
            if (0 < fixindex(x92 => if (1 < x92) 1 else x8_B_top(100)(100)("tail") != (A,top))) 
              "undefined" 
            else 
              x8_B_top(100)(100)("head")), 
          "A"  -> Map("top" -> Map()), 
          "&x" -> Map("val" -> 
            if (0 < fixindex(x92 => if (1 < x92) 1 else x8_B_top(100)(100)("tail") != (A,top))) 
              "undefined" 
            else 
              x8_B_top(100)(100)("tail")), 
          "&z" -> Map("val" -> (A,top)), 
          "&y" -> Map("val" -> (B,(top,100)))
        )
      """
*/
    }
  }




    // (to try: fac, first as while loop, then as recursive
    // function with defunctionalized continuations in store)



    // modify stuff after a loop

  def testC = withOutFileChecked(prefix+"C") {
    import Test1._
    Test1.runAndCheck { //test6
      Block(List(
        Assign("i", Const(0)),
        Assign("z", New("A")),
        Assign("x", Ref("z")),
        Assign("y", New("B")),
        While(Less(Ref("i"),Const(100)), Block(List(
          Put(Ref("y"), Const("head"), Ref("i")),
          Put(Ref("y"), Const("tail"), Ref("x")),
          Assign("x", Ref("y")),
          Assign("i", Plus(Ref("i"), Const(1)))
        ))),
        Put(Ref("y"), Const("tail"), Ref("z")),
        Put(Ref("y"), Const("head"), Const(7))
      ))
    } {
      """
        Map(
          "&i" -> Map("val" -> 100), 
          "B"  -> Map("top" -> Map("head" -> 7, "tail" -> (A,top))), 
          "A"  -> Map("top" -> Map()), 
          "&x" -> Map("val" -> (B,top)), 
          "&z" -> Map("val" -> (A,top)), 
          "&y" -> Map("val" -> (B,top))
        )
      """
    }
  }

    // strong update for if

  def testD = withOutFileChecked(prefix+"D") {
    import Test1._
    Test1.runAndCheck { //test7
      Block(List(
        Assign("x", New("A")),
        If(Direct(vref("input")),
          Block(List(
            Put(Ref("x"), Const("a"), New("B")),
            Put(Get(Ref("x"), Const("a")), Const("foo"), Const(5))
          )),
          Block(List(
            Put(Ref("x"), Const("a"), New("C")),
            Put(Get(Ref("x"), Const("a")), Const("bar"), Const(5))
          ))
        ),
        Assign("foo", Get(Get(Ref("x"), Const("a")), Const("foo"))),
        Assign("bar", Get(Get(Ref("x"), Const("a")), Const("bar")))
      ))
    }{
      """
        Map(
          "B"  -> Map("top" -> Map("foo" -> 5)), 
          "A"  -> Map("top" -> Map("a" -> (B,top))), 
          "&x" -> Map("val" -> (A,top)), 
          "&bar" -> Map("val" -> "undefined"), 
          "&foo" -> Map("val" -> 5)
        )
      """
    }
    Test1.runAndCheck { //test8
      Block(List(
        Assign("x", New("A")),
        Put(Ref("x"), Const("a"), New("A2")),
        Put(Get(Ref("x"), Const("a")), Const("baz"), Const(3)),
        If(Direct(vref("input")),
          Block(List(
            Put(Ref("x"), Const("a"), New("B")), // strong update, overwrite
            Put(Get(Ref("x"), Const("a")), Const("foo"), Const(5))
          )),
          Block(List(
            Put(Ref("x"), Const("a"), New("C")), // strong update, overwrite
            Put(Get(Ref("x"), Const("a")), Const("bar"), Const(5))
          ))
        ),
        Put(Get(Ref("x"), Const("a")), Const("bar"), Const(7)), // this is not a strong update, because 1.a may be one of two allocs
        Assign("xbar", Get(Get(Ref("x"), Const("a")), Const("bar"))) // should still yield 7!
      ))
    }{
      """
        Map(
          "B"  -> Map("top" -> Map("foo" -> 5, "bar" -> 7)), 
          "A2" -> Map("top" -> Map("baz" -> 3)), 
          "A"  -> Map("top" -> Map("a" -> (B,top))), 
          "&x" -> Map("val" -> (A,top)), 
          "&xbar" -> Map("val" -> 7)
        )
      """
    }
  }

    // update stuff allocated in a loop

  def testE = withOutFileChecked(prefix+"E") {
    import Test1._
    Test1.runAndCheck { //test9
      Block(List(
        Assign("i", Const(0)),
        Assign("x", New("X")),
        Put(Ref("x"), Const("a"), New("A")),
        Put(Get(Ref("x"), Const("a")), Const("baz"), Const(3)),
        While(Less(Ref("i"),Direct(vref("COUNT"))),
          Block(List(
            Put(Ref("x"), Const("a"), New("B")), // strong update, overwrite
            Put(Get(Ref("x"), Const("a")), Const("foo"), Const(5)),
            Assign("i", Plus(Ref("i"),Const(1)))
          ))
        ),
        Put(Get(Ref("x"), Const("a")), Const("bar"), Const(7)), // this is not a strong update, because 1.a may be one of two allocs
        Assign("xbar", Get(Get(Ref("x"), Const("a")), Const("bar"))) // should still yield 7!
      ))
    } {
      """
      Map(
        "&i" -> Map("val" -> "COUNT"), 
        "B"  -> Map("top" -> 
          if (1 < "COUNT") 
            collect("COUNT") { x14_B_top_x15 => Map("foo" -> 5) } 
            + ("COUNT" + -1 -> Map("foo" -> 5, "baz" -> "nil", "bar" -> 7)) 
          else 
            collect("COUNT") { x14_B_top_x15 => Map("foo" -> 5) }
        ), 
        "X"  -> Map("top" -> Map("a" -> 
          if (1 < "COUNT") 
            ("B",("top","COUNT" + -1)) 
          else 
            (A,top)
        )), 
        "A"  -> Map("top" -> Map("baz" -> 3, "foo" -> "nil", "bar" -> if (1 < "COUNT") "nil" else 7)), 
        "&x" -> Map("val" -> (X,top)), 
        "&xbar" -> Map("val" -> 7)
      )
      """
    }
  }


  // factorial: direct
  def testF1 = withOutFileChecked(prefix+"F1") {
    import Test1._
    Test1.runAndCheck {
      Block(List(
        Assign("n", Direct(vref("N"))),
        Assign("i", Const(0)),
        Assign("r", Const(1)),
        While(Less(Ref("i"),Ref("n")),
          Block(List(
            Assign("i", Plus(Ref("i"),Const(1))),
            Assign("r", Times(Ref("r"),Ref("i")))
          ))
        )
      ))
    } {
      """
        val x7_&r_val = { x8 => if (0 < x8) x7_&r_val(x8 + -1) * x8 + x7_&r_val(x8 + -1) else x8 + 1 }
        Map(
          "&n" -> Map("val" -> "N"), 
          "&i" -> Map("val" -> "N"), 
          "&r" -> Map("val" -> x7_&r_val("N" + -1))
        )
      """
    }
  }
}



/*

McCarthy's 91 program:

MC(n)= if (n>100) n-10 else MC(MC(n + 11)) // n ≤ 100

equivalent to:

MC(n)= (n>100) n-10 else 91

non-recursive version:

 int mccarthy(int n)
 {
     int c;
     for (c = 1; c != 0; ) {
         if (n > 100) {
             n = n - 10;
             c--;
         } else {
             n = n + 11;
             c++;
         }
     }
     return n;
 }


*/