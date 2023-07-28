package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._

import firrtl._
import firrtl.options.Dependency
import firrtl.transforms.DedupModules
import firrtl.annotations.NoTargetAnnotation
import firrtl.passes.PassException
import firrtl.stage.RunFirrtlTransformAnnotation

import chisel3.experimental.{annotate, ChiselAnnotation}

class Top(implicit val conf : HBMGraphConfiguration) extends Module{
	val io = IO(new Bundle{
        val ap_start_pulse = Input(Bool())
		val ap_done = Output(Bool())
        val hbm = Vec(conf.channel_num,new AXIMasterIF_reader(conf.HBM_Addr_width, conf.HBM_Data_width_to_reader,conf.memIDBits))
        val offsets = Input(new offsets)
        val levels = Input(new levels)
        val node_num = Input(UInt(conf.Data_width.W))

        val mem_clock = Input(Clock())
        val reset_h = Input(Bool()) 
        val root = Input(UInt(conf.Data_width.W))
	})
    dontTouch(io)



    val master = Module(new master)
    val SLR0_Mem = Module(new SLR0_Mem)
    // val SLR0 = Module(new SLR0)
    val SLR1 = Module(new SLR1)
    val SLR2 =  Module(new SLR2)
    val crossbar_array_mem = Module(new crossbar(is_double_width=true))
    val crossbar_array_visit = Module(new crossbar(is_double_width=false))


//----------------- count kernel time && ap_done ------------------------------
    val kernel_count = RegInit(0.U(32.W))
    kernel_count := kernel_count + 1.U
    val start_r = RegNext(io.ap_start_pulse)
    val start_pulse = io.ap_start_pulse & ~start_r
    val start_state = RegInit(false.B)
    when(start_pulse){ //io.ap_start_pulse
        kernel_count := 0.U
        start_state := true.B
    }.elsewhen(RegNext(master.io.global_finish)){
        start_state := false.B
    }

    val write_finish_vec = new Array[Bool](conf.channel_num)
    for(i <- 0 until conf.channel_num) {
        write_finish_vec(i) = SLR0_Mem.io.write_finish(i)
    }

    val master_finish_count = RegInit(0.U(8.W))
    val global_write_finish = RegNext(write_finish_vec.reduce(_&&_))

    when(global_write_finish && master_finish_count < 210.U){
        master_finish_count := master_finish_count + 1.U
    }

    when(master_finish_count>=200.U){
        io.ap_done := true.B
    }.otherwise{
        io.ap_done := false.B
    }
//----------------- count kernel time && ap_done ------------------------------


//----------------- unified signal start ------------------------------------

    val node_num_0_r = RegInit(0.U(conf.Data_width.W))
    node_num_0_r := io.node_num
    val current_level_0_r = RegInit(0.U(conf.Data_width.W))
    current_level_0_r := master.io.current_level
    val push_or_pull_0_r = RegInit(0.U(1.W))
    push_or_pull_0_r := master.io.push_or_pull

    SLR0_Mem.io.level                <> current_level_0_r 
    SLR0_Mem.io.kernel_count         <> kernel_count          
    SLR0_Mem.io.mem_clock            <> io.mem_clock      
    SLR0_Mem.io.reset_h              <> io.reset_h   
    SLR0_Mem.io.master_finish        <> RegNext(master.io.global_finish)           
    SLR0_Mem.io.node_num             <> node_num_0_r      
    SLR0_Mem.io.push_or_pull_state   <> push_or_pull_0_r              
    SLR0_Mem.io.offsets              <> RegNext(io.offsets)   
    SLR0_Mem.io.HBM_interface        <> io.hbm

    // SLR0.io.p2_end               <> RegNext(master.io.p2_end)   
    // SLR0.io.start                <> RegNext(master.io.start)   
    // SLR0.io.frontier_flag        <> RegNext(master.io.frontier_flag)           
    // SLR0.io.level                <> RegNext(master.io.current_level)   
    // SLR0.io.offsets              <> RegNext(io.offsets)   
    // SLR0.io.node_num             <> RegNext(io.node_num)       
    // SLR0.io.root                 <> RegNext(io.root)   
    // SLR0.io.push_or_pull         <> RegNext(master.io.push_or_pull)           
    // SLR0.io.start_init           <> RegNext(start_state)

    val node_num_1_r = RegInit(0.U(conf.Data_width.W))
    node_num_1_r := io.node_num
    val current_level_1_r = RegInit(0.U(conf.Data_width.W))
    current_level_1_r := master.io.current_level
    val push_or_pull_1_r = RegInit(0.U(1.W))
    push_or_pull_1_r := master.io.push_or_pull
    val frontier_flag_1_r = RegInit(0.U(1.W))
    frontier_flag_1_r := master.io.frontier_flag
    val start_1_r = RegInit(false.B)
    start_1_r := master.io.start
    val p2_end_1_r = RegInit(false.B)
    p2_end_1_r := master.io.p2_end

