package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._

// for module memory
class SLR0_Mem_IO (implicit val conf : HBMGraphConfiguration) extends Bundle{
    //Input
    // unified signal
    val level = Input(UInt(conf.Data_width.W))                           // input level (constant in one iter)
    val kernel_count = Input(UInt(32.W))
    val mem_clock = Input(Clock())
    val reset_h = Input(Bool()) 
    val master_finish = Input(Bool())
    val node_num = Input(UInt(conf.Data_width.W)) 
    val push_or_pull_state = Input(Bool())                               // 0 for push
    val offsets = Input(new offsets)                                     // offsets
    // val if_write = Input(Bool()) 

    // independent signal
    val R_array_index = Vec(conf.numSubGraphs, Flipped(Decoupled(UInt(conf.Data_width.W)))) 
    val p1_end = Input(Vec(conf.channel_num, Bool())) 
    val uram_out_a = Vec(conf.numSubGraphs, Input(UInt(conf.Data_width_uram.W)))

    //Output
    val neighbour_cnt = Output(Vec(conf.channel_num, UInt(conf.Data_width.W)))          // to master
    val mem_end = Output(Vec(conf.channel_num, Bool()))                                 // to master
    val neighbours = Vec(conf.numSubGraphs, Decoupled(UInt((conf.Data_width*2).W)))
    val uram_addr_a = Vec(conf.numSubGraphs, Output(UInt(conf.Addr_width_uram.W)))
    val write_finish = Output(Vec(conf.channel_num, Bool()))                            // to master
    val HBM_interface = Vec(conf.channel_num, new AXIMasterIF_reader(conf.HBM_Addr_width, conf.HBM_Data_width_to_reader,conf.memIDBits))
}


class SLR0_Mem (implicit val conf : HBMGraphConfiguration) extends Module{
	val io = IO(new SLR0_Mem_IO())
	dontTouch(io)
    
    val memory = new Array[Memory](conf.channel_num)
    for(i <- 0 until conf.channel_num){
        memory(i) = Module(new Memory(i))
    }

    val copy_level = Module(new copy(32))
    val copy_kernel_count = Module(new copy(32))
    val copy_master_finish = Module(new copy(1))
    val copy_node_num = Module(new copy(conf.Data_width))
    val copy_push_or_pull = Module(new copy(1))
    val copy_offset_CSR_R_offset = Module(new copy(conf.Data_width))
    val copy_offset_CSR_C_offset = Module(new copy(conf.Data_width))
    val copy_offset_CSC_R_offset = Module(new copy(conf.Data_width))
    val copy_offset_CSC_C_offset = Module(new copy(conf.Data_width))
    val copy_offset_level_offset = Module(new copy(conf.Data_width))


    copy_level.io.in            <> io.level
    copy_kernel_count.io.in     <> io.kernel_count        
    copy_master_finish.io.in    <> io.master_finish        
    copy_node_num.io.in         <> io.node_num    
    copy_push_or_pull.io.in     <> io.push_or_pull_state       

    copy_offset_CSR_R_offset.io.in := RegNext(io.offsets.CSR_R_offset)
    copy_offset_CSR_C_offset.io.in := RegNext(io.offsets.CSR_C_offset)
    copy_offset_CSC_R_offset.io.in := RegNext(io.offsets.CSC_R_offset)
    copy_offset_CSC_C_offset.io.in := RegNext(io.offsets.CSC_C_offset)
    copy_offset_level_offset.io.in := RegNext(io.offsets.level_offset)

    for(i <- 0 until conf.channel_num){
        memory(i).io.level               <> copy_level.io.out(i)
        memory(i).io.kernel_count        <> copy_kernel_count.io.out(i)
        memory(i).io.mem_clock           <> io.mem_clock
        memory(i).io.reset_h             <> io.reset_h
        memory(i).io.master_finish       <> copy_master_finish.io.out(i)
        memory(i).io.node_num            <> ((copy_node_num.io.out(i) + conf.channel_num.U - 1.U - i.U) >> conf.shift_channel.U) //node num in each memory channel
        memory(i).io.push_or_pull_state  <> copy_push_or_pull.io.out(i)
        memory(i).io.offsets.CSR_R_offset             <> copy_offset_CSR_R_offset.io.out(i)
        memory(i).io.offsets.CSR_C_offset             <> copy_offset_CSR_C_offset.io.out(i)
        memory(i).io.offsets.CSC_R_offset             <> copy_offset_CSC_R_offset.io.out(i)
        memory(i).io.offsets.CSC_C_offset             <> copy_offset_CSC_C_offset.io.out(i)
        memory(i).io.offsets.level_offset             <> copy_offset_level_offset.io.out(i)
        // memory(i).io.if_write            <> io.if_write
        memory(i).io.HBM_interface       <> io.HBM_interface(i)
    }

    for(i <- 0 until conf.channel_num){
        memory(i).io.p1_end              <> io.p1_end(i)
        memory(i).io.neighbour_cnt       <> io.neighbour_cnt(i) 
        memory(i).io.mem_end             <> io.mem_end(i)
        memory(i).io.write_finish        <> io.write_finish(i)

    }
    for(i <- 0 until conf.channel_num){
        for(j <- 0 until conf.pipe_num_per_channel){
            memory(i).io.R_array_index(j)       <> io.R_array_index(i * conf.pipe_num_per_channel + j)
            memory(i).io.uram_out_a(j)          <> io.uram_out_a(i * conf.pipe_num_per_channel + j)
            memory(i).io.neighbours(j)          <> io.neighbours(i * conf.pipe_num_per_channel + j)
            memory(i).io.uram_addr_a(j)         <> io.uram_addr_a(i * conf.pipe_num_per_channel + j)
                  
        }
    }
}