package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._


class master(implicit val conf : HBMGraphConfiguration) extends Module{
  val io = IO(new Bundle{
        val global_start = Input(Bool())
        val global_finish = Output(Bool())
        val start = Output(Bool())      //send to p1 and frontier
        val frontier_flag = Output(UInt(1.W))   //send to frontier
        val current_level = Output(UInt(32.W))  //send to mem for level write

        val mem_end = Input(Vec(conf.channel_num,Bool()))   //mem end
        val p2_end = Output(Bool())   //send to frontier 
        val end = Input(Vec(conf.channel_num,Bool()))   // end for each step

        val p2_count = Input(Vec(conf.channel_num,UInt(conf.Data_width.W)))   // count for neighbour check nodes
        val mem_count = Input(Vec(conf.channel_num,UInt(conf.Data_width.W)))  // count for neighbour nodes
        val frontier_pull_count = Input(Vec(conf.channel_num,UInt(conf.Data_width.W)))  // count for pull crossbar nodes
        val p2_pull_count = Input(Vec(conf.channel_num,UInt(conf.Data_width.W)))  // count for pull crossbar nodes
        val last_iteration_state = Input(Vec(conf.channel_num,Bool()))    //show frontier write to assert global_finish

        val levels = Input(new levels)
        val push_or_pull = Output(UInt(1.W))

        val init_bram = Input(Vec(conf.channel_num,Bool())) 
  })


  val last_iteration_state = RegInit(false.B)
  val mem_end_state = RegInit(false.B)
  val end_state = RegInit(false.B)
  
  val and_last_iteration_state  = Module(new and(conf.channel_num))
  val and_mem_end_state         = Module(new and(conf.channel_num))
  val and_end_state             = Module(new and(conf.channel_num))
  for(i <- 0 until conf.channel_num){
    and_last_iteration_state.io.in(i)   <> io.last_iteration_state(i)
    and_mem_end_state.io.in(i)          <> io.mem_end(i)
    and_end_state.io.in(i)              <> io.end(i)
  }
  last_iteration_state <> and_last_iteration_state.io.out
  mem_end_state        <> and_mem_end_state.io.out
  end_state            <> and_end_state.io.out


  val init_state = RegNext(io.init_bram.reduce(_|_))

  val global_finish_state = RegInit(false.B)
  io.global_finish := global_finish_state



  val p2_cnt_total = RegInit(0.U(conf.Data_width.W))
  val mem_cnt_total = RegInit(0.U(conf.Data_width.W))
  val p2_pull_count_total = RegInit(0.U(conf.Data_width.W))
  val frontier_pull_count_total = RegInit(0.U(conf.Data_width.W))

  val adder_p2_cnt = Module(new adder(conf.channel_num, conf.Data_width))
  val adder_mem_cnt = Module(new adder(conf.channel_num, conf.Data_width))
  val adder_p2_pull_count = Module(new adder(conf.channel_num, conf.Data_width))
  val adder_frontier_pull_count = Module(new adder(conf.channel_num, conf.Data_width))
  

  for(i <- 0 until conf.channel_num){
    adder_p2_cnt.io.in(i)               <> io.p2_count(i)
    adder_mem_cnt.io.in(i)              <> io.mem_count(i)
    adder_p2_pull_count.io.in(i)        <> io.p2_pull_count(i)
    adder_frontier_pull_count.io.in(i)  <> io.frontier_pull_count(i)
  }

  p2_cnt_total  <> adder_p2_cnt.io.out
  mem_cnt_total <> adder_mem_cnt.io.out
  p2_pull_count_total <> adder_p2_pull_count.io.out
  frontier_pull_count_total <> adder_frontier_pull_count.io.out
  

  val push_or_pull_state = RegInit(0.U(1.W))
  io.push_or_pull := push_or_pull_state
  dontTouch(io)
  val level = RegInit(0.U(32.W))
  val frontier_flag = RegInit(1.U(1.W))
  io.current_level := level
  io.frontier_flag := frontier_flag
  io.start := false.B

