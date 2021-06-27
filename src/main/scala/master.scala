package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._


/* chisel中无inout端口，各process所需的上一process的finish信号，是否按照设计从master接出？
 * 添加flag_Qc，其中p1与p4所使用的flag信号应该相反
 * 添加current_level处理
 */



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
  })

  val mem_end_state = Wire(Bool())
  val end_state = Wire(Bool())
  mem_end_state := io.mem_end.reduce(_&_)
  end_state := io.end.reduce(_&_)

  val global_finish_state = RegInit(false.B)
  io.global_finish := global_finish_state

  val p2_cnt_total = RegInit(0.U(conf.Data_width.W))
  val mem_cnt_total = RegInit(0.U(conf.Data_width.W))
  p2_cnt_total := io.p2_count.reduce(_+_)
  mem_cnt_total := io.mem_count.reduce(_+_)
  val p2_pull_count_total = RegNext(io.p2_pull_count.reduce(_+_))
  val frontier_pull_count_total = RegNext(io.frontier_pull_count.reduce(_+_))

  val push_or_pull_state = RegInit(0.U(1.W))
  io.push_or_pull := push_or_pull_state
  dontTouch(io)
  val level = RegInit(0.U(32.W))
  val frontier_flag = RegInit(1.U(1.W))
  io.current_level := level
  io.frontier_flag := frontier_flag
  io.start := false.B

  val state0 :: state1  :: Nil = Enum(2)
  val stateReg = RegInit(state0)
  switch(stateReg){
    is(state0){
      io.start := false.B
      when(end_state && io.global_start){
        stateReg := state1
        frontier_flag := frontier_flag + 1.U

        
        when(level === io.levels.push_to_pull_level){       //change push or pull mode logic
            push_or_pull_state := 1.U
        }.elsewhen(level === io.levels.pull_to_push_level){
            push_or_pull_state := 0.U
        }

        when(io.last_iteration_state.reduce(_&_)){
          global_finish_state := true.B
          stateReg := state0
        } .otherwise{
          level := level + 1.U
        }
      }
    }
    is(state1){
      io.start := RegNext(true.B)
      stateReg := state0
    }
  }

  when(mem_end_state && mem_cnt_total===p2_cnt_total && p2_pull_count_total===frontier_pull_count_total){
        io.p2_end := true.B
  }.otherwise{
        io.p2_end := false.B

  }

  // val ila = Module(new ila_master)

  // ila.io.probe0 := io.start                  //master start
  // ila.io.probe1 := level                     //master current_level
  // ila.io.probe2 := p2_cnt_total              //master p2_cnt_total
  // ila.io.probe3 := mem_cnt_total             //master mem_cnt_total
  // ila.io.probe4 := p2_pull_count_total       //master p2_pull_count_total
  // ila.io.probe5 := frontier_pull_count_total //master frontier_pull_count_total
  // ila.io.probe6 := mem_end_state             //master mem_end_state
  // ila.io.probe7 := io.p2_end                 //master p2_end
  // ila.io.probe8 := end_state                 //master end
  // ila.io.probe9 := stateReg                  //master STATEREG
  // ila.io.clk    := clock




}

class copy(val length : Int)(implicit val conf : HBMGraphConfiguration) extends Module{
  val io = IO(new Bundle{
    val in = Input(UInt(length.W))
    val out = Output(Vec(conf.channel_num, UInt(length.W)))
  })
  for(i <- 0 until conf.channel_num){
    io.out(i) := RegNext(io.in)
  }
}
