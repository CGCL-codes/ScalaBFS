package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._

// Read neighbour using burst and deal with the situation where neighbour size > 256
class Read_neighbour(implicit val conf : HBMGraphConfiguration) extends Module {
    val io = IO(new Bundle {
        // input
        // val readData = Flipped(Decoupled(new AXIReadData(64, conf.memIDBits))) //HBM data out
        val readData = Flipped(Decoupled(new ReadData(64, conf.memIDBits))) //HBM data out
        val offsets = Input(new offsets)  // offsets
        val push_or_pull_state = Input(Bool()) // 0 for push

        //output
        val to_arbiter = Decoupled(new Bundle{
            val index = UInt(conf.Data_width.W)
            val burst_len = UInt(8.W) // in 64 bits
            val id = UInt(conf.memIDBits.W) // 0->index, 1->neighbour
        })

        // these are for counters
        val count_val_n0 = Output(UInt(conf.Data_width.W)) // burst number summation
        val queue_ready = Output(Bool())
        val queue_valid = Output(Bool())

        // these are for src_index_queue
        val src_q0_deq = Flipped(Decoupled(UInt(conf.Data_width.W)))
        val src_q1_enq = Decoupled(UInt(conf.Data_width.W))
    })
    val read_rsp :: loop :: Nil = Enum(2)
    val state = RegInit(read_rsp)
    val neighbour_count = RegInit(0.U(conf.Data_width.W))
    val temp_index = RegInit(0.U(conf.Data_width.W))
    val queue_readData_l1 = Module(new Queue(new ReadData(64, conf.memIDBits), 2)) // big queue to ensure no deadlock
    val queue_readData_l2 = Module(new Queue(new ReadData(64, conf.memIDBits), 2)) // big queue to ensure no deadlock
    val queue_readData = Module(new Queue(new ReadData(64, conf.memIDBits), conf.Mem_queue_readData_len)) // big queue to ensure no deadlock
    val burst_sum = RegInit(0.U(conf.Data_width.W))
    // val edge_sum = RegInit(0.U(conf.Data_width.W))
    val before_4k_bound_count = Wire(UInt(conf.Data_width.W))
    before_4k_bound_count := DontCare
    val C_offset = Mux(io.push_or_pull_state, io.offsets.CSC_C_offset ,io.offsets.CSR_C_offset)


    burst_sum <> io.count_val_n0

    io.to_arbiter.bits <> DontCare


    queue_readData_l1.io.enq <> io.readData
    queue_readData_l2.io.enq <> queue_readData_l1.io.deq
    queue_readData.io.enq <> queue_readData_l2.io.deq

    // queue_readData <> to_arbiter
    queue_readData.io.deq.ready := false.B
    io.to_arbiter.valid := false.B

    // for counter
    io.queue_ready := RegNext(queue_readData.io.deq.ready)
    io.queue_valid := RegNext(queue_readData.io.deq.valid)
    val unpacked_readData = queue_readData.io.deq.bits.data.asTypeOf(
        Vec(2, UInt(conf.Data_width.W)) // 0->size, 1->index
    )

    //for src_index_queue0 <> src_index_queue1 todo
    io.src_q1_enq.bits  <> io.src_q0_deq.bits
    io.src_q0_deq.ready := false.B // send
    io.src_q1_enq.valid := false.B // recieve

    val start_loc = unpacked_readData(1) + C_offset
    val start_addr = start_loc * (conf.HBM_Data_width_to_reader >> 3).asUInt()

    val start_loc_loop = temp_index + C_offset
    val start_addr_loop = start_loc_loop * (conf.HBM_Data_width_to_reader >> 3).asUInt()


    

