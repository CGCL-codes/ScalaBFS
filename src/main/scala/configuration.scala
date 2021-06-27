package HBMGraph
import chisel3._
import chisel3.util._
case class HBMGraphConfiguration()
{
    val numSubGraphs = 64
    val pipe_num_per_channel = 2
    val channel_num = numSubGraphs/pipe_num_per_channel

    val sub_crossbar_size = 
    if(numSubGraphs < 64){
        numSubGraphs
    }else if(numSubGraphs == 64){
        4
    }else if(numSubGraphs == 128){
        4
    }else{
        4
    }
    val sub_crossbar_size_2 = 2
    val sub_crossbar_number = 
    if(numSubGraphs == 64){
        16
    }else if(numSubGraphs == 128){
        32
    }else{
        16
    }
    val sub_crossbar_number_2 = 64
    val multi_32 = 1 // 0
    val Data_width_uram = 72
    val Addr_width_uram = 20
    val Write_width_uram = 9

    val Addr_width = 17
    val Data_width = 32
    val Data_width_bram = 64
    // val root_node = 5
    val if_write = true.B

    // Memory
    val memIDBits = 6
    val HBM_Data_width = 64 * pipe_num_per_channel
    val HBM_Addr_width = 64
    val HBM_base_addr = "h10000000".U

        // // Queue
        // // p1
        // val q_frontier_to_p1_len = 8
        // // p2
        // val q_neighbours_len = 8
        // val q_p2_to_mem_len = 8  // write level
        // val q_visited_map_len = 8
        // val q_mem_to_p2_len = 8
        // // frontier
        // val q_p1_to_frontier_len = 8
        // val q_p2_to_frontier_len = 8

        // // memory
        // val Mem_queue_readData_len = 64 //big to prevent deadlock
        // val src_index_queue_len = 64    //big to prevent deadlock
        // val Mem_R_array_index_queue_len = 16
        // val to_readAddr_queue_len = 16
        // val write_vertex_index_pre_len = 8 // write level
        // val write_vertex_index_len = 8 // write level

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

        // memory
        val Mem_queue_readData_len = 64 //big to prevent deadlock
        val src_index_queue_len = 64    //big to prevent deadlock
        val Mem_R_array_index_queue_len = 16
        val to_readAddr_queue_len = 16
        val write_vertex_index_pre_len = 8 // write level
        val write_vertex_index_len = 8 // write level


    // crossbar
    val crossbar_in_fifo_len = 16
    val crossbar_main_fifo_len =
    if(numSubGraphs < 64){ 
        (64 / sub_crossbar_size)*2+2
    }else{
        (64 / sub_crossbar_number)*2+2
    }
    val crossbar_data_width = 24
    val crossbar_connect_fifo_len = 8

    val Node_width = Data_width_bram.U.getWidth - 1

    val Data_width_p2_to_f = Addr_width + Node_width  + 1

    val loc_addrb = Node_width * 2 + Addr_width + 3
    val loc_addra = Node_width * 2 + Addr_width * 2 + 3

    val fifo_in_depth = loc_addra + Data_width_bram * 2 + 2
    val fifo_out_depth = Data_width_bram * 2

    val node_queue_depth = 32

    //将fifo generator按a，b端口拆分
}

object loc{
    implicit val conf = HBMGraphConfiguration()
    def addr(n : UInt) : UInt = 
        n(conf.Data_width_p2_to_f - 1, conf.Node_width + 1)

    def we(n : UInt) : UInt = 
        n(conf.Node_width)

    def node(n : UInt) : UInt = 
        n(conf.Node_width - 1, 0)

    def wmode(n : UInt) : UInt = 
        n(conf.Data_width_p2_to_f)

    def din(n : UInt) : UInt = 
        n(conf.Data_width_p2_to_f + conf.Data_width_bram, conf.Data_width_p2_to_f + 1)

    def en(n : UInt) : UInt = 
        n(conf.Data_width_p2_to_f + conf.Data_width_bram + 1)

    def no_read(n : UInt) : UInt = 
        n(conf.Data_width_p2_to_f + conf.Data_width_bram + 2)
    // def valida(n : UInt) : UInt = 
    //     n(conf.Data_width_p2_to_f - 1)

    // def validb(n : UInt) : UInt = 
    //     n(conf.Data_width_p2_to_f - 2)
        // io.ena, io.addra, io.dina, io.wea, 
        // io.enb, io.addrb(), io.dinb(Node_width*2+2 - Node_width*2 + Data_width_bram +1), io.web(Node_width*2+1), 
        // io.wmode(Node_width*2), io.nodea(Node_width-Node_width*2-1), io.nodeb (0-Node_width-1)
    // def nodeb(n : UInt) : UInt = 
    //     n(conf.Node_width - 1, 0)

    // def nodea(n : UInt) : UInt = 
    //     n(conf.Node_width * 2 - 1, conf.Node_width)

    // def wmode(n : UInt) : UInt = 
    //     n(conf.Node_width * 2)

    // def web(n : UInt) : UInt = 
    //     n(conf.Node_width * 2 + 1)

    // def wea(n : UInt) : UInt = 
    //     n(conf.Node_width * 2 + 2)

    // def addrb(n : UInt) : UInt = 
    //     n(conf.loc_addrb - 1, conf.Node_width * 2 + 3)

    // def addra(n : UInt) : UInt = 
    //     n(conf.loc_addra - 1, conf.loc_addrb)
        
    // def dinb(n : UInt) : UInt = 
    //     n(conf.loc_addra + conf.Data_width_bram - 1, conf.loc_addra)

    // def dina(n : UInt) : UInt = 
    //     n(conf.loc_addra + conf.Data_width_bram * 2 - 1, conf.loc_addra + conf.Data_width_bram)

    // def enb(n : UInt) : UInt = 
    //     n(conf.loc_addra + conf.Data_width_bram * 2)

    // def ena(n : UInt) : UInt = 
    //     n(conf.loc_addra + conf.Data_width_bram * 2 + 1)

    // def douta(n : UInt) : UInt = 
    //     n(conf.Data_width_bram * 2 - 1, conf.Data_width_bram)

    // def doutb(n : UInt) : UInt = 
    //     n(conf.Data_width_bram - 1, 0)

}
