# ScalaBFS Graph Data Preprocess Usage




## Process Graphs

i) Download original graph data which have correct data format

Format: The fist line of graph file contains total vertices and edges of the graph. Then comes graph edges with adjacency list format.

| M[Vertices]    | N[Edges]      |
| -------------- | ------------- |
| a1[First node] | b1[Tail node] |
| a2             | b2            |
| ...            | ...           |
| an             | bn            |

ii) Generate Divided Graph Data with Scalable Channels and PEs, only directed graphs require the input of the last argument

Usage: 

for directed graph: 

```bash
[executable program] [filename with suffix] [the number of channels] [the number of PEs] [any value]
```
for directed graph: 

```bash
[executable program] [filename with suffix] [the number of channels] [the number of PEs]


Example:

```bash
cd data_preprocess
make all
./GraphToScalaBFS soc-livejournal.txt 32 128 1
`
## Well-tested Graph Data Set


| **Graphs**               | **Directed** | **\#Vertices (M)** | **\#Edges (M)** | **Average Degree** | **Pre\-processing time (s)** | **Edge\-data Expansion Rate** |
|:------------------------:|:------------:|:--------------:|:-----------:|:-----------:|:------------------------:|:------------------------:|
| soc\-Pokec \(PK\)        | Y            | 1\.63          | 30\.62      | 18\.75      | 3\.547                   | 1\.19                    |
| soc\-LiveJournal \(LJ\)  | Y            | 4\.85          | 68\.99      | 14\.23      | 8\.775                   | 1\.28                    |
| com\-Orkut \(OR\)        | N            | 3\.07          | 234\.37     | 76\.28      | 17\.544                  | 1\.05                    |
| hollywood\-2009 \(HO\)   | N            | 1\.14          | 113\.89     | 99\.91      | 7\.601                   | 1\.04                    |
| web\-hudong \(HD\)       | Y            | 1\.98          | 14\.87      | 7\.49       | 2\.246                   | 1\.48                    |
| web\-baidu\-baike \(BB\) | Y            | 2\.14          | 17\.80      | 8\.31       | 2\.438                   | 1\.44                    |
| RMAT18\-8 \(R18\-8\)     | N            | 0\.26          | 2\.05       | 7\.81       | 0\.339                   | 1\.30                    |
| RMAT18\-16 \(R18\-16\)   | N            | 0\.26          | 4\.03       | 15\.39      | 0\.494                   | 1\.18                    |
| RMAT18\-32 \(R18\-32\)   | N            | 0\.26          | 7\.88       | 30\.06      | 0\.727                   | 1\.10                    |
| RMAT18\-64 \(R18\-64\)   | N            | 0\.26          | 15\.22      | 58\.07      | 1\.129                   | 1\.06                    |
| RMAT22\-16 \(R22\-16\)   | N            | 4\.19          | 65\.97      | 15\.73      | 6\.602                   | 1\.15                    |
| RMAT22\-32 \(R22\-32\)   | N            | 4\.19          | 130\.49     | 31\.11      | 10\.209                  | 1\.09                    |
| RMAT22\-64 \(R22\-64\)   | N            | 4\.19          | 256\.62     | 61\.18      | 17\.818                  | 1\.05                    |
| RMAT23\-64 \(R23\-64\)   | N            | 8\.39          | 517\.34     | 61\.67      | 35\.951                  | 1\.05                    |


