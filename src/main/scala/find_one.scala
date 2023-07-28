package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._

class find_one_8 (implicit val conf : HBMGraphConfiguration) extends Module{
    val io = IO(new Bundle{
        val data = Input(UInt(8.W))
        val loc = Output(UInt(3.W))
    })

    val data_4 = Wire(UInt(4.W))
    val data_2 = Wire(UInt(2.W))

    // val loc_2 = ~(VecInit(io.data(3,0).asBools).reduce(_|_))
    val loc_2 = !(io.data(3,0))
    data_4 := Mux(loc_2, io.data(7,4), io.data(3,0))
    // val loc_1 = ~(VecInit(data_4(1,0).asBools).reduce(_|_))
    val loc_1 = !data_4(1,0)
    data_2 := Mux(loc_1, data_4(3,2), data_4(1,0))
    val loc_0 = ~data_2(0)
    io.loc := Cat(loc_2, loc_1, loc_0)

}


class find_one_16 (implicit val conf : HBMGraphConfiguration) extends Module{
    val io = IO(new Bundle{
        val data = Input(UInt(16.W))
        val loc = Output(UInt(4.W))
    })

    val one_0 = Module(new find_one_8)
    val one_1 = Module(new find_one_8)

    one_0.io.data := io.data(7, 0)
    one_1.io.data := io.data(15, 8)

    io.loc := Mux(!io.data(7, 0), Cat(1.U(1.W), one_1.io.loc), Cat(0.U(1.W), one_0.io.loc))

}

class find_one_32 (implicit val conf : HBMGraphConfiguration) extends Module{
    val io = IO(new Bundle{
        val data = Input(UInt(conf.Data_width_bram.W))
        val loc = Output(UInt(5.W))
    })

    val one_0 = Module(new find_one_16)
    val one_1 = Module(new find_one_16)

    one_0.io.data := io.data(15, 0)
    one_1.io.data := io.data(31, 16)

    io.loc := Mux(!io.data(15, 0), Cat(1.U(1.W), one_1.io.loc), Cat(0.U(1.W), one_0.io.loc))

}