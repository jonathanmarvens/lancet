package lancet
package analysis

class TestAnalysis4 extends FileDiffSuite {

  val prefix = "test-out/test-analysis-4"

/* 
  make loop ranges explicit, reason about
  an infinite number of memory addresses
  (allocation site indexed by loop iteration)

  TODO -- UNFINISHED
*/

/*



*/

  object Test1 {

    type Var = String
    type Addr = String
    type Alloc = String
    type Field = String


    //val store0: Store = Store(Map.empty, Map.empty, Set.empty, Set.empty)
    //var store: Store = _

    //val itvec0 = List(1)
    //var loopDepth = loopDepth0

    abstract class Exp
    case class Const(x: Int) extends Exp
    case class Direct(x: Val) extends Exp
    case class Ref(x: Var) extends Exp
    case class Assign(x: Var, y: Exp) extends Exp
    case class Plus(x: Exp, y: Exp) extends Exp
    case class Less(x: Exp, y: Exp) extends Exp
    case class New(x: Alloc) extends Exp
    case class Get(x: Exp, f: Field) extends Exp
    case class Put(x: Exp, f: Field, y: Exp) extends Exp
    case class If(c: Exp, a: Exp, b: Exp) extends Exp
    case class While(c: Exp, b: Exp) extends Exp
    case class Block(xs: List[Exp]) extends Exp {
      override def toString = "{\n  " + xs.map(_.toString).mkString("\n").replace("\n","\n  ") + "\n}"
    }

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

    case class DUpdate(x: GVal, f: GVal, y: GVal) extends Def
    case class DSelect(x: GVal, f: GVal) extends Def
    case class DPlus(x: GVal, y: GVal) extends Def
    case class DLess(x: GVal, y: GVal) extends Def
    case class DIf(c: GVal, x: GVal, y: GVal) extends Def
    case class DFixIndex(c: GVal) extends Def
    case class DCall(f: GVal, x: GVal) extends Def
    case class DOther(s: String) extends Def

    def mirrorDef(d: Def, dst: DIntf { type From >: GVal }): dst.To = d match {
      case DUpdate(x: GVal, f: GVal, y: GVal) => dst.update(x,f,y)
      case DSelect(x: GVal, f: GVal)          => dst.select(x,f)
      case DPlus(x: GVal, y: GVal)            => dst.plus(x,y)
      case DLess(x: GVal, y: GVal)            => dst.less(x,y)
      case DIf(c: GVal, x: GVal, y: GVal)     => dst.iff(c,x,y)
      case DFixIndex(c: GVal)                 => dst.fixindex(c)
      case DCall(f: GVal, x: GVal)            => dst.call(f,x)
      case DOther(s: String)                  => dst.other(s)
    }

    trait DIntf {
      type From
      type To
      def update(x: From, f: From, y: From): To
      def select(x: From, f: From): To
      def plus(x: From, y: From): To
      def less(x: From, y: From): To
      def iff(c: From, x: From, y: From): To
      def fixindex(c: From): To
      def call(f: From, x: From): To
      def other(s: String): To
    }

    object DString extends DIntf {
      type From = Any
      type To = String
      def update(x: From, f: From, y: From) = s"$x + ($f -> $y)"
      def select(x: From, f: From)          = s"$x($f)"
      def plus(x: From, y: From)            = s"$x + $y"
      def less(x: From, y: From)            = s"$x < $y"
      def iff(c: From, x: From, y: From)    = s"if ($c) $x else $y"
      def fixindex(c: From)                 = s"fixindex($c)"
      def call(f: From, x: From)            = s"$f($x)"
      def other(s: String)                  = s
    }

    object DDef extends DIntf {
      type From = GVal
      type To = Def
      def update(x: From, f: From, y: From) = DUpdate(x,f,y)
      def select(x: From, f: From)          = DSelect(x,f)
      def plus(x: From, y: From)            = DPlus(x,y)
      def less(x: From, y: From)            = DLess(x,y)
      def iff(c: From, x: From, y: From)    = DIf(c,x,y)
      def fixindex(c: From)                 = DFixIndex(c)
      def call(f: From, x: From)            = DCall(f,x)
      def other(s: String)                  = DOther(s)
    }