  val count_for_start = RegInit(0.U(5.W))
  count_for_start := 0.U
  //init bram
  val init_bram = io.init_bram

  val state0 :: state1  :: Nil = Enum(2)
  val stateReg = RegInit(state0)
  switch(stateReg){
    is(state0){
      io.start := false.B
      count_for_start := 0.U
      when(end_state && io.global_start && init_state){
        stateReg := state1
        frontier_flag := frontier_flag + 1.U

        
        when(level === io.levels.push_to_pull_level){       //change push or pull mode logic
            push_or_pull_state := 1.U
        }.elsewhen(level === io.levels.pull_to_push_level){
            push_or_pull_state := 0.U
        }

        when(last_iteration_state){ //io.last_iteration_state.reduce(_&_)
          global_finish_state := true.B
          stateReg := state0
        } .otherwise{
          level := level + 1.U
        }
      }
    }
    is(state1){
      count_for_start := count_for_start + 1.U
      io.start := RegNext(true.B)
      when(count_for_start === 20.U){
        stateReg := state0
        count_for_start := 0.U
      }

    }
  }

  when(mem_end_state && mem_cnt_total===p2_cnt_total && p2_pull_count_total===frontier_pull_count_total){
        io.p2_end := true.B
  }.otherwise{
        io.p2_end := false.B

  }

}

class copy(val length : Int)(implicit val conf : HBMGraphConfiguration) extends Module{
  val io = IO(new Bundle{
    val in = Input(UInt(length.W))
    val out = Output(Vec(conf.channel_num, UInt(length.W)))
  })
  val out_reg = RegInit(VecInit(Seq.fill(conf.channel_num)(0.U(length.W))))
  for(i <- 0 until conf.channel_num){
    out_reg(i) := io.in
    // io.out(i) := RegNext(io.in)
  }
  io.out := out_reg
}

class copy_pipe(val length : Int)(implicit val conf : HBMGraphConfiguration) extends Module{
  val io = IO(new Bundle{
    val in = Input(UInt(length.W))
    val out = Output(Vec(conf.pipe_num_per_channel, UInt(length.W)))
  })
  val out_reg = RegInit(VecInit(Seq.fill(conf.pipe_num_per_channel)(0.U(length.W))))
  for(i <- 0 until conf.pipe_num_per_channel){
    out_reg(i) := io.in
  }
  io.out := out_reg
}

class copy_slr0(val length : Int)(implicit val conf : HBMGraphConfiguration) extends Module{
  val io = IO(new Bundle{
    val in = Input(UInt(length.W))
    val out = Output(Vec(conf.slr0_channel_num, UInt(length.W)))
  })
  val out_reg = RegInit(VecInit(Seq.fill(conf.slr0_channel_num)(0.U(length.W))))
  for(i <- 0 until conf.slr0_channel_num){
    out_reg(i) := io.in
  }
  io.out := out_reg
}

class copy_slr1(val length : Int)(implicit val conf : HBMGraphConfiguration) extends Module{
  val io = IO(new Bundle{
    val in = Input(UInt(length.W))
    val out = Output(Vec(conf.slr1_channel_num, UInt(length.W)))
  })
  val out_reg = RegInit(VecInit(Seq.fill(conf.slr1_channel_num)(0.U(length.W))))
  for(i <- 0 until conf.slr1_channel_num){
    out_reg(i) := io.in
  }
  io.out := out_reg
}

class copy_slr2(val length : Int)(implicit val conf : HBMGraphConfiguration) extends Module{
  val io = IO(new Bundle{
    val in = Input(UInt(length.W))
    val out = Output(Vec(conf.slr2_channel_num, UInt(length.W)))
  })
  val out_reg = RegInit(VecInit(Seq.fill(conf.slr2_channel_num)(0.U(length.W))))
  for(i <- 0 until conf.slr2_channel_num){
    out_reg(i) := io.in
  }
  io.out := out_reg
}