    switch(state) {
        is(read_rsp){
            when(io.to_arbiter.ready && queue_readData.io.deq.valid && io.src_q0_deq.valid && io.src_q1_enq.ready){
                when(unpacked_readData(0) > 256.U){ // split into multiple burst
                    // when(start_addr >> 12.U =/= (start_addr + conf.hbm_mul_256_min_1.U) >> 12.U){
                    when(start_addr(12) =/= (start_addr + conf.hbm_mul_256_min_1.U)(12)){
                    // when burst cross 4k boundary
                        // before_4k_bound_count := (((start_addr & 0xFFFFF000L.U) + 0x1000.U) - start_addr) >> (conf.shift_hbm - 3).U
                        before_4k_bound_count := (0x1000.U - start_addr(11,0)) >> (conf.shift_hbm - 3).U
                        io.to_arbiter.bits.id := 1.U(conf.memIDBits.W)
                        io.to_arbiter.bits.index := start_loc
                        io.to_arbiter.bits.burst_len := before_4k_bound_count - 1.U
                        neighbour_count := unpacked_readData(0) - before_4k_bound_count
                        burst_sum := burst_sum + before_4k_bound_count
                        temp_index := unpacked_readData(1) + before_4k_bound_count
                        io.src_q0_deq.ready := false.B // send
                        io.src_q1_enq.valid := true.B  // recieve
                        queue_readData.io.deq.ready := false.B // send
                        io.to_arbiter.valid := true.B // recieve
                        state := loop
                    }. otherwise{
                        io.to_arbiter.bits.index := start_loc
                        io.to_arbiter.bits.burst_len := 255.U(8.W)
                        io.to_arbiter.bits.id := 1.U(conf.memIDBits.W)
                        neighbour_count := unpacked_readData(0) - 256.U
                        burst_sum := burst_sum + 256.U
                        temp_index := unpacked_readData(1) + 256.U
                        io.src_q0_deq.ready := false.B // send
                        io.src_q1_enq.valid := true.B  // recieve
                        queue_readData.io.deq.ready := false.B // send
                        io.to_arbiter.valid := true.B // recieve
                        state := loop
                    }
                }.elsewhen(unpacked_readData(0) =/= 0.U){ //0<neighbour<=256
                    when(start_addr(12) =/= (start_addr + (unpacked_readData(0) * (conf.HBM_Data_width_to_reader >> 3).U)-1.U)(12)){
                    // cross 4k boundary    
                        before_4k_bound_count := (0x1000.U - start_addr(11,0)) >> (conf.shift_hbm - 3).U
                        io.to_arbiter.bits.id := 1.U(conf.memIDBits.W)
                        io.to_arbiter.bits.index := start_loc
                        io.to_arbiter.bits.burst_len := before_4k_bound_count - 1.U
                        neighbour_count := unpacked_readData(0) - before_4k_bound_count
                        burst_sum := burst_sum + before_4k_bound_count
                        temp_index := unpacked_readData(1) + before_4k_bound_count
                        io.src_q0_deq.ready := false.B // send
                        io.src_q1_enq.valid := true.B  // recieve
                        queue_readData.io.deq.ready := false.B // send
                        io.to_arbiter.valid := true.B // recieve
                        state := loop
                    }. otherwise{        
                        io.to_arbiter.bits.index := start_loc
                        io.to_arbiter.bits.burst_len := unpacked_readData(0) - 1.U
                        io.to_arbiter.bits.id := 1.U(conf.memIDBits.W)
                        burst_sum := burst_sum + unpacked_readData(0)
                        io.src_q0_deq.ready := true.B // send
                        io.src_q1_enq.valid := true.B  // recieve
                        queue_readData.io.deq.ready := true.B // send
                        io.to_arbiter.valid := true.B // recieve
                    }
                } .otherwise{ // neighbour_num == 0
                    io.src_q0_deq.ready := true.B // send
                    io.src_q1_enq.valid := false.B  // recieve
                    queue_readData.io.deq.ready := true.B // send
                    io.to_arbiter.valid := false.B // recieve
                }
            }
        }
        is(loop){ // when burst size > 256
            when(io.to_arbiter.ready && io.src_q0_deq.valid && io.src_q1_enq.ready){
                when(neighbour_count > 256.U){
                    when(start_addr_loop(12) =/= (start_addr_loop + (256*(conf.HBM_Data_width_to_reader >> 3)).U-1.U)(12)){
                    // cross 4k boundary    
                        before_4k_bound_count := (0x1000.U - start_addr_loop(11,0)) >> (conf.shift_hbm - 3).U
                        io.to_arbiter.bits.id := 1.U(conf.memIDBits.W)
                        io.to_arbiter.bits.index := start_loc_loop
                        io.to_arbiter.bits.burst_len := before_4k_bound_count - 1.U
                        neighbour_count := neighbour_count - before_4k_bound_count
                        burst_sum := burst_sum + before_4k_bound_count
                        temp_index := temp_index + before_4k_bound_count
                        io.src_q0_deq.ready := false.B // send
                        io.src_q1_enq.valid := true.B  // recieve
                        queue_readData.io.deq.ready := false.B // send
                        io.to_arbiter.valid := true.B // recieve
                    }. otherwise{                         
                        io.to_arbiter.bits.index := start_loc_loop
                        io.to_arbiter.bits.burst_len := 255.U(8.W)
                        io.to_arbiter.bits.id := 1.U(conf.memIDBits.W)
                        temp_index := temp_index + 256.U
                        burst_sum := burst_sum + 256.U
                        neighbour_count := neighbour_count - 256.U
                        io.src_q0_deq.ready := false.B // send
                        io.src_q1_enq.valid := true.B  // recieve
                        queue_readData.io.deq.ready := false.B // send
                        io.to_arbiter.valid := true.B // recieve
                    }
                }.otherwise{ // <=256
                    when(start_addr_loop(12) =/= (start_addr_loop+(neighbour_count * (conf.HBM_Data_width_to_reader >> 3).U)-1.U)(12)){
                    // cross 4k boundary    
                        before_4k_bound_count := (0x1000.U - start_addr_loop(11,0)) >> (conf.shift_hbm - 3).U
                        io.to_arbiter.bits.id := 1.U(conf.memIDBits.W)
                        io.to_arbiter.bits.index := start_loc_loop
                        io.to_arbiter.bits.burst_len := before_4k_bound_count - 1.U
                        neighbour_count := neighbour_count - before_4k_bound_count
                        burst_sum := burst_sum + before_4k_bound_count
                        temp_index := temp_index + before_4k_bound_count
                        io.src_q0_deq.ready := false.B // send
                        io.src_q1_enq.valid := true.B  // recieve
                        queue_readData.io.deq.ready := false.B // send
                        io.to_arbiter.valid := true.B // recieve
                        state := loop
                    }. otherwise{                     
                        io.to_arbiter.bits.index := start_loc_loop
                        io.to_arbiter.bits.burst_len := neighbour_count - 1.U
                        io.to_arbiter.bits.id := 1.U(conf.memIDBits.W)
                        burst_sum := burst_sum + neighbour_count
                        temp_index := 0.U
                        neighbour_count := 0.U
                        io.src_q0_deq.ready := true.B // send
                        io.src_q1_enq.valid := true.B  // recieve
                        queue_readData.io.deq.ready := true.B // send
                        io.to_arbiter.valid := true.B // recieve
                        state := read_rsp
                    }
                }
            }
        }
    }
}

