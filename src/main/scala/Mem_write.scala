package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._

class Memory_write(val num :Int)(implicit val conf : HBMGraphConfiguration) extends Module {
    val io = IO(new Bundle {
        val write_vertex_index = Flipped(Decoupled(UInt(conf.Data_width.W))) // input vertex index you want to write
        val level = Input(UInt(conf.Data_width.W))                           // input level (constant in one iter)
        val offsets = Input(new offsets)                                     // CSR CSC offsets
        val if_write = Input(Bool())
        val node_num = Input(UInt(conf.Data_width.W))
        val uram_out_a = Vec(conf.pipe_num_per_channel, Input(UInt(conf.Data_width_uram.W)))
        val uram_out_b = Vec(conf.pipe_num_per_channel, Input(UInt(conf.Data_width_uram.W)))

        //kernel count reg
        val kernel_count = Input(UInt(32.W))
        val master_finish = Input(Bool())
        val HBM_write_interface = new Bundle{
            // write address channel
            val writeAddr   = Decoupled(new AXIAddress(conf.HBM_Addr_width, conf.memIDBits))
            // write data channel  
            val writeData   = Decoupled(new AXIWriteData(conf.HBM_Data_width))
            // write response channel (for memory consistency)
            val writeResp   = Flipped(Decoupled(new AXIWriteResponse(conf.memIDBits)))
        }

        // output
        val uram_addr_a = Vec(conf.pipe_num_per_channel, Output(UInt(conf.Addr_width_uram.W)))
        val uram_addr_b = Vec(conf.pipe_num_per_channel, Output(UInt(conf.Addr_width_uram.W)))
        val write_finish = Output(Bool())
    })
    val write_vertex_index_queue = Queue(io.write_vertex_index, conf.write_vertex_index_len)
    val write_addr = io.HBM_write_interface.writeAddr   //axi write address channel
    val write_data = io.HBM_write_interface.writeData   //axi write data channel
    io.HBM_write_interface.writeResp.ready := true.B
    write_addr.bits.id := 0.U   //id is useless
    write_addr.bits.addr := DontCare    //wait for write_vertex_index_queue
    write_addr.bits.len := 0.U  //dont need burst
    write_data.bits.data := DontCare

	write_data.bits.strb := "hffffffffffffffffffffffffffffffff".U(128.W)   //custom data width
    write_data.bits.last := true.B  //dont need burst
    io.write_finish := false.B
    io.uram_addr_a <> DontCare
    io.uram_addr_b <> DontCare

    //write_vertex_index_queue's ready is asserted by write address and data channels' ready

    write_vertex_index_queue.ready := write_addr.ready
    val write_count_and_max :: write_level_arr :: write_done :: Nil = Enum(3)
    val valid_state_addr = RegInit(write_count_and_max) // if false, write kernel_count and max_level; if true, write level array
    val valid_state_data = RegInit(write_count_and_max) // if false, write kernel_count and max_level; if true, write level array
    val (count_val_node_addr, counterWrap_node_addr) = Counter((valid_state_addr===write_level_arr) && write_addr.ready && write_addr.valid, 17000000) //level array index
    val (count_val_node_data, counterWrap_node_data) = Counter((valid_state_data===write_level_arr) && write_data.ready && write_data.valid, 17000000) //level array index

    //uram data fifo
    val uram_out_q = Module(new Queue(UInt(8.W), 16))
    val write_addr_flag = (valid_state_addr ===  write_level_arr) && write_addr.ready && uram_out_q.io.count<15.U
    uram_out_q.io.enq.valid := RegNext(write_addr_flag) //next cycle of write addr
    uram_out_q.io.enq.bits := io.uram_out_a((count_val_node_addr-1.U) % conf.pipe_num_per_channel.U) >> (((count_val_node_addr-1.U)/conf.pipe_num_per_channel.U)%9.U)*8.U //place at (7:0)
    uram_out_q.io.deq <> DontCare

    //write address channel
    when(io.master_finish & valid_state_addr === write_count_and_max){ // write kernel_count and max_level
        valid_state_addr := write_level_arr
        // valid_state_addr := write_done
        write_addr.bits.addr := conf.HBM_base_addr * num.asUInt()
        write_addr.valid := true.B
    }.elsewhen(write_addr_flag){ // write HBM addr
        write_addr.bits.addr := (count_val_node_addr / (conf.HBM_Data_width.U/8.U) + io.offsets.level_offset) * (conf.HBM_Data_width.U/8.U) + conf.HBM_base_addr * num.asUInt()
        write_addr.valid := true.B 
        io.uram_addr_a(count_val_node_addr % conf.pipe_num_per_channel.U) := count_val_node_addr / conf.pipe_num_per_channel.U / 9.U
    }.otherwise{
        write_addr.valid := false.B 
        write_vertex_index_queue.ready := true.B
    }
 
    //write data channel
    when(io.master_finish && (valid_state_data === write_count_and_max) && write_data.ready){ 
        valid_state_data := write_level_arr
        // valid_state_data := write_done
        // io.write_finish := RegNext(true.B)
        write_data.bits.data :=  io.kernel_count | (io.level << 32)
        write_data.valid := true.B
    }.elsewhen(valid_state_data === write_level_arr && write_data.ready && uram_out_q.io.deq.valid){ //write HBM data
        val node_level = uram_out_q.io.deq.bits //level value, 8bits
        write_data.bits.data := Mux(io.if_write, count_val_node_data << (count_val_node_data % (conf.HBM_Data_width.U/8.U))*8.U, node_level << (count_val_node_data % (conf.HBM_Data_width.U/8.U))*8.U)
        write_data.bits.strb := 1.U << (count_val_node_data % (conf.HBM_Data_width.U/8.U))
        write_data.valid := true.B
        uram_out_q.io.deq.ready := true.B
    }
    .otherwise{
        write_data.valid := false.B
    }

    //if level array write done
    when(count_val_node_addr >= io.node_num+1.U){
        valid_state_addr := write_done
    }
    when(count_val_node_data >= io.node_num+1.U){
        valid_state_data := write_done
        io.write_finish := RegNext(true.B)
    }
    // when(valid_state_data === write_done){
    //     io.write_finish := true.B
    // }

}