    SLR1.io.p2_end               <> p2_end_1_r 
    SLR1.io.start                <> start_1_r 
    SLR1.io.frontier_flag        <> frontier_flag_1_r          
    SLR1.io.level                <> current_level_1_r
    SLR1.io.offsets              <> RegNext(io.offsets)   
    SLR1.io.node_num             <> node_num_1_r      
    SLR1.io.root                 <> RegNext(io.root)   
    SLR1.io.push_or_pull         <> push_or_pull_1_r         
    SLR1.io.start_init           <> RegNext(start_state)           

    val node_num_2_r = RegInit(0.U(conf.Data_width.W))
    node_num_2_r := io.node_num
    val current_level_2_r = RegInit(0.U(conf.Data_width.W))
    current_level_2_r := master.io.current_level
    val push_or_pull_2_r = RegInit(0.U(1.W))
    push_or_pull_2_r := master.io.push_or_pull
    val frontier_flag_2_r = RegInit(0.U(1.W))
    frontier_flag_2_r := master.io.frontier_flag
    val start_2_r = RegInit(false.B)
    start_2_r := master.io.start
    val p2_end_2_r = RegInit(false.B)
    p2_end_2_r := master.io.p2_end

    SLR2.io.p2_end               <> p2_end_2_r  
    SLR2.io.start                <> start_2_r   
    SLR2.io.frontier_flag        <> frontier_flag_2_r          
    SLR2.io.level                <> current_level_2_r   
    SLR2.io.offsets              <> RegNext(io.offsets)   
    SLR2.io.node_num             <> node_num_2_r      
    SLR2.io.root                 <> RegNext(io.root)   
    SLR2.io.push_or_pull         <> push_or_pull_2_r
    SLR2.io.start_init           <> RegNext(start_state)
//----------------- unified signal end ------------------------------------


// -------------------- master io start --------------------------------------
    master.io.levels <> RegNext(io.levels)
    
    master.io.global_start := start_state

    when(start_pulse){ //io.ap_start_pulse
        kernel_count := 0.U
        start_state := true.B
    }.elsewhen(RegNext(master.io.global_finish)){
        start_state := false.B
    }

    for(i <- 0 until conf.channel_num) {
        master.io.mem_end(i)                <> RegNext(SLR0_Mem.io.mem_end(i))  
        master.io.mem_count(i)              <> RegNext(SLR0_Mem.io.neighbour_cnt(i)) 
    }
    
    // for(i <- 0 until conf.slr0_channel_num) {
    //     master.io.end(i)                    <> SLR0.io.end(i)
    //     master.io.p2_count(i)               <> SLR0.io.p2_count(i)  
    //     master.io.p2_pull_count(i)          <> SLR0.io.p2_pull_count(i)        
    //     master.io.frontier_pull_count(i)    <> SLR0.io.frontier_pull_count(i)                
    //     master.io.last_iteration_state(i)   <> SLR0.io.last_iteration(i)                
    //     master.io.init_bram(i)              <> SLR0.io.init_bram(i)
    // }

