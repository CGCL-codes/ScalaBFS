module bram(

  input         ena,
  input  [17:0] addra,
  input         clka,
  input  [0:0] dina,
  input         wea,
  output [0:0] douta,
  input         enb,
  input  [17:0] addrb,
  input         clkb,
  input  [31:0] dinb,
  input         web,
  output [31:0] doutb
);
xpm_memory_tdpram #(
      .ADDR_WIDTH_A(18),
      .ADDR_WIDTH_B(18),
      .AUTO_SLEEP_TIME(0),            // DECIMAL
      .BYTE_WRITE_WIDTH_A(1),        // DECIMAL
      .BYTE_WRITE_WIDTH_B(32),        // DECIMAL
      .CASCADE_HEIGHT(0),             // DECIMAL
      .CLOCKING_MODE("common_clock"), // String
      .ECC_MODE("no_ecc"),            // String
      .MEMORY_INIT_FILE("none"),      // String
      .MEMORY_INIT_PARAM(""),        // String
      .MEMORY_OPTIMIZATION("false"),   // String
      .MEMORY_PRIMITIVE("block"),      // String
      .MEMORY_SIZE(65536),             // DECIMAL
      .MESSAGE_CONTROL(1),            // DECIMAL
      .READ_DATA_WIDTH_A(1),         // DECIMAL
      .READ_DATA_WIDTH_B(32),         // DECIMAL
      .READ_LATENCY_A(2),             // DECIMAL
      .READ_LATENCY_B(2),             // DECIMAL
      .READ_RESET_VALUE_A("0"),       // String
      .READ_RESET_VALUE_B("0"),       // String
      .RST_MODE_A("SYNC"),            // String
      .RST_MODE_B("SYNC"),            // String
      .SIM_ASSERT_CHK(0),             // DECIMAL; 0=disable simulation messages, 1=enable simulation messages
      .USE_EMBEDDED_CONSTRAINT(0),    // DECIMAL
      .USE_MEM_INIT(1),               // DECIMAL
      .WAKEUP_TIME("disable_sleep"),  // String
      .WRITE_DATA_WIDTH_A(1),        // DECIMAL
      .WRITE_DATA_WIDTH_B(32),        // DECIMAL
      .WRITE_MODE_A("read_first"),     // String
      .WRITE_MODE_B("read_first")      // String
   )
   xpm_memory_tdpram_inst (
      .dbiterra(),             // 1-bit output: Status signal to indicate double bit error occurrence
      .dbiterrb(),             // 1-bit output: Status signal to indicate double bit error occurrence
      .douta(douta),                   // READ_DATA_WIDTH_A-bit output: Data output for port A read operations.
      .doutb(doutb),                   // READ_DATA_WIDTH_B-bit output: Data output for port B read operations.
      .sbiterra(),             // 1-bit output: Status signal to indicate single bit error occurrence
      .sbiterrb(),             // 1-bit output: Status signal to indicate single bit error occurrence
      .addra(addra),                   // ADDR_WIDTH_A-bit input: Address for port A write and read operations.
      .addrb(addrb),                   // ADDR_WIDTH_B-bit input: Address for port B write and read operations.
      .clka(clka),                     // 1-bit input: Clock signal for port A. Also clocks port B when
      .clkb(clkb),                     // 1-bit input: Clock signal for port B when parameter CLOCKING_MODE is
      .dina(dina),                     // WRITE_DATA_WIDTH_A-bit input: Data input for port A write operations.
      .dinb(dinb),                     // WRITE_DATA_WIDTH_B-bit input: Data input for port B write operations.
      .ena(ena),                       // 1-bit input: Memory enable signal for port A. Must be high on clock
      .enb(enb),                       // 1-bit input: Memory enable signal for port B. Must be high on clock
      .injectdbiterra(), // 1-bit input: Controls double bit error injection on input data when
      .injectdbiterrb(), // 1-bit input: Controls double bit error injection on input data when
      .injectsbiterra(), // 1-bit input: Controls single bit error injection on input data when
      .injectsbiterrb(), // 1-bit input: Controls single bit error injection on input data when
      .regcea(1),                 // 1-bit input: Clock Enable for the last register stage on the output
      .regceb(1),                 // 1-bit input: Clock Enable for the last register stage on the output
      .rsta(),                     // 1-bit input: Reset signal for the final port A output register stage.
      .rstb(),                     // 1-bit input: Reset signal for the final port B output register stage.
      .sleep(),                   // 1-bit input: sleep signal to enable the dynamic power saving feature.
      .wea(wea),                       // WRITE_DATA_WIDTH_A/BYTE_WRITE_WIDTH_A-bit input: Write enable vector
      .web(web)                        // WRITE_DATA_WIDTH_B/BYTE_WRITE_WIDTH_B-bit input: Write enable vector
   );

