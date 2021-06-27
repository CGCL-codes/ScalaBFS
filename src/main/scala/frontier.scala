package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._

/*  push -> pull mode
*   p1 read current_frontier -> p1 read visited_map                         √
*   p2 read visited_map -> p2 read current_frontier                         √
*   p2 write next_frontier -> crossbar write visited_map + next_frontier    √
*   visited_map will be read and write at the same time (write first)
*/


class Frontier_IO(implicit val conf : HBMGraphConfiguration) extends Bundle{
    //Input
    val frontier_count = Flipped(Decoupled(UInt(conf.Data_width.W)))        // input count of the required current_frontier
    val write_frontier = Vec(2, Flipped(Decoupled(UInt(conf.Data_width.W))))// input 2 next_frontier you want to write
    val frontier_flag = Input(UInt(1.W))    // input flag mark which frontier to use as current_frontier or next_frontier
    val p2_end = Input(Bool())           // input p2 finish signal
    val bram_clock = Input(Clock())            // input clock with 2x frenquency. used by bram.
    val start = Input(Bool())       // input start  
    val node_num = Input(UInt(conf.Data_width.W))
    val push_or_pull_state = Input(UInt(1.W))   //input flag mark push or pull state
    val level = Input(UInt(conf.Data_width.W))
    val uram_addr_a = Input(UInt(conf.Addr_width_uram.W))
    val uram_addr_b = Input(UInt(conf.Addr_width_uram.W))
    val bram_from_p2 = Vec(2, Flipped(Decoupled(UInt(conf.Data_width_p2_to_f.W))))
    
    //Output
    val frontier_value = Decoupled(UInt(conf.Data_width_bram.W))  // output frontier data
    val end = Output(Bool())    // output end signal
    val last_iteration = Output(Bool())     // output. write next frontier in last iteration or not.
    // val bram_from_p2 = Decoupled(UInt(conf.Data_width_p2_to_f.W))
    val uram_out_a = Output(UInt(conf.Data_width_uram.W))
    val uram_out_b = Output(UInt(conf.Data_width_uram.W))
    val frontier_pull_count = Output(UInt(conf.Data_width.W)) // pull crossbar count
    val frontier_to_p2 = Vec(2,Decoupled(UInt(1.W)))

}

class Frontier (val num :Int) (implicit val conf : HBMGraphConfiguration) extends Module{
    val io = IO(new Frontier_IO())
    dontTouch(io)
    io.frontier_value.valid := false.B
    io.frontier_value.bits := DontCare
    io.frontier_to_p2(0).valid := false.B
    io.frontier_to_p2(0).bits := DontCare
    io.frontier_to_p2(1).valid := false.B
    io.frontier_to_p2(1).bits := DontCare
    io.end := true.B

    val last_iteration_reg = RegInit(false.B)
    io.last_iteration := last_iteration_reg


    // val copy_sign_1 = Module(new copy_sign_1)
    // copy_sign_1.io.a := io.frontier_flag
    // copy_sign_1.io.clk := clock
    // copy_sign_1.io.reset := reset.asBool()
    // val frontier_flag_copy1 = copy_sign_1.io.b
    // val frontier_flag_copy2 = copy_sign_1.io.c
    // val frontier_flag_copy3 = copy_sign_1.io.d

    // val copy_sign_2 = Module(new copy_sign_1)
    // copy_sign_2.io.a := io.push_or_pull_state
    // copy_sign_2.io.clk := clock
    // copy_sign_2.io.reset := reset.asBool()
    // val push_or_pull_state_copy1 = copy_sign_2.io.b
    // val push_or_pull_state_copy2 = copy_sign_2.io.c
    // val push_or_pull_state_copy3 = copy_sign_2.io.d



    // two frontiers
    val frontier_0 = Module(new bram_controller_clock_domain_conversion(num, 1))
    val frontier_1 = Module(new bram_controller_clock_domain_conversion(num, 2))
    val uram = Module(new uram_controller(num, 3))
    frontier_0.io := DontCare
    // frontier_0.io.ena := true.B
    // frontier_0.io.enb := true.B
    frontier_0.io.ena := false.B
    frontier_0.io.enb := false.B
    frontier_0.io.clka := io.bram_clock
    frontier_0.io.clkb := io.bram_clock
    frontier_0.io.wea := false.B
    frontier_0.io.web := false.B
    frontier_0.io.wmode := 0.U
    frontier_0.io.data_out_ready_a := false.B
    frontier_0.io.data_out_ready_b := false.B
    frontier_0.io.no_read_a := 0.U
    frontier_0.io.no_read_b := 0.U