    for(i <- 0 until conf.slr1_channel_num) {
        master.io.end(i + conf.slr0_channel_num)                    <> RegNext(SLR1.io.end(i))
        master.io.p2_count(i + conf.slr0_channel_num)               <> RegNext(SLR1.io.p2_count(i))  
        master.io.p2_pull_count(i + conf.slr0_channel_num)          <> RegNext(SLR1.io.p2_pull_count(i))        
        master.io.frontier_pull_count(i + conf.slr0_channel_num)    <> RegNext(SLR1.io.frontier_pull_count(i))                
        master.io.last_iteration_state(i + conf.slr0_channel_num)   <> RegNext(SLR1.io.last_iteration(i))                
        master.io.init_bram(i + conf.slr0_channel_num)              <> RegNext(SLR1.io.init_bram(i))
    }
    for(i <- 0 until conf.slr2_channel_num) {
        master.io.end(i + conf.slr0_channel_num + conf.slr1_channel_num)                    <> RegNext(SLR2.io.end(i))
        master.io.p2_count(i + conf.slr0_channel_num + conf.slr1_channel_num)               <> RegNext(SLR2.io.p2_count(i))  
        master.io.p2_pull_count(i + conf.slr0_channel_num + conf.slr1_channel_num)          <> RegNext(SLR2.io.p2_pull_count(i))        
        master.io.frontier_pull_count(i + conf.slr0_channel_num + conf.slr1_channel_num)    <> RegNext(SLR2.io.frontier_pull_count(i))                
        master.io.last_iteration_state(i + conf.slr0_channel_num + conf.slr1_channel_num)   <> RegNext(SLR2.io.last_iteration(i))                
        master.io.init_bram(i + conf.slr0_channel_num + conf.slr1_channel_num)              <> RegNext(SLR2.io.init_bram(i))
    }
// -------------------- master io end --------------------------------------

// --------------------- SLR0_Mem <> SLR0 ----------------------------------
    // for(i <- 0 until conf.slr0_channel_num) {
    //     SLR0_Mem.io.p1_end(i)           <> RegNext(SLR0.io.p1_end(i))
    // }
    // for(i <- 0 until conf.slr0_channel_num * conf.pipe_num_per_channel) {
    //     val tmp_q_r_array_00 = Queue(SLR0.io.R_array_index(i), conf.cross_slr_len)
    //     SLR0_Mem.io.R_array_index(i)    <> tmp_q_r_array_00
    //     // SLR0_Mem.io.R_array_index(i)    <> SLR0.io.R_array_index(i)        
    //     SLR0_Mem.io.uram_out_a(i)       <> RegNext(SLR0.io.uram_out_a(i))    
    //     RegNext(SLR0_Mem.io.uram_addr_a(i))      <> SLR0.io.uram_addr_a(i)    
    // }
// --------------------- SLR0_Mem <> SLR0 ----------------------------------

// --------------------- SLR0_Mem <> SLR1 ----------------------------------
    val p1_end_q_01_slr0 = RegInit(VecInit(Seq.fill(conf.slr1_channel_num)(false.B)))
    val p1_end_q_01_slr1 = RegInit(VecInit(Seq.fill(conf.slr1_channel_num)(false.B)))

    for(i <- 0 until conf.slr1_channel_num) {
        p1_end_q_01_slr1(i) := SLR1.io.p1_end(i)
        p1_end_q_01_slr0(i) := p1_end_q_01_slr1(i)
        SLR0_Mem.io.p1_end(i + conf.slr0_channel_num) := p1_end_q_01_slr0(i)
    }

    // 为了与SLR2-SLR0之间的3层缓冲保持一致，SLR1与SLR0之间的连接也用3层
    val uram_out_a_01_slr0_1 = RegInit(VecInit(Seq.fill(conf.slr1_channel_num * conf.pipe_num_per_channel)(0.U(conf.Data_width_uram.W))))
    val uram_out_a_01_slr0_2 = RegInit(VecInit(Seq.fill(conf.slr1_channel_num * conf.pipe_num_per_channel)(0.U(conf.Data_width_uram.W))))
    val uram_out_a_01_slr1 = RegInit(VecInit(Seq.fill(conf.slr1_channel_num * conf.pipe_num_per_channel)(0.U(conf.Data_width_uram.W))))
    val uram_addr_a_01_slr0_1 = RegInit(VecInit(Seq.fill(conf.slr1_channel_num * conf.pipe_num_per_channel)(0.U(conf.Data_width_uram.W))))
    val uram_addr_a_01_slr0_2 = RegInit(VecInit(Seq.fill(conf.slr1_channel_num * conf.pipe_num_per_channel)(0.U(conf.Data_width_uram.W))))
    val uram_addr_a_01_slr1 = RegInit(VecInit(Seq.fill(conf.slr1_channel_num * conf.pipe_num_per_channel)(0.U(conf.Data_width_uram.W))))

    for(i <- 0 until conf.slr1_channel_num * conf.pipe_num_per_channel) {
        uram_out_a_01_slr1(i)   := SLR1.io.uram_out_a(i)
        uram_out_a_01_slr0_2(i) := uram_out_a_01_slr1(i)
        uram_out_a_01_slr0_1(i)   := uram_out_a_01_slr0_2(i)
        SLR0_Mem.io.uram_out_a(i + conf.slr0_channel_num * conf.pipe_num_per_channel) := uram_out_a_01_slr0_1(i)

        uram_addr_a_01_slr0_1(i)  := SLR0_Mem.io.uram_addr_a(i + conf.slr0_channel_num * conf.pipe_num_per_channel)
        uram_addr_a_01_slr0_2(i):= uram_addr_a_01_slr0_1(i)
        uram_addr_a_01_slr1(i)  := uram_addr_a_01_slr0_2(i)
        SLR1.io.uram_addr_a(i)  := uram_addr_a_01_slr1(i)
    }

