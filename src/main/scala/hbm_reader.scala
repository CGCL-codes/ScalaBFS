package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._


class HBM_reader_IO (implicit val conf : HBMGraphConfiguration) extends Bundle {
    // to HBM
    val HBM_interface = new AXIMasterIF_reader(conf.HBM_Addr_width, conf.HBM_Data_width_to_reader, conf.memIDBits) // HBM interface
    
    // to io.sysclock domain conversion module
    val interface_to_pe = new AXIMasterIF_flip(conf.HBM_Addr_width, conf.HBM_Data_width_to_reader, conf.memIDBits) // HBM interface
    val sysclock = Input(Clock()) // low frequency
    val src = Flipped(Decoupled(UInt(conf.Data_width.W)))
    val burst_cnt = Output(UInt(conf.Data_width.W))
    val neighbour_cnt = Output(UInt(conf.Data_width.W))
}


class HBM_reader (val num :Int)(implicit val conf : HBMGraphConfiguration) extends Module {
    val io = IO(new HBM_reader_IO)
    dontTouch(io)

    val asynFIFO_num = conf.pipe_num_per_channel

    val receive_data ::loop :: Nil = Enum(2)
    val stateReg = RegInit(receive_data)

    // asynchronous FIFOs
    val writeAddr = Module(new fifo_generator_writeAddr(io.HBM_interface.writeAddr.getWidth - 2))
    val writeData = Module(new fifo_generator_writeData(io.HBM_interface.writeData.getWidth - 2))
    val writeResp = Module(new fifo_generator_writeResp(io.HBM_interface.writeResp.getWidth - 2))
    val readAddr = Module(new fifo_generator_readAddr(io.HBM_interface.readAddr.getWidth - 2))


    val readData_vec_0 = VecInit(Seq.fill(asynFIFO_num)(Module(new fifo_generator_readData_syn(2*conf.Data_width)).io))
    val readData_vec_1 = VecInit(Seq.fill(asynFIFO_num)(Module(new fifo_generator_readData_syn(2*conf.Data_width)).io))
    val readData_vec_asy = VecInit(Seq.fill(asynFIFO_num)(Module(new fifo_generator_readData(2*conf.Data_width)).io))
    
    val RRarbiter_vec = Array.ofDim[RRArbiter[UInt]](asynFIFO_num)

    for(i <- 0 until asynFIFO_num){
        RRarbiter_vec(i) = Module(new RRArbiter(UInt((2*conf.Data_width).W), 2))
    }
    for(i <- 0 until asynFIFO_num){
        RRarbiter_vec(i).io.in(0).valid <> !readData_vec_0(i).empty
        RRarbiter_vec(i).io.in(0).bits  <> ~(~(readData_vec_0(i).dout))
        RRarbiter_vec(i).io.in(0).ready <> readData_vec_0(i).rd_en
        RRarbiter_vec(i).io.in(1).valid <> !readData_vec_1(i).empty
        RRarbiter_vec(i).io.in(1).bits  <> ~(~(readData_vec_1(i).dout))
        RRarbiter_vec(i).io.in(1).ready <> readData_vec_1(i).rd_en
    }

    
    val readData_offset = Module(new fifo_generator_readData(2*conf.Data_width))

    val src_index_queue_asyn = Module(new fifo_generator_src(conf.Data_width))

    // Convert the count signal to use asynchronous fifo delivery
    val burst_cnt = Module(new fifo_generator_src(conf.Data_width))
    val neighbour_cnt = Module(new fifo_generator_src(conf.Data_width))



//---------------------------writeAddr------------------------

    writeAddr.io.rst        <> reset
    writeAddr.io.wr_clk     <> io.sysclock
    writeAddr.io.rd_clk     <> clock
    // writeAddr.io.din.asTypeOf(new AXIAddress(conf.HBM_Addr_width, conf.memIDBits))        <> io.interface_to_pe.writeAddr.bits 
    writeAddr.io.din        <> Cat(io.interface_to_pe.writeAddr.bits.addr, io.interface_to_pe.writeAddr.bits.len, io.interface_to_pe.writeAddr.bits.id)
    writeAddr.io.wr_en      <> io.interface_to_pe.writeAddr.valid
    writeAddr.io.rd_en      <> io.HBM_interface.writeAddr.ready
    writeAddr.io.dout.asTypeOf(new AXIAddress(conf.HBM_Addr_width, conf.memIDBits))       <> io.HBM_interface.writeAddr.bits 
    !writeAddr.io.full      <> io.interface_to_pe.writeAddr.ready
    !writeAddr.io.empty     <> io.HBM_interface.writeAddr.valid

//---------------------------writeData------------------------

