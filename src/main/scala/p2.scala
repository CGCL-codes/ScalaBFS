package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._

class P2_IO (implicit val conf : HBMGraphConfiguration) extends Bundle{
    //Input
    // val start = Input(Bool())           // input p1 finish signal
    val neighbours = Vec(2, Flipped(Decoupled(UInt((conf.Data_width * 2).W ))))    // input 2 neighbours in local subgraph
    val bram_clock = Input(Clock())  // bram clock
    val push_or_pull_state = Input(UInt(1.W))   //input flag mark push or pull state
    val if_write = Input(Bool())
    val visited_map_or_frontier = Vec(2, Flipped(Decoupled(UInt(1.W))))    // input 2 neighbours in local subgraph

    //Output
    val p2_count = Output(UInt(conf.Data_width.W))
    val p2_pull_count = Output(UInt(conf.Data_width.W))
    val write_vertex_index = Decoupled(UInt(conf.Data_width.W))// output vertex index you want to write
    // write next_frontier in push mode
    // write next_frontier and visited_map in pull mode
    val write_frontier = Vec(2, Decoupled(UInt(conf.Data_width.W)))         // output 2 next_frontier you want to write
    val bram_to_frontier = Vec(2, Decoupled(UInt(conf.Data_width_p2_to_f.W)))
    
}

class P2 (val num :Int)(implicit val conf : HBMGraphConfiguration) extends Module{
	val io = IO(new P2_IO)
	dontTouch(io)
    val p2_read_visited_map_or_frontier = Module(new p2_read_visited_map_or_frontier(num))
    val write_frontier_and_level = Module(new write_frontier_and_level(num))
    
    // connect between p2 io and p2_read_visited_map_or_frontier
    // p2_read_visited_map_or_frontier.io.bram_to_frontier.start := io.start
    p2_read_visited_map_or_frontier.io.bram_clock := io.bram_clock
    p2_read_visited_map_or_frontier.io.neighbours <> io.neighbours
    val q_bram_to_frontier_0_l1 = Queue(p2_read_visited_map_or_frontier.io.bram_to_frontier(0), 2)
    val q_bram_to_frontier_0_l2 = Queue(q_bram_to_frontier_0_l1,2)
    // val q_bram_to_frontier_0_l3 = Queue(q_bram_to_frontier_0_l2,1)
    // val q_bram_to_frontier_0_l4 = Queue(q_bram_to_frontier_0_l3,1)

    val q_bram_to_frontier_1_l1 = Queue(p2_read_visited_map_or_frontier.io.bram_to_frontier(1), 2)
    val q_bram_to_frontier_1_l2 = Queue(q_bram_to_frontier_1_l1,2)
    // val q_bram_to_frontier_1_l3 = Queue(q_bram_to_frontier_1_l2,1)
    // val q_bram_to_frontier_1_l4 = Queue(q_bram_to_frontier_1_l3,1)

    Queue(q_bram_to_frontier_0_l2, 4) <> io.bram_to_frontier(0)
    Queue(q_bram_to_frontier_1_l2, 4) <> io.bram_to_frontier(1)
    // Queue(p2_read_visited_map_or_frontier.io.frontier_to_p2, 4) <> io.frontier_to_p2
    p2_read_visited_map_or_frontier.io.push_or_pull_state := io.push_or_pull_state

    
    // connect between p2_read_visited_map_or_frontier and write_frontier_and_level
    // val queue_0 = Queue(p2_read_visited_map_or_frontier.io.visited_map_or_frontier(0), conf.q_visited_map_len)
    // val queue_1 = Queue(queue_0,conf.q_visited_map_len)
    // val queue_2 = Queue(p2_read_visited_map_or_frontier.io.visited_map_or_frontier(1), conf.q_visited_map_len)
    // val queue_3 = Queue(queue_2,conf.q_visited_map_len)

