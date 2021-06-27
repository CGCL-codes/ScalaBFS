package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._

class bram_IO(implicit val conf : HBMGraphConfiguration) extends Bundle{
    val ena = Input(Bool())
    val addra = Input(UInt(conf.Addr_width.W))
    val clka = Input(Clock())
    val dina = Input(UInt(conf.Data_width_bram.W))
    val wea = Input(Bool())
    val douta = Output(UInt(conf.Data_width_bram.W))
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
    val dina = Input(UInt(conf.Data_width_uram.W))
    val wea = Input(UInt((conf.Data_width_uram / 8).W))
    val douta = Output(UInt(conf.Data_width_uram.W))
    val enb = Input(Bool())
    val addrb = Input(UInt(conf.Addr_width_uram.W))
    val clkb = Input(Clock())
    val dinb = Input(UInt(conf.Data_width_uram.W))
    val web = Input(UInt((conf.Data_width_uram / 8).W))
    val doutb = Output(UInt(conf.Data_width_uram.W))
}


// num is the channel num
// when bram_num = 0 means visited_map
// when bram_num = 1 or 2 means frontier

class bram(val num : Int, bram_num : Int)(implicit val conf : HBMGraphConfiguration) extends BlackBox{
	val io = IO(new bram_IO)
	override def desiredName =
    if(bram_num < 3){
        "bram_" + num + "_" + bram_num
    }else{
        "uram_" + num
    }
}
class uram(val num : Int, bram_num : Int)(implicit val conf : HBMGraphConfiguration) extends BlackBox{
	val io = IO(new uram_IO)
	override def desiredName =
    if(bram_num < 3){
        "bram_" + num + "_" + bram_num
    }else{
        "uram_" + num
    }
}

class bram_controller_clock_domain_conversion_IO(implicit val conf : HBMGraphConfiguration) extends Bundle{
    // bram io
    // val bram = new bram_IO
    val ena = Input(Bool())
    val addra = Input(UInt(conf.Addr_width.W))
    val clka = Input(Clock())
    val dina = Input(UInt(conf.Data_width_bram.W))
    val wea = Input(Bool())
    val douta = Output(UInt(conf.Data_width_bram.W))
    val enb = Input(Bool())
    val addrb = Input(UInt(conf.Addr_width.W))
    val clkb = Input(Clock())
    val dinb = Input(UInt(conf.Data_width_bram.W))
    val web = Input(Bool())
    val doutb = Output(UInt(conf.Data_width_bram.W))

    // wmode 0 : read-or-write 1 : write 2 data for clear
    val wmode = Input(UInt(1.W))
    // nodea and nodeb's value is between 0 and conf.Data_width_bram - 1
    val nodea = Input(UInt((conf.Data_width_bram.U.getWidth - 1).W))
    val nodeb = Input(UInt((conf.Data_width_bram.U.getWidth - 1).W))

    val data_in_ready_a = Output(Bool())
    val data_in_ready_b = Output(Bool())
    val data_out_ready_a = Input(Bool())
    val data_out_ready_b = Input(Bool())
    val data_out_valid_a = Output(Bool())
    val data_out_valid_b = Output(Bool())
    val no_read_a = Input(Bool())
    val no_read_b = Input(Bool())
    // only visited_map need these two signals to show if the node is visited 
    // val visited_a = Output(UInt(1.W))
    // val visited_b = Output(UInt(1.W))
}


class bram_controller_clock_domain_conversion(val num : Int, bram_num : Int)(implicit val conf : HBMGraphConfiguration) extends Module{
    val io = IO(new bram_controller_clock_domain_conversion_IO)
    val bram_controller = withClock(io.clka){Module(new bram_controller(num, bram_num))}
    // val bram_in_from_io = Cat(io.ena, io.enb, io.dina, io.dinb, 
    //                   io.addra, io.addrb, io.wea, io.web, 
    //                   io.wmode, io.nodea, io.nodeb)
    // val bram_out = Cat(io.douta, io.doutb)
    
    //使用FIFO generator ip核
    val fifo_bram_in_a = Module(new fifo_generator_1)
    val fifo_bram_out_a = Module(new fifo_generator_2)
    
    val fifo_bram_in_b = Module(new fifo_generator_1)
    val fifo_bram_out_b = Module(new fifo_generator_2)
    //使用chisel实现的异步fifo
    // val fifo_bram_in_a = Module(new AsyncFIFO(conf.Data_width_p2_to_f + conf.Data_width_bram + 3, 4))
    // val fifo_bram_in_b = Module(new AsyncFIFO(conf.Data_width_p2_to_f + conf.Data_width_bram + 3, 4))
    // val fifo_bram_out_a = Module(new AsyncFIFO(conf.Data_width_bram, 4))
    // val fifo_bram_out_b = Module(new AsyncFIFO(conf.Data_width_bram, 4))





