package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._



class AXIAddress(val addrWidthBits: Int, val idBits: Int) extends Bundle {
    // address for the transaction, should be burst aligned if bursts are used
    val addr    = UInt(addrWidthBits.W)
    val len     = UInt(8.W)
    val id      = UInt(idBits.W)

}

class AXIWriteResponse(val idBits: Int) extends Bundle {
    val id      = UInt(idBits.W)
    val resp    = UInt(2.W)
}

class AXIWriteData(val dataWidthBits: Int) extends Bundle {
    val data    = UInt(dataWidthBits.W)
    val strb    = UInt((dataWidthBits/8).W)
    val last    = Bool()
}

class AXIReadData(val dataWidthBits: Int, val idBits: Int) extends Bundle {
    // 64 bits data can be divided into 2 32-bits data
    val data    = UInt((dataWidthBits).W)
    val id      = UInt(idBits.W)
    val last    = Bool()
    val resp    = UInt(2.W)
}

class ReadData(val dataWidthBits: Int, val idBits: Int) extends Bundle {
    val data    = UInt((dataWidthBits).W)
}

// --------------------- for hbm reader ---------------------------------------
//  (dataWidthBits = conf.HBM_Data_width)

// Part II: Definitions for the actual AXI interfaces
class AXIMasterIF_reader(val addrWidthBits: Int, val dataWidthBits: Int, val idBits: Int) extends Bundle {
    // write address channel
    val writeAddr   = Decoupled(new AXIAddress(addrWidthBits, idBits))
    // write data channel
    val writeData   = Decoupled(new AXIWriteData(dataWidthBits))
    // write response channel (for memory consistency)
    val writeResp   = Flipped(Decoupled(new AXIWriteResponse(idBits)))
  
    // read address channel
    val readAddr    = Decoupled(new AXIAddress(addrWidthBits, idBits))
    // read data channel
    val readData    = Flipped(Decoupled(new AXIReadData(dataWidthBits, idBits)))
}


// --------------------- between hbm reader and PE (flip) ---------------------------------------
//  (dataWidthBits = conf.HBM_Data_width)

class AXIMasterIF_flip(val addrWidthBits: Int, val dataWidthBits: Int, val idBits: Int)(implicit val conf : HBMGraphConfiguration) extends Bundle {
    // write address channel
    val writeAddr   = Flipped(Decoupled(new AXIAddress(addrWidthBits, idBits)))
    // write data channel
    val writeData   = Flipped(Decoupled(new AXIWriteData(dataWidthBits)))
    // write response channel (for memory consistency)
    val writeResp   = Decoupled(new AXIWriteResponse(idBits))
  
    // read address channel
    val readAddr    = Flipped(Decoupled(new AXIAddress(addrWidthBits, idBits)))
    // read data channel
    val readData_array     = Vec(conf.pipe_num_per_channel, Decoupled(new ReadData(2*conf.Data_width, idBits)))
    val readData_offset    = Decoupled(new ReadData(2 * conf.Data_width, idBits))
}















// // TODO dalate
// class AXIMasterIF(val addrWidthBits: Int, val dataWidthBits: Int, val idBits: Int) extends Bundle {
//     // write address channel
//     val writeAddr   = Decoupled(new AXIAddress(addrWidthBits, idBits))
//     // write data channel
//     val writeData   = Decoupled(new AXIWriteData(dataWidthBits))
//     // write response channel (for memory consistency)
//     val writeResp   = Flipped(Decoupled(new AXIWriteResponse(idBits)))
  
//     // read address channel
//     val readAddr    = Decoupled(new AXIAddress(addrWidthBits, idBits))
//     // read data channel
//     val readData    = Flipped(Decoupled(new AXIReadData(dataWidthBits, idBits)))
// }