    frontier_1.io := DontCare
    frontier_1.io.ena := false.B
    frontier_1.io.enb := false.B
    frontier_1.io.clka := io.bram_clock
    frontier_1.io.clkb := io.bram_clock
    frontier_1.io.wea := false.B
    frontier_1.io.web := false.B
    frontier_1.io.wmode := 0.U
    frontier_1.io.data_out_ready_a := false.B
    frontier_1.io.data_out_ready_b := false.B
    frontier_1.io.no_read_a := 0.U
    frontier_1.io.no_read_b := 0.U

    uram.io := DontCare
    uram.io.clka := clock //io.bram_clock
    uram.io.clkb := clock //io.bram_clock
    uram.io.wea := 0.U
    uram.io.web := 0.U
    io.uram_out_a := uram.io.douta
    io.uram_out_b := uram.io.doutb
    io.uram_addr_a <> uram.io.addra
    io.uram_addr_b <> uram.io.addrb
    
    // input Queue
    // 使用多层Queue进行缓冲，改善时序，缓冲所使用的Queue深度不能为1，如果深度为1，隔一拍才能处理一个数据
    val q_frontier_count_l1 = Queue(io.frontier_count, 2)
    val q_frontier_count_l2 = Queue(q_frontier_count_l1, 2)
    // val q_frontier_count_l3 = Queue(q_frontier_count_l2, 1)
    // val q_frontier_count_l4 = Queue(q_frontier_count_l3, 1)
    val q_frontier_count = Queue(q_frontier_count_l2, conf.q_p1_to_frontier_len)

    val bram_from_p2_0 = Queue(io.bram_from_p2(0), conf.q_p2_to_frontier_len)
    val bram_from_p2_1 = Queue(io.bram_from_p2(1), conf.q_p2_to_frontier_len)


    val q_write_frontier_0_l1 = Queue(io.write_frontier(0), 2)
    val q_write_frontier_0_l2 = Queue(q_write_frontier_0_l1, 2)
    // val q_write_frontier_0_l3 = Queue(q_write_frontier_0_l2, 1)
    // val q_write_frontier_0_l4 = Queue(q_write_frontier_0_l3, 1)

    val q_write_frontier_1_l1 = Queue(io.write_frontier(1), 2)
    val q_write_frontier_1_l2 = Queue(q_write_frontier_1_l1, 2)
    // val q_write_frontier_1_l3 = Queue(q_write_frontier_1_l2, 1)
    // val q_write_frontier_1_l4 = Queue(q_write_frontier_1_l3, 1)

    val q_write_frontier_0 = Queue(q_write_frontier_0_l2, conf.q_p2_to_frontier_len)
    val q_write_frontier_1 = Queue(q_write_frontier_1_l2, conf.q_p2_to_frontier_len)
    q_frontier_count.ready := false.B
    q_write_frontier_0.ready := false.B
    q_write_frontier_1.ready := false.B

    // val copy_sign_write_frontier_0 = Module(new copy_sign_32)
    // copy_sign_write_frontier_0.io.a := q_write_frontier_0.bits
    // copy_sign_write_frontier_0.io.clk := clock
    // copy_sign_write_frontier_0.io.reset := reset.asBool()
    // val copy_sign_write_frontier_0_copy1 = copy_sign_write_frontier_0.io.b
    // val copy_sign_write_frontier_0_copy2 = copy_sign_write_frontier_0.io.c
    // val copy_sign_write_frontier_0_copy3 = copy_sign_write_frontier_0.io.d

    // val copy_sign_write_frontier_1 = Module(new copy_sign_32)
    // copy_sign_write_frontier_1.io.a := q_write_frontier_1.bits
    // copy_sign_write_frontier_1.io.clk := clock
    // copy_sign_write_frontier_1.io.reset := reset.asBool()
    // val copy_sign_write_frontier_1_copy1 = copy_sign_write_frontier_1.io.b
    // val copy_sign_write_frontier_1_copy2 = copy_sign_write_frontier_1.io.c
    // val copy_sign_write_frontier_1_copy3 = copy_sign_write_frontier_1.io.d

