package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._


// n*n crossbar, is_double_width is true when src info needed
class crossbar(val is_double_width: Boolean)(implicit val conf : HBMGraphConfiguration) extends Module{
    def high(n : UInt) : UInt = 
        if(is_double_width){
            n(conf.crossbar_data_width * 2 - 1, conf.crossbar_data_width)
        }else{
            n
        }

    val cb_datawidth = 
    if(is_double_width){
        (conf.crossbar_data_width*2).W
    }else{
        conf.crossbar_data_width.W
    }

    val io = IO(new Bundle {
      val in = Vec(conf.numSubGraphs, Flipped(Decoupled(UInt(cb_datawidth))))
      val out = Vec(conf.numSubGraphs, Decoupled(UInt(cb_datawidth)))
    })
    if (conf.numSubGraphs < 32 || (conf.numSubGraphs == 32 && conf.multi_32 == 0)){
        val sub_crossbar = Module(new sub_crossbar(is_double_width, conf.numSubGraphs, 1, 0,conf.sub_crossbar_size))
        io.in <> sub_crossbar.io.in
        io.out <> sub_crossbar.io.out
    }
    else if(conf.numSubGraphs == 32 && conf.multi_32 == 1){
        val crossbar_array_in = new Array[sub_crossbar](8)
        val crossbar_array_second = new Array[sub_crossbar](8)
        val crossbar_array_out = new Array[sub_crossbar](16)
        for(i <- 0 until 8){
            crossbar_array_in(i) = Module(new sub_crossbar(is_double_width, 4, 1, 0, 4))
            crossbar_array_second(i) = Module(new sub_crossbar(is_double_width, 16, 4, i % 4, 4))
        }
        for(i <- 0 until 16){
            crossbar_array_out(i) = Module(new sub_crossbar(is_double_width, 32, 16, i, 2))

        }
        
        for(i <- 0 until 8){
            for(j <- 0 until 4){
                crossbar_array_in(i).io.in(j)       <> io.in(i * 4 + j)
                crossbar_array_in(i).io.out(j)      <> crossbar_array_second((i / 4) * 4 + j).io.in(i % 4)
                crossbar_array_second(i).io.out(j)  <> crossbar_array_out(i % 4 + j * 4).io.in(i / 4)
            }
        }
        for(i <- 0 until 16){
            for(j <- 0 until 2){
                crossbar_array_out(i).io.out(j)     <> io.out(j * 16 + i)
            }
        }
    }
    else if(conf.numSubGraphs == 64){
        val crossbar_array_in = new Array[sub_crossbar](conf.sub_crossbar_number)
        val crossbar_array_second = new Array[sub_crossbar](conf.sub_crossbar_number)
        val crossbar_array_out = new Array[sub_crossbar](conf.sub_crossbar_number)
        for(i <- 0 until conf.sub_crossbar_number){
            crossbar_array_in(i) = Module(new sub_crossbar(is_double_width, conf.sub_crossbar_size, 1, 0, conf.sub_crossbar_size))
            crossbar_array_second(i) = Module(new sub_crossbar(is_double_width, conf.sub_crossbar_number, conf.sub_crossbar_size, i % conf.sub_crossbar_size, conf.sub_crossbar_size))
            crossbar_array_out(i) = Module(new sub_crossbar(is_double_width, conf.numSubGraphs, conf.sub_crossbar_number, i, conf.sub_crossbar_size))
        }
        
        for(i <- 0 until conf.sub_crossbar_number){
            // crossbar_array_in(i).io.modnum := conf.sub_crossbar_size.asUInt(conf.crossbar_data_width.W)
            // crossbar_array_in(i).io.size := 1.U
            // crossbar_array_in(i).io.number := 0.U
            // crossbar_array_out(i).io.modnum := conf.numSubGraphs.asUInt(conf.crossbar_data_width.W)
            // crossbar_array_out(i).io.size := conf.sub_crossbar_size.U
            // crossbar_array_out(i).io.number := i.U
            for(j <- 0 until conf.sub_crossbar_size){
                crossbar_array_in(i).io.in(j)       <> io.in(i * conf.sub_crossbar_size + j)
                crossbar_array_in(i).io.out(j)      <> crossbar_array_second((i / conf.sub_crossbar_size) * conf.sub_crossbar_size + j).io.in(i % conf.sub_crossbar_size)
                crossbar_array_second(i).io.out(j)  <> crossbar_array_out(i % conf.sub_crossbar_size + j * conf.sub_crossbar_size).io.in(i / conf.sub_crossbar_size)
                crossbar_array_out(i).io.out(j)     <> io.out(j * conf.sub_crossbar_number + i)
            }
        }
    }
    else if(conf.numSubGraphs == 128){
        val crossbar_array_in = new Array[sub_crossbar](conf.sub_crossbar_number)
        val crossbar_array_second = new Array[sub_crossbar](conf.sub_crossbar_number)
        val crossbar_array_third = new Array[sub_crossbar](conf.sub_crossbar_number)
        val crossbar_array_out = new Array[sub_crossbar](conf.sub_crossbar_number_2)
        for(i <- 0 until conf.sub_crossbar_number){
            crossbar_array_in(i) = Module(new sub_crossbar(is_double_width, 4, 1, 0, conf.sub_crossbar_size))
            crossbar_array_second(i) = Module(new sub_crossbar(is_double_width, 16, 4, i % 4, conf.sub_crossbar_size))
            crossbar_array_third(i) = Module(new sub_crossbar(is_double_width, 64, 16, i % 16, conf.sub_crossbar_size))
        }
        for( i <- 0 until conf.sub_crossbar_number_2){
            crossbar_array_out(i) = Module(new sub_crossbar(is_double_width, conf.numSubGraphs, 64, i, conf.sub_crossbar_size_2))
        }
        for(i <- 0 until conf.sub_crossbar_number){
            for(j <- 0 until conf.sub_crossbar_size){
                crossbar_array_in(i).io.in(j)       <> io.in(i * conf.sub_crossbar_size + j)
                crossbar_array_in(i).io.out(j)      <> crossbar_array_second((i / conf.sub_crossbar_size) * conf.sub_crossbar_size + j).io.in(i % conf.sub_crossbar_size)
                crossbar_array_second(i).io.out(j)  <> crossbar_array_third(i % 4 + j * 4 + 16 * (i / 16)).io.in((i % 16) / conf.sub_crossbar_size)
                crossbar_array_third(i).io.out(j)   <> crossbar_array_out(i % 16 + j * 16).io.in(i / 16)
            }

        }
        for(i <- 0 until conf.sub_crossbar_number_2){
            for(j <- 0 until conf.sub_crossbar_size_2){
                crossbar_array_out(i).io.out(j)     <> io.out(j * conf.sub_crossbar_number_2 + i)
            }
        }
    }


}
class sub_crossbar(val is_double_width: Boolean, val modnum : Int, val size : Int, val number : Int, val sub_crossbar_size: Int )(implicit val conf : HBMGraphConfiguration) extends Module{