    val R_array_index_01_slr0 = Array.ofDim[syn_fifo](conf.slr1_channel_num * conf.pipe_num_per_channel)
    val R_array_index_01_slr1 = Array.ofDim[syn_fifo](conf.slr1_channel_num * conf.pipe_num_per_channel)
    val R_array_index_01_slr0_q = Array.ofDim[q_R_array_index_slr0[UInt]](conf.slr1_channel_num * conf.pipe_num_per_channel)

    for(i <- 0 until conf.slr1_channel_num * conf.pipe_num_per_channel) {
        R_array_index_01_slr0(i) = Module(new syn_fifo(0))
        R_array_index_01_slr1(i) = Module(new syn_fifo(1))
        R_array_index_01_slr0_q(i) = Module(new q_R_array_index_slr0(UInt(conf.Data_width.W), conf.cross_slr_len))

        
        R_array_index_01_slr0(i).io.clock := clock
        R_array_index_01_slr1(i).io.clock := clock
        R_array_index_01_slr0(i).io.reset := reset//_reg
        R_array_index_01_slr1(i).io.reset := reset//_reg
    }
    for(i <- 0 until conf.slr1_channel_num * conf.pipe_num_per_channel) {

        // SLR0_Mem.io.R_array_index(i + conf.slr0_channel_num * conf.pipe_num_per_channel) <>  SLR1.io.R_array_index(i)
        R_array_index_01_slr1(i).io.enq_valid <> SLR1.io.R_array_index(i).valid
        R_array_index_01_slr1(i).io.enq_ready <> SLR1.io.R_array_index(i).ready
        R_array_index_01_slr1(i).io.enq_bits  <> SLR1.io.R_array_index(i).bits

        R_array_index_01_slr0(i).io.enq_valid <> RegNext(R_array_index_01_slr1(i).io.deq_valid)
        R_array_index_01_slr0(i).io.enq_ready <> R_array_index_01_slr1(i).io.deq_ready
        R_array_index_01_slr0(i).io.enq_bits  <> R_array_index_01_slr1(i).io.deq_bits 

        R_array_index_01_slr0_q(i).io.enq.valid <> RegNext(R_array_index_01_slr0(i).io.deq_valid)
        (R_array_index_01_slr0_q(i).io.count < conf.cross_slr_len.U - 1.U)  <> R_array_index_01_slr0(i).io.deq_ready
        R_array_index_01_slr0_q(i).io.enq.bits  <> R_array_index_01_slr0(i).io.deq_bits 

        SLR0_Mem.io.R_array_index(i + conf.slr0_channel_num * conf.pipe_num_per_channel) <> R_array_index_01_slr0_q(i).io.deq

        // SLR0_Mem.io.R_array_index(i + conf.slr0_channel_num * conf.pipe_num_per_channel).valid <> RegNext(R_array_index_01_slr0(i).io.deq_valid)
        // SLR0_Mem.io.R_array_index(i + conf.slr0_channel_num * conf.pipe_num_per_channel).ready <> R_array_index_01_slr0(i).io.deq_ready
        // SLR0_Mem.io.R_array_index(i + conf.slr0_channel_num * conf.pipe_num_per_channel).bits  <> R_array_index_01_slr0(i).io.deq_bits

        // R_array_index_01_slr1(i).io.enq <> SLR1.io.R_array_index(i)
        // R_array_index_01_slr0(i).io.enq <> R_array_index_01_slr1(i).io.deq
        // SLR0_Mem.io.R_array_index(i + conf.slr0_channel_num * conf.pipe_num_per_channel) <> R_array_index_01_slr0(i).io.deq
    }

    val r_array_count = RegInit(VecInit(Seq.fill(conf.slr1_channel_num * conf.pipe_num_per_channel)(0.U(conf.Data_width.W))))
    val r_array_count_total = r_array_count.reduce(_+_)
    for(i <- 0 until conf.slr1_channel_num * conf.pipe_num_per_channel) {
        when(SLR0_Mem.io.R_array_index(i + conf.slr0_channel_num * conf.pipe_num_per_channel).valid && SLR0_Mem.io.R_array_index(i + conf.slr0_channel_num * conf.pipe_num_per_channel).ready){
            r_array_count(i) := r_array_count(i) + 1.U
        }
    }
    // for(i <- 0 until conf.slr1_channel_num) {
    //     SLR0_Mem.io.p1_end(i + conf.slr0_channel_num)                                       <> RegNext(SLR1.io.p1_end(i))
    // }
    // for(i <- 0 until conf.slr1_channel_num * conf.pipe_num_per_channel) {
    //     val tmp_q_r_array_01 = Queue(SLR1.io.R_array_index(i), conf.cross_slr_len)
    //     SLR0_Mem.io.R_array_index(i + conf.slr0_channel_num * conf.pipe_num_per_channel)    <> tmp_q_r_array_01      
    //     SLR0_Mem.io.uram_out_a(i + conf.slr0_channel_num * conf.pipe_num_per_channel)       <> RegNext(SLR1.io.uram_out_a(i))    
    //     RegNext(SLR0_Mem.io.uram_addr_a(i + conf.slr0_channel_num * conf.pipe_num_per_channel))      <> SLR1.io.uram_addr_a(i)    
    // }
// --------------------- SLR0_Mem <> SLR1 ----------------------------------

// --------------------- SLR0_Mem <> SLR2 ----------------------------------
    val p1_end_q_02_slr0 = RegInit(VecInit(Seq.fill(conf.slr2_channel_num)(false.B)))
    val p1_end_q_02_slr1 = RegInit(VecInit(Seq.fill(conf.slr2_channel_num)(false.B)))
    val p1_end_q_02_slr2 = RegInit(VecInit(Seq.fill(conf.slr2_channel_num)(false.B)))

