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
    p1_read_frontier_or_visited_map.io.start := RegNext(io.start)
    io.frontier_count <> p1_read_frontier_or_visited_map.io.frontier_count
    p1_read_frontier_or_visited_map.io.node_num := RegNext(io.node_num)
    
    // io <> read_R_array_index
    read_R_array_index.io.node_num := RegNext(io.node_num)
    read_R_array_index.io.start := RegNext(io.start)
    read_R_array_index.io.frontier_value <> io.frontier_value
    read_R_array_index.io.push_or_pull_state := RegNext(io.push_or_pull_state)

    val q_R_array_index  = Queue(read_R_array_index.io.R_array_index, 2)
    val q_R_array_index_1  = Queue(q_R_array_index, 2)
    io.R_array_index <> q_R_array_index_1
    // io.R_array_index <> read_R_array_index.io.R_array_index
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
    val state0 ::state1 ::state2 :: Nil = Enum(3)
    // This is changed by an error in chisel->verilog, when the 'state' has only 1 bit
    // when(io.start) state0->state1 converts to an error code for unknown reasons
    // _GEN_1 = io_start | stateReg
    // '| stateReg' is not need
    val stateReg = RegInit(state0) // mark state of read current_frontier
    
    // local variables
    val count = RegInit(0.U(32.W))  // count the number of current frontier to require
    val size =  RegNext(((RegNext(io.node_num) - 1.U) >> conf.shift_bram.U) + 1.U )// the total number of current frontier 

    val delay_for_end = RegInit(0.U(5.W))
    // require current_frontier from frontier module
    // not process p1_end signal
    switch(stateReg){
        is(state0){
            io.frontier_count.valid := false.B
            io.frontier_count.bits := DontCare
            delay_for_end := 0.U
            when(io.start){ 
                count := 0.U
                stateReg := state1
                
            }
        }
        is(state1){
            // require current frontier
            io.frontier_count.valid := true.B
            io.frontier_count.bits := count
            when(io.frontier_count.ready){
                count := count + 1.U
                when(count === size - 1.U){
                    stateReg := state2
                }
                .otherwise{
                    stateReg := state1
                }
            }
        }
        // The start signal needs to be pulled up for more than one beat to support the RegNext signal to be completely transmitted
        // When the number of PEs is large and the number of points is small, it may cause redundant data transmission during the start stage
        // Add state state2 to solve the problem
        is(state2){ 
            delay_for_end := delay_for_end + 1.U
            when(delay_for_end === 10.U){
                delay_for_end := 0.U
                stateReg := state0
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
    val q_frontier_value_l2 = Module(new Queue(UInt(conf.Data_width_bram.W), 2))
    q_frontier_value_l2.io.enq <> q_frontier_value_l1
    q_frontier_value_l2.io.enq.bits <> ~(~q_frontier_value_l1.bits)
    q_frontier_value_l2.io.enq.valid <> ~(~q_frontier_value_l1.valid)
    q_frontier_value_l2.io.enq.ready <> q_frontier_value_l1.ready
    val q_frontier_value = Queue(q_frontier_value_l2.io.deq, conf.q_frontier_to_p1_len)  // use a FIFO queue to receive data
    
    
    val size =  RegNext(((RegNext(io.node_num) - 1.U) >> conf.shift_bram.U) + 1.U) // the total number of current frontier 
    val count_f = RegInit(0.U(32.W))    // count the number of frontier received
    val frontier = RegInit(0.U(conf.Data_width_bram.W))  // store current frontier received
    val node = RegInit(0.U(conf.Data_width.W))  // the node num inside current frontier
    val node_num_in_frontier = RegInit(0.U(32.W)) // the number of node in current frontier(Data_width bits)
    val count_node_in_frontier = RegInit(0.U(32.W)) // count the number of node dealed in current frontier(Data_width bits)
    q_frontier_value.ready := false.B // equivalent to q_frontier_value.nodeq()

    val find_one = Module (new find_one_32)
    find_one.io.data := DontCare
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
                    find_one.io.data := q_frontier_value.bits
                    node := Custom_function.find_node(find_one.io.loc, conf.Data_width_bram, count_f)
                    frontier := Custom_function.remove_one(q_frontier_value.bits)
                    count_node_in_frontier := 0.U
                    stateReg := state2
                }
                // pull mode
                .elsewhen((~q_frontier_value.bits) =/= 0.U && io.push_or_pull_state === 1.U){
                    //exist node unvisited
                    find_one.io.data := ~q_frontier_value.bits
                    node := Custom_function.find_node(find_one.io.loc, conf.Data_width_bram, count_f)
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
            when(node > RegNext(io.node_num - 1.U)){
                // all the points have been processed and the round is over
                stateReg := state0 
            }
            .elsewhen(count_node_in_frontier === node_num_in_frontier){
                stateReg := state3
            }
            .otherwise{
                io.R_array_index.valid := true.B
                // convert the number of points inside the pipeline to the total number
                io.R_array_index.bits := (node << conf.shift.U) + num.U 
                when(io.R_array_index.ready){
                    count_node_in_frontier := count_node_in_frontier + 1.U
                    frontier := Custom_function.remove_one(frontier)
                    find_one.io.data := frontier
                    node := Custom_function.find_node(find_one.io.loc, conf.Data_width_bram, count_f - 1.U)
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
        n + data_width.U * count
        // find_one(n) + data_width.U * count

    def remove_one(n : UInt) : UInt = 
        n & (n - 1.U)
}
