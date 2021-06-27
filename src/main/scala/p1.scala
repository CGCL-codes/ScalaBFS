package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._

class P1_IO (implicit val conf : HBMGraphConfiguration) extends Bundle{
    //Input
    val start = Input(Bool())       // input start signal
	val frontier_value = Flipped(Decoupled(UInt(conf.Data_width_bram.W)))  // input frontier data
    val node_num = Input(UInt(conf.Data_width.W))
    val push_or_pull_state = Input(UInt(1.W))   //input flag mark push or pull state
    
    //Output
   	val frontier_count = Decoupled(UInt(conf.Data_width.W)) // output count of the required current_frontier
    val R_array_index = Decoupled(UInt(conf.Data_width.W))  // output vertex index of the required CSR
    val p1_end = Output(Bool())  // output p1 finish signal
}


class P1 (val num :Int)(implicit val conf : HBMGraphConfiguration) extends Module{
	val io = IO(new P1_IO())
	dontTouch(io)
    // io := DontCare
    val p1_read_frontier_or_visited_map = Module(new p1_read_frontier_or_visited_map)
    val read_R_array_index = Module(new read_R_array_index(num))
    
    // io <> p1_read_frontier_or_visited_map
    p1_read_frontier_or_visited_map.io.start := io.start
    io.frontier_count <> p1_read_frontier_or_visited_map.io.frontier_count
    p1_read_frontier_or_visited_map.io.node_num := io.node_num
    
    // io <> read_R_array_index
    read_R_array_index.io.node_num := io.node_num
    read_R_array_index.io.start := io.start
    read_R_array_index.io.frontier_value <> io.frontier_value
    read_R_array_index.io.push_or_pull_state := io.push_or_pull_state
    io.R_array_index <> read_R_array_index.io.R_array_index
    io.p1_end := read_R_array_index.io.p1_end
    
}

// read frontier valud in push state 
// read visited_map value in pull state
class p1_read_frontier_or_visited_map (implicit val conf : HBMGraphConfiguration) extends Module{
    val io = IO(new Bundle{
        //Input
        val start = Input(Bool())       // input start signal
        val node_num = Input(UInt(conf.Data_width.W))
        //Output
        val frontier_count = Decoupled(UInt(conf.Data_width.W)) // output count of the required current_frontier
    })
	dontTouch(io)

    // init signals
    io.frontier_count.valid := false.B
    io.frontier_count.bits := DontCare
    val state0 ::state1 :: Nil = Enum(2)
    val stateReg = RegInit(state0) // mark state of read current_frontier
    
    // local variables
    val count = RegInit(0.U(32.W))  // count the number of current frontier to require
    val size =  ((io.node_num - 1.U) / conf.Data_width_bram.U) + 1.U // the total number of current frontier 

    // require current_frontier from frontier module
    // not process p1_end signal
    switch(stateReg){
        is(state0){
            io.frontier_count.valid := false.B
            io.frontier_count.bits := DontCare
            when(io.start){
                stateReg := state1
                count := 0.U
            }
        }
        is(state1){
            // require current frontier
            io.frontier_count.valid := true.B
            io.frontier_count.bits := count
            when(io.frontier_count.ready){
                count := count + 1.U
                when(count === size - 1.U){
                    stateReg := state0
                }
                .otherwise{
                    stateReg := state1
                }
            }
        }
    }
}

class read_R_array_index (val num :Int)(implicit val conf : HBMGraphConfiguration) extends Module{
    val io = IO(new Bundle{
        //Input
        val start = Input(Bool())       // input start signal
        val frontier_value = Flipped(Decoupled(UInt(conf.Data_width_bram.W)))  // input frontier data
        val node_num = Input(UInt(conf.Data_width.W))
        val push_or_pull_state = Input(UInt(1.W))   //input flag mark push or pull state
        
        //Output
        val R_array_index = Decoupled(UInt(conf.Data_width.W))  // output vertex index of the required CSR
        val p1_end = Output(Bool())  // output p1 finish signal       
    })
	dontTouch(io)

    // init signals
    io.R_array_index.valid := false.B
    io.R_array_index.bits := DontCare
    io.p1_end := false.B