    // write_frontier_and_level.io.visited_map_or_frontier(0) <> queue_1
    // write_frontier_and_level.io.visited_map_or_frontier(1) <> queue_3
    val q_visited_map_or_frontier_0_l1 = Queue(io.visited_map_or_frontier(0), 2)
    val q_visited_map_or_frontier_0_l2 = Queue(q_visited_map_or_frontier_0_l1, 2)
    // val q_visited_map_or_frontier_0_l3 = Queue(q_visited_map_or_frontier_0_l2, 1)
    // val q_visited_map_or_frontier_0_l4 = Queue(q_visited_map_or_frontier_0_l3, 1)

    val q_visited_map_or_frontier_1_l1 = Queue(io.visited_map_or_frontier(1), 2)
    val q_visited_map_or_frontier_1_l2 = Queue(q_visited_map_or_frontier_1_l1, 2)
    // val q_visited_map_or_frontier_1_l3 = Queue(q_visited_map_or_frontier_1_l2, 1)
    // val q_visited_map_or_frontier_1_l4 = Queue(q_visited_map_or_frontier_1_l3, 1)

    write_frontier_and_level.io.visited_map_or_frontier(0) <> Queue(q_visited_map_or_frontier_0_l2, conf.q_visited_map_len)
    write_frontier_and_level.io.visited_map_or_frontier(1) <> Queue(q_visited_map_or_frontier_1_l2, conf.q_visited_map_len)
    


    write_frontier_and_level.io.neighbours(0) <> Queue(p2_read_visited_map_or_frontier.io.neighbours_out(0), conf.q_neighbours_len)    // the FIFO queue is in module p2_read_visited_map_or_frontier
    write_frontier_and_level.io.neighbours(1) <> Queue(p2_read_visited_map_or_frontier.io.neighbours_out(1), conf.q_neighbours_len)

    // connect between p2 io and write_frontier_and_level
    // write_frontier_and_level.io.start := io.start
    io.p2_count := write_frontier_and_level.io.p2_count
    io.p2_pull_count := write_frontier_and_level.io.p2_pull_count
    io.write_vertex_index <> write_frontier_and_level.io.write_vertex_index
    io.write_frontier <> write_frontier_and_level.io.write_frontier
    io.if_write       <> write_frontier_and_level.io.if_write
    write_frontier_and_level.io.push_or_pull_state := io.push_or_pull_state
}


class p2_read_visited_map_or_frontier (val num :Int)(implicit val conf : HBMGraphConfiguration) extends Module{
    val io = IO(new Bundle{
        //Input
        // val start = Input(Bool())           // input p1 finish signal
        val neighbours = Vec(2, Flipped(Decoupled(UInt((conf.Data_width * 2).W))))    // input 2 neighbours in local subgraph
        val bram_clock = Input(Clock())  // bram clock
        val push_or_pull_state = Input(UInt(1.W))   //input flag mark push or pull state

        //Output
        // val visited_map_or_frontier = Vec(2, Decoupled(UInt(1.W))) // output 2 visited_map result in push mode or frontier result in pull mode
        val neighbours_out = Vec(2, Decoupled(UInt((conf.Data_width * 2).W)))    // output 2 neighbours in local subgraph
        // val bram_to_frontier = Flipped(new bram_controller_IO)
        val bram_to_frontier = Vec(2, Decoupled(UInt(conf.Data_width_p2_to_f.W)))
        // val frontier_to_p2 = Flipped(Decoupled(UInt((conf.Data_width_bram * 2).W)))
    })
    dontTouch(io)

    // init signals
    // io.visited_map_or_frontier(0).valid := false.B
    // io.visited_map_or_frontier(1).valid := false.B
    // io.visited_map_or_frontier(0).bits := DontCare
    // io.visited_map_or_frontier(1).bits := DontCare

    // local variables
    val count0 = RegInit(0.U(conf.Data_width.W)) // count the number of neighbour0 received
    val count1 = RegInit(0.U(conf.Data_width.W)) // count the number of neighbour0 received