class myArbiterIO(implicit val conf : HBMGraphConfiguration) extends Bundle {
    val index = UInt(conf.Data_width.W) // in 64 bits
    val burst_len = UInt(8.W) // in 64 bits
    val id = UInt(2.W) // 0->index, 1->neighbour
}


// Memory logic
// (read modified CSR: R indices followed by neighbour number)
class Memory(val num :Int)(implicit val conf : HBMGraphConfiguration) extends Module {
    val io = IO(new Bundle {
        // input
        val R_array_index = Vec(conf.pipe_num_per_channel, Flipped(Decoupled(UInt(conf.Data_width.W)))) 
        val p1_end = Input(Bool())                                           // input p1_end
        val level = Input(UInt(conf.Data_width.W))                           // input level (constant in one iter)
        val push_or_pull_state = Input(Bool())                               // 0 for push
        val offsets = Input(new offsets)                                     // offsets
        val uram_out_a = Vec(conf.pipe_num_per_channel, Input(UInt(conf.Data_width_uram.W)))
        //kernel count reg
        val kernel_count = Input(UInt(32.W))
        val master_finish = Input(Bool())
        val node_num = Input(UInt(conf.Data_width.W))

        // output
        val neighbour_cnt = Output(UInt(conf.Data_width.W))       // output neighbour count of the vertex
        val mem_end = Output(Bool())                              // output neighbour count of the vertex
        val neighbours = Vec(conf.pipe_num_per_channel, Decoupled(UInt((conf.Data_width*2).W)))
        val uram_addr_a = Vec(conf.pipe_num_per_channel, Output(UInt(conf.Addr_width_uram.W)))
        val write_finish = Output(Bool())
        val HBM_interface = new AXIMasterIF_reader(conf.HBM_Addr_width, conf.HBM_Data_width_to_reader, conf.memIDBits) // HBM interface
        val mem_clock = Input(Clock())
        val reset_h = Input(Bool()) 
    })
    dontTouch(io)

