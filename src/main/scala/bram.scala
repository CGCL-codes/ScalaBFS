package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._

// BRAM: used for current_frontier, next_frontier and visited_map
// URAM: used for level

class bram_IO(implicit val conf : HBMGraphConfiguration) extends Bundle{
    val ena = Input(Bool())
    val addra = Input(UInt(conf.Addr_width.W))
    val clka = Input(Clock())
    val dina = Input(UInt(1.W))
    val wea = Input(Bool())
    val douta = Output(UInt(1.W))
    val enb = Input(Bool())
    val addrb = Input(UInt(conf.Addr_width.W))
    val clkb = Input(Clock())
    val dinb = Input(UInt(conf.Data_width_bram.W))
    val web = Input(Bool())
    val doutb = Output(UInt(conf.Data_width_bram.W))
}

class uram_IO(implicit val conf : HBMGraphConfiguration) extends Bundle{
    val ena = Input(Bool())
    val addra = Input(UInt(conf.Addr_width_uram.W))
    val clka = Input(Clock())
    val dina = Input(UInt(conf.Data_width_uram_phy.W))
    val wea = Input(UInt((conf.Data_width_uram_phy / 8).W))
    val douta = Output(UInt(conf.Data_width_uram_phy.W))
    val enb = Input(Bool())
    val addrb = Input(UInt(conf.Addr_width_uram.W))
    val clkb = Input(Clock())
    val dinb = Input(UInt(conf.Data_width_uram_phy.W))
    val web = Input(UInt((conf.Data_width_uram_phy / 8).W))
    val doutb = Output(UInt(conf.Data_width_uram_phy.W))
}

class bram(implicit val conf : HBMGraphConfiguration) extends BlackBox{
	val io = IO(new bram_IO)
}

class uram(implicit val conf : HBMGraphConfiguration) extends BlackBox{
	val io = IO(new uram_IO)
}

class bram_controller_IO(implicit val conf : HBMGraphConfiguration) extends Bundle{
    // bram io
    // val bram = new bram_IO
    val ena = Input(Bool())
    val addra = Input(UInt(conf.Addr_width.W))
    val clka = Input(Clock())
    val dina = Input(UInt(1.W))
    val wea = Input(Bool())
    val douta = Output(UInt(1.W))
    val enb = Input(Bool())
    val addrb = Input(UInt(conf.Addr_width.W))
    val clkb = Input(Clock())
    val dinb = Input(UInt(conf.Data_width_bram.W))
    val web = Input(Bool())
    val doutb = Output(UInt(conf.Data_width_bram.W))
}

class bram_controller(implicit val conf : HBMGraphConfiguration) extends Module{
    val io = IO(new bram_controller_IO)
    dontTouch(io)
    val ram = Module(new bram)
    ram.io.ena := io.ena
    ram.io.enb := io.enb
	ram.io.addra := io.addra
	ram.io.addrb := io.addrb
	ram.io.clka := io.clka
	ram.io.clkb := io.clkb
	ram.io.wea := io.wea
	ram.io.web := io.web
    ram.io.dina := io.dina
	ram.io.dinb := io.dinb
    io.douta := ram.io.douta
    io.doutb := ram.io.doutb
}

class uram_controller_IO(implicit val conf : HBMGraphConfiguration) extends Bundle{

    val clka = Input(Clock())
    val clkb = Input(Clock())    
    val addra = Input(UInt(conf.Addr_width_uram.W))
    val dina = Input(UInt(conf.Data_width_uram.W))
    val wea = Input(UInt((conf.Data_width_uram / 8).W))
    val douta = Output(UInt(conf.Data_width_uram.W))
    val addrb = Input(UInt(conf.Addr_width_uram.W))
    val dinb = Input(UInt(conf.Data_width_uram.W))
    val web = Input(UInt((conf.Data_width_uram / 8).W))
    val doutb = Output(UInt(conf.Data_width_uram.W))

}

class uram_controller(implicit val conf : HBMGraphConfiguration) extends Module{
    val io = IO(new uram_controller_IO)
    dontTouch(io)
    val ram = Module(new uram)//(num, bram_num))
    // init signals
    ram.io.ena := true.B
	ram.io.enb := true.B
	ram.io.addra := io.addra
	ram.io.addrb := io.addrb
	ram.io.clka := io.clka
	ram.io.clkb := io.clkb
	ram.io.wea := Cat(0.U(1.W), io.wea)
	ram.io.web := Cat(0.U(1.W), io.web)
    ram.io.dina := Cat(0.U(8.W), io.dina)
	ram.io.dinb := Cat(0.U(8.W), io.dinb)
    io.douta := ram.io.douta(conf.Data_width_uram - 1, 0)
    io.doutb := ram.io.doutb(conf.Data_width_uram - 1, 0)
}
