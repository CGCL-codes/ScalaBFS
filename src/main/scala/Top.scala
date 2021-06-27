package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._

class offsets (implicit val conf : HBMGraphConfiguration) extends Bundle{
    val CSR_R_offset = Input(UInt(conf.Data_width.W))  // input R_offset in 64 bits (constant in one iter)
    val CSR_C_offset = Input(UInt(conf.Data_width.W))  // input C_offset in 64 bits (constant in one iter)
    val CSC_R_offset = Input(UInt(conf.Data_width.W))  // input R_offset in 64 bits (constant in one iter)
    val CSC_C_offset = Input(UInt(conf.Data_width.W))  // input C_offset in 64 bits (constant in one iter)
    val level_offset = Input(UInt(conf.Data_width.W))      // input level_offset (constant in one iter)
}

class levels (implicit val conf : HBMGraphConfiguration) extends Bundle{
    val push_to_pull_level = Input(UInt(conf.Data_width.W))  // input push_to_pull_level in 64 bits (constant in one iter)
    val pull_to_push_level = Input(UInt(conf.Data_width.W))  // input pull_to_push_level in 64 bits (constant in one iter)
}

class Top(implicit val conf : HBMGraphConfiguration) extends Module{
	val io = IO(new Bundle{
        val ap_start_pulse = Input(Bool())
		val ap_done = Output(Bool())
        val hbm = Vec(conf.channel_num,new AXIMasterIF(conf.HBM_Addr_width, conf.HBM_Data_width,conf.memIDBits))
        val offsets = Input(new offsets)
        val levels = Input(new levels)
        val node_num = Input(UInt(conf.Data_width.W))
        val if_write = Input(UInt(32.W))
	})
    dontTouch(io)
    //count kernel time
    val kernel_count = RegInit(0.U(32.W))
    kernel_count := kernel_count + 1.U
    val reset_reg = RegNext(reset)
    val reset_reg_array = VecInit(Seq.fill(conf.channel_num)(RegNext(reset))) //new Array[Reset](conf.channel_num)
    // for(i <- 0 until conf.channel_num){
    //     reset_reg_array(i) := RegNext(reset)
    // }
    val clk_wiz = withReset(reset_reg){Module(new clk_wiz_0)}
    clk_wiz.io.clock := clock
    

    val pipeline_array = new Array[pipeline](conf.channel_num)
    val write_finish_vec = new Array[Bool](conf.channel_num)
    for(i <- 0 until conf.channel_num) {
        pipeline_array(i) = withReset(reset_reg_array(i)){Module(new pipeline(i))}
    }


    val if_write_state = withReset(reset_reg){RegInit(false.B)}
    val master = withReset(reset_reg){Module(new master)}
    master.io.levels <> io.levels
    val crossbar_array_mem = new Array[crossbar](2)
    val crossbar_array_visit = new Array[crossbar](2)


    val copy_start = Module(new copy(1))
    val copy_global_finish = Module(new copy(1))
    val copy_frontier_flag = Module(new copy(1))
    val copy_current_level = Module(new copy(32))
    val copy_p2_end = Module(new copy(1))
    val copy_push_or_pull = Module(new copy(1))
    copy_start.io.in := master.io.start
    copy_global_finish.io.in := master.io.global_finish
    copy_frontier_flag.io.in := master.io.frontier_flag
    copy_current_level.io.in := master.io.current_level
    copy_p2_end.io.in := master.io.p2_end
    copy_push_or_pull.io.in := master.io.push_or_pull

    when(io.if_write===1.U){
      if_write_state := true.B
    }.otherwise{
      if_write_state := false.B
    }
    for(i <- 0 until 2){
        crossbar_array_mem(i) = withReset(reset_reg){Module(new crossbar(is_double_width=true))}
        crossbar_array_visit(i) = withReset(reset_reg){Module(new crossbar(is_double_width=false))}
    }