    for(i <- 0 until conf.slr2_channel_num) {
        p1_end_q_02_slr2(i) := SLR2.io.p1_end(i)
        p1_end_q_02_slr1(i) := p1_end_q_02_slr2(i)
        p1_end_q_02_slr0(i) := p1_end_q_02_slr1(i)
        SLR0_Mem.io.p1_end(i + conf.slr0_channel_num + conf.slr1_channel_num) := p1_end_q_02_slr0(i)
        // SLR0_Mem.io.p1_end(i + conf.slr0_channel_num + conf.slr1_channel_num)               <> RegNext(SLR2.io.p1_end(i))
    }

    val uram_out_a_02_slr0 = RegInit(VecInit(Seq.fill(conf.slr2_channel_num * conf.pipe_num_per_channel)(0.U(conf.Data_width_uram.W))))
    val uram_out_a_02_slr1 = RegInit(VecInit(Seq.fill(conf.slr2_channel_num * conf.pipe_num_per_channel)(0.U(conf.Data_width_uram.W))))
    val uram_out_a_02_slr2 = RegInit(VecInit(Seq.fill(conf.slr2_channel_num * conf.pipe_num_per_channel)(0.U(conf.Data_width_uram.W))))
    val uram_addr_a_02_slr0 = RegInit(VecInit(Seq.fill(conf.slr2_channel_num * conf.pipe_num_per_channel)(0.U(conf.Data_width_uram.W))))
    val uram_addr_a_02_slr1 = RegInit(VecInit(Seq.fill(conf.slr2_channel_num * conf.pipe_num_per_channel)(0.U(conf.Data_width_uram.W))))
    val uram_addr_a_02_slr2 = RegInit(VecInit(Seq.fill(conf.slr2_channel_num * conf.pipe_num_per_channel)(0.U(conf.Data_width_uram.W))))

    for(i <- 0 until conf.slr2_channel_num * conf.pipe_num_per_channel) {
        uram_out_a_02_slr2(i) := SLR2.io.uram_out_a(i)
        uram_out_a_02_slr1(i) := uram_out_a_02_slr2(i)
        uram_out_a_02_slr0(i) := uram_out_a_02_slr1(i)
        SLR0_Mem.io.uram_out_a(i + (conf.slr0_channel_num + conf.slr1_channel_num) * conf.pipe_num_per_channel) := uram_out_a_02_slr0(i)

        uram_addr_a_02_slr0(i) := SLR0_Mem.io.uram_addr_a(i + (conf.slr0_channel_num + conf.slr1_channel_num) * conf.pipe_num_per_channel)
        uram_addr_a_02_slr1(i) := uram_addr_a_02_slr0(i)
        uram_addr_a_02_slr2(i) := uram_addr_a_02_slr1(i)
        SLR2.io.uram_addr_a(i) := uram_addr_a_02_slr2(i)
    }

    val R_array_index_02_slr0 = Array.ofDim[syn_fifo](conf.slr2_channel_num * conf.pipe_num_per_channel)
    val R_array_index_02_slr1 = Array.ofDim[syn_fifo](conf.slr2_channel_num * conf.pipe_num_per_channel)
    val R_array_index_02_slr2 = Array.ofDim[syn_fifo](conf.slr2_channel_num * conf.pipe_num_per_channel)
    val R_array_index_02_slr0_q = Array.ofDim[q_R_array_index_slr0[UInt]](conf.slr2_channel_num * conf.pipe_num_per_channel)