    // local variables
    val state0 ::state1 :: state2 :: state3 :: Nil = Enum(4)
    val stateReg = RegInit(state0) // mark state of read R array
    val q_frontier_value_l1 = Queue(io.frontier_value, 2)
    val q_frontier_value_l2 = Queue(q_frontier_value_l1, 2)
    // val q_frontier_value_l3 = Queue(q_frontier_value_l2, 1)
    // val q_frontier_value_l4 = Queue(q_frontier_value_l3, 1)
    val q_frontier_value = Queue(q_frontier_value_l2, conf.q_frontier_to_p1_len)  // use a FIFO queue to receive data
    
    
    // dontTouch(q_frontier_value) // to prevent the optimization of port io_enq_bits and io_deq_bits
    val size =  ((io.node_num - 1.U) / conf.Data_width_bram.U) + 1.U // the total number of current frontier 
    val count_f = RegInit(0.U(32.W))    // count the number of frontier received
    val frontier = RegInit(0.U(conf.Data_width_bram.W))  // store current frontier received
    val node = RegInit(0.U(conf.Data_width.W))  // the node num inside current frontier
    val node_num_in_frontier = RegInit(0.U(32.W)) // the number of node in current frontier(Data_width bits)
    val count_node_in_frontier = RegInit(0.U(32.W)) // count the number of node dealed in current frontier(Data_width bits)
    q_frontier_value.ready := false.B // equivalent to q_frontier_value.nodeq()
    
    // receive current_frontier from frontier module and require R array from Memory
    // give p1_end signal
    switch(stateReg){
        is(state0){
            io.p1_end := true.B
            q_frontier_value.ready := false.B
            io.R_array_index.valid := false.B
            io.R_array_index.bits := DontCare
            q_frontier_value.ready := false.B
            when(io.start){
                io.p1_end := false.B
                count_f := 0.U
                stateReg := state1
            }
        }
        is(state1){
            //receive current frontier
            io.p1_end := false.B
            q_frontier_value.ready := true.B
            io.R_array_index.valid := false.B
            when(q_frontier_value.valid){
                stateReg := state2
                count_f := count_f + 1.U
                // differentiate between push mode and pull mode
                node_num_in_frontier := Mux(io.push_or_pull_state === 0.U, PopCount(q_frontier_value.bits), PopCount(~q_frontier_value.bits))  // the number of node need to process in current frontier received
                // push mode
                when(q_frontier_value.bits =/= 0.U && io.push_or_pull_state === 0.U){    
                    //exist node inside current frontier
                    // node := Log2(q_frontier_value.bits - (q_frontier_value.bits & (q_frontier_value.bits - 1.U))) + conf.Data_width.U * count_f
                    node := Custom_function.find_node(q_frontier_value.bits, conf.Data_width_bram, count_f)
                    // frontier := q_frontier_value.bits & (q_frontier_value.bits - 1.U)
                    frontier := Custom_function.remove_one(q_frontier_value.bits)
                    count_node_in_frontier := 0.U
                    stateReg := state2
                }
                // pull mode
                .elsewhen((~q_frontier_value.bits) =/= 0.U && io.push_or_pull_state === 1.U){
                    //exist node unvisited
                    node := Custom_function.find_node(~q_frontier_value.bits, conf.Data_width_bram, count_f)
                    // frontier := q_frontier_value.bits & (q_frontier_value.bits - 1.U)
                    frontier := Custom_function.remove_one(~q_frontier_value.bits)
                    count_node_in_frontier := 0.U
                    stateReg := state2
                }
                .otherwise{
                    stateReg := state3
                }
            }
        }
        is(state2){
            // send R array 
            q_frontier_value.ready := false.B
            when(node > io.node_num - 1.U){
                // all the points have been processed and the round is over
                stateReg := state0 
            }
            .elsewhen(count_node_in_frontier === node_num_in_frontier){
                stateReg := state3
            }
            .otherwise{
                io.R_array_index.valid := true.B
                // convert the number of points inside the pipeline to the total number
                io.R_array_index.bits := node * conf.numSubGraphs.U + num.U 
                when(io.R_array_index.ready){
                    count_node_in_frontier := count_node_in_frontier + 1.U
                    // frontier := frontier & (frontier - 1.U)
                    frontier := Custom_function.remove_one(frontier)
                    // node := Log2(frontier - (frontier & (frontier - 1.U))) + conf.Data_width_bram.U * (count_f - 1.U)
                    node := Custom_function.find_node(frontier, conf.Data_width_bram, count_f - 1.U)
                    stateReg := state2
                }
            }
        }
        is(state3){
            q_frontier_value.ready := false.B
            io.R_array_index.valid := false.B
            when(count_f === size){
                // all the points have been processed and the round is over
                stateReg := state0
            }
            .otherwise{
                // to receive new current frontier
                stateReg := state1
            }
        }
    }
}

object Custom_function{
    def find_one(n : UInt) : UInt = 
        Log2(n - (n & (n - 1.U)))

    def find_node(n : UInt, data_width : Int, count : UInt) : UInt = 
        find_one(n) + data_width.U * count

    def remove_one(n : UInt) : UInt = 
        n & (n - 1.U)
}