    // counter
    // frontier_pull_count 计数内容为p2发送过来的write frontier数量
    val (count_wf0, _) = Counter(q_write_frontier_0.ready && q_write_frontier_0.valid, 2147483647)
    val (count_wf1, _) = Counter(q_write_frontier_1.ready && q_write_frontier_1.valid, 2147483647)
    io.frontier_pull_count := count_wf0 + count_wf1

    //as designed , at beginning io.p2_end is high. should wait it low and high again.
    val p2_end_flag = RegInit(0.U(1.W))
    when(io.p2_end === false.B){p2_end_flag := 1.U}

    val clear_addr = RegInit(0.U(conf.Addr_width.W))
    val state0 :: state1 :: state2 :: state_write_uram :: Nil = Enum(4)
    val stateReg = RegInit(state2)

    //在p2读取visited_map或current_frontier中某个点的值时，缓冲点编号
    val node_queue_a = Module(new Queue(UInt(conf.Node_width.W), conf.node_queue_depth))
    val node_queue_b = Module(new Queue(UInt(conf.Node_width.W), conf.node_queue_depth))

    node_queue_a.io.enq.valid := false.B
    node_queue_a.io.enq.bits := DontCare
    node_queue_a.io.deq.ready := false.B

    node_queue_b.io.enq.valid := false.B
    node_queue_b.io.enq.bits := DontCare
    node_queue_b.io.deq.ready := false.B

    bram_from_p2_0.ready := false.B
    bram_from_p2_1.ready := false.B


    // 处理从p2发送过来的读数据内容
    // io.bram_from_p2 := DontCare
    //for visited_map
    val visited_map = Module(new bram_controller_clock_domain_conversion(num, 0))
    visited_map.io := DontCare
    // push mode, read and write
    visited_map.io.clka := io.bram_clock
    visited_map.io.clkb := io.bram_clock
    visited_map.io.ena := false.B
    visited_map.io.enb := false.B
    visited_map.io.wea := false.B
    visited_map.io.web := false.B
    visited_map.io.data_out_ready_a := false.B
    visited_map.io.data_out_ready_b := false.B
    visited_map.io.no_read_a := 0.U
    visited_map.io.no_read_b := 0.U