    trait DXForm extends DIntf {
      type From
      type To
      val next: DIntf
      def pre(x: From): next.From
      def post(x: next.To): To
      def update(x: From, f: From, y: From) = post(next.update(pre(x),pre(f),pre(y)))
      def select(x: From, f: From)          = post(next.select(pre(x),pre(f)))
      def plus(x: From, y: From)            = post(next.plus(pre(x),pre(y)))
      def less(x: From, y: From)            = post(next.less(pre(x),pre(y)))
      def iff(c: From, x: From, y: From)    = post(next.iff(pre(c),pre(x),pre(y)))
      def fixindex(c: From)                 = post(next.fixindex(pre(c)))
      def call(f: From, x: From)            = post(next.call(pre(f),pre(x)))
      def other(s: String)                  = post(next.other(s))
    }

    object IRS extends DXForm {
      type From = Val
      type To = Val
      val next = DString
      def const(x: Any) = s"$x"
      def pre(x: Val) = x
      def post(x: String): Val = reflect(x)
    }

    object IRD extends DXForm {
      type From = GVal
      type To = GVal
      val next = DDef
      def pre(x: GVal) = x
      def post(x: Def): GVal = ???//reflect(x)
    }


    type Val = String

    def vref(x: String): Val = x

    val varCount0 = 0
    var varCount = varCount0

    def reflect(s: String): String = { println(s"val x$varCount = $s"); varCount += 1; s"x${varCount-1}" }
    def reify(x: => String): String = captureOutputResult(x)._1

    val IR = IRS

    val store0 = IR.const(Map())
    val itvec0 = IR.const(List(1))

    var store = store0
    var itvec = itvec0

    def eval(e: Exp): String = e match {
      case Const(x)    => IR.const(x)
      case Direct(x)   => IR.const(x)
      case Ref(x)      => IR.select(IR.select(store,"&"+x), "val")
      case Assign(x,y) => 
        store = IR.update(store, "&"+x, IR.update("Map()", "val", eval(y)))
        "()"
      case Plus(x,y)   => IR.plus(eval(x),eval(y))
      case Less(x,y)   => IR.less(eval(x),eval(y))
      case New(x) => 
        val a = s"${x}_$itvec"
        store = IR.update(store, a, "Map()")
        a
      case Get(x, f) => 
        IR.select(IR.select(store, eval(x)), "f")
      case Put(x, f, y) => 
        val a = eval(x)
        val old = IR.select(store, a)
        store = IR.update(store, a, IR.update(old, "f", eval(y)))
        "()"
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

        /*{println(s"def loop(store0, n0) = {")
        val savest = store
        store = "store0"
        val saveit = itvec
        itvec = itvec+"::n0"
        val c0x = eval(c)
        eval(b)
        println(s"if ($c0x) loop($store,n0+1) else (store0,n0)")
        println("}")
        store = savest
        itvec = saveit
        val n = reflect(s"loop($store,0)._2 // count")}*/


        {println(s"def loop(n0: Int): (Int,Store) = {")
        val savevc = varCount
        val savest = store
        val saveit = itvec
        val prev = reflect(s"if (n0 <= 0) (0,$savest) else loop(n0-1)")
        store = s"$prev._2"
        itvec = IR.plus(itvec,"n0")
        val c0x = eval(c)
        val afterC = store
        eval(b)
        println(s"if ($c0x) ($prev._1+1,$store) else ($prev._1,$afterC)")
        println("}")
        store = savest
        itvec = saveit
        val n = reflect(s"fixindex(loop)")
        store = reflect(s"loop($n)._2")}

        // TODO: fixpoint

        "()"

      case Block(Nil) => "()"
      case Block(xs) => xs map eval reduceLeft ((a,b) => b)
    }




    def run(testProg: Exp) = {
      println("prog: " + testProg)
      store = store0
      // /loopDepth = loopDepth0
      val res = eval(testProg)
      println("res: " + res)
      println(store)
      //store.printBounds
      println("----")
    }

    // test some integer computations

    val testProg1 = Block(List(
      Assign("i", Const(0)),
      Assign("y", Const(0)),
      Assign("x", Const(8)),
      While(Less(Ref("i"),Const(100)), Block(List(
        Assign("x", Const(7)),
        Assign("x", Plus(Ref("x"), Const(1))),
        Assign("y", Plus(Ref("y"), Const(1))), // TOOD: how to relate to loop var??
        Assign("i", Plus(Ref("i"), Const(1)))
      )))
    ))

