package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._


class fifo_generator_writeAddr_IO(val dw : Int)(implicit val conf : HBMGraphConfiguration) extends Bundle{
    val rst = Input(Reset())

    val wr_clk = Input(Clock())
    val rd_clk = Input(Clock())
    val din = Input(UInt(dw.W))
    val wr_en = Input(Bool())
    val rd_en = Input(Bool())
    val dout = Output(UInt(dw.W))
    val full = Output(Bool())
    val empty = Output(Bool())
}

class fifo_generator_writeAddr(val dw : Int)(implicit val conf : HBMGraphConfiguration) extends BlackBox{
	val io = IO(new fifo_generator_writeAddr_IO(dw))
	override def desiredName = "fifo_generator_writeAddr"

}

class fifo_generator_writeData_IO(val dw : Int)(implicit val conf : HBMGraphConfiguration) extends Bundle{
    val rst = Input(Reset())

    val wr_clk = Input(Clock())
    val rd_clk = Input(Clock())
    val din = Input(UInt(dw.W))
    val wr_en = Input(Bool())
    val rd_en = Input(Bool())
    val dout = Output(UInt(dw.W))
    val full = Output(Bool())
    val empty = Output(Bool())
}

class fifo_generator_writeData(val dw : Int)(implicit val conf : HBMGraphConfiguration) extends BlackBox{
	val io = IO(new fifo_generator_writeData_IO(dw))
	override def desiredName = "fifo_generator_writeData"

}
class fifo_generator_writeResp_IO(val dw : Int)(implicit val conf : HBMGraphConfiguration) extends Bundle{
    val rst = Input(Reset())

    val wr_clk = Input(Clock())
    val rd_clk = Input(Clock())
    val din = Input(UInt(dw.W))
    val wr_en = Input(Bool())
    val rd_en = Input(Bool())
    val dout = Output(UInt(dw.W))
    val full = Output(Bool())
    val empty = Output(Bool())
}

class fifo_generator_writeResp(val dw : Int)(implicit val conf : HBMGraphConfiguration) extends BlackBox{
	val io = IO(new fifo_generator_writeResp_IO(dw))
	override def desiredName = "fifo_generator_writeResp"

}
class fifo_generator_readAddr_IO(val dw : Int)(implicit val conf : HBMGraphConfiguration) extends Bundle{
    val rst = Input(Reset())

    val wr_clk = Input(Clock())
    val rd_clk = Input(Clock())
    val din = Input(UInt(dw.W))
    val wr_en = Input(Bool())
    val rd_en = Input(Bool())
    val dout = Output(UInt(dw.W))
    val full = Output(Bool())
    val empty = Output(Bool())
}

class fifo_generator_readAddr(val dw : Int)(implicit val conf : HBMGraphConfiguration) extends BlackBox{
	val io = IO(new fifo_generator_readAddr_IO(dw))
	override def desiredName = "fifo_generator_readAddr"

}
class fifo_generator_readData_IO(val dw : Int)(implicit val conf : HBMGraphConfiguration) extends Bundle{
    val rst = Input(Reset())

    val wr_clk = Input(Clock())
    val rd_clk = Input(Clock())
    val din = Input(UInt(dw.W))
    val wr_en = Input(Bool())
    val rd_en = Input(Bool())
    val dout = Output(UInt(dw.W))
    val full = Output(Bool())
    val empty = Output(Bool())
}

class fifo_generator_readData(val dw : Int)(implicit val conf : HBMGraphConfiguration) extends BlackBox{
	val io = IO(new fifo_generator_readData_IO(dw))
	override def desiredName = "fifo_generator_readData"

}

class fifo_generator_readData_syn_IO(val dw : Int)(implicit val conf : HBMGraphConfiguration) extends Bundle{
    val rst = Input(Reset())
    val clk = Input(Clock())
    val din = Input(UInt(dw.W))
    val wr_en = Input(Bool())
    val rd_en = Input(Bool())
    val dout = Output(UInt(dw.W))
    val full = Output(Bool())
    val empty = Output(Bool())
}

class fifo_generator_readData_syn(val dw : Int)(implicit val conf : HBMGraphConfiguration) extends BlackBox{
	val io = IO(new fifo_generator_readData_syn_IO(dw))
	override def desiredName = "fifo_generator_readData_syn"

}

class fifo_generator_src_IO(val dw : Int)(implicit val conf : HBMGraphConfiguration) extends Bundle{
    val rst = Input(Reset())
    val wr_clk = Input(Clock())
    val rd_clk = Input(Clock())
    val din = Input(UInt(dw.W))
    val wr_en = Input(Bool())
    val rd_en = Input(Bool())
    val dout = Output(UInt(dw.W))
    val full = Output(Bool())
    val empty = Output(Bool())
}

class fifo_generator_src(val dw : Int)(implicit val conf : HBMGraphConfiguration) extends BlackBox{
	val io = IO(new fifo_generator_src_IO(dw))
	override def desiredName = "fifo_generator_src"
}

// for Queues
class fifo_cxb(val dw_cxb : Int, val is_double : Boolean)(implicit val conf : HBMGraphConfiguration) extends BlackBox{
	val io = IO(new fifo_generator_readData_syn_IO(dw_cxb))
	override def desiredName = 
        if (is_double) {"fifo_cxb_48"}
        else {"fifo_cxb_24"}
}