    // push模式下需要读后写visited_map,需要在visited_map能够读取，且node编号可以缓冲的情况下给出ready信号
    // 需要给出ready信号等待valid信号
    when(io.push_or_pull_state === 0.U){
        bram_from_p2_0.ready := visited_map.io.data_in_ready_a && node_queue_a.io.enq.ready
        bram_from_p2_1.ready := visited_map.io.data_in_ready_b && node_queue_b.io.enq.ready
        visited_map.io.wmode := 0.U
        when(bram_from_p2_0.valid){ // && io.frontier_to_p2(0).ready){
            visited_map.io.ena := true.B
            visited_map.io.addra := loc.addr(bram_from_p2_0.bits) 
            
            visited_map.io.wea := loc.we(bram_from_p2_0.bits)
            // visited_map.io.wmode := loc.wmode(bram_from_p2_0.bits)
            visited_map.io.nodea := loc.node(bram_from_p2_0.bits)
            visited_map.io.no_read_a := 0.U

            node_queue_a.io.enq.valid := true.B
            node_queue_a.io.enq.bits := loc.node(bram_from_p2_0.bits)
            // io.frontier_to_p2(0).valid := true.B
            // io.frontier_to_p2(0).bits := visited_map.io.douta(loc.node(bram_from_p2_0.bits))


        }
        when(bram_from_p2_1.valid){ // && io.frontier_to_p2(1).ready){
            visited_map.io.enb := true.B
            visited_map.io.addrb := loc.addr(bram_from_p2_1.bits) 
            visited_map.io.web := loc.we(bram_from_p2_1.bits)
            // visited_map.io.wmode := loc.wmode(bram_from_p2_1.bits)
            visited_map.io.nodeb := loc.node(bram_from_p2_1.bits)
            visited_map.io.no_read_b := 0.U

            node_queue_b.io.enq.valid := true.B
            node_queue_b.io.enq.bits := loc.node(bram_from_p2_1.bits)
            // io.frontier_to_p2(1).valid := true.B
            // io.frontier_to_p2(1).bits := visited_map.io.doutb(loc.node(bram_from_p2_1.bits))
        }


        when(io.frontier_to_p2(0).ready && visited_map.io.data_out_valid_a && node_queue_a.io.deq.valid){
            node_queue_a.io.deq.ready := true.B
            visited_map.io.data_out_ready_a := true.B
            io.frontier_to_p2(0).valid := true.B
            io.frontier_to_p2(0).bits := visited_map.io.douta(node_queue_a.io.deq.bits)
        }


        when(io.frontier_to_p2(1).ready && visited_map.io.data_out_valid_b  && node_queue_b.io.deq.valid){
            node_queue_b.io.deq.ready := true.B
            visited_map.io.data_out_ready_b := true.B
            io.frontier_to_p2(1).valid := true.B
            io.frontier_to_p2(1).bits := visited_map.io.doutb(node_queue_b.io.deq.bits)
        }
        // visited_map.io.addra := io.bram_from_p2.addra
        // visited_map.io.addrb := io.bram_from_p2.addrb

        // // visited_map.io.clka := io.bram_from_p2.clka
        // // visited_map.io.clkb := io.bram_from_p2.clkb
        // visited_map.io.wea := io.bram_from_p2.wea
        // visited_map.io.web := io.bram_from_p2.web
        // visited_map.io.wmode := io.bram_from_p2.wmode
        // visited_map.io.nodea := io.bram_from_p2.nodea
        // visited_map.io.nodeb := io.bram_from_p2.nodeb
        // io.bram_from_p2.douta := visited_map.io.douta
        // io.bram_from_p2.doutb := visited_map.io.doutb
    }
    // pull mode, current_frontier is frontier_0, read only
    when(io.push_or_pull_state === 1.U && io.frontier_flag === 0.U){

        bram_from_p2_0.ready := frontier_0.io.data_in_ready_a && node_queue_a.io.enq.ready
        bram_from_p2_1.ready := frontier_0.io.data_in_ready_b && node_queue_b.io.enq.ready

        when(bram_from_p2_0.valid){ //  && io.frontier_to_p2(0).ready){
            frontier_0.io.ena := true.B
            frontier_0.io.addra := loc.addr(bram_from_p2_0.bits)

            frontier_0.io.no_read_a := 0.U
            node_queue_a.io.enq.valid := true.B
            node_queue_a.io.enq.bits := loc.node(bram_from_p2_0.bits)
            // io.frontier_to_p2(0).valid := true.B
            // io.frontier_to_p2(0).bits := frontier_0.io.douta(loc.node(bram_from_p2_0.bits))
                   
        }
        when(bram_from_p2_1.valid){ //&& io.frontier_to_p2(1).ready){
            frontier_0.io.enb := true.B
            frontier_0.io.addrb := loc.addr(bram_from_p2_1.bits)
            frontier_0.io.no_read_b := 0.U
            node_queue_b.io.enq.valid := true.B
            node_queue_b.io.enq.bits := loc.node(bram_from_p2_1.bits)
            // io.frontier_to_p2(1).valid := true.B
            // io.frontier_to_p2(1).bits := frontier_0.io.doutb(loc.node(bram_from_p2_1.bits))
        }


        when(io.frontier_to_p2(0).ready && frontier_0.io.data_out_valid_a && node_queue_a.io.deq.valid){
            node_queue_a.io.deq.ready := true.B
            frontier_0.io.data_out_ready_a := true.B
            io.frontier_to_p2(0).valid := true.B
            io.frontier_to_p2(0).bits := frontier_0.io.douta(node_queue_a.io.deq.bits)
        }


        when(io.frontier_to_p2(1).ready && frontier_0.io.data_out_valid_b && node_queue_b.io.deq.valid){
            node_queue_b.io.deq.ready := true.B
            frontier_0.io.data_out_ready_b := true.B
            io.frontier_to_p2(1).valid := true.B
            io.frontier_to_p2(1).bits := frontier_0.io.doutb(node_queue_b.io.deq.bits)
        }
        // frontier_0.io.addra := io.bram_from_p2.addra
        // frontier_0.io.addrb := io.bram_from_p2.addrb
        // io.bram_from_p2.douta := frontier_0.io.douta
        // io.bram_from_p2.doutb := frontier_0.io.doutb
    }
    // pull mode, current_frontier is frontier_1, read only
    when(io.push_or_pull_state === 1.U && io.frontier_flag === 1.U){

        bram_from_p2_0.ready := frontier_1.io.data_in_ready_a && node_queue_a.io.enq.ready
        bram_from_p2_1.ready := frontier_1.io.data_in_ready_b && node_queue_b.io.enq.ready

        when(bram_from_p2_0.valid){ // && io.frontier_to_p2(0).ready){
            frontier_1.io.ena := true.B
            frontier_1.io.addra := loc.addr(bram_from_p2_0.bits)
            frontier_1.io.no_read_a := 0.U
            node_queue_a.io.enq.valid := true.B
            node_queue_a.io.enq.bits := loc.node(bram_from_p2_0.bits)
            // io.frontier_to_p2(0).valid := true.B
            // io.frontier_to_p2(0).bits := frontier_1.io.douta(loc.node(bram_from_p2_0.bits))
                   
        }
        when(bram_from_p2_1.valid){ //&& io.frontier_to_p2(1).ready){
            frontier_1.io.enb := true.B
            frontier_1.io.addrb := loc.addr(bram_from_p2_1.bits)
            frontier_1.io.no_read_b := 0.U
            node_queue_b.io.enq.valid := true.B
            node_queue_b.io.enq.bits := loc.node(bram_from_p2_1.bits)
            // io.frontier_to_p2(1).valid := true.B
            // io.frontier_to_p2(1).bits := frontier_1.io.doutb(loc.node(bram_from_p2_1.bits))
        }


        when(io.frontier_to_p2(0).ready && frontier_1.io.data_out_valid_a && node_queue_a.io.deq.valid){
            node_queue_a.io.deq.ready := true.B
            frontier_1.io.data_out_ready_a := true.B
            io.frontier_to_p2(0).valid := true.B
            io.frontier_to_p2(0).bits := frontier_1.io.douta(node_queue_a.io.deq.bits)
        }


        when(io.frontier_to_p2(1).ready && frontier_1.io.data_out_valid_b && node_queue_b.io.deq.valid){
            node_queue_b.io.deq.ready := true.B
            frontier_1.io.data_out_ready_b := true.B
            io.frontier_to_p2(1).valid := true.B
            io.frontier_to_p2(1).bits := frontier_1.io.doutb(node_queue_b.io.deq.bits)
        }
        // frontier_1.io.addra := io.bram_from_p2.addra
        // frontier_1.io.addrb := io.bram_from_p2.addrb
        // io.bram_from_p2.douta := frontier_1.io.douta
        // io.bram_from_p2.doutb := frontier_1.io.doutb
    }