    // use a FIFO queue to receive data
    // val q_neighbour0 = Queue(io.neighbours(0), conf.q_mem_to_p2_len)
    // val q_neighbour1 = Queue(io.neighbours(1), conf.q_mem_to_p2_len)
    val q_neighbour0 = Module(new Queue(UInt((conf.Data_width * 2).W), conf.q_mem_to_p2_len))
    val q_neighbour1 = Module(new Queue(UInt((conf.Data_width * 2).W), conf.q_mem_to_p2_len))
    // /*  the queue is longer ?? different length will cause data loss
    //     ensure that the data passed to module write_frontier_and_level will not be lost
    // */ 
    val q_neighbours_out0 = Module(new Queue(UInt((conf.Data_width * 2).W), conf.q_neighbours_len))
    val q_neighbours_out1 = Module(new Queue(UInt((conf.Data_width * 2).W), conf.q_neighbours_len))

    io.neighbours(0).ready := q_neighbour0.io.enq.ready & q_neighbours_out0.io.enq.ready
    q_neighbour0.io.enq.valid := io.neighbours(0).valid & q_neighbours_out0.io.enq.ready
    q_neighbour0.io.enq.bits := io.neighbours(0).bits

    io.neighbours(1).ready := q_neighbour1.io.enq.ready & q_neighbours_out1.io.enq.ready
    q_neighbour1.io.enq.valid := io.neighbours(1).valid & q_neighbours_out1.io.enq.ready
    q_neighbour1.io.enq.bits := io.neighbours(1).bits
    // q_neighbour0.ready := false.B
    // q_neighbour1.io.deq.ready := false.B
    q_neighbour0.io.deq.ready := false.B
    q_neighbour1.io.deq.ready := false.B


    q_neighbours_out0.io.enq.valid := io.neighbours(0).valid & q_neighbour0.io.enq.ready
    q_neighbours_out0.io.enq.bits := io.neighbours(0).bits
    io.neighbours_out(0) <> q_neighbours_out0.io.deq

    q_neighbours_out1.io.enq.valid := io.neighbours(1).valid & q_neighbour1.io.enq.ready
    q_neighbours_out1.io.enq.bits := io.neighbours(1).bits
    io.neighbours_out(1) <> q_neighbours_out1.io.deq
    // io.neighbours_out(0) <> Queue(io.neighbours(0), conf.q_neighbours_len)
    // io.neighbours_out(1) <> Queue(io.neighbours(1), conf.q_neighbours_len)

    /*  delay one cycle 
        if visited_req equals 1, it means that we have read visited map and get the result
    */
    // val visited_req0 = ShiftRegister(Mux(q_neighbour0.io.deq.valid && io.visited_map(0).ready, 1.U(1.W), 0.U(1.W)), 1,0.U(4.W), true.B)    
    // val visited_req1 = ShiftRegister(Mux(q_neighbour1.io.deq.valid && io.visited_map(1).ready, 1.U(1.W), 0.U(1.W)), 1,0.U(4.W), true.B)  

    // val visited_map = withClock(io.bram_clock){Module(new bram_controller(num, 0))}
    

    // io.bram_to_frontier := DontCare
    // io.bram_to_frontier.ena := true.B
    // io.bram_to_frontier.enb := true.B
    // io.bram_to_frontier.clka := io.bram_clock
    // io.bram_to_frontier.clkb := io.bram_clock
    // io.bram_to_frontier.wea := false.B
    // io.bram_to_frontier.web := false.B
    // io.bram_to_frontier.wmode := 0.U

    // val valid_a = Wire(UInt(1.W))
    // val valid_b = Wire(UInt(1.W))

    val bram_to_frontier_addra = Wire(UInt(conf.Addr_width.W))
    val bram_to_frontier_addrb = Wire(UInt(conf.Addr_width.W))
    val bram_to_frontier_wea = Wire(UInt(1.W))
    val bram_to_frontier_web = Wire(UInt(1.W))
    // val bram_to_frontier_wmode = Wire(UInt(1.W))
    val bram_to_frontier_nodea = Wire(UInt(conf.Node_width.W))
    val bram_to_frontier_nodeb = Wire(UInt(conf.Node_width.W))

