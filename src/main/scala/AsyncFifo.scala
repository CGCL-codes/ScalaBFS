package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._
/** A hardware module implementing a AsyncFifo
  * param gen The type of data to AsyncFifo
  * param entries The max number of entries in the AsyncFifo
  
  开发：wmk
  主体代码借鉴自https://blog.csdn.net/qq_34291505/article/details/87901659
  主要做了两点修改。
  其一ram类型由SyncReadMem改变至mem。SyncReadMem在读请求的下一拍给出数据，mem当拍给出数据。
  其二修改I/O端口，使用enq deq端口，类似于Queue。
  */
class AsyncFIFO(width: Int, depth: Int) extends RawModule {
  val io = IO(new Bundle {
    // write-domain
    val din = Input(UInt(width.W))
    val wr_en = Input(Bool())
    val wr_clk = Input(Clock())
    val full = Output(Bool())
    // read-domain
    val dout = Output(UInt(width.W))
    val rd_en = Input(Bool())
    val rd_clk = Input(Clock())
    val empty = Output(Bool())
    // reset
    val rst = Input(Reset())
  })
  val ram = Mem(1 << depth, UInt(width.W))   // 2^depth
  val writeToReadPtr = Wire(UInt((depth + 1).W))  // to read clock domain
  val readToWritePtr = Wire(UInt((depth + 1).W))  // to write clock domain
  // write clock domain
  withClockAndReset(io.wr_clk, io.rst) {
    val binaryWritePtr = RegInit(0.U((depth + 1).W))
    val binaryWritePtrNext = Wire(UInt((depth + 1).W))
    val grayWritePtr = RegInit(0.U((depth + 1).W))
    val grayWritePtrNext = Wire(UInt((depth + 1).W))
    val isFull = RegInit(false.B)
    val fullValue = Wire(Bool())
    val grayReadPtrDelay0 = RegNext(readToWritePtr)
    val grayReadPtrDelay1 = RegNext(grayReadPtrDelay0)
    binaryWritePtrNext := binaryWritePtr + (io.wr_en && !isFull).asUInt
    binaryWritePtr := binaryWritePtrNext
    grayWritePtrNext := (binaryWritePtrNext >> 1) ^ binaryWritePtrNext
    grayWritePtr := grayWritePtrNext
    writeToReadPtr := grayWritePtr
    fullValue := (grayWritePtrNext === Cat(~grayReadPtrDelay1(depth, depth - 1), grayReadPtrDelay1(depth - 2, 0)))
    isFull := fullValue
    when(io.wr_en && !isFull) {
      ram.write(binaryWritePtr(depth - 1, 0), io.din)
    }
    io.full := isFull    
  }
  // read clock domain
  withClockAndReset(io.rd_clk, io.rst) {
    val binaryReadPtr = RegInit(0.U((depth + 1).W))
    val binaryReadPtrNext = Wire(UInt((depth + 1).W))
    val grayReadPtr = RegInit(0.U((depth + 1).W))
    val grayReadPtrNext = Wire(UInt((depth + 1).W))
    val isEmpty = RegInit(true.B)
    val emptyValue = Wire(Bool())
    val grayWritePtrDelay0 = RegNext(writeToReadPtr)
    val grayWritePtrDelay1 = RegNext(grayWritePtrDelay0)
    binaryReadPtrNext := binaryReadPtr + (io.rd_en && !isEmpty).asUInt
    binaryReadPtr := binaryReadPtrNext
    grayReadPtrNext := (binaryReadPtrNext >> 1) ^ binaryReadPtrNext
    grayReadPtr := grayReadPtrNext
    readToWritePtr := grayReadPtr
    emptyValue := (grayReadPtrNext === grayWritePtrDelay1)
    isEmpty := emptyValue
    io.dout := ram.read(binaryReadPtr(depth - 1, 0))//, io.rd_en && !isEmpty)
    io.empty := isEmpty
  }  
}


// object AsyncFIFO extends App {
//   chisel3.Driver.execute(args, () => new AsyncFIFO(UInt(4.W), 4))
// }
