package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._

// for the PGs placed in SLR1

class SLR1_IO (implicit val conf : HBMGraphConfiguration) extends Bundle{

    // unified signal
    val p2_end = Input(Bool())
    val start = Input(Bool())
    val frontier_flag = Input(UInt(1.W))
    val level = Input(UInt(conf.Data_width.W))
    val offsets = Input(new offsets)
    val node_num = Input(UInt(conf.Data_width.W))
    val root = Input(UInt(conf.Data_width.W))
    val push_or_pull = Input(UInt(1.W))
    val start_init = Input(Bool())
    

    // independent signal
    val end = Output(Vec(conf.slr1_channel_num, Bool()))
    val p2_count = Output(Vec(conf.slr1_channel_num, UInt(conf.Data_width.W)))
    val p2_pull_count = Output(Vec(conf.slr1_channel_num, UInt(conf.Data_width.W)))
    val frontier_pull_count = Output(Vec(conf.slr1_channel_num, UInt(conf.Data_width.W)))
    val last_iteration = Output(Vec(conf.slr1_channel_num, Bool()))
    val init_bram = Output(Vec(conf.slr1_channel_num, Bool()))

    val p2_in = Vec(conf.pipe_num_per_channel * conf.slr1_channel_num, Flipped(Decoupled(UInt((conf.Data_width*2).W))))
    val frontier_in = Vec(conf.pipe_num_per_channel * conf.slr1_channel_num, Flipped(Decoupled(UInt(conf.Data_width.W))))
    val p2_out = Vec(conf.pipe_num_per_channel * conf.slr1_channel_num, Decoupled(UInt(conf.Data_width.W)))

    val R_array_index = Vec(conf.pipe_num_per_channel * conf.slr1_channel_num, Decoupled(UInt(conf.Data_width.W)))
    val p1_end = Output(Vec(conf.slr1_channel_num,Bool()))
    val uram_out_a = Vec(conf.pipe_num_per_channel * conf.slr1_channel_num, Output(UInt(conf.Data_width_uram.W)))
    val uram_addr_a = Input(Vec(conf.pipe_num_per_channel * conf.slr1_channel_num, UInt(conf.Addr_width_uram.W)))

}


class SLR1 (implicit val conf : HBMGraphConfiguration) extends Module{
	val io = IO(new SLR1_IO())
	dontTouch(io)

    val reset_reg = RegNext(reset)
    val reset_reg_array = VecInit(Seq.fill(conf.slr1_channel_num)(RegNext(reset))) //new Array[Reset](conf.channel_num)

    val pipeline_array = new Array[pipeline](conf.slr1_channel_num)
    for(i <- 0 until conf.slr1_channel_num) {
        pipeline_array(i) = withReset(reset_reg_array(i)){Module(new pipeline(i + conf.channel_num - conf.slr1_channel_num - conf.slr2_channel_num))}
    }

    val copy_start = Module(new copy_slr1(1))
    // val copy_master_finish = Module(new copy_slr1(1))
    val copy_frontier_flag = Module(new copy_slr1(1))
    val copy_current_level = Module(new copy_slr1(32))
    val copy_p2_end = Module(new copy_slr1(1))
    val copy_push_or_pull = Module(new copy_slr1(1))
    val copy_node_num = Module(new copy_slr1(conf.Data_width))
    val copy_root = Module(new copy_slr1(conf.Data_width))

    copy_start.io.in := io.start
    // copy_master_finish.io.in := io.master_finish
    copy_frontier_flag.io.in := io.frontier_flag
    copy_current_level.io.in := io.level
    copy_p2_end.io.in := io.p2_end
    copy_push_or_pull.io.in := io.push_or_pull
    copy_node_num.io.in := RegNext(io.node_num)
    copy_root.io.in := RegNext(io.root)

