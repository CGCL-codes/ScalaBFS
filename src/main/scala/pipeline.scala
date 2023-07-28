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


class pipeline(val num: Int)(implicit val conf : HBMGraphConfiguration) extends Module{
	val io = IO(new Bundle{
        //frontier io
        val frontier_flag = Input(UInt(1.W))
        val p2_end = Input(Bool())
        val start = Input(Bool())
        val start_init = Input(Bool())
        val end = Output(Bool())
        val last_iteration = Output(Bool())
        val init_bram = Output(Bool())
        //p2 io
        val p2_count = Output(UInt(conf.Data_width.W))
        //mem io
        val frontier_pull_count = Output(UInt(conf.Data_width.W))
        val p2_pull_count = Output(UInt(conf.Data_width.W))
        val level = Input(UInt(conf.Data_width.W))
        val offsets = Input(new offsets)

        //mem <> p2
        val p2_in = Vec(conf.pipe_num_per_channel, Flipped(Decoupled(UInt((conf.Data_width*2).W))))

        // p2 <> frontier
        val frontier_in = Vec(conf.pipe_num_per_channel, Flipped(Decoupled(UInt(conf.Data_width.W))))
        val p2_out = Vec(conf.pipe_num_per_channel, Decoupled(UInt(conf.Data_width.W)))

        // parameter
        val node_num = Input(UInt(conf.Data_width.W))
        val push_or_pull = Input(UInt(1.W))      //input flag mark push or pull state

        val root = Input(UInt(conf.Data_width.W))

        // ------------ connect memory ---------------------------------

        val R_array_index = Vec(conf.pipe_num_per_channel, Decoupled(UInt(conf.Data_width.W)))
        val p1_end = Output(Bool())
        val uram_out_a = Vec(conf.pipe_num_per_channel, Output(UInt(conf.Data_width_uram.W)))
        val uram_addr_a = Input(Vec(conf.pipe_num_per_channel, UInt(conf.Addr_width_uram.W)))

	})


    val copy_start = Module(new copy_pipe(1))
    val copy_start_init = Module(new copy_pipe(1))
    val copy_frontier_flag = Module(new copy_pipe(1))
    val copy_level = Module(new copy_pipe(32))
    val copy_p2_end = Module(new copy_pipe(1))
    val copy_push_or_pull = Module(new copy_pipe(1))
    val copy_node_num = Module(new copy_pipe(conf.Data_width))
    val copy_root = Module(new copy_pipe(conf.Data_width))
    
    
    copy_start.io.in := io.start
    copy_start_init.io.in := io.start_init
    copy_frontier_flag.io.in := io.frontier_flag
    copy_level.io.in := io.level
    copy_p2_end.io.in := io.p2_end
    copy_push_or_pull.io.in := io.push_or_pull
    copy_node_num.io.in := RegNext(io.node_num)
    copy_root.io.in := RegNext(io.root)

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
        copy_frontier_flag.io.out(i)  <> frontier(i).io.frontier_flag
        copy_p2_end.io.out(i)         <> frontier(i).io.p2_end
        copy_start.io.out(i)          <> frontier(i).io.start
        copy_start_init.io.out(i)     <> frontier(i).io.start_init
        copy_level.io.out(i)          <> frontier(i).io.level 
        frontier_end_vec(i)     = frontier(i).io.end
        last_iteration_vec(i)   = frontier(i).io.last_iteration
        copy_push_or_pull.io.out(i)   <> frontier(i).io.push_or_pull_state
        frontier(i).io.node_num <> RegNext((copy_node_num.io.out(i) + conf.numSubGraphs.U - 1.U - (i.U * conf.channel_num.U + num.U)) >> conf.shift.U)  //node num in each PE
        frontier_pull_count_vec(i) = frontier(i).io.frontier_pull_count
        frontier(i).io.root   <> copy_root.io.out(i)
        frontier(i).io.init_bram    <> io.init_bram
    }
    io.frontier_pull_count := RegNext(frontier_pull_count_vec.reduce(_+_))
    
    io.end := RegNext(frontier_end_vec.reduce(_&_))
    
    io.last_iteration := RegNext(last_iteration_vec.reduce(_&_))

    

    //io <> p2
    for(i <- 0 until conf.pipe_num_per_channel){
        copy_push_or_pull.io.out(i)   <> p2(i).io.push_or_pull_state
        p2_count_vec(i)   = p2(i).io.p2_count
        p2_pull_count_vec(i) = p2(i).io.p2_pull_count
    }
    io.p2_count := RegNext(p2_count_vec.reduce(_+_))
    io.p2_pull_count := RegNext(p2_pull_count_vec.reduce(_+_))


    //io <> p1
    for(i <- 0 until conf.pipe_num_per_channel){
        copy_start.io.out(i)          <> p1(i).io.start
        copy_push_or_pull.io.out(i)   <> p1(i).io.push_or_pull_state
        p1(i).io.node_num <> RegNext((copy_node_num.io.out(i) + conf.numSubGraphs.U - 1.U - (i.U * conf.channel_num.U + num.U)) >> conf.shift.U)  //node num in each PE
    }

    //p1 <> frontier
    for(i <- 0 until conf.pipe_num_per_channel){
        p1(i).io.frontier_value <> frontier(i).io.frontier_value
        p1(i).io.frontier_count <> frontier(i).io.frontier_count
    }

    //p1 <> mem
    for(i <- 0 until conf.pipe_num_per_channel){
        p1(i).io.R_array_index <> io.R_array_index(i)
        p1_end_vec(i)          = p1(i).io.p1_end
    }
    io.p1_end    <> RegNext(p1_end_vec.reduce(_&_))
            

    //mem <> p2
    for(i <- 0 until conf.pipe_num_per_channel){
        io.p2_in(i)         <> p2(i).io.neighbours
    }


    //p2 <> frontier
    for(i <- 0 until conf.pipe_num_per_channel){
        p2(i).io.write_frontier       <> io.p2_out(i)
        frontier(i).io.write_frontier <> io.frontier_in(i)

        p2(i).io.bram_to_frontier     <> frontier(i).io.bram_from_p2
        p2(i).io.visited_map_or_frontier <> frontier(i).io.frontier_to_p2
    }


    //frontier <> mem
    for(i <- 0 until conf.pipe_num_per_channel){
        frontier(i).io.uram_addr_a <> io.uram_addr_a(i)
        frontier(i).io.uram_out_a <> io.uram_out_a(i)
    }

}

