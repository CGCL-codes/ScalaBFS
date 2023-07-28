package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._

class P2_IO (implicit val conf : HBMGraphConfiguration) extends Bundle{
    //Input
    val neighbours = Flipped(Decoupled(UInt((conf.Data_width * 2).W)))    // input 2 neighbours in local subgraph
    val push_or_pull_state = Input(UInt(1.W))   //input flag mark push or pull state
    val visited_map_or_frontier = Flipped(Decoupled(UInt(1.W)))    // input 2 neighbours in local subgraph

    //Output
    val p2_count = Output(UInt(conf.Data_width.W))
    val p2_pull_count = Output(UInt(conf.Data_width.W))
    val write_frontier = Decoupled(UInt(conf.Data_width.W))        // output 2 next_frontier you want to write
    val bram_to_frontier = Decoupled(UInt(conf.Data_width_p2_to_f.W))
    
}

class P2 (val num :Int)(implicit val conf : HBMGraphConfiguration) extends Module{
	val io = IO(new P2_IO)
	dontTouch(io)
    val p2_read_visited_map_or_frontier = Module(new p2_read_visited_map_or_frontier(num))
    val write_frontier_and_level = Module(new write_frontier_and_level(num))
    
    // connect between p2 io and p2_read_visited_map_or_frontier
    p2_read_visited_map_or_frontier.io.neighbours <> io.neighbours
    val q_bram_to_frontier_l1 = Queue(p2_read_visited_map_or_frontier.io.bram_to_frontier, 2)
    val q_bram_to_frontier_l2 = Queue(q_bram_to_frontier_l1,2)

    Queue(q_bram_to_frontier_l2, 4) <> io.bram_to_frontier
    p2_read_visited_map_or_frontier.io.push_or_pull_state := RegNext(io.push_or_pull_state)

    val q_visited_map_or_frontier_l1 = Module(new Queue(UInt(1.W), 2))
    val q_visited_map_or_frontier_l2 = Module(new Queue(UInt(1.W), 2))

    val q_visited_map_or_frontier = Module(new Queue(UInt(1.W), conf.q_visited_map_len))

    q_visited_map_or_frontier_l1.io.enq   <> io.visited_map_or_frontier
    q_visited_map_or_frontier_l2.io.enq   <> q_visited_map_or_frontier_l1.io.deq
    q_visited_map_or_frontier.io.enq      <> q_visited_map_or_frontier_l2.io.deq


    ~(~write_frontier_and_level.io.visited_map_or_frontier.ready) <> q_visited_map_or_frontier.io.deq.ready
    write_frontier_and_level.io.visited_map_or_frontier.valid <> ~(~q_visited_map_or_frontier.io.deq.valid)
    write_frontier_and_level.io.visited_map_or_frontier.bits <> q_visited_map_or_frontier.io.deq.bits


    write_frontier_and_level.io.neighbours <> Queue(p2_read_visited_map_or_frontier.io.neighbours_out, conf.q_neighbours_len)    // the FIFO queue is in module p2_read_visited_map_or_frontier

    // connect between p2 io and write_frontier_and_level
    io.p2_count := write_frontier_and_level.io.p2_count
    io.p2_pull_count := write_frontier_and_level.io.p2_pull_count
    io.write_frontier <> write_frontier_and_level.io.write_frontier
    write_frontier_and_level.io.push_or_pull_state := RegNext(io.push_or_pull_state)
}


class p2_read_visited_map_or_frontier (val num :Int)(implicit val conf : HBMGraphConfiguration) extends Module{
    val io = IO(new Bundle{
        //Input
        val neighbours = Flipped(Decoupled(UInt((conf.Data_width * 2).W)))    // input 2 neighbours in local subgraph
        val push_or_pull_state = Input(UInt(1.W))   //input flag mark push or pull state

        //Output
        val neighbours_out = Decoupled(UInt((conf.Data_width * 2).W))    // output 2 neighbours in local subgraph
        val bram_to_frontier = Decoupled(UInt(conf.Data_width_p2_to_f.W))
    })
    dontTouch(io)

    // local variables
    val count0 = RegInit(0.U(conf.Data_width.W)) // count the number of neighbour0 received

    val q_neighbour = Module(new Queue(UInt((conf.Data_width * 2).W), conf.q_mem_to_p2_len))

    val q_neighbours_out = Module(new Queue(UInt((conf.Data_width * 2).W), conf.q_neighbours_len))