    val copy_offset_CSR_R_offset = Module(new copy_slr1(conf.Data_width))
    val copy_offset_CSR_C_offset = Module(new copy_slr1(conf.Data_width))
    val copy_offset_CSC_R_offset = Module(new copy_slr1(conf.Data_width))
    val copy_offset_CSC_C_offset = Module(new copy_slr1(conf.Data_width))
    val copy_offset_level_offset = Module(new copy_slr1(conf.Data_width))
    copy_offset_CSR_R_offset.io.in := RegNext(io.offsets.CSR_R_offset)
    copy_offset_CSR_C_offset.io.in := RegNext(io.offsets.CSR_C_offset)
    copy_offset_CSC_R_offset.io.in := RegNext(io.offsets.CSC_R_offset)
    copy_offset_CSC_C_offset.io.in := RegNext(io.offsets.CSC_C_offset)
    copy_offset_level_offset.io.in := RegNext(io.offsets.level_offset)

    val r_array_count = RegInit(VecInit(Seq.fill(conf.slr1_channel_num * conf.pipe_num_per_channel)(0.U(conf.Data_width.W))))
    val r_array_count_total = r_array_count.reduce(_+_)
    for(i <- 0 until conf.slr1_channel_num) {

        io.end(i) := pipeline_array(i).io.end
        io.p2_count(i) := pipeline_array(i).io.p2_count
        io.p2_pull_count(i) := pipeline_array(i).io.p2_pull_count
        io.frontier_pull_count(i) := pipeline_array(i).io.frontier_pull_count
        io.last_iteration(i) := pipeline_array(i).io.last_iteration
        io.init_bram(i) := pipeline_array(i).io.init_bram


        pipeline_array(i).io.p2_end := copy_p2_end.io.out(i)
        pipeline_array(i).io.start := copy_start.io.out(i)
        pipeline_array(i).io.frontier_flag := copy_frontier_flag.io.out(i)
        pipeline_array(i).io.level := copy_current_level.io.out(i)
        pipeline_array(i).io.offsets.CSR_R_offset <> copy_offset_CSR_R_offset.io.out(i) //io.offsets
        pipeline_array(i).io.offsets.CSR_C_offset <> copy_offset_CSR_C_offset.io.out(i) //io.offsets
        pipeline_array(i).io.offsets.CSC_R_offset <> copy_offset_CSC_R_offset.io.out(i) //io.offsets
        pipeline_array(i).io.offsets.CSC_C_offset <> copy_offset_CSC_C_offset.io.out(i) //io.offsets
        pipeline_array(i).io.offsets.level_offset <> copy_offset_level_offset.io.out(i) //io.offsets

        pipeline_array(i).io.node_num <> copy_node_num.io.out(i)
        pipeline_array(i).io.root     <> copy_root.io.out(i)
        pipeline_array(i).io.push_or_pull <> copy_push_or_pull.io.out(i)     // pure pull

        pipeline_array(i).io.start_init := RegNext(io.start_init) //*
        pipeline_array(i).io.p1_end <> io.p1_end(i)


        for(j <- 0 until conf.pipe_num_per_channel){
            pipeline_array(i).io.p2_in(j)          <> io.p2_in(i * conf.pipe_num_per_channel + j)
            pipeline_array(i).io.frontier_in(j)    <> io.frontier_in(i * conf.pipe_num_per_channel + j)        
            pipeline_array(i).io.p2_out(j)         <> io.p2_out(i * conf.pipe_num_per_channel + j)    
            pipeline_array(i).io.R_array_index(j)  <> io.R_array_index(i * conf.pipe_num_per_channel + j)    
            RegNext(pipeline_array(i).io.uram_out_a(j))     <> io.uram_out_a(i * conf.pipe_num_per_channel + j)    
            pipeline_array(i).io.uram_addr_a(j)    <> RegNext(io.uram_addr_a(i * conf.pipe_num_per_channel + j))  
            when(io.R_array_index(i * conf.pipe_num_per_channel + j).valid && io.R_array_index(i * conf.pipe_num_per_channel + j).ready){r_array_count(i * conf.pipe_num_per_channel + j) := r_array_count(i * conf.pipe_num_per_channel + j) + 1.U}  
        }
    }
}