    // val fifo_bram_in_a_empty = RegInit(0.U(1.W))
    // val fifo_bram_in_b_empty = RegInit(0.U(1.W))
    // fifo_bram_in_a_empty := 0.U
    // fifo_bram_in_b_empty := 0.U
    // when(fifo_bram_in_a.io.empty){
    //     fifo_bram_in_a_empty := 1.U
    // }
    // when(fifo_bram_in_b.io.empty){
    //     fifo_bram_in_b_empty := 1.U
    // }
    val hold_empty_a = withClock(io.clka){Module (new hold)}
    hold_empty_a.io.in := fifo_bram_in_a.io.empty
    val fifo_bram_in_a_empty = hold_empty_a.io.out


    val hold_empty_b = withClock(io.clkb){Module (new hold)}
    hold_empty_b.io.in := fifo_bram_in_b.io.empty
    val fifo_bram_in_b_empty = hold_empty_b.io.out

    val hold_no_read_a = withClock(io.clka){Module (new hold)}
    hold_no_read_a.io.in := loc.no_read(fifo_bram_in_a.io.dout)
    val fifo_bram_in_a_no_read = hold_no_read_a.io.out


    val hold_no_read_b = withClock(io.clkb){Module (new hold)}
    hold_no_read_b.io.in := loc.no_read(fifo_bram_in_b.io.dout)
    val fifo_bram_in_b_no_read = hold_no_read_b.io.out


    // val clock_hold = RegNext(clock.asBool())
    // val hold_no_read_a = RegNext(loc.no_read(fifo_bram_in_a.io.dout))
    // val hold_no_read_b = RegNext(loc.no_read(fifo_bram_in_b.io.dout))

    fifo_bram_in_a.io.rst := reset
    fifo_bram_in_a.io.wr_clk := clock
    fifo_bram_in_a.io.din <> Cat(io.no_read_a, io.ena, io.dina, io.wmode, io.addra, io.wea, io.nodea)
    fifo_bram_in_a.io.wr_en <> io.ena 
    fifo_bram_in_a.io.full <> DontCare
    fifo_bram_in_a.io.rd_clk := io.clka
    //对于只写操作，不需要考虑fifo_out是否满了，因为fifo generator的设置为First_Word_Fall_Through，数据会优先于rd_en到来，直接使用dout判断不会出错(?)
    // fifo_bram_in_a.io.rd_en <> ((bram_controller.io.cnt === 0.U) && ((!fifo_bram_out_a.io.full) || (loc.no_read(fifo_bram_in_a.io.dout) === 1.U ))) 
    //牺牲部分性能，不对写操作进行单独处理
    fifo_bram_in_a.io.rd_en <> ((bram_controller.io.cnt === 0.U) && (!fifo_bram_out_a.io.full)) 

    fifo_bram_in_b.io.rst := reset
    fifo_bram_in_b.io.wr_clk := clock
    fifo_bram_in_b.io.din <> Cat(io.no_read_b, io.enb, io.dinb, io.wmode, io.addrb, io.web, io.nodeb)
    fifo_bram_in_b.io.wr_en <> io.enb 
    fifo_bram_in_b.io.full <> DontCare
    fifo_bram_in_b.io.rd_clk := io.clkb
    // fifo_bram_in_b.io.rd_en <> ((bram_controller.io.cnt === 0.U) && ((!fifo_bram_out_b.io.full) || (loc.no_read(fifo_bram_in_b.io.dout) === 1.U )))
    //牺牲性能，不对写进行单独处理
    fifo_bram_in_b.io.rd_en <> ((bram_controller.io.cnt === 0.U) && (!fifo_bram_out_b.io.full))

    fifo_bram_out_a.io.rst := reset
    fifo_bram_out_a.io.wr_clk := io.clka
    fifo_bram_out_a.io.din <> bram_controller.io.douta
    fifo_bram_out_a.io.wr_en <> ((bram_controller.io.cnt === 1.U) && (!fifo_bram_in_a_empty) && (fifo_bram_in_a_no_read === 0.U) && (!fifo_bram_out_a.io.full))
    fifo_bram_out_a.io.full <> DontCare
    fifo_bram_out_a.io.rd_clk := clock
    fifo_bram_out_a.io.rd_en <> io.data_out_ready_a