    io.neighbours.ready := q_neighbour.io.enq.ready & q_neighbours_out.io.enq.ready
    q_neighbour.io.enq.valid := io.neighbours.valid & q_neighbours_out.io.enq.ready
    q_neighbour.io.enq.bits := io.neighbours.bits
    q_neighbour.io.deq.ready := false.B


    q_neighbours_out.io.enq.valid := io.neighbours.valid & q_neighbour.io.enq.ready
    q_neighbours_out.io.enq.bits := io.neighbours.bits
    io.neighbours_out <> q_neighbours_out.io.deq

    val bram_to_frontier_addra = Wire(UInt(conf.Addr_width.W))
    val bram_to_frontier_wea = Wire(UInt(1.W))

    val bram_to_frontier = Wire(UInt(conf.Data_width_p2_to_f.W))

    bram_to_frontier_wea := 0.U
    bram_to_frontier_addra := 0.U

    bram_to_frontier := Cat(bram_to_frontier_addra, bram_to_frontier_wea)//, bram_to_frontier_nodea)
    io.bram_to_frontier.bits := bram_to_frontier
    io.bram_to_frontier.valid := false.B

    
    /*  To prevent the number of received signals neighbour0 and neighbour1 not match,
        we will only deal when all valid(receive) and ready(send) signals are pulled high.
        Because the data read from bram cannot be paused, only when there is room for the queue 
        that stores the result read in bram, the new data will continue to be processed.
     */

    // Concatenate the data into a UInt and transmit it to the frontier, q_neighbor0 and 1 correspond to bram_to_frontier0 and 1 respectively
    when(io.bram_to_frontier.ready && q_neighbour.io.deq.valid){
            q_neighbour.io.deq.ready := true.B
            io.bram_to_frontier.valid := true.B
            // only need to read in push mode
            bram_to_frontier_wea := true.B && (io.push_or_pull_state === 0.U)
            bram_to_frontier_addra := (Custom_function2.high(q_neighbour.io.deq.bits) >> conf.shift) /// conf.Data_width_bram.U
    }
    .otherwise{
        io.bram_to_frontier.valid := false.B
    }

}

class write_frontier_and_level (val num :Int)(implicit val conf : HBMGraphConfiguration) extends Module{
    val io = IO(new Bundle{
        //Input
        val visited_map_or_frontier = Flipped(Decoupled(UInt(1.W)))    // input 2 neighbours in local subgraph
        val neighbours = Flipped(Decoupled(UInt((conf.Data_width * 2).W)))
        val push_or_pull_state = Input(UInt(1.W))   //input flag mark push or pull state

        //Output
        val write_frontier = Decoupled(UInt(conf.Data_width.W))         // output 2 next_frontier you want to write
        val p2_count = Output(UInt(conf.Data_width.W))
        val p2_pull_count = Output(UInt(conf.Data_width.W)) // to pull crossbar count
    })
    dontTouch(io)

    // init signals
    io.visited_map_or_frontier.ready := false.B
    io.neighbours.ready := false.B
    io.write_frontier.valid := false.B
    io.write_frontier.bits := DontCare

    // local variables
    val count0 = RegInit(0.U(conf.Data_width.W)) // count the number of neighbour0 received
    val (count_wf0, _) = Counter(io.write_frontier.ready && io.write_frontier.valid, 2147483647)
    io.p2_count := count0 //+ count1
    io.p2_pull_count := count_wf0 //+ count_wf1

    when( io.neighbours.valid && io.write_frontier.ready){// && q_vertex_index.io.enq.ready){
        io.visited_map_or_frontier.ready := true.B
        when(io.visited_map_or_frontier.valid){

            io.neighbours.ready := true.B
            // push
            when(io.visited_map_or_frontier.bits === 0.U && io.push_or_pull_state === 0.U){ // write unvisited_node
                io.write_frontier.valid := true.B
                io.write_frontier.bits := Custom_function2.high(io.neighbours.bits) 
            }
            // pull
            when(io.visited_map_or_frontier.bits === 1.U && io.push_or_pull_state === 1.U){ // write unvisited_node
                io.write_frontier.valid := true.B
                io.write_frontier.bits := Custom_function2.low(io.neighbours.bits) 
            }
            count0 := count0 + 1.U
        }
    }
} 

object Custom_function2{
    implicit val conf = HBMGraphConfiguration()
    def high(n : UInt) : UInt = 
        n(conf.crossbar_data_width * 2 - 1, conf.crossbar_data_width)

    def low(n : UInt) : UInt = 
        n(conf.crossbar_data_width - 1, 0)

}

