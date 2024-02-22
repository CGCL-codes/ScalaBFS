package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._

class Frontier_IO(implicit val conf : HBMGraphConfiguration) extends Bundle{
    //Input
    val frontier_count = Flipped(Decoupled(UInt(conf.Data_width.W)))        // input count of the required current_frontier
    val write_frontier = Flipped(Decoupled(UInt(conf.Data_width.W)))// input 1 next_frontier you want to write
    val frontier_flag = Input(UInt(1.W))    // input flag mark which frontier to use as current_frontier or next_frontier
    val p2_end = Input(Bool())           // input p2 finish signal
    val start = Input(Bool())       // input start  
    val start_init = Input(Bool())
    val node_num = Input(UInt(conf.Data_width.W))
    val push_or_pull_state = Input(UInt(1.W))   //input flag mark push or pull state
    val level = Input(UInt(conf.Data_width.W))
    val uram_addr_a = Input(UInt(conf.Addr_width_uram.W))
    val bram_from_p2 = Flipped(Decoupled(UInt(conf.Data_width_p2_to_f.W)))
    val root = Input(UInt(conf.Data_width.W))

    //Output
    val frontier_value = Decoupled(UInt(conf.Data_width_bram.W))  // output frontier data
    val end = Output(Bool())    // output end signal
    val last_iteration = Output(Bool())     // output. write next frontier in last iteration or not.
    val uram_out_a = Output(UInt(conf.Data_width_uram.W))
    val frontier_pull_count = Output(UInt(conf.Data_width.W)) // pull crossbar count
    val frontier_to_p2 = Decoupled(UInt(1.W))
    val init_bram = Output(Bool()) // init root state
    

}



class Frontier (val num :Int) (implicit val conf : HBMGraphConfiguration) extends Module{
    val io = IO(new Frontier_IO())
    dontTouch(io)
    io.frontier_to_p2.valid := false.B
    io.frontier_to_p2.bits := DontCare
    io.end := true.B
    val level = RegInit(0.U(8.W))
    level := io.level

    val last_iteration_reg = RegInit(false.B)
    io.last_iteration := last_iteration_reg

    val q_frontier_value = Module(new Queue(UInt(conf.Data_width_bram.W), conf.q_out))
    io.frontier_value.valid := q_frontier_value.io.deq.valid
    io.frontier_value.bits := q_frontier_value.io.deq.bits
    q_frontier_value.io.deq.ready := io.frontier_value.ready
    q_frontier_value.io.enq.valid := false.B
    q_frontier_value.io.enq.bits := DontCare
    // two frontiers
    val frontier_0 = Module(new bram_controller)
    val frontier_1 = Module(new bram_controller)
    val uram = Module(new uram_controller)
    frontier_0.io := DontCare

    frontier_0.io.ena := false.B
    frontier_0.io.enb := false.B
    frontier_0.io.clka := clock
    frontier_0.io.clkb := clock
    frontier_0.io.wea := false.B
    frontier_0.io.web := false.B

    frontier_1.io := DontCare
    frontier_1.io.ena := false.B
    frontier_1.io.enb := false.B
    frontier_1.io.clka := clock
    frontier_1.io.clkb := clock
    frontier_1.io.wea := false.B
    frontier_1.io.web := false.B

    val uram_addra = RegInit(0.U(conf.Addr_width_uram.W))
    val uram_dina  = RegInit(0.U(conf.Data_width_uram.W))
    val uram_wea   = RegInit(0.U((conf.Data_width_uram >> 3).W))
    val uram_addrb = RegInit(0.U(conf.Addr_width_uram.W))
    val uram_dinb  = RegInit(0.U(conf.Data_width_uram.W))
    val uram_web   = RegInit(0.U((conf.Data_width_uram >> 3).W))
    val uram_douta   = RegInit(0.U((conf.Data_width_uram).W))

    uram.io := DontCare
    uram.io.clka := clock //io.bram_clock
    uram.io.clkb := clock //io.bram_clock

    //------------------------------------
    uram_wea := 0.U
    uram_web := 0.U

    uram_dina := 0.U
    uram_addra := 0.U
    
    uram.io.wea    <> uram_wea
    uram.io.web    <> uram_web
    uram.io.addra  <> uram_addra
    uram.io.dina   <> uram_dina
    uram.io.addrb  <> uram_addrb
    uram.io.dinb   <> uram_dinb