    fifo_bram_out_b.io.rst := reset
    fifo_bram_out_b.io.wr_clk := io.clkb
    fifo_bram_out_b.io.din <> bram_controller.io.doutb
    fifo_bram_out_b.io.wr_en <> ((bram_controller.io.cnt === 1.U) && (!fifo_bram_in_b_empty) && (fifo_bram_in_b_no_read === 0.U) && (!fifo_bram_out_b.io.full))
    fifo_bram_out_b.io.full <> DontCare
    fifo_bram_out_b.io.rd_clk := clock
    fifo_bram_out_b.io.rd_en <> io.data_out_ready_b

    // Cat(bram_controller.io.ena, bram_controller.io.enb, bram_controller.io.dina, bram_controller.io.dinb, 
    // bram_controller.io.addra, bram_controller.io.addrb, bram_controller.io.wea, bram_controller.io.web, 
    // bram_controller.io.wmode, bram_controller.io.nodea, bram_controller.io.nodeb) <> fifo_bram_in.io.dout

    bram_controller.io.ena <> Mux((!fifo_bram_in_a.io.empty) && (bram_controller.io.cnt === 0.U), loc.en(fifo_bram_in_a.io.dout), false.B)
    bram_controller.io.enb <> Mux((!fifo_bram_in_b.io.empty) && (bram_controller.io.cnt === 0.U), loc.en(fifo_bram_in_b.io.dout), false.B)
    bram_controller.io.dina <> loc.din(fifo_bram_in_a.io.dout)
    bram_controller.io.dinb <> loc.din(fifo_bram_in_b.io.dout)
    bram_controller.io.addra <> loc.addr(fifo_bram_in_a.io.dout)
    bram_controller.io.addrb <> loc.addr(fifo_bram_in_b.io.dout)
    bram_controller.io.wea <> Mux((!fifo_bram_in_a.io.empty) && (bram_controller.io.cnt === 0.U), loc.we(fifo_bram_in_a.io.dout), false.B)
    bram_controller.io.web <> Mux((!fifo_bram_in_b.io.empty) && (bram_controller.io.cnt === 0.U), loc.we(fifo_bram_in_b.io.dout), false.B)
    bram_controller.io.wmode <> ((loc.wmode(fifo_bram_in_a.io.dout)) & (loc.wmode(fifo_bram_in_b.io.dout)))
    bram_controller.io.nodea <> loc.node(fifo_bram_in_a.io.dout)
    bram_controller.io.nodeb <> loc.node(fifo_bram_in_b.io.dout)
    bram_controller.io.clka <> io.clka
    bram_controller.io.clkb <> io.clkb


    // Cat(io.douta, io.doutb) <> fifo_bram_out.io.dout(conf.Data_width_bram * 2 - 1, 0)

    io.douta <> fifo_bram_out_a.io.dout
    io.doutb <> fifo_bram_out_b.io.dout
    io.data_in_ready_a <> !fifo_bram_in_a.io.full
    io.data_in_ready_b <> !fifo_bram_in_b.io.full
    io.data_out_valid_a <> !fifo_bram_out_a.io.empty
    io.data_out_valid_b <> !fifo_bram_out_b.io.empty

}

class bram_controller_IO(implicit val conf : HBMGraphConfiguration) extends Bundle{
    // bram io
    // val bram = new bram_IO
    val ena = Input(Bool())
    val addra = Input(UInt(conf.Addr_width.W))
    val clka = Input(Clock())
    val dina = Input(UInt(conf.Data_width_bram.W))
    val wea = Input(Bool())
    val douta = Output(UInt(conf.Data_width_bram.W))
    val enb = Input(Bool())
    val addrb = Input(UInt(conf.Addr_width.W))
    val clkb = Input(Clock())
    val dinb = Input(UInt(conf.Data_width_bram.W))
    val web = Input(Bool())
    val doutb = Output(UInt(conf.Data_width_bram.W))

    // wmode 0 : read-or-write 1 : write 2 data for clear
    val wmode = Input(UInt(1.W))
    // nodea and nodeb's value is between 0 and conf.Data_width_bram - 1
    val nodea = Input(UInt((conf.Data_width_bram.U.getWidth - 1).W))
    val nodeb = Input(UInt((conf.Data_width_bram.U.getWidth - 1).W))
    val cnt = Output(UInt(1.W))
}

class bram_controller(val num : Int, bram_num : Int)(implicit val conf : HBMGraphConfiguration) extends Module{
    val io = IO(new bram_controller_IO)
    dontTouch(io)
    val ram = Module(new bram(num, bram_num))
    // init signals

    val ena_hold = RegNext(io.ena)
    val enb_hold = RegNext(io.enb)