    def high(n : UInt) : UInt = 
        if(is_double_width){
            n(conf.crossbar_data_width * 2 - 1, conf.crossbar_data_width)
        }else{
            n
        }

    val cb_datawidth = 
    if(is_double_width){
        (conf.crossbar_data_width*2).W
    }else{
        conf.crossbar_data_width.W
    }

    val io = IO(new Bundle {
      val in = Vec(sub_crossbar_size, Flipped(Decoupled(UInt(cb_datawidth))))
      val out = Vec(sub_crossbar_size, Decoupled(UInt(cb_datawidth)))
    //   val modnum = Input(UInt(conf.crossbar_data_width.W))
    //   val size = Input(UInt(conf.crossbar_data_width.W))
    //   val number = Input(UInt(conf.crossbar_data_width.W))
    })
    // val modnum = sub_crossbar_size.asUInt(conf.crossbar_data_width.W)
    // val modnum = io.modnum
    // val size = io.size
    // val number = io.number
    
    // Generate array
    val in_queue_vec = Array.ofDim[Queue[UInt]](sub_crossbar_size)
    val queue_vec = Array.ofDim[Queue[UInt]](sub_crossbar_size, sub_crossbar_size)
    val RRarbiter_vec = Array.ofDim[RRArbiter[UInt]](sub_crossbar_size)
    // val fifo_ready_vec = Array.ofDim[Bool](conf.numSubGraphs, conf.numSubGraphs)
    

    for(in_idx <- 0 until sub_crossbar_size){
        in_queue_vec(in_idx) = Module(new Queue(UInt(cb_datawidth), conf.crossbar_in_fifo_len))
        in_queue_vec(in_idx).io.enq <> io.in(in_idx)
        in_queue_vec(in_idx).io.deq <> DontCare
        RRarbiter_vec(in_idx) = Module(new RRArbiter(UInt(cb_datawidth), sub_crossbar_size))
        for(in_idy <- 0 until sub_crossbar_size){
            queue_vec(in_idx)(in_idy) = Module(new Queue(UInt(cb_datawidth), conf.crossbar_main_fifo_len))
            queue_vec(in_idx)(in_idy).io.enq <> DontCare
            // fifo_ready_vec(in_idx)(in_idy) = queue_vec(in_idx)(in_idy).io.enq.ready
        }

    }
    
    // pre queue logic
    for(in_idx <- 0 until sub_crossbar_size){
        when(in_queue_vec(in_idx).io.deq.valid){
            for(in_idy <- 0 until sub_crossbar_size){
                when(high(in_queue_vec(in_idx).io.deq.bits) % modnum.U === (in_idy.asUInt(conf.crossbar_data_width.W) * size.U + number.U)){  //32bits compare
                    in_queue_vec(in_idx).io.deq <> queue_vec(in_idx)(in_idy).io.enq
                } .otherwise {
                    queue_vec(in_idx)(in_idy).io.enq.valid := false.B
                }      
            }
        } .otherwise {
            for(in_idy <- 0 until sub_crossbar_size){
                queue_vec(in_idx)(in_idy).io.enq.valid := false.B
            }
            in_queue_vec(in_idx).io.deq.ready := false.B // fifo_ready_vec(in_idx).reduce(_ && _)
        }
    }
    
    //post queue logic
    for(out_idy <- 0 until sub_crossbar_size){
        for(out_idx <- 0 until sub_crossbar_size){
            queue_vec(out_idx)(out_idy).io.deq <> RRarbiter_vec(out_idy).io.in(out_idx)
        }
    }
    
    // output
    for(out_id <- 0 until sub_crossbar_size){
        RRarbiter_vec(out_id).io.out <> io.out(out_id)
    }
    
}

// object Custom_function_cb{
//     implicit val conf = HBMGraphConfiguration()
//     def high(n : UInt) : UInt = 
//         n(conf.crossbar_data_width * 2 - 1, conf.crossbar_data_width)

//     def low(n : UInt) : UInt = 
//         n(conf.crossbar_data_width - 1, 0)
// }