    // for pull mode
    val port_a_is_writing_flag = Wire(Bool())  // visited_map is write first in pull mode
    val port_b_is_writing_flag = Wire(Bool())  // visited_map is write first in pull mode
    port_a_is_writing_flag := false.B
    port_b_is_writing_flag := false.B

    // for write level
    // val node_a0 = RegInit(0.U(conf.Data_width.W))
    // val node_a1 = RegInit(0.U(conf.Data_width.W))
    // val frontier_a = RegInit(0.U(conf.Data_width.W))
    // val count_node_in_frontier_a0 = RegInit(0.U(conf.Data_width.W))
    // val count_node_in_frontier_a1 = RegInit(0.U(conf.Data_width.W))
    // val node_num_in_frontier_a0 = RegInit(0.U(conf.Data_width.W))
    // val node_num_in_frontier_a1 = RegInit(0.U(conf.Data_width.W))
    // val node_b0 = RegInit(0.U(conf.Data_width.W))
    // val node_b1 = RegInit(0.U(conf.Data_width.W))
    // val frontier_b = RegInit(0.U(conf.Data_width.W))
    // val count_node_in_frontier_b0 = RegInit(0.U(conf.Data_width.W))
    // val count_node_in_frontier_b1 = RegInit(0.U(conf.Data_width.W))
    // val node_num_in_frontier_b0 = RegInit(0.U(conf.Data_width.W))
    // val node_num_in_frontier_b1 = RegInit(0.U(conf.Data_width.W))
    val delay_for_clear = RegInit(0.U(5.W))
    val cond1 = !q_write_frontier_0.valid //|| !q_write_frontier_1.valid
    val cond2 = cond1 || io.push_or_pull_state === 0.U