    // ---------------- add HBM reader -----------------------------
    val HBM_reader = withClockAndReset(io.mem_clock, io.reset_h){Module(new HBM_reader(num))}

    // write module
    val write_channel = Module(new Memory_write(num))
    for(i <- 0 until conf.pipe_num_per_channel){
        // uram
        io.uram_addr_a <> write_channel.io.uram_addr_a
        io.uram_out_a <> write_channel.io.uram_out_a
    }
    write_channel.io.level <> RegNext(io.level)
    write_channel.io.offsets <> io.offsets
    write_channel.io.HBM_write_interface.writeAddr <> HBM_reader.io.interface_to_pe.writeAddr
    write_channel.io.HBM_write_interface.writeData <> HBM_reader.io.interface_to_pe.writeData
    write_channel.io.HBM_write_interface.writeResp <> HBM_reader.io.interface_to_pe.writeResp
    write_channel.io.kernel_count := RegNext(io.kernel_count)
    write_channel.io.master_finish := RegNext(io.master_finish)
    RegNext(write_channel.io.write_finish) <> io.write_finish
    write_channel.io.node_num     <> RegNext(io.node_num)
    // modules
    val arb = Module(new Arbiter(new myArbiterIO, 2))
    val R_array_index_queue_l1 = Module(new Queue(UInt(conf.Data_width.W), 2))
    val R_array_index_queue_l2 = Module(new Queue(UInt(conf.Data_width.W), 2))
    val R_array_index_queue = Module(new Queue(UInt(conf.Data_width.W), conf.Mem_R_array_index_queue_len))
    val src_index_queue0 = Module(new Queue(UInt(conf.Data_width.W), conf.src_index_queue_len))
    val read_neighbour = Module(new Read_neighbour)






    //reader
    HBM_reader.io.HBM_interface <> io.HBM_interface
    HBM_reader.io.sysclock <> clock
    HBM_reader.io.src <> read_neighbour.io.src_q1_enq

    


    //counters
    val (count_val_i0, counterWrap_i0) = Counter(R_array_index_queue.io.enq.ready && R_array_index_queue.io.enq.valid, 2147483647)
    val (count_val_i1, counterWrap_i1) = Counter(read_neighbour.io.queue_ready && read_neighbour.io.queue_valid, 2147483647)
    val count_val_n0 = RegNext(read_neighbour.io.count_val_n0)

    val count_n_vec = Array.ofDim[UInt](conf.pipe_num_per_channel)
    val count_w_vec = Array.ofDim[Bool](conf.pipe_num_per_channel)

    for(n_id <- 0 until conf.pipe_num_per_channel){
        val (tmp0, tmp1)  = Counter(io.neighbours(n_id).ready && io.neighbours(n_id).valid, 2147483647)
        count_n_vec(n_id) = tmp0
        count_w_vec(n_id) = tmp1
    }

    for(i <- 0 until conf.pipe_num_per_channel){
        io.neighbours(i).bits := HBM_reader.io.interface_to_pe.readData_array(i).bits.data
    }

    // neighbour_cnt
    io.neighbour_cnt := RegNext(count_n_vec.reduce(_ + _))

    io.mem_end := RegNext(count_val_i0 === count_val_i1 && count_val_n0 === HBM_reader.io.burst_cnt && io.neighbour_cnt === HBM_reader.io.neighbour_cnt && io.p1_end
        && HBM_reader.io.interface_to_pe.writeAddr.valid === false.B && HBM_reader.io.interface_to_pe.writeData.valid === false.B
        && HBM_reader.io.interface_to_pe.readAddr.valid === false.B ) //先不加readdata_array().valid
        