    for(i <- 0 until conf.channel_num) {
        io.hbm(i)<>pipeline_array(i).io.axiport
        pipeline_array(i).io.bram_clock<>clk_wiz.io.clk_bram

        master.io.mem_end(i) := pipeline_array(i).io.mem_end
        master.io.end(i) := pipeline_array(i).io.end
        master.io.p2_count(i) := pipeline_array(i).io.p2_count
        master.io.mem_count(i) := pipeline_array(i).io.mem_count
        master.io.p2_pull_count(i) := pipeline_array(i).io.p2_pull_count
        master.io.frontier_pull_count(i) := pipeline_array(i).io.frontier_pull_count
        master.io.last_iteration_state(i) := pipeline_array(i).io.last_iteration
        //kernel count
        pipeline_array(i).io.kernel_count := kernel_count
        pipeline_array(i).io.master_finish := copy_global_finish.io.out(i)
        
        pipeline_array(i).io.p2_end := copy_p2_end.io.out(i)
        pipeline_array(i).io.start := copy_start.io.out(i)
        pipeline_array(i).io.frontier_flag := copy_frontier_flag.io.out(i)
        pipeline_array(i).io.level := copy_current_level.io.out(i)
        pipeline_array(i).io.offsets <> io.offsets
        // pipeline_array(i).io.node_num <> (io.node_num + conf.numSubGraphs.U - 1.U - i.U) / conf.numSubGraphs.U
        pipeline_array(i).io.node_num <> io.node_num
        pipeline_array(i).io.push_or_pull := copy_push_or_pull.io.out(i)     // pure pull
        pipeline_array(i).io.if_write := if_write_state  // pure pull
        write_finish_vec(i) = pipeline_array(i).io.write_finish

        
        for(j <- 0 until 2){
            for(k <- 0 until conf.pipe_num_per_channel){
                crossbar_array_mem(j).io.in(conf.channel_num * k + i)  <> pipeline_array(i).io.mem_out(2 * k + j)
                crossbar_array_mem(j).io.out(conf.channel_num * k + i) <> pipeline_array(i).io.p2_in(2 * k + j)
            
            // crossbar_array_mem(j).io.in(i)  <> pipeline_array(i).io.mem_out(j)
            // crossbar_array_mem(j).io.out(i) <> pipeline_array(i).io.p2_in(j)  
            // TODO
                when(RegNext(master.io.push_or_pull) === 0.U){ // push mode
                    crossbar_array_visit(j).io.in(conf.channel_num * k + i)  <> DontCare
                    crossbar_array_visit(j).io.out(conf.channel_num * k + i) <> DontCare
                    crossbar_array_visit(j).io.in(conf.channel_num * k + i).valid := false.B
                    pipeline_array(i).io.p2_out(2 * k + j)    <> pipeline_array(i).io.frontier_in(2 * k + j)
                }.otherwise {                                    // pull mode
                    crossbar_array_visit(j).io.in(conf.channel_num * k + i)  <> pipeline_array(i).io.p2_out(2 * k + j)
                    crossbar_array_visit(j).io.out(conf.channel_num * k + i) <> pipeline_array(i).io.frontier_in(2 * k + j)
                } 
            }
        }
    }

    val start_state = withReset(reset_reg){RegInit(false.B)}
    master.io.global_start := start_state
    val master_finish_count = RegInit(0.U(8.W))
    val global_write_finish = RegNext(write_finish_vec.reduce(_&&_))
    when(global_write_finish){// && master_finish_count < 210.U){
        master_finish_count := master_finish_count + 1.U
    }

    when(master_finish_count>=200.U){
        io.ap_done := true.B
    }.otherwise{
        io.ap_done := false.B
    }

    when(io.ap_start_pulse){
        kernel_count := 0.U
        start_state := true.B
    }.elsewhen(RegNext(master.io.global_finish)){
        start_state := false.B
    }
}