    switch(stateReg){
        is(state0){
            io.end := false.B
            //state 0 read and write
            q_frontier_count.ready := false.B
            q_write_frontier_0.ready := false.B
            q_write_frontier_1.ready := false.B
            frontier_0.io.wea := false.B
            frontier_0.io.web := false.B
            frontier_1.io.wea := false.B
            frontier_1.io.web := false.B
            frontier_0.io.wmode := 0.U
            frontier_1.io.wmode := 0.U
            // frontier_0.io.data_out_ready_a := false.B
            // frontier_0.io.data_out_ready_b := false.B
            frontier_0.io.no_read_a := 0.U
            frontier_0.io.no_read_b := 0.U
            // frontier_1.io.data_out_ready_a := false.B
            // frontier_1.io.data_out_ready_b := false.B
            frontier_1.io.no_read_a := 0.U
            frontier_1.io.no_read_b := 0.U  

            // visited_map is write first in pull mode
            // when(q_frontier_count.valid && io.frontier_value.ready && (!port_a_is_writing_flag || !port_b_is_writing_flag)){
            //接收p1读请求
/*          在接收frontier_cout时，虽然bram已经修改为类似decoupled接口，仍需要使用frontier_value.ready做约束条件，否则会因为fifo_in中的ready信号造成死锁
            e.g.（pull）原因追溯
            memory c ready 0 卡住 
            -> p2 收 neighbour ready 0 
            -> p2 write visited_map ready 0
            -> visited_map data_in_ready 0
            -> visited_map data_out_ready 0
            -> p1 read frontier_value ready 0
            -> p1 read memory R_array_index ready 0
            -> memory c 卡住
*/
            when(q_frontier_count.valid && io.frontier_value.ready && cond2){
                // q_frontier_count.ready := true.B
                // read visited_map in pull mode
                when(io.push_or_pull_state === 1.U){
                    // when(!q_write_frontier_0.valid && !q_write_frontier_0.valid){         // port a & b are free
                    when(!q_write_frontier_0.valid && visited_map.io.data_in_ready_a){ 
                        q_frontier_count.ready := true.B
                        visited_map.io.ena := true.B
                        visited_map.io.addra := q_frontier_count.bits
                        visited_map.io.no_read_a := 0.U
 
                    }
                }

                // read from current frontier in push mode
                when(io.push_or_pull_state === 0.U ){
                    when(io.frontier_flag === 0.U && frontier_0.io.data_in_ready_a){
                        q_frontier_count.ready := true.B
                        frontier_0.io.ena := true.B
                        frontier_0.io.addra := q_frontier_count.bits
                        frontier_0.io.no_read_a := 0.U

                    }
                    when(io.frontier_flag === 1.U && frontier_1.io.data_in_ready_a){
                        q_frontier_count.ready := true.B
                        frontier_1.io.ena := true.B
                        frontier_1.io.addra := q_frontier_count.bits
                        frontier_1.io.no_read_a := 0.U

                    }
                }

            }

            //反馈p1读请求,出数据不需要考虑write_frontier
            when(io.frontier_value.ready){
                // q_frontier_count.ready := true.B
                // read visited_map in pull mode
                when(io.push_or_pull_state === 1.U){
                    // when(!q_write_frontier_0.valid && !q_write_frontier_0.valid){         // port a & b are free
                    when(visited_map.io.data_out_valid_a){ 

                        visited_map.io.data_out_ready_a := true.B
                        io.frontier_value.valid := true.B
                        io.frontier_value.bits := visited_map.io.douta    
                    }
                }

                // read from current frontier in push mode
                when(io.push_or_pull_state === 0.U ){
                    when(io.frontier_flag === 0.U){
                        when(frontier_0.io.data_out_valid_a){
                            io.frontier_value.valid := true.B
                            io.frontier_value.bits := frontier_0.io.douta
                            frontier_0.io.data_out_ready_a := true.B
                        }
                    }
                    when(io.frontier_flag === 1.U){
                        when(frontier_1.io.data_out_valid_a){
                            io.frontier_value.valid := true.B
                            io.frontier_value.bits := frontier_1.io.douta
                            frontier_1.io.data_out_ready_a := true.B
                        }
                    }
                }

            }

            //根据处理能力拉高write_frontier的ready信号
            //实际上只需要在pull模式下判断vitited_map即可，因为只有visited_map才会在读frontier_value和write_frontier阶段有冲突，可能会导致写frontier丢失
            when(io.push_or_pull_state === 0.U){
                when((io.frontier_flag === 0.U && frontier_1.io.data_in_ready_a) || (io.frontier_flag === 1.U && frontier_0.io.data_in_ready_a)){
                    q_write_frontier_0.ready := true.B
                }
                when((io.frontier_flag === 0.U && frontier_1.io.data_in_ready_b) || (io.frontier_flag === 1.U && frontier_0.io.data_in_ready_b)){
                    q_write_frontier_1.ready := true.B
                }
            }

            when(io.push_or_pull_state === 1.U){
                when(visited_map.io.data_in_ready_a && ((io.frontier_flag === 0.U && frontier_1.io.data_in_ready_a) || (io.frontier_flag === 1.U && frontier_0.io.data_in_ready_a))){
                    q_write_frontier_0.ready := true.B
                }
                when(visited_map.io.data_in_ready_b && ((io.frontier_flag === 0.U && frontier_1.io.data_in_ready_b) || (io.frontier_flag === 1.U && frontier_0.io.data_in_ready_b))){
                    q_write_frontier_1.ready := true.B
                }
            } 
            //p2写请求
            when(q_write_frontier_0.valid){

                last_iteration_reg := false.B
                //write port a in next frontier
                when(io.push_or_pull_state === 1.U){
                    visited_map.io.ena := true.B
                    visited_map.io.wea := true.B

                    visited_map.io.no_read_a := 1.U
                    
                    // convert the total number of points to the number inside the pipeline
                    visited_map.io.nodea := (Custom_function3.low(q_write_frontier_0.bits) / conf.numSubGraphs.U) % conf.Data_width_bram.U
                    visited_map.io.addra := (Custom_function3.low(q_write_frontier_0.bits) / conf.numSubGraphs.U) / conf.Data_width_bram.U
                    port_a_is_writing_flag := true.B   // port a is write first
                }
                when(io.frontier_flag === 0.U){
                    //now next frontier is frontier_1
                    frontier_1.io.ena := true.B
                    frontier_1.io.wea := true.B
                    frontier_1.io.no_read_a := 1.U
                    frontier_1.io.nodea := (Custom_function3.low(q_write_frontier_0.bits) / conf.numSubGraphs.U) % conf.Data_width_bram.U
                    frontier_1.io.addra := (Custom_function3.low(q_write_frontier_0.bits) / conf.numSubGraphs.U) / conf.Data_width_bram.U
                }
                when(io.frontier_flag === 1.U){
                    //now next frontier is frontier_0
                    frontier_0.io.ena := true.B
                    frontier_0.io.wea := true.B
                    frontier_0.io.no_read_a := 1.U
                    frontier_0.io.nodea := (Custom_function3.low(q_write_frontier_0.bits) / conf.numSubGraphs.U) % conf.Data_width_bram.U
                    frontier_0.io.addra := (Custom_function3.low(q_write_frontier_0.bits) / conf.numSubGraphs.U) / conf.Data_width_bram.U
                }
                uram.io.wea := 1.U << ((Custom_function3.low(q_write_frontier_0.bits) / conf.numSubGraphs.U) % conf.Write_width_uram.U)
                uram.io.dina := io.level << (8.U * ((Custom_function3.low(q_write_frontier_0.bits) / conf.numSubGraphs.U) % conf.Write_width_uram.U))
                uram.io.addra := (Custom_function3.low(q_write_frontier_0.bits) / conf.numSubGraphs.U) / conf.Write_width_uram.U
            }
            when(q_write_frontier_1.valid){
                last_iteration_reg := false.B
                //write port b in next frontier
                when(io.push_or_pull_state === 1.U){
                    visited_map.io.enb := true.B
                    visited_map.io.web := true.B
                    visited_map.io.no_read_b := 1.U
                    visited_map.io.nodeb := (Custom_function3.low(q_write_frontier_1.bits) / conf.numSubGraphs.U) % conf.Data_width_bram.U
                    visited_map.io.addrb := (Custom_function3.low(q_write_frontier_1.bits) / conf.numSubGraphs.U) / conf.Data_width_bram.U
                    port_b_is_writing_flag := true.B
                }
                when(io.frontier_flag === 0.U){
                    //now next frontier is frontier_1
                    frontier_1.io.enb := true.B
                    frontier_1.io.web := true.B
                    frontier_1.io.no_read_b := 1.U
                    frontier_1.io.nodeb := (Custom_function3.low(q_write_frontier_1.bits) / conf.numSubGraphs.U) % conf.Data_width_bram.U
                    frontier_1.io.addrb := (Custom_function3.low(q_write_frontier_1.bits) / conf.numSubGraphs.U) / conf.Data_width_bram.U
                }
                when(io.frontier_flag === 1.U){
                    //now next frontier is frontier_0
                    frontier_0.io.enb := true.B
                    frontier_0.io.web := true.B
                    frontier_0.io.no_read_b := 1.U
                    frontier_0.io.nodeb := (Custom_function3.low(q_write_frontier_1. bits) / conf.numSubGraphs.U) % conf.Data_width_bram.U
                    frontier_0.io.addrb := (Custom_function3.low(q_write_frontier_1.bits) / conf.numSubGraphs.U) / conf.Data_width_bram.U
                }
                uram.io.web := 1.U << ((Custom_function3.low(q_write_frontier_1.bits) / conf.numSubGraphs.U) % conf.Write_width_uram.U)
                uram.io.dinb := io.level << (8.U * ((Custom_function3.low(q_write_frontier_1.bits) / conf.numSubGraphs.U) % conf.Write_width_uram.U))
                uram.io.addrb := (Custom_function3.low(q_write_frontier_1.bits) / conf.numSubGraphs.U) / conf.Write_width_uram.U
            }

            when(io.p2_end && p2_end_flag === 1.U && !q_frontier_count.valid && !q_write_frontier_0.valid && !q_write_frontier_1.valid){
                stateReg := state1
                clear_addr := 0.U
            }
        }
        is(state1){  // clear bits
            io.end := false.B
            q_frontier_count.ready := false.B
            q_write_frontier_0.ready := false.B
            q_write_frontier_1.ready := false.B
            frontier_0.io.wea := false.B
            frontier_0.io.web := false.B
            frontier_1.io.wea := false.B
            frontier_1.io.web := false.B
            frontier_0.io.wmode := 1.U
            frontier_1.io.wmode := 1.U
            frontier_0.io.data_out_ready_a := false.B
            frontier_0.io.data_out_ready_b := false.B
            frontier_0.io.no_read_a := 1.U
            frontier_0.io.no_read_b := 1.U
            frontier_1.io.data_out_ready_a := false.B
            frontier_1.io.data_out_ready_b := false.B
            frontier_1.io.no_read_a := 1.U
            frontier_1.io.no_read_b := 1.U  

            clear_addr := clear_addr + 4.U
            when(io.frontier_flag === 0.U){
                // now current frontier is frontier 0 , at next level it will become next frontier , so clear it
                frontier_0.io.wea := true.B
                frontier_0.io.web := true.B
                frontier_0.io.dina := 0.U
                frontier_0.io.dinb := 0.U
                frontier_0.io.addra := clear_addr
                frontier_0.io.addrb := clear_addr + 2.U
                frontier_0.io.ena := true.B
                frontier_0.io.enb := true.B

                when(clear_addr >= (io.node_num / conf.Data_width_bram.U)){ // >= -> >
                    stateReg := state2
                    // io.end := true.B
                }

            }
            when(io.frontier_flag === 1.U){
                // now current frontier is frontier 1 , at next level it will become next frontier , so clear it
                frontier_1.io.wea := true.B
                frontier_1.io.web := true.B
                frontier_1.io.dina := 0.U
                frontier_1.io.dinb := 0.U
                frontier_1.io.addra := clear_addr
                frontier_1.io.addrb := clear_addr + 2.U
                frontier_1.io.ena := true.B
                frontier_1.io.enb := true.B

                when(clear_addr >= (io.node_num / conf.Data_width_bram.U)){
                    stateReg := state2
                    // io.end := true.B
                }
            }
        }
        is(state2){
            //添加延迟保证清空操作完成，预计延迟为5拍，空出20拍保证正确性
            when(delay_for_clear < 20.U){
                delay_for_clear := delay_for_clear + 1.U
            }
            // when(delay_for_clear === 20.U){
            //     io.end := true.B
            // }
            io.end := Mux(delay_for_clear === 20.U, true.B, false.B)
            q_frontier_count.ready := false.B
            q_write_frontier_0.ready := false.B
            q_write_frontier_1.ready := false.B
            frontier_0.io.wea := false.B
            frontier_0.io.web := false.B
            frontier_1.io.wea := false.B
            frontier_1.io.web := false.B
            frontier_0.io.wmode := 0.U
            frontier_1.io.wmode := 0.U
            when(io.start){
                io.end := false.B
                stateReg := state0
                last_iteration_reg := true.B
                delay_for_clear := 0.U
                p2_end_flag := 0.U
            }
        }
    }
}

object Custom_function3{
    implicit val conf = HBMGraphConfiguration()
    def low(n : UInt) : UInt = 
        n(conf.crossbar_data_width - 1, 0)

}