    writeData.io.rst        <> reset
    writeData.io.wr_clk     <> io.sysclock
    writeData.io.rd_clk     <> clock
    // writeData.io.din.asTypeOf(new AXIWriteData(conf.HBM_Data_width_to_reader))        <> io.interface_to_pe.writeData.bits 
    writeData.io.din        <> Cat(io.interface_to_pe.writeData.bits.data, io.interface_to_pe.writeData.bits.strb, io.interface_to_pe.writeData.bits.last)
    writeData.io.wr_en      <> io.interface_to_pe.writeData.valid
    writeData.io.rd_en      <> io.HBM_interface.writeData.ready
    writeData.io.dout.asTypeOf(new AXIWriteData(conf.HBM_Data_width_to_reader))       <> io.HBM_interface.writeData.bits 
    !writeData.io.full      <> io.interface_to_pe.writeData.ready
    !writeData.io.empty     <> io.HBM_interface.writeData.valid

//---------------------------ReadAddr------------------------

    readAddr.io.rst        <> reset
    readAddr.io.wr_clk     <> io.sysclock
    readAddr.io.rd_clk     <> clock
    // readAddr.io.din.asTypeOf(new AXIAddress(conf.HBM_Addr_width, conf.memIDBits))        <> io.interface_to_pe.readAddr.bits 
    readAddr.io.din        <> Cat(io.interface_to_pe.readAddr.bits.addr, io.interface_to_pe.readAddr.bits.len, io.interface_to_pe.readAddr.bits.id)
    readAddr.io.wr_en      <> io.interface_to_pe.readAddr.valid
    readAddr.io.rd_en      <> io.HBM_interface.readAddr.ready
    readAddr.io.dout.asTypeOf(new AXIAddress(conf.HBM_Addr_width, conf.memIDBits))       <> io.HBM_interface.readAddr.bits 
    !readAddr.io.full      <> io.interface_to_pe.readAddr.ready
    !readAddr.io.empty     <> io.HBM_interface.readAddr.valid
 
//---------------------------writeResp------------------------

    writeResp.io.rst        <> reset
    writeResp.io.wr_clk     <> clock
    writeResp.io.rd_clk     <> io.sysclock
    // writeResp.io.din.asTypeOf(new AXIWriteResponse(conf.memIDBits))       <> io.HBM_interface.writeResp.bits 
    writeResp.io.din        <> Cat(io.HBM_interface.writeResp.bits.id, io.HBM_interface.writeResp.bits.resp) 
    writeResp.io.wr_en      <> io.HBM_interface.writeResp.valid
    writeResp.io.rd_en      <> io.interface_to_pe.writeResp.ready
    writeResp.io.dout.asTypeOf(new AXIWriteResponse(conf.memIDBits))       <> io.interface_to_pe.writeResp.bits 
    !writeResp.io.full      <> io.HBM_interface.writeResp.ready
    !writeResp.io.empty     <> io.interface_to_pe.writeResp.valid


//---------------------------readData_array------------------------

for(i <- 0 until asynFIFO_num) {

    readData_vec_asy(i).rst        <> reset
    readData_vec_asy(i).wr_clk     <> clock
    readData_vec_asy(i).rd_clk     <> io.sysclock
    readData_vec_asy(i).din        := RRarbiter_vec(i).io.out.bits
    readData_vec_asy(i).wr_en      := RRarbiter_vec(i).io.out.valid && RRarbiter_vec(i).io.out.ready
    readData_vec_asy(i).rd_en      <> io.interface_to_pe.readData_array(i).ready
    readData_vec_asy(i).dout.asTypeOf(new ReadData(conf.HBM_Data_width_to_reader, conf.memIDBits))       <> io.interface_to_pe.readData_array(i).bits 
    !readData_vec_asy(i).full      <> RRarbiter_vec(i).io.out.ready
    !readData_vec_asy(i).empty     <> io.interface_to_pe.readData_array(i).valid
}

for(i <- 0 until asynFIFO_num) {

    readData_vec_0(i).rst        <> reset
    readData_vec_0(i).clk     <> clock
    readData_vec_0(i).din        := DontCare
    readData_vec_0(i).wr_en      := false.B
}
for(i <- 0 until asynFIFO_num) {

    readData_vec_1(i).rst        <> reset
    readData_vec_1(i).clk     <> clock
    readData_vec_1(i).din        := DontCare
    readData_vec_1(i).wr_en      := false.B
}

//---------------------------readData_offset------------------------