    io.uram_addr_a <> uram_addra
    uram.io.douta  <> uram_douta
    io.uram_out_a := uram_douta




    // input Queue
    // * Use multi-layer Queue for buffering to improve timing. 
    // * The depth of Queue used for buffering cannot be 1. 
    // * If the depth is 1, only one data can be processed every other beat.
    val q_frontier_count_l1 = Queue(io.frontier_count, 2)
    val q_frontier_count_l2 = Queue(q_frontier_count_l1, 2)

    val q_frontier_count = Queue(q_frontier_count_l2, conf.q_p1_to_frontier_len)

    val bram_from_p2_l1 = Queue(io.bram_from_p2, 2)
    val bram_from_p2_l2 = Queue(bram_from_p2_l1, 2)

    val bram_from_p2 = Queue(bram_from_p2_l2, conf.q_p2_to_frontier_len)

    val q_write_frontier_l1 = Queue(io.write_frontier, 2)
    val q_write_frontier_l2 = Queue(q_write_frontier_l1, 2)

    val q_write_frontier = Queue(q_write_frontier_l2, conf.q_p2_to_frontier_len)
    q_frontier_count.ready := false.B
    q_write_frontier.ready := false.B

    // counter
    // frontier_pull_count calculate the number of write frontiers sent by p2
    val (count_wf0, _) = Counter(q_write_frontier.ready && q_write_frontier.valid, 2147483647)
    io.frontier_pull_count := count_wf0 

    //as designed , at beginning io.p2_end is high. should wait it low and high again.
    val p2_end_flag = RegInit(0.U(1.W))
    when(io.p2_end === false.B){p2_end_flag := 1.U}

    val clear_addr = RegInit(0.U(conf.Addr_width.W))
    val state0 :: state1 :: state2 :: state3 :: state4 :: state_write_uram :: Nil = Enum(6)
    val stateReg = RegInit(state3)

    // buffer the result when p2 reads the value of a vertex in visited_map or current_frontier
    val read_queue = Module(new Queue(UInt(1.W), conf.node_queue_depth))
    read_queue.io.enq.valid := false.B
    read_queue.io.enq.bits := DontCare
    read_queue.io.deq.ready := false.B
    
    bram_from_p2.ready := false.B

    // Process the read data content sent from p2
    //for visited_map
    val visited_map = Module(new bram_controller)
    visited_map.io := DontCare
    // push mode, read and write
    visited_map.io.clka := clock
    visited_map.io.clkb := clock
    visited_map.io.ena := false.B
    visited_map.io.enb := false.B
    visited_map.io.wea := false.B
    visited_map.io.web := false.B

    val flag_read = ShiftRegister(bram_from_p2.valid && bram_from_p2.ready, 2)
    when(io.push_or_pull_state === 0.U){

        // ready can be set to 1
        // First update flag_read, if there is still reading in this beat, flag_read will be set to 1 again later, it will not affect
        when(flag_read === true.B){
            when(read_queue.io.enq.ready === true.B){ // must be true
                read_queue.io.enq.valid := true.B
                read_queue.io.enq.bits := visited_map.io.douta
            }
            
        }
        bram_from_p2.ready := read_queue.io.count < conf.node_queue_depth.U - 2.U //read_queue.io.enq.ready
        when(bram_from_p2.valid && bram_from_p2.ready){
            visited_map.io.ena := true.B
            visited_map.io.addra := loc.addr(bram_from_p2.bits) 
            visited_map.io.wea := loc.we(bram_from_p2.bits)
            visited_map.io.dina := 1.U
        }
        
        
        when(io.frontier_to_p2.ready && read_queue.io.deq.valid){

            read_queue.io.deq.ready := true.B
            io.frontier_to_p2.valid := true.B
            io.frontier_to_p2.bits := read_queue.io.deq.bits 
        }

    }
    // pull mode, current_frontier is frontier_0, read only
    when(io.push_or_pull_state === 1.U && io.frontier_flag === 0.U){
        when(flag_read === true.B){
            when(read_queue.io.enq.ready === true.B){
                read_queue.io.enq.valid := true.B
                read_queue.io.enq.bits := frontier_0.io.douta
            }
        }
        bram_from_p2.ready := read_queue.io.count < conf.node_queue_depth.U - 2.U//read_queue.io.enq.ready
        when(bram_from_p2.valid && bram_from_p2.ready){ 
            frontier_0.io.ena := true.B
            frontier_0.io.addra := loc.addr(bram_from_p2.bits)
        }


        when(io.frontier_to_p2.ready && read_queue.io.deq.valid){
            read_queue.io.deq.ready := true.B
            io.frontier_to_p2.valid := true.B
            io.frontier_to_p2.bits := read_queue.io.deq.bits
        }

    }
    // pull mode, current_frontier is frontier_1, read only
    when(io.push_or_pull_state === 1.U && io.frontier_flag === 1.U){

        when(flag_read === true.B){
            when(read_queue.io.enq.ready === true.B){
                read_queue.io.enq.valid := true.B
                read_queue.io.enq.bits := frontier_1.io.douta
            }
        }
        bram_from_p2.ready := read_queue.io.count < conf.node_queue_depth.U - 2.U
        when(bram_from_p2.valid && bram_from_p2.ready){ 
            frontier_1.io.ena := true.B
            frontier_1.io.addra := loc.addr(bram_from_p2.bits)        
        }


        when(io.frontier_to_p2.ready && read_queue.io.deq.valid){
            read_queue.io.deq.ready := true.B
            io.frontier_to_p2.valid := true.B
            io.frontier_to_p2.bits := read_queue.io.deq.bits
        }
    }