    for(i <- 0 until conf.slr2_channel_num * conf.pipe_num_per_channel) {
        R_array_index_02_slr0(i) = Module(new syn_fifo(2))
        R_array_index_02_slr1(i) = Module(new syn_fifo(3))
        R_array_index_02_slr2(i) = Module(new syn_fifo(4))

        R_array_index_02_slr0(i).io.clock := clock
        R_array_index_02_slr1(i).io.clock := clock
        R_array_index_02_slr2(i).io.clock := clock
        R_array_index_02_slr0(i).io.reset := reset//_reg
        R_array_index_02_slr1(i).io.reset := reset//_reg
        R_array_index_02_slr2(i).io.reset := reset//_reg

        R_array_index_02_slr0_q(i) = Module(new q_R_array_index_slr0(UInt(conf.Data_width.W), conf.cross_slr_len))
    }
    for(i <- 0 until conf.slr2_channel_num * conf.pipe_num_per_channel) {
        // SLR0_Mem.io.R_array_index(i + (conf.slr0_channel_num + conf.slr1_channel_num) * conf.pipe_num_per_channel) <> SLR2.io.R_array_index(i)
        R_array_index_02_slr2(i).io.enq_valid <> SLR2.io.R_array_index(i).valid
        R_array_index_02_slr2(i).io.enq_ready <> SLR2.io.R_array_index(i).ready
        R_array_index_02_slr2(i).io.enq_bits  <> SLR2.io.R_array_index(i).bits

        R_array_index_02_slr1(i).io.enq_valid <> RegNext(R_array_index_02_slr2(i).io.deq_valid) 
        R_array_index_02_slr1(i).io.enq_ready <> R_array_index_02_slr2(i).io.deq_ready 
        R_array_index_02_slr1(i).io.enq_bits  <> R_array_index_02_slr2(i).io.deq_bits  
        
        R_array_index_02_slr0(i).io.enq_valid <> RegNext(R_array_index_02_slr1(i).io.deq_valid) 
        R_array_index_02_slr0(i).io.enq_ready <> R_array_index_02_slr1(i).io.deq_ready 
        R_array_index_02_slr0(i).io.enq_bits  <> R_array_index_02_slr1(i).io.deq_bits  

        R_array_index_02_slr0_q(i).io.enq.valid <> RegNext(R_array_index_02_slr0(i).io.deq_valid) 
        (R_array_index_02_slr0_q(i).io.count < conf.cross_slr_len.U - 1.U) <> R_array_index_02_slr0(i).io.deq_ready 
        R_array_index_02_slr0_q(i).io.enq.bits  <> R_array_index_02_slr0(i).io.deq_bits  

        SLR0_Mem.io.R_array_index(i + (conf.slr0_channel_num + conf.slr1_channel_num) * conf.pipe_num_per_channel) <> R_array_index_02_slr0_q(i).io.deq

        // SLR0_Mem.io.R_array_index(i + (conf.slr0_channel_num + conf.slr1_channel_num) * conf.pipe_num_per_channel).valid  <> RegNext(R_array_index_02_slr0(i).io.deq_valid) 
        // SLR0_Mem.io.R_array_index(i + (conf.slr0_channel_num + conf.slr1_channel_num) * conf.pipe_num_per_channel).ready  <> R_array_index_02_slr0(i).io.deq_ready 
        // SLR0_Mem.io.R_array_index(i + (conf.slr0_channel_num + conf.slr1_channel_num) * conf.pipe_num_per_channel).bits   <> R_array_index_02_slr0(i).io.deq_bits  
    }

    val r_array_count_2 = RegInit(VecInit(Seq.fill(conf.slr2_channel_num * conf.pipe_num_per_channel)(0.U(conf.Data_width.W))))
    val r_array_count_total_2 = r_array_count_2.reduce(_+_)
    for(i <- 0 until conf.slr2_channel_num * conf.pipe_num_per_channel) {
        when(SLR0_Mem.io.R_array_index(i + (conf.slr0_channel_num + conf.slr1_channel_num) * conf.pipe_num_per_channel).valid && SLR0_Mem.io.R_array_index(i + (conf.slr0_channel_num + conf.slr1_channel_num) * conf.pipe_num_per_channel).ready){
            r_array_count_2(i) := r_array_count_2(i) + 1.U
        }
    }
    // for(i <- 0 until conf.slr2_channel_num) {
    //     SLR0_Mem.io.p1_end(i + conf.slr0_channel_num + conf.slr1_channel_num)               <> RegNext(SLR2.io.p1_end(i))
    // }
    // for(i <- 0 until conf.slr2_channel_num * conf.pipe_num_per_channel) {
    //     val tmp_q_r_array_02 = Queue(SLR2.io.R_array_index(i), conf.cross_slr_len)
    //     SLR0_Mem.io.R_array_index(i + (conf.slr0_channel_num + conf.slr1_channel_num) * conf.pipe_num_per_channel)    <> tmp_q_r_array_02
    //     // SLR0_Mem.io.R_array_index(i + (conf.slr0_channel_num + conf.slr1_channel_num) * conf.pipe_num_per_channel)    <> SLR2.io.R_array_index(i)        
    //     SLR0_Mem.io.uram_out_a(i + (conf.slr0_channel_num + conf.slr1_channel_num) * conf.pipe_num_per_channel)       <> RegNext(SLR2.io.uram_out_a(i))    
    //     RegNext(SLR0_Mem.io.uram_addr_a(i + (conf.slr0_channel_num + conf.slr1_channel_num) * conf.pipe_num_per_channel))      <> SLR2.io.uram_addr_a(i)    
    // }
// --------------------- SLR0_Mem <> SLR2 ----------------------------------


// --------------------- crossbar start ----------------------------------