    readData_offset.io.rst        <> reset
    readData_offset.io.wr_clk     <> clock
    readData_offset.io.rd_clk     <> io.sysclock
    readData_offset.io.din        <> io.HBM_interface.readData.bits.data(2*conf.Data_width - 1, 0)
    readData_offset.io.wr_en      := false.B
    readData_offset.io.rd_en      <> io.interface_to_pe.readData_offset.ready
    readData_offset.io.dout.asTypeOf(new ReadData (2*conf.Data_width, conf.memIDBits))       <> io.interface_to_pe.readData_offset.bits 
    !readData_offset.io.empty     <> io.interface_to_pe.readData_offset.valid

//---------------------------src------------------------

    val src_index_queue_l1 = Module(new Queue(UInt(conf.Data_width.W), 2))
    val src_index_queue_l2 = Module(new Queue(UInt(conf.Data_width.W), 2))
    val src_index_queue = Module(new Queue(UInt(conf.Data_width.W), conf.src_index_queue_len))

    src_index_queue_asyn.io.rst        <> reset
    src_index_queue_asyn.io.wr_clk     <> io.sysclock
    src_index_queue_asyn.io.rd_clk     <> clock
    src_index_queue_asyn.io.din        <> ~(~io.src.bits)
    src_index_queue_asyn.io.wr_en      <> ~(~io.src.valid)
    src_index_queue_asyn.io.rd_en      <> ~(~src_index_queue_l1.io.enq.ready)
    ~(~src_index_queue_asyn.io.dout)       <> src_index_queue_l1.io.enq.bits
    ~(~(!src_index_queue_asyn.io.full))      <> io.src.ready
    ~(~(!src_index_queue_asyn.io.empty))     <> src_index_queue_l1.io.enq.valid

    src_index_queue_l1.io.deq <> src_index_queue_l2.io.enq
    src_index_queue_l2.io.deq <> src_index_queue.io.enq
 

 //-----------------------------------------------counters-----------------------------------
    val (count_burst_i0, counterWrap_i0) = Counter(io.HBM_interface.readData.ready && io.HBM_interface.readData.valid && io.HBM_interface.readData.bits.id === 1.U, 2147483647)

    val count_n_vec = Array.ofDim[UInt](asynFIFO_num)
    val count_w_vec = Array.ofDim[Bool](asynFIFO_num)

    
    for(i <- 0 until asynFIFO_num){
        //try to fix hold?
        val (tmp0, tmp1)  = Counter(~(~readData_vec_asy(i).wr_en) === true.B, 2147483647)
        count_n_vec(i) = tmp0
        count_w_vec(i) = tmp1
    }

    burst_cnt.io.rst        <> reset
    burst_cnt.io.wr_clk     <> clock
    burst_cnt.io.rd_clk     <> io.sysclock
    burst_cnt.io.din        <> RegNext(count_burst_i0)
    burst_cnt.io.wr_en      <> true.B
    burst_cnt.io.rd_en      <> true.B
    burst_cnt.io.dout       <> io.burst_cnt

    neighbour_cnt.io.rst        <> reset
    neighbour_cnt.io.wr_clk     <> clock
    neighbour_cnt.io.rd_clk     <> io.sysclock
    neighbour_cnt.io.din        <> RegNext(count_n_vec.reduce(_ + _))
    neighbour_cnt.io.wr_en      <> true.B
    neighbour_cnt.io.rd_en      <> true.B
    neighbour_cnt.io.dout       <> io.neighbour_cnt

//---------------------------------------------------------------------------------------
    // src_queue
    // src_index_queue.io.enq <> io.src

    val process_src_all = Wire(Bool())
    process_src_all := false.B
    src_index_queue.io.deq.ready := io.HBM_interface.readData.bits.last && io.HBM_interface.readData.valid && io.HBM_interface.readData.ready && (io.HBM_interface.readData.bits.id === 1.U) && process_src_all


    val src = Wire(Vec(asynFIFO_num, UInt(conf.crossbar_data_width.W))) 
    val unpacked_readData = io.HBM_interface.readData.bits.data.asTypeOf(
        Vec(conf.reader_accept_num, UInt(conf.Data_width.W))
    )
    // correspond to 2*pipe_num_per_channel asynchronous FIFOs
    for(C_id <- 0 until asynFIFO_num){
        src(C_id) := src_index_queue.io.deq.bits
    }
    // deal 
    // the invalid data is padded at the begin of one element
    val loc_flag_data_0 = RegInit(0.U(8.W))
    val loc_flag_data_1 = RegInit(0.U(8.W))
    val used = RegInit(0.U(8.W))
    // judge if each of the asynchronous fifo is not full
    val full_vec_0 = Array.ofDim[UInt](asynFIFO_num)
    for(i <- 0 until asynFIFO_num) {
        full_vec_0(i) = readData_vec_0(i).full
    }
    val full_vec_1 = Array.ofDim[UInt](asynFIFO_num)
    for(i <- 0 until asynFIFO_num) {
        full_vec_1(i) = readData_vec_1(i).full
    }
    val not_full_0 = Wire(Bool())
    not_full_0 := !full_vec_0.reduce(_|_)