    val testProg2 = Block(List(
      Assign("x", Const(900)), // input
      Assign("y", Const(0)),
      Assign("z", Const(0)),
      While(Less(Const(0), Ref("x")), Block(List(
        Assign("z", Plus(Ref("z"), Ref("x"))),
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

    // test store logic

    val testProg3 = Block(List(
      Assign("i", Const(0)),
      Assign("z", New("A")),
      Assign("x", Ref("z")),
      While(Less(Ref("i"),Const(100)), Block(List(
        Assign("y", New("B")),
        Put(Ref("y"), "head", Ref("i")),
        Put(Ref("y"), "tail", Ref("x")),
        Assign("x", Ref("y")),
        Assign("i", Plus(Ref("i"), Const(1)))
      )))
    ))

    val testProg4 = Block(List(
      Assign("i", Const(0)),
      Assign("z", New("A")),
      Assign("x", Ref("z")),
      Assign("y", New("B")),
      While(Less(Ref("i"),Const(100)), Block(List(
        Put(Ref("y"), "head", Ref("i")),
        Put(Ref("y"), "tail", Ref("x")),
        Assign("x", Ref("y")),
        Assign("i", Plus(Ref("i"), Const(1)))
      )))
    ))

    val testProg5 = Block(List(
      Assign("i", Const(0)),
      Assign("z", New("A")),
      Assign("x", Ref("z")),
      While(Less(Ref("i"),Const(100)), Block(List(
        Put(Ref("x"), "head", Ref("i")),
        Assign("i", Plus(Ref("i"), Const(1)))
      )))
    ))

    // modify stuff after a loop

    val testProg6 = Block(List(
      Assign("i", Const(0)),
      Assign("z", New("A")),
      Assign("x", Ref("z")),
      Assign("y", New("B")),
      While(Less(Ref("i"),Const(100)), Block(List(
        Put(Ref("y"), "head", Ref("i")),
        Put(Ref("y"), "tail", Ref("x")),
        Assign("x", Ref("y")),
        Assign("i", Plus(Ref("i"), Const(1)))
      ))),
      Put(Ref("y"), "tail", Ref("z")),
      Put(Ref("y"), "head", Const(7))
    ))

    // strong update for if

    val testProg7 = Block(List(
      Assign("x", New("A")),
      If(Direct(vref("input")),
        Block(List(
          Put(Ref("x"), "a", New("B")),
          Put(Get(Ref("x"), "a"), "foo", Const(5))
        )),
        Block(List(
          Put(Ref("x"), "a", New("C")),
          Put(Get(Ref("x"), "a"), "bar", Const(5))
        ))
      ),
      Assign("foo", Get(Get(Ref("x"), "a"), "foo")),
      Assign("bar", Get(Get(Ref("x"), "a"), "bar"))
    ))

    val testProg8 = Block(List(
      Assign("x", New("A")),
      Put(Ref("x"), "a", New("A2")),
      Put(Get(Ref("x"), "a"), "baz", Const(3)),
      If(Direct(vref("input")),
        Block(List(
          Put(Ref("x"), "a", New("B")), // strong update, overwrite
          Put(Get(Ref("x"), "a"), "foo", Const(5))
        )),
        Block(List(
          Put(Ref("x"), "a", New("C")), // strong update, overwrite
          Put(Get(Ref("x"), "a"), "bar", Const(5))
        ))
      ),
      Put(Get(Ref("x"), "a"), "bar", Const(7)), // this is not a strong update, because 1.a may be one of two allocs
      Assign("xbar", Get(Get(Ref("x"), "a"), "bar")) // should still yield 7!
    ))

    // update stuff allocated in a loop

    val testProg9 = Block(List(
      Assign("x", New("X")),
      Put(Ref("x"), "a", New("A")),
      Put(Get(Ref("x"), "a"), "baz", Const(3)),
      While(Direct(vref("input")),
        Block(List(
          Put(Ref("x"), "a", New("B")), // strong update, overwrite
          Put(Get(Ref("x"), "a"), "foo", Const(5))
        ))
      ),
      Put(Get(Ref("x"), "a"), "bar", Const(7)), // this is not a strong update, because 1.a may be one of two allocs
      Assign("xbar", Get(Get(Ref("x"), "a"), "bar")) // should still yield 7!
    ))

  }

  def testA = withOutFileChecked(prefix+"A") {
    Test1.run(Test1.testProg1)
    Test1.run(Test1.testProg2)
  }

  def testB = withOutFileChecked(prefix+"B") {
    Test1.run(Test1.testProg3)
    Test1.run(Test1.testProg4) // 3 and 4 should be different: alloc within the loop vs before
    Test1.run(Test1.testProg5)
  }
  def testC = withOutFileChecked(prefix+"C") {
    Test1.run(Test1.testProg6)
  }
  def testD = withOutFileChecked(prefix+"D") {
    Test1.run(Test1.testProg7)
    Test1.run(Test1.testProg8)
  }
  def testE = withOutFileChecked(prefix+"E") {
    Test1.run(Test1.testProg9)
  }



}