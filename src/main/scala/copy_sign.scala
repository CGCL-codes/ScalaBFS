package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._



class copy_sign_1(implicit val conf : HBMGraphConfiguration) extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val a = Input(UInt(1.W))
    val clk = Input(Clock())
    val reset = Input(Bool())
    val b = Output(UInt(1.W))  
    val c = Output(UInt(1.W))  
    val d = Output(UInt(1.W)) 
  })
 
  setInline("copy_sign.v",
            """
            |module copy_sign_1(input [0:0] a,
            |           input clk,
            |           input reset,
            |           output reg [0:0] b,
            |           output reg [0:0] c,
            |           output reg [0:0] d
            |   );
            |  
            |
            |  always @ (posedge clk)
            |    if(reset)begin
            |      b <= 'b0;
            |      c <= 'b0;
            |      d <= 'b0;
            |    end else begin
            |    
            |      b = a;
            |      c = a;
            |      d = a;
            |    end
            |endmodule
            """.stripMargin)
}

class copy_sign_32(implicit val conf : HBMGraphConfiguration) extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val a = Input(UInt(32.W))
    val clk = Input(Clock())
    val reset = Input(Bool())
    val b = Output(UInt(32.W))  
    val c = Output(UInt(32.W))  
    val d = Output(UInt(32.W)) 
  })
 
  setInline("copy_sign_32.v",
            """
            |module copy_sign_32(input [31:0] a,
            |           input clk,
            |           input reset,
            |           output reg [31:0] b,
            |           output reg [31:0] c,
            |           output reg [31:0] d
            |   );
            |  
            |
            |  always @ (posedge clk)
            |    if(reset)begin
            |      b <= 'b0;
            |      c <= 'b0;
            |      d <= 'b0;
            |    end else begin
            |    
            |      b = a;
            |      c = a;
            |      d = a;
            |    end
            |endmodule
            """.stripMargin)
}
