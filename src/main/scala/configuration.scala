package HBMGraph
import chisel3._
import chisel3.util._
case class HBMGraphConfiguration()
{
    //***************** Configurations ************************************
    val channel_num = 32            // the number of PCs
    val pipe_num_per_channel = 4    // the number of PEs per PC

    val shift_channel = 5           // Log2(channel_num) //
    val shift_pipe = 2              // Log2(pipe_num_per_channel)

    val slr0_channel_num = 0        // the number of PGs placed in SLR0, recommended as 0
    val slr1_channel_num = 14       // the number of PGs placed in SLR1
    val slr2_channel_num = channel_num - slr0_channel_num - slr1_channel_num    // the number of PGs placed in SLR2

    //*********************************************************************

    val numSubGraphs = channel_num * pipe_num_per_channel   // the number of PEs
    val shift = shift_channel + shift_pipe                  //Log2(numSubGraphs)


    

    // Crossbar configuration (0->full crossbar, 1->multi-layer crossbar)
    val multi_16 = 1 // 0
    val multi_32 = 1 // 0
    val multi_64 = 1
    val multi_128 = 1


    val sub_crossbar_size = 
    if(numSubGraphs < 64 || (multi_64 == 0 && numSubGraphs == 64) || (multi_128 == 0 && numSubGraphs == 128)){
        numSubGraphs
    }else if(numSubGraphs == 64 && multi_64 == 1){
        4
    }else if(numSubGraphs == 64 && multi_64 == 2){
        2
    }else if(numSubGraphs == 128){
        4
    }else if(numSubGraphs == 192){
        4
    }else{
        4
    }
    val sub_crossbar_size_2 = 
    if(numSubGraphs == 128){
        2
    }else{
        3
    }

    val sub_crossbar_number = 
    if(numSubGraphs == 64 && multi_64 == 1){
        16
    }else if(numSubGraphs == 64 && multi_64 == 2){
        32
    }else if(numSubGraphs == 128){
        32
    }else if(numSubGraphs == 192){
        48
    }else{
        16
    }
    val sub_crossbar_number_2 = 64

    
        
    
    
    // addr width and data width
    val Addr_width_uram = 20
    val Data_width_uram = 64
    val Data_width_uram_phy = 72
    val Write_width_uram = Data_width_uram / 8
    val shift_uram = 3      // Log2(Write_width_uram)

    val Addr_width = 18
    val Data_width = 32

    val Data_width_bram = 32
    val shift_bram = 5      // Log2(Data_width_bram)

    val write_level = 0
    val if_write = true.B

    // Memory
    val memIDBits = 6
    val HBM_Data_width_to_reader = 256
    val shift_hbm = 8                   // Log2(HBM_Data_width_to_reader)
    val shift_hbm_left = 5              // Log2(HBM_Data_width_to_reader >> 3)
    val hbm_mul_256_min_1 = 8191        // 256 * (HBM_Data_width_to_reader >> 3) - 1 

    val HBM_Data_width_to_pe = 64 * pipe_num_per_channel
    
    val reader_accept_num = HBM_Data_width_to_reader / Data_width

    val unpacked_data_que_len = 32
    // --------------------------------
    val HBM_Addr_width = 64
    val HBM_base_addr = "h10000000".U


    // Queue
    // p1
    val q_frontier_to_p1_len = 32
    // p2
    val q_neighbours_len = 32
    val q_p2_to_mem_len = 8  // write level
    val q_visited_map_len = 32
    val q_mem_to_p2_len = 32
    // frontier
    val q_p1_to_frontier_len = 32
    val q_p2_to_frontier_len = 32
    val q_out = 8

    // memory
    val Mem_queue_readData_len = 64         // big to prevent deadlock
    val src_index_queue_len = 64            // big to prevent deadlock
    val Mem_R_array_index_queue_len = 16
    val to_readAddr_queue_len = 16
    val write_vertex_index_pre_len = 8      // write level
    val write_vertex_index_len = 8          // write level
    val R_array_index_len = 8


    // crossbar
    val crossbar_in_fifo_len = 16
    val crossbar_main_fifo_len =
    if(numSubGraphs < 64){ 
        (64 / sub_crossbar_size)*2+2
    }else{
        (64 / sub_crossbar_number)*2+2
    }
    val crossbar_data_width = 24


    val Data_width_p2_to_f = Addr_width + 1

    val cross_slr_len = 8
    val node_queue_depth = 32

}

object loc{
    implicit val conf = HBMGraphConfiguration()
    def addr(n : UInt) : UInt = 
        n(conf.Data_width_p2_to_f - 1, 1)
    def we(n : UInt) : UInt = 
        n(0)    
}
