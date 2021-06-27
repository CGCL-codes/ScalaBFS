package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._

class fifo_generator_1_IO(implicit val conf : HBMGraphConfiguration) extends Bundle{
    val rst = Input(Reset())

    val wr_clk = Input(Clock())
    val rd_clk = Input(Clock())
    val din = Input(UInt((conf.Data_width_p2_to_f + conf.Data_width_bram + 3).W))
    val wr_en = Input(UInt())
    val rd_en = Input(Bool())
    val dout = Output(UInt((conf.Data_width_p2_to_f + conf.Data_width_bram + 3).W))
    val full = Output(Bool())
    val empty = Output(Bool())
    // val wr_rst_busy = Output(Bool())
    // val rd_rst_busy = Output(Bool())

}

class fifo_generator_1(implicit val conf : HBMGraphConfiguration) extends BlackBox{
	val io = IO(new fifo_generator_1_IO)
	override def desiredName = "fifo_generator_1"

}

class fifo_generator_2_IO(implicit val conf : HBMGraphConfiguration) extends Bundle{
    val rst = Input(Reset())

    val wr_clk = Input(Clock())
    val rd_clk = Input(Clock())
    val din = Input(UInt((conf.Data_width_bram).W))
    val wr_en = Input(UInt())
    val rd_en = Input(Bool())
    val dout = Output(UInt((conf.Data_width_bram).W))
    val full = Output(Bool())
    val empty = Output(Bool())
    // val wr_rst_busy = Output(Bool())
    // val rd_rst_busy = Output(Bool())

}

class fifo_generator_2(implicit val conf : HBMGraphConfiguration) extends BlackBox{
	val io = IO(new fifo_generator_2_IO)
	override def desiredName = "fifo_generator_2"

}