    val not_full_1 = Wire(Bool())
    not_full_1 := !full_vec_1.reduce(_|_)
    val valid_vec = Array.ofDim[UInt](8)
    for(i <- 0 until conf.reader_accept_num) {
        valid_vec(i) = Cat(0.U(7.W), !(unpacked_readData(i) === ~(0.U(32.W))))
    }
    val valid_data = Wire(UInt(8.W))
    valid_data := valid_vec.reduce(_+_)
    val valid_data_num = RegInit(0.U((8.W))) // use a reg to store in the FSM

    // cut data width
    val neighbour = Wire(Vec(conf.reader_accept_num, UInt(conf.crossbar_data_width.W)))
    for(i <- 0 until conf.reader_accept_num) {
        neighbour(i) := unpacked_readData(i)
    }

    io.HBM_interface.readData.ready := false.B

    val flag_readData_vec = RegInit(0.U(1.W))

    if(asynFIFO_num >= conf.reader_accept_num){
        process_src_all := false.B
        io.HBM_interface.readData.ready := false.B
        when(io.HBM_interface.readData.valid === true.B){
            //receive offset array
            when(io.HBM_interface.readData.bits.id === 0.U && !readData_offset.io.full){
                io.HBM_interface.readData.ready := true.B
                readData_offset.io.wr_en := true.B
            }
            when(io.HBM_interface.readData.bits.id === 1.U && not_full_0 && src_index_queue.io.deq.valid && flag_readData_vec === 0.U){
                for(C_id <- 0 until conf.reader_accept_num){
                    readData_vec_0(C_id.U).wr_en := ~(~(unpacked_readData(C_id.U) =/= ~(0.U(32.W))))
                    readData_vec_0(C_id.U).din := ~(~(Cat(Fill((conf.Data_width - conf.crossbar_data_width) * 2, 0.U(1.W)), neighbour(C_id.U), src(C_id.U))))
                }
                io.HBM_interface.readData.ready := true.B
                loc_flag_data_0 := 0.U
                process_src_all := true.B
                flag_readData_vec := 1.U
            }
            when(io.HBM_interface.readData.bits.id === 1.U && not_full_1 && src_index_queue.io.deq.valid && flag_readData_vec === 1.U){
                for(C_id <- 0 until conf.reader_accept_num){
                    readData_vec_1(C_id.U).wr_en := ~(~(unpacked_readData(C_id.U) =/= ~(0.U(32.W))))
                    readData_vec_1(C_id.U).din := ~(~(Cat(Fill((conf.Data_width - conf.crossbar_data_width) * 2, 0.U(1.W)), neighbour(C_id.U), src(C_id.U))))
                }
                io.HBM_interface.readData.ready := true.B
                loc_flag_data_1 := 0.U
                process_src_all := true.B
                flag_readData_vec := 0.U
            }
        }
    }
    else{
        switch (stateReg){
            is(receive_data){
                //receive data from HBM
                process_src_all := false.B
                io.HBM_interface.readData.ready := false.B
                when(io.HBM_interface.readData.valid === true.B){
                    //receive offset array
                    // flag_readData_vec = 0
                    when(io.HBM_interface.readData.bits.id === 0.U && !readData_offset.io.full){
                        io.HBM_interface.readData.ready := true.B
                        readData_offset.io.wr_en := true.B
                    }
                    when(io.HBM_interface.readData.bits.id === 1.U && not_full_0 && src_index_queue.io.deq.valid && flag_readData_vec === 0.U){
                        for(C_id <- 0 until asynFIFO_num){
                            readData_vec_0(C_id.U).wr_en := ~(~(unpacked_readData(C_id.U + loc_flag_data_0) =/= ~(0.U(32.W))))
                            readData_vec_0(C_id.U).din := ~(~(Cat(Fill((conf.Data_width - conf.crossbar_data_width) * 2, 0.U(1.W)), neighbour(C_id.U + loc_flag_data_0), src(C_id.U + loc_flag_data_0))))
                        }
                        when(valid_data > asynFIFO_num.U){    
                            // loc_flag not change
                            io.HBM_interface.readData.ready := false.B 
                            valid_data_num := valid_data - asynFIFO_num.U // store the number of valid data
                            loc_flag_data_0 := loc_flag_data_0 + asynFIFO_num.U
                            process_src_all := false.B
                            stateReg := loop
                        }
                        when(valid_data <= asynFIFO_num.U){    
                            // loc_flag not change when ===
                            io.HBM_interface.readData.ready := true.B
                            loc_flag_data_0 := 0.U
                            process_src_all := true.B
                            flag_readData_vec := 1.U
                            stateReg := receive_data

                        }
                    }
                    // flag_readData_vec = 1
                    when(io.HBM_interface.readData.bits.id === 1.U && not_full_1 && src_index_queue.io.deq.valid && flag_readData_vec === 1.U){
                        for(C_id <- 0 until asynFIFO_num){
                            readData_vec_1(C_id.U).wr_en := ~(~(unpacked_readData(C_id.U + loc_flag_data_1) =/= ~(0.U(32.W))))
                            readData_vec_1(C_id.U).din := ~(~(Cat(Fill((conf.Data_width - conf.crossbar_data_width) * 2, 0.U(1.W)), neighbour(C_id.U + loc_flag_data_1), src(C_id.U + loc_flag_data_1))))
                        }
                        when(valid_data > asynFIFO_num.U){    
                            // loc_flag not change
                            io.HBM_interface.readData.ready := false.B
                            valid_data_num := valid_data - asynFIFO_num.U // store the number of valid data
                            loc_flag_data_1 := loc_flag_data_1 + asynFIFO_num.U
                            process_src_all := false.B
                            stateReg := loop
                        }
                        when(valid_data <= asynFIFO_num.U){    
                            // loc_flag not change when ===
                            io.HBM_interface.readData.ready := true.B
                            loc_flag_data_1 := 0.U
                            process_src_all := true.B
                            flag_readData_vec := 0.U
                            stateReg := receive_data

                        }
                    }   
                }            
            }
            is(loop){
                io.HBM_interface.readData.ready := false.B
                // flag_readData_vec = 0
                when(not_full_0 && flag_readData_vec === 0.U){
                    io.HBM_interface.readData.ready := false.B
                    for(C_id <- 0 until asynFIFO_num){
                        readData_vec_0(C_id.U).wr_en := ~(~(unpacked_readData(C_id.U + loc_flag_data_0) =/= ~(0.U(32.W))))
                        readData_vec_0(C_id.U).din := ~(~(Cat(Fill((conf.Data_width - conf.crossbar_data_width) * 2, 0.U(1.W)), neighbour(C_id.U + loc_flag_data_0), src(C_id.U + loc_flag_data_0))))
                    }

                    when(valid_data_num > asynFIFO_num.U){  
                        // loc_flag not change
                        io.HBM_interface.readData.ready := false.B
                        valid_data_num := valid_data_num - asynFIFO_num.U // store the number of valid data
                        loc_flag_data_0 := loc_flag_data_0 + asynFIFO_num.U
                        process_src_all := false.B
                        stateReg := loop
                    }
                    when(valid_data_num <= asynFIFO_num.U){    
                        // loc_flag not change when ===
                        io.HBM_interface.readData.ready := true.B
                        loc_flag_data_0 := 0.U
                        process_src_all := true.B
                        flag_readData_vec := 1.U
                        stateReg := receive_data

                    }
                }
                // flag_readData_vec = 1
                when(not_full_1 && flag_readData_vec === 1.U){
                    io.HBM_interface.readData.ready := false.B
                    for(C_id <- 0 until asynFIFO_num){
                        readData_vec_1(C_id.U).wr_en := ~(~(unpacked_readData(C_id.U + loc_flag_data_1) =/= ~(0.U(32.W))))
                        readData_vec_1(C_id.U).din := ~(~(Cat(Fill((conf.Data_width - conf.crossbar_data_width) * 2, 0.U(1.W)), neighbour(C_id.U + loc_flag_data_1), src(C_id.U + loc_flag_data_1))))
                    }

                    when(valid_data_num > asynFIFO_num.U){  
                        // loc_flag not change
                        io.HBM_interface.readData.ready := false.B
                        valid_data_num := valid_data_num - asynFIFO_num.U // store the number of valid data
                        loc_flag_data_1 := loc_flag_data_1 + asynFIFO_num.U
                        process_src_all := false.B
                        stateReg := loop
                    }
                    when(valid_data_num <= asynFIFO_num.U){    
                        // loc_flag not change when ===
                        io.HBM_interface.readData.ready := true.B
                        loc_flag_data_1 := 0.U
                        process_src_all := true.B
                        flag_readData_vec := 0.U
                        stateReg := receive_data

                    }
                }
            }

        }
    }

}