    val bram_to_frontier_0 = Wire(UInt(conf.Data_width_p2_to_f.W))
    val bram_to_frontier_1 = Wire(UInt(conf.Data_width_p2_to_f.W))
    
    // bram_to_frontier_wmode := 0.U
    bram_to_frontier_wea := 0.U
    bram_to_frontier_web := 0.U
    bram_to_frontier_addra := 0.U
    bram_to_frontier_addrb := 0.U
    bram_to_frontier_nodea := 0.U
    bram_to_frontier_nodeb := 0.U
    // valid_a := 0.U
    // valid_b := 0.U

    bram_to_frontier_0 := Cat(bram_to_frontier_addra, bram_to_frontier_wea, bram_to_frontier_nodea)
    bram_to_frontier_1 := Cat(bram_to_frontier_addrb, bram_to_frontier_web, bram_to_frontier_nodeb)
    io.bram_to_frontier(0).bits := bram_to_frontier_0
    io.bram_to_frontier(0).valid := false.B
    io.bram_to_frontier(1).bits := bram_to_frontier_1
    io.bram_to_frontier(1).valid := false.B
    // receive neighbours and require visited_map
    // deal neighbour0
    
    /*  To prevent the number of received signals neighbour0 and neighbour1 not match,
        we will only deal when all valid(receive) and ready(send) signals are pulled high.
        Because the data read from bram cannot be paused, only when there is room for the queue 
        that stores the result read in bram, the new data will continue to be processed.
     */

    // 将数据拼合成一个UInt传输到frontier，q_neighbour0和1分别对应bram_to_frontier0和1
    when(io.bram_to_frontier(0).ready && q_neighbour0.io.deq.valid){
            q_neighbour0.io.deq.ready := true.B
            io.bram_to_frontier(0).valid := true.B
            // only need to read in push mode
            bram_to_frontier_wea := true.B && (io.push_or_pull_state === 0.U)
            // valid_a := 1.U

            // convert the total number of points to the number inside the pipeline
            bram_to_frontier_nodea := (Custom_function2.high(q_neighbour0.io.deq.bits) / conf.numSubGraphs.U) % conf.Data_width_bram.U
            bram_to_frontier_addra := (Custom_function2.high(q_neighbour0.io.deq.bits) / conf.numSubGraphs.U) / conf.Data_width_bram.U
    }
    .otherwise{
        io.bram_to_frontier(0).valid := false.B
    }
    when(io.bram_to_frontier(1).ready && q_neighbour1.io.deq.valid){
            q_neighbour1.io.deq.ready := true.B
            io.bram_to_frontier(1).valid := true.B
            bram_to_frontier_web := true.B && (io.push_or_pull_state === 0.U)
            // valid_b := 1.U
            bram_to_frontier_nodeb := (Custom_function2.high(q_neighbour1.io.deq.bits) / conf.numSubGraphs.U) % conf.Data_width_bram.U
            bram_to_frontier_addrb := (Custom_function2.high(q_neighbour1.io.deq.bits) / conf.numSubGraphs.U) / conf.Data_width_bram.U
    }
    .otherwise{
        io.bram_to_frontier(1).valid := false.B
    }
    
    // when(q_neighbour0.io.deq.valid && io.visited_map_or_frontier(0).ready){
    //     q_neighbour0.io.deq.ready := true.B
    //     // only need to read in push mode
    //     io.bram_to_frontier.wea := true.B && (io.push_or_pull_state === 0.U)
    //     // convert the total number of points to the number inside the pipeline
    //     io.bram_to_frontier.nodea := (Custom_function2.high(q_neighbour0.io.deq.bits) / conf.numSubGraphs.U) % conf.Data_width_bram.U
    //     io.bram_to_frontier.addra := (Custom_function2.high(q_neighbour0.io.deq.bits) / conf.numSubGraphs.U) / conf.Data_width_bram.U
    //     // TODO 检查直接不延迟,直接读dout(node)的数据，在bram的第二拍是正确的，待检测在流水线中是否正确
    //     io.visited_map_or_frontier(0).valid := true.B
    //     io.visited_map_or_frontier(0).bits := io.bram_to_frontier.douta((Custom_function2.high(q_neighbour0.io.deq.bits) / conf.numSubGraphs.U) % conf.Data_width_bram.U)
    //     count0 := count0 + 1.U
    // }