    for(i <- 0 until conf.numSubGraphs){
        crossbar_array_mem.io.in(i)  <> SLR0_Mem.io.neighbours(i)
    }
    // for(i <- 0 until conf.channel_num){
    //     for(j <- 0 until conf.pipe_num_per_channel){
    //         crossbar_array_mem.io.in(j * conf.channel_num + i)  <> SLR0_Mem.io.neighbours(j * conf.channel_num + i)
    //     }
    // }

    // for(i <- 0 until conf.slr0_channel_num){
    //     for(j <- 0 until conf.pipe_num_per_channel){
    //         crossbar_array_mem.io.out(j * conf.channel_num + i)  <> SLR0.io.p2_in(i * conf.pipe_num_per_channel + j)
    //         when(RegNext(master.io.push_or_pull) === 0.U){
    //             crossbar_array_visit.io.in(j * conf.channel_num + i)  <> DontCare
    //             crossbar_array_visit.io.out(j * conf.channel_num + i) <> DontCare
    //             crossbar_array_visit.io.in(j * conf.channel_num + i).valid := false.B
    //             SLR0.io.p2_out(i * conf.pipe_num_per_channel + j)    <> SLR0.io.frontier_in(i * conf.pipe_num_per_channel + j)
    //         }
    //         .otherwise {                                    // pull mode
    //             crossbar_array_visit.io.in(j * conf.channel_num + i)  <> SLR0.io.p2_out(i * conf.pipe_num_per_channel + j)
    //             crossbar_array_visit.io.out(j * conf.channel_num + i) <> SLR0.io.frontier_in(i * conf.pipe_num_per_channel + j)
    //         }
    //     }
    // }

    // // for(i <- 0 until conf.slr0_channel_num * conf.pipe_num_per_channel) {
    // //     // crossbar_array_mem.io.out(i) <> SLR0.io.p2_in(i)
    // //     when(RegNext(master.io.push_or_pull) === 0.U){
    // //         crossbar_array_visit.io.in(i)  <> DontCare
    // //         crossbar_array_visit.io.out(i) <> DontCare
    // //         crossbar_array_visit.io.in(i).valid := false.B
    // //         SLR0.io.p2_out(i)    <> SLR0.io.frontier_in(i)
    // //     }
    // //     .otherwise {                                    // pull mode
    // //         crossbar_array_visit.io.in(i)  <> SLR0.io.p2_out(i)
    // //         crossbar_array_visit.io.out(i) <> SLR0.io.frontier_in(i)
    // //     }
    // // }


    for(i <- conf.slr0_channel_num until conf.slr1_channel_num + conf.slr0_channel_num){
        for(j <- 0 until conf.pipe_num_per_channel){
            crossbar_array_mem.io.out(j * conf.channel_num + i)  <> SLR1.io.p2_in((i - conf.slr0_channel_num) * conf.pipe_num_per_channel + j)
            when(RegNext(master.io.push_or_pull) === 0.U){
                crossbar_array_visit.io.in(j * conf.channel_num + i)  <> DontCare
                crossbar_array_visit.io.out(j * conf.channel_num + i) <> DontCare
                crossbar_array_visit.io.in(j * conf.channel_num + i).valid := false.B
                SLR1.io.p2_out((i - conf.slr0_channel_num) * conf.pipe_num_per_channel + j)    <> SLR1.io.frontier_in((i - conf.slr0_channel_num) * conf.pipe_num_per_channel + j)
            }
            .otherwise {                                    // pull mode
                crossbar_array_visit.io.in(j * conf.channel_num + i)  <> SLR1.io.p2_out((i - conf.slr0_channel_num) * conf.pipe_num_per_channel + j)
                crossbar_array_visit.io.out(j * conf.channel_num + i) <> SLR1.io.frontier_in((i - conf.slr0_channel_num) * conf.pipe_num_per_channel + j)
            }
        }
    }