    ram.io.ena := io.ena || ena_hold
	ram.io.enb := io.enb || enb_hold
    // ram.io.ena := 1.U
	// ram.io.enb := 1.U
	ram.io.addra := io.addra
	ram.io.addrb := io.addrb
	ram.io.clka := io.clka
	ram.io.clkb := io.clkb
	ram.io.wea := false.B
	ram.io.web := false.B
    ram.io.dina := DontCare
	ram.io.dinb := DontCare
	io.douta := ram.io.douta
    io.doutb := ram.io.doutb

    val wea_hold = RegNext(io.wea)
    val web_hold = RegNext(io.web)
    val addr_a_hold = RegNext(io.addra)
    val addr_b_hold = RegNext(io.addrb)
    val node_a_hold = RegNext(io.nodea)
    val node_b_hold = RegNext(io.nodeb)
    // val visited_a = RegInit(0.U(1.W))
    // val visited_b = RegInit(0.U(1.W))
    // io.visited_a := visited_a
    // io.visited_b := visited_b
    val cnt = RegInit(0.U(1.W))
    val count_output = RegInit(0.U(1.W))
    count_output := count_output + 1.U
    io.cnt := count_output
    // init cnt
    cnt := 0.U
    when((io.wea || io.web || wea_hold || web_hold) && io.wmode === 0.U){  //need to write data
        when(cnt === 0.U){
            // read
            cnt := cnt + 1.U
            ram.io.wea := false.B
            ram.io.web := false.B
            ram.io.addra := io.addra
            ram.io.addrb := io.addrb
            // ram.io.addra := io.addra
            // ram.io.addrb := io.addrb
        }
        .otherwise{ //write
            cnt := cnt + 1.U
            ram.io.addra := addr_a_hold
            ram.io.addrb := addr_b_hold
            // visited_map result
            // visited_a := ram.io.douta(io.nodea)
            // visited_b := ram.io.doutb(io.nodeb)

            when(addr_a_hold === addr_b_hold && wea_hold && web_hold){ // just write in port a
                // // we only write when the node is not visited or the node is not in frontier
                // when(ram.io.douta(io.nodea) === 0.U || ram.io.doutb(io.nodeb) === 0.U){
                ram.io.wea := true.B
                ram.io.web := false.B
                ram.io.dina := ram.io.douta | (1.U << node_a_hold) | (1.U << node_b_hold)
                // }
            }
            .otherwise{
                // write in port a
                // when(ram.io.douta(io.nodea) === 0.U){
                ram.io.wea := true.B & wea_hold
                ram.io.dina := ram.io.douta | (1.U << node_a_hold) 
                // }
                // write in port b
                // when(ram.io.doutb(io.nodeb) === 0.U){
                ram.io.web := true.B & web_hold
                ram.io.dinb := ram.io.doutb | (1.U << node_b_hold)
                // }                
            }
        }
    }
    // for clear
    when((io.wea || io.web || wea_hold || web_hold) && io.wmode === 1.U){
        when(io.wea || wea_hold){
            when(cnt === 0.U){
                cnt := cnt + 1.U
                ram.io.wea := io.wea
                ram.io.addra := io.addra
                ram.io.dina := io.dina
            
                
            }
            .otherwise{
                cnt := cnt + 1.U
                ram.io.wea := wea_hold
                ram.io.addra := addr_a_hold + 1.U
                ram.io.dina := io.dina
            }
           
        }
        when(io.web || web_hold){
            when(cnt === 0.U){
                cnt := cnt + 1.U
                ram.io.web := io.web
                ram.io.addrb := io.addrb
                ram.io.dinb := io.dinb
            }
            .otherwise{
                cnt := cnt + 1.U
                ram.io.web := web_hold
                ram.io.addrb := addr_b_hold + 1.U
                ram.io.dinb := io.dinb
            }
        }
    }
    when(!(io.wea || io.web || wea_hold || web_hold)){
        ram.io.wea := false.B
        ram.io.web := false.B
    }
}


class uram_controller_IO(implicit val conf : HBMGraphConfiguration) extends Bundle{

    val clka = Input(Clock())
    val clkb = Input(Clock())    
    
    val addra = Input(UInt(conf.Addr_width_uram.W))
    // val addra1 = Input(UInt(conf.Addr_width_uram.W))
    val dina = Input(UInt(conf.Data_width_uram.W))
    // val dina1 = Input(UInt(conf.Data_width_uram.W))
    val wea = Input(UInt((conf.Data_width_uram / 8).W))
    val douta = Output(UInt(conf.Data_width_uram.W))
    // val wea1 = Input(UInt(conf.Data_width_uram / 8).W))
    val addrb = Input(UInt(conf.Addr_width_uram.W))
    // val addrb1 = Input(UInt(conf.Addr_width_uram.W))
    val dinb = Input(UInt(conf.Data_width_uram.W))
    // val dinb1 = Input(UInt(conf.Data_width_uram.W))
    val web = Input(UInt((conf.Data_width_uram / 8).W))
    // val web1 = Input(UInt(conf.Data_width_uram / 8).W))
    val doutb = Output(UInt(conf.Data_width_uram.W))

}