class pipeline(val num: Int)(implicit val conf : HBMGraphConfiguration) extends Module{
	val io = IO(new Bundle{

        val bram_clock = Input(Clock())
        //kernel count reg
        val kernel_count = Input(UInt(32.W))
        val master_finish = Input(Bool())
        //frontier io
        val frontier_flag = Input(UInt(1.W))
        val p2_end = Input(Bool())
        val start = Input(Bool())
        val end = Output(Bool())
        val last_iteration = Output(Bool())
        //p2 io
        val p2_count = Output(UInt(conf.Data_width.W))
        //mem io
        val axiport = new AXIMasterIF(conf.HBM_Addr_width, conf.HBM_Data_width,conf.memIDBits)
        val mem_count =Output(UInt(conf.Data_width.W))
        val frontier_pull_count = Output(UInt(conf.Data_width.W))
        val p2_pull_count = Output(UInt(conf.Data_width.W))
        val mem_end = Output(Bool())
        val level = Input(UInt(conf.Data_width.W))
        val offsets = Input(new offsets)
        val write_finish = Output(Bool())

        //crossbar io
        // TODO
        //mem <> p2
        val p2_in = Vec(2 * conf.pipe_num_per_channel, Flipped(Decoupled(UInt((conf.Data_width*2).W))))
        val mem_out = Vec(2 * conf.pipe_num_per_channel, Decoupled(UInt((conf.Data_width*2).W)))
        // p2 <> frontier
        val frontier_in = Vec(2 * conf.pipe_num_per_channel, Flipped(Decoupled(UInt(conf.Data_width.W))))
        val p2_out = Vec(2 * conf.pipe_num_per_channel, Decoupled(UInt(conf.Data_width.W)))

        // parameter
        val node_num = Input(UInt(conf.Data_width.W))
        val push_or_pull = Input(UInt(1.W))      //input flag mark push or pull state
        val if_write = Input(Bool())


	})
    
    val memory = Module(new Memory(num))
    val p1 = new Array[P1](conf.pipe_num_per_channel)
    val p2 = new Array[P2](conf.pipe_num_per_channel)
    val frontier = new Array[Frontier](conf.pipe_num_per_channel)

    val frontier_end_vec = Array.ofDim[Bool](conf.pipe_num_per_channel)
    val last_iteration_vec = Array.ofDim[Bool](conf.pipe_num_per_channel)
    val p2_count_vec = Array.ofDim[UInt](conf.pipe_num_per_channel)
    val p1_end_vec = Array.ofDim[Bool](conf.pipe_num_per_channel)
    val frontier_pull_count_vec = Array.ofDim[UInt](conf.pipe_num_per_channel)
    val p2_pull_count_vec = Array.ofDim[UInt](conf.pipe_num_per_channel)

    for(i <- 0 until conf.pipe_num_per_channel){
        p1(i) = Module(new P1(i * conf.channel_num + num))
        p2(i) = Module(new P2(i * conf.channel_num + num))
        frontier(i) = Module(new Frontier(i * conf.channel_num + num))
    }
    //io <> frontier
    for(i <- 0 until conf.pipe_num_per_channel){
        io.frontier_flag  <> frontier(i).io.frontier_flag
        io.p2_end         <> frontier(i).io.p2_end
        io.start          <> frontier(i).io.start
        io.level          <> frontier(i).io.level
        frontier_end_vec(i)     = frontier(i).io.end
        last_iteration_vec(i)   = frontier(i).io.last_iteration
        // io.end            <> frontier(i).io.end
        io.bram_clock     <> frontier(i).io.bram_clock
        io.push_or_pull   <> frontier(i).io.push_or_pull_state
        frontier(i).io.node_num <> (io.node_num + conf.numSubGraphs.U - 1.U - (i.U * conf.channel_num.U + num.U)) / conf.numSubGraphs.U  //node num in each PE
        frontier_pull_count_vec(i) = frontier(i).io.frontier_pull_count
    }
    io.frontier_pull_count := RegNext(frontier_pull_count_vec.reduce(_+_))
    
    io.end := frontier_end_vec.reduce(_&_)
    
    io.last_iteration := last_iteration_vec.reduce(_&_)
    // io.last_iteration <> frontier.io.last_iteration
    // io.node_num       <> frontier.io.node_num
    

