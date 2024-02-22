package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._


// n*n crossbar, is_double_width is true when src info needed
// For Data-Relay Crossbar, is_double_width = true
// For Inter-PE Crossbar, is_double_width = false

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
    if(conf.numSubGraphs == 8){
        val crossbar_array_in = new Array[sub_crossbar](2)
        val crossbar_array_out = new Array[sub_crossbar](4)
        for(i <- 0 until 2){
            crossbar_array_in(i) = Module(new sub_crossbar(is_double_width, 4, 1, 0, 4))
        }
        for(i <- 0 until 4){
            crossbar_array_out(i) = Module(new sub_crossbar(is_double_width, 8, 4, i, 2))

        }
        for(i <- 0 until 2){
            for(j <- 0 until 4){
                crossbar_array_in(i).io.in(j)       <> io.in(i * 4 + j)
                crossbar_array_in(i).io.out(j)      <> crossbar_array_out(j).io.in(i)
            }
        }
        for(i <- 0 until 4){
            for(j <- 0 until 2){
                crossbar_array_out(i).io.out(j)     <> io.out(j * 4 + i)
            }
        }
    }
    else if (conf.numSubGraphs < 16 || (conf.numSubGraphs == 16 && conf.multi_16 == 0) || (conf.numSubGraphs == 32 && conf.multi_32 == 0) || (conf.numSubGraphs == 64 && conf.multi_64 == 0) || (conf.numSubGraphs == 128 && conf.multi_128 == 0)){
        val sub_crossbar = Module(new sub_crossbar(is_double_width, conf.numSubGraphs, 1, 0,conf.sub_crossbar_size))
         io.in <> sub_crossbar.io.in
        io.out <> sub_crossbar.io.out
    }
    else if(conf.numSubGraphs == 16 && conf.multi_16 == 1){
        val crossbar_array_in = new Array[sub_crossbar](4)
        val crossbar_array_out = new Array[sub_crossbar](4)
        for(i <- 0 until 4){
            crossbar_array_in(i) = Module(new sub_crossbar(is_double_width, 4, 1, 0, 4))
            crossbar_array_out(i) = Module(new sub_crossbar(is_double_width, 16, 4, i % 4, 4))
        }
        for(i <- 0 until 4){
            for(j <- 0 until 4){
                crossbar_array_in(i).io.in(j)       <> io.in(i * 4 + j)
                crossbar_array_in(i).io.out(j)      <> crossbar_array_out((i >> 2) * 4 + j).io.in(i % 4)
                crossbar_array_out(i).io.out(j)     <> io.out(j * 4 + i)
            }
        }
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
                crossbar_array_in(i).io.out(j)      <> crossbar_array_second((i >> 2) * 4 + j).io.in(i % 4)
                crossbar_array_second(i).io.out(j)  <> crossbar_array_out(i % 4 + j * 4).io.in(i >> 2)
            }
        }
        for(i <- 0 until 16){
            for(j <- 0 until 2){
                crossbar_array_out(i).io.out(j)     <> io.out(j * 16 + i)
            }
        }
    }
    else if(conf.numSubGraphs == 64 && conf.multi_64 == 1){
        val crossbar_array_in = new Array[sub_crossbar](conf.sub_crossbar_number)
        val crossbar_array_second = new Array[sub_crossbar](conf.sub_crossbar_number)
        val crossbar_array_out = new Array[sub_crossbar](conf.sub_crossbar_number)
        for(i <- 0 until conf.sub_crossbar_number){
            crossbar_array_in(i) = Module(new sub_crossbar(is_double_width, conf.sub_crossbar_size, 1, 0, conf.sub_crossbar_size))
            crossbar_array_second(i) = Module(new sub_crossbar(is_double_width, conf.sub_crossbar_number, conf.sub_crossbar_size, i % conf.sub_crossbar_size, conf.sub_crossbar_size))
            crossbar_array_out(i) = Module(new sub_crossbar(is_double_width, conf.numSubGraphs, conf.sub_crossbar_number, i, conf.sub_crossbar_size))
        }
        
        for(i <- 0 until conf.sub_crossbar_number){
            for(j <- 0 until conf.sub_crossbar_size){
                crossbar_array_in(i).io.in(j)       <> io.in(i * conf.sub_crossbar_size + j)
                crossbar_array_in(i).io.out(j)      <> crossbar_array_second((i / conf.sub_crossbar_size) * conf.sub_crossbar_size + j).io.in(i % conf.sub_crossbar_size)
                crossbar_array_second(i).io.out(j)  <> crossbar_array_out(i % conf.sub_crossbar_size + j * conf.sub_crossbar_size).io.in(i / conf.sub_crossbar_size)
                crossbar_array_out(i).io.out(j)     <> io.out(j * conf.sub_crossbar_number + i)
            }
        }
    }
    else if(conf.numSubGraphs == 64 && conf.multi_64 == 2){
        val crossbar_array_in = new Array[sub_crossbar](conf.sub_crossbar_number)
        val crossbar_array_second = new Array[sub_crossbar](conf.sub_crossbar_number)
        val crossbar_array_third = new Array[sub_crossbar](conf.sub_crossbar_number)
        val crossbar_array_fourth = new Array[sub_crossbar](conf.sub_crossbar_number)
        val crossbar_array_fifth = new Array[sub_crossbar](conf.sub_crossbar_number)
        val crossbar_array_out = new Array[sub_crossbar](conf.sub_crossbar_number)
        for(i <- 0 until conf.sub_crossbar_number){
            crossbar_array_in(i)        = Module(new sub_crossbar(is_double_width, 2, 1, 0, conf.sub_crossbar_size))
            crossbar_array_second(i)    = Module(new sub_crossbar(is_double_width, 4, 2, i % 2, conf.sub_crossbar_size))
            crossbar_array_third(i)     = Module(new sub_crossbar(is_double_width, 8, 4, i % 4, conf.sub_crossbar_size))
            crossbar_array_fourth(i)    = Module(new sub_crossbar(is_double_width, 16, 8, i % 8, conf.sub_crossbar_size))
            crossbar_array_fifth(i)     = Module(new sub_crossbar(is_double_width, 32, 16, i % 16, conf.sub_crossbar_size))
            crossbar_array_out(i)       = Module(new sub_crossbar(is_double_width, 64, 32, i, conf.sub_crossbar_size))
        }
        
        for(i <- 0 until conf.sub_crossbar_number){
            for(j <- 0 until conf.sub_crossbar_size){
                crossbar_array_in(i).io.in(j)           <> io.in(i * conf.sub_crossbar_size + j)
                crossbar_array_in(i).io.out(j)          <> crossbar_array_second(   (i >> 1) * 2  +          j     ).io.in(i % 2)
                crossbar_array_second(i).io.out(j)      <> crossbar_array_third(    (i >> 2) * 4  + i % 2 +  j * 2 ).io.in((i >> 1) % 2)
                crossbar_array_third(i).io.out(j)       <> crossbar_array_fourth(   (i >> 3) * 8  + i % 4 +  j * 4 ).io.in((i >> 2) % 2)
                crossbar_array_fourth(i).io.out(j)      <> crossbar_array_fifth(    (i >> 4) * 16 + i % 8 +  j * 8 ).io.in((i >> 3) % 2)
                crossbar_array_fifth(i).io.out(j)       <> crossbar_array_out(                     i % 16 + j * 16).io.in(i >> 4)
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
                crossbar_array_in(i).io.out(j)      <> crossbar_array_second((i >> 2) * conf.sub_crossbar_size + j).io.in(i % conf.sub_crossbar_size)
                crossbar_array_second(i).io.out(j)  <> crossbar_array_third(i % 4 + j * 4 + 16 * (i >> 4)).io.in((i % 16) >> 2)
                crossbar_array_third(i).io.out(j)   <> crossbar_array_out(i % 16 + j * 16).io.in(i >> 4)
            }

        }
        for(i <- 0 until conf.sub_crossbar_number_2){
            for(j <- 0 until conf.sub_crossbar_size_2){
                crossbar_array_out(i).io.out(j)     <> io.out(j * conf.sub_crossbar_number_2 + i)
            }
        }
    }
    else if(conf.numSubGraphs == 160){
        val crossbar_array_in = new Array[sub_crossbar](40)
        val crossbar_array_second = new Array[sub_crossbar](40)
        val crossbar_array_third = new Array[sub_crossbar](80)
        val crossbar_array_out = new Array[sub_crossbar](32)
        for(i <- 0 until 40){
            crossbar_array_in(i) = Module(new sub_crossbar(is_double_width, 4, 1, 0, 4))
            crossbar_array_second(i) = Module(new sub_crossbar(is_double_width, 16, 4, i % 4, 4))
        }
        for(i <- 0 until 80){
            crossbar_array_third(i) = Module(new sub_crossbar(is_double_width, 32, 16, i % 16, 2))
        }
        for( i <- 0 until 32){
            crossbar_array_out(i) = Module(new sub_crossbar(is_double_width, 160, 32, i, 5))
        }
        for(i <- 0 until 40){
            for(j <- 0 until 4){
                crossbar_array_in(i).io.in(j)       <> io.in(i * 4 + j)
                crossbar_array_in(i).io.out(j)      <> crossbar_array_second((i / 4) * 4 + j).io.in(i % 4)
                crossbar_array_second(i).io.out(j)  <> crossbar_array_third(i % 4 + j * 4 + 16 * (i >> 3)).io.in((i / 4) % 2)
            }

        }
        for(i <- 0 until 80){
            for(j <- 0 until 2){
                crossbar_array_third(i).io.out(j)   <> crossbar_array_out(i % 16 + j * 16).io.in(i >> 4)
            }
        }
        for(i <- 0 until 32){
            for(j <- 0 until 5){
                crossbar_array_out(i).io.out(j)     <> io.out(j * 32 + i)
            }
        }
    }
    else if(conf.numSubGraphs == 192){
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
                crossbar_array_second(i).io.out(j)  <> crossbar_array_third(i % 4 + j * 4 + 16 * (i >> 4)).io.in((i % 16) / conf.sub_crossbar_size)
                crossbar_array_third(i).io.out(j)   <> crossbar_array_out(i % 16 + j * 16).io.in(i >> 4)
            }

        }
        for(i <- 0 until conf.sub_crossbar_number_2){
            for(j <- 0 until conf.sub_crossbar_size_2){
                crossbar_array_out(i).io.out(j)     <> io.out(j * conf.sub_crossbar_number_2 + i)
            }
        }
    }
    else if(conf.numSubGraphs == 256){
        val crossbar_array_in = new Array[sub_crossbar](64)
        val crossbar_array_second = new Array[sub_crossbar](64)
        val crossbar_array_third = new Array[sub_crossbar](64)
        val crossbar_array_out = new Array[sub_crossbar](64)
        for(i <- 0 until 64){
            crossbar_array_in(i) = Module(new sub_crossbar(is_double_width, 4, 1, 0, 4))
            crossbar_array_second(i) = Module(new sub_crossbar(is_double_width, 16, 4, i % 4, 4))
            crossbar_array_third(i) = Module(new sub_crossbar(is_double_width, 64, 16, i % 16, 4))
            crossbar_array_out(i) = Module(new sub_crossbar(is_double_width, 128, 64, i, 4))
        }
        for(i <- 0 until 64){
            for(j <- 0 until 4){
                crossbar_array_in(i).io.in(j)       <> io.in(i * 4 + j)
                crossbar_array_in(i).io.out(j)      <> crossbar_array_second((i >> 2) * 4 + j).io.in(i % 4)
                crossbar_array_second(i).io.out(j)  <> crossbar_array_third( (i >> 4) *16 + i % 4 + j * 4).io.in((i % 16) >> 2)
                crossbar_array_third(i).io.out(j)   <> crossbar_array_out(   i % 16 + j * 16).io.in(i >> 4)
                crossbar_array_out(i).io.out(j)     <> io.out(j * 64 + i)
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
    })

    
    // Generate array
    val RRarbiter_vec = Array.ofDim[RRArbiter[UInt]](sub_crossbar_size)
    val in_queue_vec = new Array [fifo_cxb](sub_crossbar_size)
    val queue_vec = Array.ofDim[fifo_cxb](sub_crossbar_size, sub_crossbar_size)
    
    
    val dw_cxb = if(is_double_width){
        (conf.crossbar_data_width*2)
    }else{
        conf.crossbar_data_width
    }
    for(in_idx <- 0 until sub_crossbar_size){
        in_queue_vec(in_idx) = Module(new fifo_cxb(dw_cxb, is_double_width))
        RRarbiter_vec(in_idx) = Module(new RRArbiter(UInt(cb_datawidth), sub_crossbar_size))
        for(in_idy <- 0 until sub_crossbar_size){
            queue_vec(in_idx)(in_idy) = Module(new fifo_cxb(dw_cxb, is_double_width))
        }

    }
    for(in_idx <- 0 until sub_crossbar_size){
        in_queue_vec(in_idx).io.rst         <> reset
        in_queue_vec(in_idx).io.clk         <> clock
        in_queue_vec(in_idx).io.din         := io.in(in_idx).bits
        in_queue_vec(in_idx).io.wr_en       := io.in(in_idx).valid
        in_queue_vec(in_idx).io.rd_en       := false.B
        !in_queue_vec(in_idx).io.full       <> io.in(in_idx).ready
    }
    for(in_idx <- 0 until sub_crossbar_size){
        for(in_idy <- 0 until sub_crossbar_size){
            queue_vec(in_idx)(in_idy).io.rst         <> reset
            queue_vec(in_idx)(in_idy).io.clk         <> clock
            queue_vec(in_idx)(in_idy).io.din         := DontCare
            queue_vec(in_idx)(in_idy).io.wr_en       := false.B
            queue_vec(in_idx)(in_idy).io.rd_en       := false.B
        }
    }

    // pre queue logic
    for(in_idx <- 0 until sub_crossbar_size){
        when(!in_queue_vec(in_idx).io.empty){
            in_queue_vec(in_idx).io.rd_en := false.B
            for(in_idy <- 0 until sub_crossbar_size){
                queue_vec(in_idx)(in_idy).io.wr_en := false.B
                when(high(in_queue_vec(in_idx).io.dout) % modnum.U === (in_idy.asUInt(conf.crossbar_data_width.W) * size.U + number.U)){  // 32bits compare
                    in_queue_vec(in_idx).io.dout <> queue_vec(in_idx)(in_idy).io.din
                    !in_queue_vec(in_idx).io.empty <> queue_vec(in_idx)(in_idy).io.wr_en
                    in_queue_vec(in_idx).io.rd_en <> !queue_vec(in_idx)(in_idy).io.full
                } .otherwise {
                    queue_vec(in_idx)(in_idy).io.wr_en := false.B
                }      
            }
        } .otherwise {
            for(in_idy <- 0 until sub_crossbar_size){
                queue_vec(in_idx)(in_idy).io.wr_en := false.B
            }
            in_queue_vec(in_idx).io.rd_en := false.B // fifo_ready_vec(in_idx).reduce(_ && _)
        }
    }

    //post queue logic
    for(out_idy <- 0 until sub_crossbar_size){
        for(out_idx <- 0 until sub_crossbar_size){
            queue_vec(out_idx)(out_idy).io.rd_en <> RRarbiter_vec(out_idy).io.in(out_idx).ready
            queue_vec(out_idx)(out_idy).io.dout <> RRarbiter_vec(out_idy).io.in(out_idx).bits
            !queue_vec(out_idx)(out_idy).io.empty <> RRarbiter_vec(out_idy).io.in(out_idx).valid
        }
    }
    
    // output
    for(out_id <- 0 until sub_crossbar_size){
        RRarbiter_vec(out_id).io.out <> io.out(out_id)
    }
    
    
}
