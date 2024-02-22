package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._

import chisel3.experimental.{DataMirror, Direction, requireIsChiselType}
import chisel3.internal.naming._  // can't use chisel3_ version because of compile order

// FIFOs used for inter-SLR connections
// The Verilog code of the FIFOS corresponding to BlackBox is in syn_fifo.v

class syn_fifo(val num : Int)(implicit val conf : HBMGraphConfiguration) extends BlackBox with HasBlackBoxPath {
    val io = IO(new Bundle {
        val clock = Input(Clock())
        val reset = Input(Reset())
        val enq_valid = Input(UInt(1.W))
        val enq_ready = Output(UInt(1.W)) 
        val enq_bits = Input(UInt(conf.Data_width.W))
        val deq_valid = Output(UInt(1.W)) 
        val deq_ready = Input(UInt(1.W))
        val deq_bits = Output(UInt(conf.Data_width.W)) 
    })

    addPath("src/main/scala/syn_fifo.v")
    override def desiredName = 
    if(num == 0){"R_array_index_01_slr0"}
    else if(num == 1){"R_array_index_01_slr1"}
    else if(num == 2){"R_array_index_02_slr0"}
    else if(num == 3){"R_array_index_02_slr1"}
    else{"R_array_index_02_slr2"}
}




class q_R_array_index_slr0[T <: Data](gen: T,
                       val entries: Int,
                       pipe: Boolean = false,
                       flow: Boolean = false)
                      (implicit compileOptions: chisel3.CompileOptions)
    extends Module() {
    require(entries > -1, "Queue must have non-negative number of entries")
    require(entries != 0, "Use companion object Queue.apply for zero entries")
    val genType = if (compileOptions.declaredTypeMustBeUnbound) {
        requireIsChiselType(gen)
        gen
    } else {
        if (DataMirror.internal.isSynthesizable(gen)) {
        chiselTypeOf(gen)
        } else {
        gen
        }
    }

    val io = IO(new QueueIO(genType, entries))

    val ram = Mem(entries, genType)
    val enq_ptr = Counter(entries)
    val deq_ptr = Counter(entries)
    val maybe_full = RegInit(false.B)

    val ptr_match = enq_ptr.value === deq_ptr.value
    val empty = ptr_match && !maybe_full
    val full = ptr_match && maybe_full
    val do_enq = WireDefault(io.enq.fire())
    val do_deq = WireDefault(io.deq.fire())

    when (do_enq) {
        ram(enq_ptr.value) := io.enq.bits
        enq_ptr.inc()
    }
    when (do_deq) {
        deq_ptr.inc()
    }
    when (do_enq =/= do_deq) {
        maybe_full := do_enq
    }

    io.deq.valid := !empty
    io.enq.ready := RegNext(io.count < entries.U - 1.U)//!full
    io.deq.bits := ram(deq_ptr.value)

    if (flow) {
        when (io.enq.valid) { io.deq.valid := true.B }
        when (empty) {
        io.deq.bits := io.enq.bits
        do_deq := false.B
        when (io.deq.ready) { do_enq := false.B }
        }
    }

    if (pipe) {
        when (io.deq.ready) { io.enq.ready := true.B }
    }

    val ptr_diff = enq_ptr.value - deq_ptr.value
    if (isPow2(entries)) {
        io.count := Mux(maybe_full && ptr_match, entries.U, 0.U) | ptr_diff
    } else {
        io.count := Mux(ptr_match,
                        Mux(maybe_full,
                        entries.asUInt, 0.U),
                        Mux(deq_ptr.value > enq_ptr.value,
                        entries.asUInt + ptr_diff, ptr_diff))
    }
}