    // when(q_neighbour1.io.deq.valid && io.visited_map_or_frontier(1).ready){
    //     q_neighbour1.io.deq.ready := true.B
    //     io.bram_to_frontier.web := true.B && (io.push_or_pull_state === 0.U)
    //     io.bram_to_frontier.nodeb := (Custom_function2.high(q_neighbour1.io.deq.bits) / conf.numSubGraphs.U) % conf.Data_width_bram.U
    //     io.bram_to_frontier.addrb := (Custom_function2.high(q_neighbour1.io.deq.bits) / conf.numSubGraphs.U) / conf.Data_width_bram.U
    //     io.visited_map_or_frontier(1).valid := true.B
    //     io.visited_map_or_frontier(1).bits := io.bram_to_frontier.doutb((Custom_function2.high(q_neighbour1.io.deq.bits) / conf.numSubGraphs.U) % conf.Data_width_bram.U)
    //     count1 := count1 + 1.U
    // }

    // // store the data read from bram into FIFO queue
    // when(visited_req0 === 1.U){
    //     io.visited_map(0).valid := true.B
    //     io.visited_map(0).bits := io.bram_to_frontier.visited_a
    // }

    // when(visited_req1 === 1.U){
    //     io.visited_map(1).valid := true.B
    //     io.visited_map(1).bits := io.bram_to_frontier.visited_b
    // }
}

class write_frontier_and_level (val num :Int)(implicit val conf : HBMGraphConfiguration) extends Module{
    val io = IO(new Bundle{
        //Input
        // val start = Input(Bool())           // input p1 finish signal
        val visited_map_or_frontier = Vec(2, Flipped(Decoupled(UInt(1.W))))    // input 2 neighbours in local subgraph
        val neighbours = Vec(2, Flipped(Decoupled(UInt((conf.Data_width * 2).W))))
        val push_or_pull_state = Input(UInt(1.W))   //input flag mark push or pull state
        val if_write = Input(Bool())

        //Output
        val write_vertex_index = Decoupled(UInt(conf.Data_width.W)) // output vertex index you want to write to mem (deprecated)
        val write_frontier = Vec(2, Decoupled(UInt(conf.Data_width.W)))         // output 2 next_frontier you want to write
        val p2_count = Output(UInt(conf.Data_width.W))
        val p2_pull_count = Output(UInt(conf.Data_width.W)) // to pull crossbar count
    })
    dontTouch(io)

    // init signals
    io.visited_map_or_frontier(0).ready := false.B
    io.visited_map_or_frontier(1).ready := false.B
    io.neighbours(0).ready := false.B
    io.neighbours(1).ready := false.B
    io.write_frontier(0).valid := false.B
    io.write_frontier(1).valid := false.B
    io.write_frontier(0).bits := DontCare
    io.write_frontier(1).bits := DontCare

    // local variables
    val count0 = RegInit(0.U(conf.Data_width.W)) // count the number of neighbour0 received
    val count1 = RegInit(0.U(conf.Data_width.W)) // count the number of neighbour1 received
    val (count_wf0, _) = Counter(io.write_frontier(0).ready && io.write_frontier(0).valid, 2147483647)
    val (count_wf1, _) = Counter(io.write_frontier(1).ready && io.write_frontier(1).valid, 2147483647)
    io.p2_count := count0 + count1
    io.p2_pull_count := count_wf0 + count_wf1

    // to store the vertex_index need to write
    // val q_vertex_index = Array(2, Module(new Queue(UInt(conf.Data_width.W), 64)))
    val q_vertex_index0 = Module(new Queue(UInt(conf.Data_width.W), conf.q_p2_to_mem_len))
    val q_vertex_index1 = Module(new Queue(UInt(conf.Data_width.W), conf.q_p2_to_mem_len))
    q_vertex_index0.io.enq.valid := false.B
    q_vertex_index1.io.enq.valid := false.B
    q_vertex_index0.io.enq.bits := DontCare
    q_vertex_index1.io.enq.bits := DontCare

