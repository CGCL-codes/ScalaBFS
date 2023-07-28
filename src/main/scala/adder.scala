package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._


class adder (num: Int, data_width : Int)(implicit val conf : HBMGraphConfiguration) extends Module{
    val io = IO(new Bundle{
        val in = Input(Vec(num, UInt(data_width.W)))
        val out = Output(UInt(data_width.W))
    })

    if(num >= 2){
        val adder_half_0 = Module(new adder(num / 2, data_width)) 
        val adder_half_1 = Module(new adder(num / 2, data_width)) 
        for(i <- 0 until num / 2){
            adder_half_0.io.in(i) := io.in(i)
            adder_half_1.io.in(i) := io.in(i + num / 2)
        }
        val out = RegInit(0.U(data_width.W))
        out := adder_half_0.io.out + adder_half_1.io.out

        io.out := out
    }
    else{
        io.out := io.in(0)
    }
}

// class adder_wire (num: Int, data_width : Int)(implicit val conf : HBMGraphConfiguration) extends Module{
//     val io = IO(new Bundle{
//         val in = Input(Vec(num, UInt(data_width.W)))
//         val out = Output(UInt(data_width.W))
//     })

//     if(num > 2){
//         val adder_half_0 = Module(new adder_wire(num / 2, data_width)) 
//         val adder_half_1 = Module(new adder_wire(num / 2, data_width)) 
//         for(i <- 0 until num / 2){
//             adder_half_0.io.in(i) := io.in(i)
//             adder_half_1.io.in(i) := io.in(i + num / 2)
//         }
//         io.out := adder_half_0.io.out + adder_half_1.io.out
//     }
//     else{
//         io.out := io.in(0) + io.in(1)
//     }
// }

// class adder_wire_8 (data_width : Int)(implicit val conf : HBMGraphConfiguration) extends Module{
//     val io = IO(new Bundle{
//         val in = Input(Vec(8, UInt(data_width.W)))
//         val out = Output(UInt(data_width.W))
//     })
    
//     io.out := ((io.in(0) + io.in(1)) + (io.in(2) + io.in(3))) + ((io.in(4) + io.in(5)) + (io.in(6) + io.in(7)))
// }

class and (num: Int)(implicit val conf : HBMGraphConfiguration) extends Module{
    val io = IO(new Bundle{
        val in = Input(Vec(num, Bool()))
        val out = Output(Bool())
    })

    if(num >= 2){
        val and_half_0 = Module(new and(num / 2)) 
        val and_half_1 = Module(new and(num / 2)) 
        for(i <- 0 until num / 2){
            and_half_0.io.in(i) := io.in(i)
            and_half_1.io.in(i) := io.in(i + num / 2)
        }
        val out = RegInit(false.B)
        out := and_half_0.io.out & and_half_1.io.out

        io.out := out
    }
    else{
        io.out := io.in(0)
    }
}