package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._

class ila_master_IO(implicit val conf : HBMGraphConfiguration) extends Bundle{
    val clk    = Input(Clock())
    val probe0 = Input(UInt(1.W))   //master start
    val probe1 = Input(UInt(32.W))   //master current_level
    val probe2 = Input(UInt(conf.Data_width.W))   //master p2_cnt_total
    val probe3 = Input(UInt(conf.Data_width.W))   //master mem_cnt_total
    val probe4 = Input(UInt(conf.Data_width.W))   //master p2_pull_count_total
    val probe5 = Input(UInt(conf.Data_width.W))   //master frontier_pull_count_total
    val probe6 = Input(UInt(1.W))   //master mem_end_state
    val probe7 = Input(UInt(1.W))   //master p2_end
    val probe8 = Input(UInt(1.W))   //master end
    val probe9 = Input(UInt(2.W))   //master STATEREG
    // val probe10 = Input(UInt(1.W))   //master 

}

class ila_master(implicit val conf : HBMGraphConfiguration) extends BlackBox{
	val io = IO(new ila_master_IO)
	// override def desiredName = "ila_" + num

}