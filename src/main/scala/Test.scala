package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._
import chisel3.iotesters.PeekPokeTester


object Testp1 extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new P1(0)(configuration))
  
}


object Testp2 extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new P2(0)(configuration))
  
}


object TestMem_write extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new Memory_write(0)(configuration))
  
}

object TestMemory extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new Memory(0)(configuration))
  
}

object Testread_visited_map extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new p2_read_visited_map_or_frontier(0)(configuration))
  
}

object Testwrite_frontier_and_level extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new write_frontier_and_level(0)(configuration))
  
}

object Testfrontier extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new Frontier(0)(configuration))
  
}

object Testmaster extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new master()(configuration))
  
}

object Testreader extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new HBM_reader(0)(configuration))
  
}

object Testone extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new find_one_32()(configuration))
    
}

object Testadder extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new adder(32, 32)(configuration))
    
}

object Testcxb_24 extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new crossbar(false)(configuration))

}

// object Testslr0 extends App{
//     implicit val configuration = HBMGraphConfiguration()
//     chisel3.Driver.execute(Array[String](), () => new SLR0()(configuration))
    
// }

// object Testslr1 extends App{
//     implicit val configuration = HBMGraphConfiguration()
//     chisel3.Driver.execute(Array[String](), () => new SLR1()(configuration))
    
// }

// object Testslr2 extends App{
//     implicit val configuration = HBMGraphConfiguration()
//     chisel3.Driver.execute(Array[String](), () => new SLR2()(configuration))
    
// }

// object Testreadertop extends App{
//     implicit val configuration = HBMGraphConfiguration()
//     chisel3.Driver.execute(Array[String](), () => new top_for_hbm_reader()(configuration))
  
// }


// class Test(c:Top) extends PeekPokeTester(c){
//   for(t <- 0 until 50){
//     println("-----------------------------------------------------------------------------------------------")
//     step(1)
//   }
// }


// object bfsTester {
//   def main(args: Array[String]): Unit = {
//     println("Testing bfs")
//     implicit val configuration = HBMGraphConfiguration()
//     iotesters.Driver.execute(Array[String](), () => new Top()) {
//       c => new Test(c)
//     }
//   }
// }

/*object Testbram extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new bram_top)
}*/

  