class uram_controller(val num : Int, bram_num : Int)(implicit val conf : HBMGraphConfiguration) extends Module{
    val io = IO(new uram_controller_IO)
    dontTouch(io)
    val ram = Module(new uram(num, bram_num))
    // init signals
    ram.io.ena := true.B
	ram.io.enb := true.B
	ram.io.addra := DontCare
	ram.io.addrb := DontCare
	ram.io.clka := io.clka
	ram.io.clkb := io.clkb
	ram.io.wea := false.B
	ram.io.web := false.B
    ram.io.dina := DontCare
	ram.io.dinb := DontCare
    io.douta := ram.io.douta
    io.doutb := ram.io.doutb


    // val cnt = RegInit(0.U(1.W))
    // cnt := cnt + 1.U

    // when(cnt === 0.U){
    ram.io.addra := io.addra
    ram.io.addrb := io.addrb
    ram.io.wea   := io.wea
    ram.io.web   := io.web
    ram.io.dina  := io.dina
    ram.io.dinb  := io.dinb
    // }.otherwise{
    //     ram.io.addra := io.addra1
    //     ram.io.addrb := io.addrb1
    // 	ram.io.wea   := io.wea1
	//     ram.io.web   := io.web1
    //     ram.io.dina  := io.dina1
	//     ram.io.dinb  := io.dinb1
    // }
}


class hold(implicit val conf : HBMGraphConfiguration) extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(1.W))
    val out = Output(UInt(1.W))  
  })
    
    // val delay_1 = RegNext(io.in)
    // val delay_2 = RegNext(delay_1)
    // io.out := RegNext(delay_2)
    io.out := RegNext(io.in)
}


// //测试bram能达到的最高主频
// class Top(implicit val conf : HBMGraphConfiguration) extends Module{
// 	val io = IO(new Bundle{
//         val ap_start_pulse = Input(Bool())
// 		val ap_done = Output(Bool())
//         val hbm = Vec(conf.channel_num,new AXIMasterIF(conf.HBM_Addr_width, conf.HBM_Data_width,conf.memIDBits))
//         val offsets = Input(new offsets)
//         val levels = Input(new levels)
//         val node_num = Input(UInt(conf.Data_width.W))
//         val if_write = Input(UInt(32.W))
// 	})
//     dontTouch(io)
//     io <> DontCare
//     val reset_reg = RegNext(reset)
//     val clk_wiz = withReset(reset_reg){Module(new clk_wiz_0)}
//     val frontier_0 = Module(new bram_controller_clock_domain_conversion(0, 1))
//     clk_wiz.io.clock := clock

//     frontier_0.io.clka := clk_wiz.io.clk_bram
//     frontier_0.io.clkb := clk_wiz.io.clk_bram
//     frontier_0.io := DontCare
//     // frontier_0.io.ena := true.B
//     // frontier_0.io.enb := true.B
//     frontier_0.io.ena := true.B
//     frontier_0.io.enb := true.B

//     frontier_0.io.wea := true.B
//     frontier_0.io.web := false.B
//     frontier_0.io.wmode := 0.U
//     frontier_0.io.data_out_ready_a := true.B
//     frontier_0.io.data_out_ready_b := true.B
//     frontier_0.io.no_read_a := 0.U
//     frontier_0.io.no_read_b := 0.U

//     val count = RegInit(0.U(32.W))
//     val start_state = RegInit(false.B)
//     val finish = RegInit(false.B)
//     when(io.ap_start_pulse){
//         start_state := true.B
//     }
//     when(start_state){
//         when(count < 1000.U){
//             frontier_0.io.dina := count
//             frontier_0.io.addra := count
//             frontier_0.io.addrb := count
//             count := count + 1.U
//         }
//         .otherwise{
//             finish := true.B
//         }
//     }
//     io.ap_done := finish

// }


// object Top extends App{
//     implicit val configuration = HBMGraphConfiguration()
//     override val args = Array("-o", "Top.v",
//                  "-X", "verilog",
//                  "--no-dce",
//                  "--info-mode=ignore"
//                  )
//     chisel3.Driver.execute(args, () => new Top)
//     //chisel3.Driver.execute(Array[String](), () => new Top())
// }