    // use RRArbiter to sequence 2 vertex_indexs into 1
    val vertex_index = Module(new RRArbiter(UInt(conf.Data_width.W),2))
    vertex_index.io.in(0) <> q_vertex_index0.io.deq
    vertex_index.io.in(1) <> q_vertex_index1.io.deq
    io.write_vertex_index <> vertex_index.io.out

    // receive visited_map_or_frontier, write next frontier and level
    // deal vec(0)
    when(io.visited_map_or_frontier(0).valid && io.neighbours(0).valid 
            && io.write_frontier(0).ready && q_vertex_index0.io.enq.ready){
        io.visited_map_or_frontier(0).ready := true.B
        io.neighbours(0).ready := true.B
        // push
        when(io.visited_map_or_frontier(0).bits === 0.U && io.push_or_pull_state === 0.U){ // write unvisited_node
            q_vertex_index0.io.enq.valid := true.B
            // data to memory: not need to convert
            q_vertex_index0.io.enq.bits := Custom_function2.high(io.neighbours(0).bits)
            io.write_frontier(0).valid := true.B
            // // data to frontier: convert the total number of points to the number inside the pipeline
            io.write_frontier(0).bits := Custom_function2.high(io.neighbours(0).bits) 
        }
        // pull
        when(io.visited_map_or_frontier(0).bits === 1.U && io.push_or_pull_state === 1.U){ // write unvisited_node
            q_vertex_index0.io.enq.valid := true.B
            // data to memory: not need to convert
            q_vertex_index0.io.enq.bits := Custom_function2.low(io.neighbours(0).bits)
            io.write_frontier(0).valid := true.B
            // // data to frontier: convert the total number of points to the number inside the pipeline
            io.write_frontier(0).bits := Custom_function2.low(io.neighbours(0).bits) 
        }
        count0 := count0 + 1.U
    }

    // deal vec(1)
    when(io.visited_map_or_frontier(1).valid && io.neighbours(1).valid 
            && io.write_frontier(1).ready && q_vertex_index1.io.enq.ready){
        io.visited_map_or_frontier(1).ready := true.B
        io.neighbours(1).ready := true.B
        // push
        when(io.visited_map_or_frontier(1).bits === 0.U && io.push_or_pull_state === 0.U){ // write unvisited_node
            q_vertex_index1.io.enq.valid := true.B
            // data to memory: not need to convert
            q_vertex_index1.io.enq.bits := Custom_function2.high(io.neighbours(1).bits)
            io.write_frontier(1).valid := true.B
            // // convert the total number of points to the number inside the pipeline
            io.write_frontier(1).bits := Custom_function2.high(io.neighbours(1).bits) 
        }
        // pull
        when(io.visited_map_or_frontier(1).bits === 1.U && io.push_or_pull_state === 1.U){ // write unvisited_node
            q_vertex_index1.io.enq.valid := true.B
            // data to memory: not need to convert
            q_vertex_index1.io.enq.bits := Custom_function2.low(io.neighbours(1).bits)
            io.write_frontier(1).valid := true.B
            // // convert the total number of points to the number inside the pipeline
            io.write_frontier(1).bits := Custom_function2.low(io.neighbours(1).bits) 
        }
        count1 := count1 + 1.U
    }

    when(io.if_write === false.B){
        q_vertex_index0.io.enq.valid := false.B    
        q_vertex_index1.io.enq.valid := false.B    
    }
} 

object Custom_function2{
    implicit val conf = HBMGraphConfiguration()
    def high(n : UInt) : UInt = 
        n(conf.crossbar_data_width * 2 - 1, conf.crossbar_data_width)

    def low(n : UInt) : UInt = 
        n(conf.crossbar_data_width - 1, 0)

}