endmodule

module uram(

  input         ena,
  input  [19:0] addra,
  input         clka,
  input  [63:0] dina,
  input  [7:0]  wea,
  output [63:0] douta,
  input         enb,
  input  [19:0] addrb,
  input         clkb,
  input  [63:0] dinb,
  input  [7:0]  web,
  output [63:0] doutb
);
xpm_memory_tdpram #(
      .ADDR_WIDTH_A(20),
      .ADDR_WIDTH_B(20),
      .AUTO_SLEEP_TIME(0),            // DECIMAL
      .BYTE_WRITE_WIDTH_A(8),        // DECIMAL
      .BYTE_WRITE_WIDTH_B(8),        // DECIMAL
      .CASCADE_HEIGHT(0),             // DECIMAL
      .CLOCKING_MODE("common_clock"), // String
      .ECC_MODE("no_ecc"),            // String
      .MEMORY_INIT_FILE("none"),      // String
      .MEMORY_INIT_PARAM(""),        // String
      .MEMORY_OPTIMIZATION("false"),   // String
      .MEMORY_PRIMITIVE("ultra"),      // String
      .MEMORY_SIZE(525312),             // DECIMAL
      .MESSAGE_CONTROL(1),            // DECIMAL
      .READ_DATA_WIDTH_A(64),         // DECIMAL
      .READ_DATA_WIDTH_B(64),         // DECIMAL
      .READ_LATENCY_A(2),             // DECIMAL
      .READ_LATENCY_B(),             // DECIMAL
      .READ_RESET_VALUE_A("0"),       // String
      .READ_RESET_VALUE_B("0"),       // String
      .RST_MODE_A("SYNC"),            // String
      .RST_MODE_B("SYNC"),            // String
      .SIM_ASSERT_CHK(0),             // DECIMAL; 0=disable simulation messages, 1=enable simulation messages
      .USE_EMBEDDED_CONSTRAINT(0),    // DECIMAL
      .USE_MEM_INIT(0),               // DECIMAL
      .WAKEUP_TIME("disable_sleep"),  // String
      .WRITE_DATA_WIDTH_A(64),        // DECIMAL
      .WRITE_DATA_WIDTH_B(64),        // DECIMAL
      .WRITE_MODE_A("no_change"),     // String
      .WRITE_MODE_B("no_change")      // String
   )
   xpm_memory_tdpram_inst (
      .dbiterra(),             // 1-bit output: Status signal to indicate double bit error occurrence
      .dbiterrb(),             // 1-bit output: Status signal to indicate double bit error occurrence
      .douta(douta),                   // READ_DATA_WIDTH_A-bit output: Data output for port A read operations.
      .doutb(doutb),                   // READ_DATA_WIDTH_B-bit output: Data output for port B read operations.
      .sbiterra(),             // 1-bit output: Status signal to indicate single bit error occurrence
      .sbiterrb(),             // 1-bit output: Status signal to indicate single bit error occurrence
      .addra(addra),                   // ADDR_WIDTH_A-bit input: Address for port A write and read operations.
      .addrb(addrb),                   // ADDR_WIDTH_B-bit input: Address for port B write and read operations.
      .clka(clka),                     // 1-bit input: Clock signal for port A. Also clocks port B when
      .clkb(clkb),                     // 1-bit input: Clock signal for port B when parameter CLOCKING_MODE is
      .dina(dina),                     // WRITE_DATA_WIDTH_A-bit input: Data input for port A write operations.
      .dinb(dinb),                     // WRITE_DATA_WIDTH_B-bit input: Data input for port B write operations.
      .ena(ena),                       // 1-bit input: Memory enable signal for port A. Must be high on clock
      .enb(enb),                       // 1-bit input: Memory enable signal for port B. Must be high on clock
      .injectdbiterra(), // 1-bit input: Controls double bit error injection on input data when
      .injectdbiterrb(), // 1-bit input: Controls double bit error injection on input data when
      .injectsbiterra(), // 1-bit input: Controls single bit error injection on input data when
      .injectsbiterrb(), // 1-bit input: Controls single bit error injection on input data when
      .regcea(1),                 // 1-bit input: Clock Enable for the last register stage on the output
      .regceb(),                 // 1-bit input: Clock Enable for the last register stage on the output
      .rsta(),                     // 1-bit input: Reset signal for the final port A output register stage.
      .rstb(),                     // 1-bit input: Reset signal for the final port B output register stage.
      .sleep(),                   // 1-bit input: sleep signal to enable the dynamic power saving feature.
      .wea(wea),                       // WRITE_DATA_WIDTH_A/BYTE_WRITE_WIDTH_A-bit input: Write enable vector
      .web(web)                        // WRITE_DATA_WIDTH_B/BYTE_WRITE_WIDTH_B-bit input: Write enable vector
   );

endmodule