    val delay_for_clear = RegInit(0.U(5.W))

    val cond1 = !q_write_frontier.valid
    val cond2 = cond1 || io.push_or_pull_state === 0.U

    val init_bram = RegInit(false.B)
    io.init_bram := init_bram
    val flag_read_from_p1 = ShiftRegister(q_frontier_count.valid && q_frontier_count.ready, 2)
    switch(stateReg){
        is(state0){
            io.end := false.B
            //state 0 read and write
            q_frontier_count.ready := false.B
            q_write_frontier.ready := false.B
            frontier_0.io.wea := false.B
            frontier_0.io.web := false.B
            frontier_1.io.wea := false.B
            frontier_1.io.web := false.B
            uram_wea := 0.U
            uram_dina := 0.U
   
            when(flag_read_from_p1 === true.B){
                
                // Feedback p1 read request,
                // data output does not need to consider write_frontier
                when(q_frontier_value.io.enq.ready){

                    when(io.push_or_pull_state === 1.U){
                        q_frontier_value.io.enq.valid := true.B
                        q_frontier_value.io.enq.bits := visited_map.io.doutb  
                    }

                    // read from current frontier in push mode
                    when(io.push_or_pull_state === 0.U ){
                        when(io.frontier_flag === 0.U){
                            q_frontier_value.io.enq.valid := true.B
                            q_frontier_value.io.enq.bits := frontier_0.io.doutb
                        }
                        when(io.frontier_flag === 1.U){
                            q_frontier_value.io.enq.valid := true.B
                            q_frontier_value.io.enq.bits := frontier_1.io.doutb
                        }
                    }

                }
            }
            // p1 read frontier_count all use port b
            q_frontier_count.ready := q_frontier_value.io.count < conf.q_out.U - 2.U
            when(q_frontier_count.valid && q_frontier_count.ready){
                when(io.push_or_pull_state === 1.U){
                    visited_map.io.enb := true.B
                    visited_map.io.addrb := q_frontier_count.bits
                }

                // read from current frontier in push mode
                when(io.push_or_pull_state === 0.U ){
                    when(io.frontier_flag === 0.U){ 
                        frontier_0.io.enb := true.B
                        frontier_0.io.addrb := q_frontier_count.bits
                    }
                    when(io.frontier_flag === 1.U){
                        frontier_1.io.enb := true.B
                        frontier_1.io.addrb := q_frontier_count.bits
                    }
                }

            }

            


            q_write_frontier.ready := true.B
            //p2 write
            when(q_write_frontier.valid){

                last_iteration_reg := false.B
                //write port a in next frontier
                when(io.push_or_pull_state === 1.U){
                    visited_map.io.ena := true.B
                    visited_map.io.wea := true.B
                    visited_map.io.addra := Custom_function3.low(q_write_frontier.bits) >> conf.shift
                    visited_map.io.dina := 1.U
                }
                when(io.frontier_flag === 0.U){
                    //now next frontier is frontier_1
                    frontier_1.io.ena := true.B
                    frontier_1.io.wea := true.B
                    frontier_1.io.addra := Custom_function3.low(q_write_frontier.bits) >> conf.shift
                    frontier_1.io.dina := 1.U
                }
                when(io.frontier_flag === 1.U){
                    //now next frontier is frontier_0
                    frontier_0.io.ena := true.B
                    frontier_0.io.wea := true.B
                    frontier_0.io.addra := Custom_function3.low(q_write_frontier.bits) >> conf.shift
                    frontier_0.io.dina := 1.U
                }
                uram_wea := 1.U << ((Custom_function3.low(q_write_frontier.bits) >> conf.shift) % conf.Write_width_uram.U)
                uram_dina := level << (8.U * ((Custom_function3.low(q_write_frontier.bits) >> conf.shift) % conf.Write_width_uram.U))
                uram_addra := Custom_function3.low(q_write_frontier.bits) >> (conf.shift + conf.shift_uram)// conf.shift / conf.Write_width_uram.U
            }
    
            when(io.p2_end && p2_end_flag === 1.U && !q_frontier_count.valid && !q_write_frontier.valid ){//&& !q_write_frontier_1.valid){
                stateReg := state1
                clear_addr := 0.U
            }
        }
        is(state1){  // clear bits
            io.end := false.B
            q_frontier_count.ready := false.B
            q_write_frontier.ready := false.B
            frontier_0.io.wea := false.B
            frontier_0.io.web := false.B
            frontier_1.io.wea := false.B
            frontier_1.io.web := false.B


            clear_addr := clear_addr + 1.U
            when(io.frontier_flag === 0.U){
                // now current frontier is frontier 0 , at next level it will become next frontier , so clear it
                frontier_0.io.web := true.B
                frontier_0.io.dinb := 0.U
                frontier_0.io.addrb := clear_addr 
                frontier_0.io.enb := true.B

                when(clear_addr >= RegNext(io.node_num >> conf.shift_bram)){ // >= -> >
                    stateReg := state2
                }

            }
            when(io.frontier_flag === 1.U){
                // now current frontier is frontier 1 , at next level it will become next frontier , so clear it
                frontier_1.io.web := true.B
                frontier_1.io.dinb := 0.U
                frontier_1.io.addrb := clear_addr
                frontier_1.io.enb := true.B

                when(clear_addr >= RegNext(io.node_num >> conf.shift_bram)){
                    stateReg := state2
                    // io.end := true.B
                }
            }
        }
        is(state2){
            // * Add a delay to ensure that the clearing operation is completed. 
            // * The expected delay is 5 beats, and 20 beats are left to ensure correctness.
            when(delay_for_clear < 20.U){
                delay_for_clear := delay_for_clear + 1.U
            }

            io.end := Mux(delay_for_clear === 20.U, true.B, false.B)
            q_frontier_count.ready := false.B
            q_write_frontier.ready := false.B
            frontier_0.io.wea := false.B
            frontier_0.io.web := false.B
            frontier_1.io.wea := false.B
            frontier_1.io.web := false.B
            visited_map.io.wea := false.B
            visited_map.io.web := false.B
            when(RegNext(io.start)){
                io.end := false.B
                stateReg := state0
                last_iteration_reg := true.B
                delay_for_clear := 0.U
                p2_end_flag := 0.U
            }
        }
        is(state3){
            q_frontier_count.ready := false.B
            q_write_frontier.ready := false.B
            frontier_0.io.wea := false.B
            frontier_0.io.web := false.B
            frontier_1.io.wea := false.B
            frontier_1.io.web := false.B
            when(RegNext(io.start_init)){
                stateReg := state4
            }
        }
        is(state4){
            q_frontier_count.ready := false.B
            q_write_frontier.ready := false.B
            frontier_0.io.wea := false.B
            frontier_0.io.web := false.B
            frontier_1.io.wea := false.B
            frontier_1.io.web := false.B
            
            // root in this pe
            when(RegNext(io.root % conf.numSubGraphs.U) === num.U){
                frontier_0.io.ena := true.B
                frontier_0.io.wea := true.B
                frontier_0.io.dina := 1.U
                frontier_0.io.addra := RegNext(io.root >> conf.shift) 


                //init visited_map
                visited_map.io.ena := true.B
                visited_map.io.wea := true.B
                visited_map.io.dina := 1.U
                visited_map.io.addra := RegNext(io.root >> conf.shift) 
            }
            init_bram := true.B
            stateReg := state2
        }
    }

}

object Custom_function3{
    implicit val conf = HBMGraphConfiguration()
    def low(n : UInt) : UInt = 
        n(conf.crossbar_data_width - 1, 0)

}