    // for(i <- 0 until conf.slr1_channel_num * conf.pipe_num_per_channel) {
    //     // crossbar_array_mem.io.out(i + conf.slr0_channel_num * conf.pipe_num_per_channel) <> SLR1.io.p2_in(i)
    //     when(RegNext(master.io.push_or_pull) === 0.U){
    //         crossbar_array_visit.io.in(i + conf.slr0_channel_num * conf.pipe_num_per_channel)  <> DontCare
    //         crossbar_array_visit.io.out(i + conf.slr0_channel_num * conf.pipe_num_per_channel) <> DontCare
    //         crossbar_array_visit.io.in(i + conf.slr0_channel_num * conf.pipe_num_per_channel).valid := false.B
    //         SLR1.io.p2_out(i)    <> SLR1.io.frontier_in(i)
    //     }
    //     .otherwise {                                    // pull mode
    //         crossbar_array_visit.io.in(i + conf.slr0_channel_num * conf.pipe_num_per_channel)  <> SLR1.io.p2_out(i)
    //         crossbar_array_visit.io.out(i + conf.slr0_channel_num * conf.pipe_num_per_channel) <> SLR1.io.frontier_in(i)
    //     }
    // }

    for(i <- conf.slr1_channel_num + conf.slr0_channel_num until conf.slr2_channel_num + conf.slr1_channel_num + conf.slr0_channel_num){
        for(j <- 0 until conf.pipe_num_per_channel){
            crossbar_array_mem.io.out(j * conf.channel_num + i)  <> SLR2.io.p2_in((i - conf.slr0_channel_num - conf.slr1_channel_num) * conf.pipe_num_per_channel + j)
            when(RegNext(master.io.push_or_pull) === 0.U){
                crossbar_array_visit.io.in(j * conf.channel_num + i)  <> DontCare
                crossbar_array_visit.io.out(j * conf.channel_num + i) <> DontCare
                crossbar_array_visit.io.in(j * conf.channel_num + i).valid := false.B
                SLR2.io.p2_out((i - conf.slr0_channel_num - conf.slr1_channel_num) * conf.pipe_num_per_channel + j)    <> SLR2.io.frontier_in((i - conf.slr0_channel_num - conf.slr1_channel_num) * conf.pipe_num_per_channel + j)
            }
            .otherwise {                                    // pull mode
                crossbar_array_visit.io.in(j * conf.channel_num + i)  <> SLR2.io.p2_out((i - conf.slr0_channel_num - conf.slr1_channel_num) * conf.pipe_num_per_channel + j)
                crossbar_array_visit.io.out(j * conf.channel_num + i) <> SLR2.io.frontier_in((i - conf.slr0_channel_num - conf.slr1_channel_num) * conf.pipe_num_per_channel + j)
            }
        }
    }

    // for(i <- 0 until conf.slr2_channel_num * conf.pipe_num_per_channel) {
    //     // crossbar_array_mem.io.out(i + (conf.slr0_channel_num + conf.slr1_channel_num) * conf.pipe_num_per_channel) <> SLR2.io.p2_in(i)
    //     when(RegNext(master.io.push_or_pull) === 0.U){
    //         crossbar_array_visit.io.in(i + (conf.slr0_channel_num + conf.slr1_channel_num) * conf.pipe_num_per_channel)  <> DontCare
    //         crossbar_array_visit.io.out(i + (conf.slr0_channel_num + conf.slr1_channel_num) * conf.pipe_num_per_channel) <> DontCare
    //         crossbar_array_visit.io.in(i + (conf.slr0_channel_num + conf.slr1_channel_num) * conf.pipe_num_per_channel).valid := false.B
    //         SLR2.io.p2_out(i)    <> SLR2.io.frontier_in(i)
    //     }
    //     .otherwise {                                    // pull mode
    //         crossbar_array_visit.io.in(i + (conf.slr0_channel_num + conf.slr1_channel_num) * conf.pipe_num_per_channel)  <> SLR2.io.p2_out(i)
    //         crossbar_array_visit.io.out(i + (conf.slr0_channel_num + conf.slr1_channel_num) * conf.pipe_num_per_channel) <> SLR2.io.frontier_in(i)
    //     }
    // }
// --------------------- crossbar end ----------------------------------


}


object Top extends App{
    implicit val configuration = HBMGraphConfiguration()
    override val args = Array("-o", "Top.v",
                 "-X", "verilog",
                 "--no-dce",
                 "--info-mode=ignore"
                 )
    chisel3.Driver.execute(args, () => new Top)
    //chisel3.Driver.execute(Array[String](), () => new Top())
}