    val R_array_index_arb = Module(new RRArbiter(UInt(conf.Data_width.W), conf.pipe_num_per_channel))
    for(p1_id <- 0 until conf.pipe_num_per_channel){

        io.R_array_index(p1_id) <> R_array_index_arb.io.in(p1_id)
    }


    R_array_index_arb.io.out <> R_array_index_queue_l1.io.enq

    R_array_index_queue_l2.io.enq.bits   <> ~(~R_array_index_queue_l1.io.deq.bits)
    R_array_index_queue_l2.io.enq.valid   <> ~(~R_array_index_queue_l1.io.deq.valid)
    R_array_index_queue_l2.io.enq.ready   <> R_array_index_queue_l1.io.deq.ready
    R_array_index_queue.io.enq      <> R_array_index_queue_l2.io.deq

    // R_array_index_queue <> arbiter & src_index_queue0
    arb.io.in(1).bits.index := (R_array_index_queue.io.deq.bits >> (conf.shift_channel).asUInt()) + Mux(io.push_or_pull_state, io.offsets.CSC_R_offset ,io.offsets.CSR_R_offset)
    arb.io.in(1).bits.burst_len := 0.U(8.W)
    arb.io.in(1).bits.id := 0.U(2.W)
    arb.io.in(1).valid := R_array_index_queue.io.deq.valid && src_index_queue0.io.enq.ready
    src_index_queue0.io.enq.valid := R_array_index_queue.io.deq.valid && arb.io.in(1).ready
    src_index_queue0.io.enq.bits <> R_array_index_queue.io.deq.bits

    R_array_index_queue.io.deq.ready := arb.io.in(1).ready && src_index_queue0.io.enq.ready

    // arbiter <> HBM_interface.readAddr
    val to_readAddr_queue = Queue(arb.io.out, conf.to_readAddr_queue_len)

    HBM_reader.io.interface_to_pe.readAddr.bits.addr <> (to_readAddr_queue.bits.index) * (conf.HBM_Data_width_to_reader >> 3).asUInt(conf.Data_width.W) + conf.HBM_base_addr * num.asUInt()
    HBM_reader.io.interface_to_pe.readAddr.bits.len <> to_readAddr_queue.bits.burst_len // assume <= 255
    HBM_reader.io.interface_to_pe.readAddr.bits.id <> to_readAddr_queue.bits.id  // 0->index, 1->neighbour
    HBM_reader.io.interface_to_pe.readAddr.ready <> to_readAddr_queue.ready
    HBM_reader.io.interface_to_pe.readAddr.valid <> to_readAddr_queue.valid

    //src_index_queue0 <> src_index_queue1
    src_index_queue0.io.deq <> read_neighbour.io.src_q0_deq

    // HBM_interface.readData <> read_neighbour
    HBM_reader.io.interface_to_pe.readData_offset.bits     <> read_neighbour.io.readData.bits
    HBM_reader.io.interface_to_pe.readData_offset.valid    <> read_neighbour.io.readData.valid

    for(C_id <- 0 until conf.pipe_num_per_channel){
        // io.neighbours(C_id).valid := neighbours_valid && (unpacked_readData(C_id) =/= ~(0.U(32.W)))
        io.neighbours(C_id).valid := HBM_reader.io.interface_to_pe.readData_array(C_id).valid && io.neighbours(C_id).ready
    }
    
    HBM_reader.io.interface_to_pe.readData_offset.ready := read_neighbour.io.readData.ready && HBM_reader.io.interface_to_pe.readData_offset.valid
    for(C_id <- 0 until conf.pipe_num_per_channel){
        HBM_reader.io.interface_to_pe.readData_array(C_id).ready := HBM_reader.io.interface_to_pe.readData_array(C_id).valid && io.neighbours(C_id).ready
    }

    // read_neighbour <> arbiter
    arb.io.in(0) <> read_neighbour.io.to_arbiter

    // read_neighbour <> offsets
    io.offsets <> read_neighbour.io.offsets
    io.push_or_pull_state <> read_neighbour.io.push_or_pull_state

}