    //io <> p2
    // io.p2_count       <> p2.io.p2_count
    for(i <- 0 until conf.pipe_num_per_channel){
        io.bram_clock     <> p2(i).io.bram_clock
        io.push_or_pull   <> p2(i).io.push_or_pull_state
        io.if_write       <> p2(i).io.if_write
        p2_count_vec(i)   = p2(i).io.p2_count
        p2_pull_count_vec(i) = p2(i).io.p2_pull_count
    }
    io.p2_count := p2_count_vec.reduce(_+_)
    io.p2_pull_count := RegNext(p2_pull_count_vec.reduce(_+_))


    //io <> p1
    for(i <- 0 until conf.pipe_num_per_channel){
        io.start          <> p1(i).io.start
        io.push_or_pull   <> p1(i).io.push_or_pull_state
        p1(i).io.node_num <> (io.node_num + conf.numSubGraphs.U - 1.U - (i.U * conf.channel_num.U + num.U)) / conf.numSubGraphs.U  //node num in each PE
    }
    // io.node_num       <> p1.io.node_num
        

    //io <> mem
    io.axiport        <> memory.io.HBM_interface
    io.mem_count      <> memory.io.neighbour_cnt
    io.mem_end        <> memory.io.mem_end
    io.level          <> memory.io.level
    io.offsets        <> memory.io.offsets
    io.push_or_pull   <> memory.io.push_or_pull_state
    io.if_write       <> memory.io.if_write
    io.write_finish   <> memory.io.write_finish
    memory.io.kernel_count := io.kernel_count
    memory.io.master_finish := io.master_finish
    memory.io.node_num := (io.node_num + conf.channel_num.U - 1.U - num.U) / conf.channel_num.U //node num in each memory channel

    //p1 <> frontier
    for(i <- 0 until conf.pipe_num_per_channel){
        p1(i).io.frontier_value <> frontier(i).io.frontier_value
        p1(i).io.frontier_count <> frontier(i).io.frontier_count
    }

    //p1 <> mem
    for(i <- 0 until conf.pipe_num_per_channel){
        p1(i).io.R_array_index <> memory.io.R_array_index(i)
        p1_end_vec(i)          = p1(i).io.p1_end
    }
    memory.io.p1_end    <> p1_end_vec.reduce(_&_)
            

    //mem <> p2
    memory.io.neighbours  <> io.mem_out     
    for(i <- 0 until conf.pipe_num_per_channel){
        io.p2_in(2 * i)         <> p2(i).io.neighbours(0)
        io.p2_in(2 * i + 1)     <> p2(i).io.neighbours(1)
        memory.io.write_vertex_index(i) <> p2(i).io.write_vertex_index
    }

    // io.p2_in              <> p2.io.neighbours
    // memory.io.write_vertex_index <> p2.io.write_vertex_index

    //p2 <> frontier
    for(i <- 0 until conf.pipe_num_per_channel){
        p2(i).io.write_frontier(0)       <> io.p2_out(2 * i)
        p2(i).io.write_frontier(1)       <> io.p2_out(2 * i + 1)
        frontier(i).io.write_frontier(0) <> io.frontier_in(2 * i)
        frontier(i).io.write_frontier(1) <> io.frontier_in(2 * i + 1)

        p2(i).io.bram_to_frontier     <> frontier(i).io.bram_from_p2
        p2(i).io.visited_map_or_frontier(0) <> frontier(i).io.frontier_to_p2(0)
        p2(i).io.visited_map_or_frontier(1) <> frontier(i).io.frontier_to_p2(1)
    }


    //frontier <> mem
    for(i <- 0 until conf.pipe_num_per_channel){
        frontier(i).io.uram_addr_a <> memory.io.uram_addr_a(i)
        frontier(i).io.uram_addr_b <> memory.io.uram_addr_b(i)
        frontier(i).io.uram_out_a <> memory.io.uram_out_a(i)
        frontier(i).io.uram_out_b <> memory.io.uram_out_b(i)
    }

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

